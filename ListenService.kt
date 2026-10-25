package com.mecnun.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.util.Calendar

/**
 * Arka planda sürekli dinleyen servis.
 * Mikrofon -> Vosk (çevrimdışı Türkçe) -> Assistant. Ekran kapalıyken de çalışır.
 */
class ListenService : Service(), Host {

    companion object {
        const val ACTION_STOP = "com.mecnun.assistant.STOP"
        private const val CHANNEL = "listen"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ListenService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ListenService::class.java))
        }
    }

    private lateinit var assistant: Assistant
    private var model: Model? = null
    @Volatile private var running = false
    @Volatile private var destroyed = false
    @Volatile private var failedStart = false
    private var thread: Thread? = null
    @Volatile private var recorder: WavRecorder? = null
    @Volatile private var captureRate = 16000
    private var overlay: View? = null
    private var lastNotifText = ""
    private var lastWarnPct = -1

    // ---------------- yaşam döngüsü ----------------

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        History.init(this)
        Notes.init(this)
        assistant = Assistant.get(this)
        createChannel()
        if (!startFg("Başlatılıyor…")) {
            failedStart = true
            stopSelf()
            return
        }
        assistant.host = this
        registerReceivers()
        ensureOverlay()
        loadModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (failedStart || intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        running = false
        thread?.interrupt()
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder = null
        assistant.host = null
        try {
            unregisterReceiver(battReceiver)
            unregisterReceiver(pkgReceiver)
        } catch (_: Exception) {
        }
        try {
            overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        } catch (_: Exception) {
        }
        overlay = null
        if (!failedStart) {
            ServiceState.status.value = Status.STOPPED
            ServiceState.note.value = ""
        }
        super.onDestroy()
    }

    // ---------------- bildirim ----------------

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Mecnun dinliyor", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Mecnun")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Durdur", stop)
            .build()
    }

    private fun startFg(text: String): Boolean {
        return try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(text),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            lastNotifText = text
            true
        } catch (e: Exception) {
            ServiceState.status.value = Status.ERROR
            ServiceState.note.value = "Servis başlatılamadı. Mikrofon izni verildi mi? (${e.javaClass.simpleName})"
            false
        }
    }

    override fun setStatus(s: Status, text: String) {
        ServiceState.status.value = s
        ServiceState.note.value = text
        val shown = if (recorder != null && s == Status.LISTENING) "$text • kayıt yapılıyor" else text
        if (shown != lastNotifText) {
            lastNotifText = shown
            try {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIF_ID, buildNotification(shown))
            } catch (_: Exception) {
            }
        }
    }

    // ---------------- Host: ses kaydı ----------------

    override fun startRecording(): Boolean {
        if (recorder != null) return true
        val r = WavRecorder(this, captureRate, Prefs.recFolder)
        if (!r.start()) return false
        recorder = r
        lastNotifText = ""
        setStatus(Status.LISTENING, "Dinliyor")
        return true
    }

    override fun stopRecording(): Boolean {
        val r = recorder ?: return false
        recorder = null
        val ok = r.stop()
        lastNotifText = ""
        setStatus(Status.LISTENING, "Dinliyor")
        return ok
    }

    override fun isRecording(): Boolean = recorder != null

    // ---------------- model ve ses döngüsü ----------------

    private fun loadModel() {
        setStatus(Status.LOADING, "Türkçe model hazırlanıyor (ilk açılışta birkaç saniye sürer)…")
        StorageService.unpack(this, "model", "model",
            { m ->
                if (destroyed) {
                    m.close()
                } else {
                    model = m
                    running = true
                    thread = Thread({ audioLoop() }, "mecnun-audio").also { it.start() }
                }
            },
            { e ->
                setStatus(Status.ERROR, "Model yüklenemedi: ${e.message}")
            })
    }

    private fun desiredRate() = if (Prefs.recQuality == 1) 48000 else 16000

    private fun createRecord(rate: Int): AudioRecord? {
        for (r in listOf(rate, 16000).distinct()) {
            try {
                val min = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (min <= 0) continue
                val ar = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, r,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, r / 2 * 2)
                )
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    ar.startRecording()
                    captureRate = r
                    return ar
                }
                ar.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun releaseRecord(ar: AudioRecord?) {
        try {
            ar?.stop()
        } catch (_: Exception) {
        }
        try {
            ar?.release()
        } catch (_: Exception) {
        }
    }

    private fun isQuiet(): Boolean {
        if (!Prefs.quietEnabled) return false
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val a = Prefs.quietStart
        val b = Prefs.quietEnd
        return if (a == b) false else if (a < b) now in a until b else (now >= a || now < b)
    }

    private fun audioLoop() {
        val m = model ?: return
        val rec = Recognizer(m, 16000.0f)
        var ar: AudioRecord? = null
        var buf = ShortArray(1600)
        var out16 = ShortArray(1600)
        var wasSpeaking = false
        var ignoreUntil = 0L
        try {
            while (running) {
                // Gece modu: mikrofonu tamamen bırak
                if (isQuiet() && recorder == null) {
                    if (ar != null) {
                        releaseRecord(ar); ar = null; rec.reset()
                    }
                    setStatus(Status.QUIET, "Gece modu: dinlemiyor")
                    Thread.sleep(5000)
                    continue
                }
                if (ServiceState.status.value == Status.QUIET || ServiceState.status.value == Status.LOADING ||
                    ServiceState.status.value == Status.ERROR
                ) {
                    setStatus(Status.LISTENING, "Dinliyor")
                }

                val desired = desiredRate()
                if (ar != null && desired != captureRate && recorder == null) {
                    releaseRecord(ar); ar = null
                }
                if (ar == null) {
                    ar = createRecord(desired)
                    if (ar == null) {
                        setStatus(Status.ERROR, "Mikrofon açılamadı (izin verildi mi? Başka uygulama mikrofonu kullanıyor olabilir)")
                        Thread.sleep(3000)
                        continue
                    }
                    buf = ShortArray(captureRate / 10)
                    out16 = ShortArray(1600)
                    setStatus(Status.LISTENING, "Dinliyor")
                }

                val n = ar.read(buf, 0, buf.size)
                if (n <= 0) {
                    releaseRecord(ar); ar = null
                    Thread.sleep(500)
                    continue
                }
                recorder?.write(buf, n)

                // Konuşurken kendi sesini duymasın
                if (assistant.speaker.speaking) {
                    wasSpeaking = true
                    continue
                }
                val now = System.currentTimeMillis()
                if (wasSpeaking) {
                    wasSpeaking = false
                    rec.reset()
                    ignoreUntil = now + 350
                }
                if (now < ignoreUntil) continue

                val count: Int
                val src: ShortArray
                val factor = captureRate / 16000
                if (factor <= 1) {
                    src = buf
                    count = n
                } else {
                    val k = n / factor
                    for (i in 0 until k) {
                        var sum = 0
                        for (j in 0 until factor) sum += buf[i * factor + j]
                        out16[i] = (sum / factor).toShort()
                    }
                    src = out16
                    count = k
                }

                if (rec.acceptWaveForm(src, count)) {
                    val text = JSONObject(rec.result).optString("text")
                    if (text.isNotBlank()) assistant.submit(text)
                } else if (assistant.needsPartial) {
                    val p = JSONObject(rec.partialResult).optString("partial")
                    if (p.isNotBlank()) assistant.notePartial()
                }
                assistant.onAudioTick()
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            setStatus(Status.ERROR, "Dinleme hatası: ${e.javaClass.simpleName}")
        } finally {
            releaseRecord(ar)
            try {
                rec.close()
            } catch (_: Exception) {
            }
            try {
                m.close()
            } catch (_: Exception) {
            }
            model = null
        }
    }

    // ---------------- yan işler ----------------

    private val battReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Prefs.batteryWarn) return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val th = Prefs.batteryLevel
            if (charging || pct > th) {
                lastWarnPct = -1
                return
            }
            if (lastWarnPct == -1 || lastWarnPct - pct >= 5) {
                lastWarnPct = pct
                if (!isQuiet()) assistant.lowBattery(pct)
            }
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Directory.invalidateApps()
        }
    }

    private fun registerReceivers() {
        ContextCompat.registerReceiver(
            this, battReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED
        )
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)
    }

    /**
     * Android 10+ arka plandan uygulama/arama başlatmayı kısıtlar.
     * "Diğer uygulamaların üzerinde göster" izni varsa 1 piksellik görünmez bir pencere tutarız;
     * bu, uygulama açma ve arama komutlarının arka plandan çalışmasına yardımcı olur.
     */
    private fun ensureOverlay() {
        try {
            if (overlay != null || !Settings.canDrawOverlays(this)) return
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val v = View(this)
            val lp = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            wm.addView(v, lp)
            overlay = v
        } catch (_: Exception) {
        }
    }
}
