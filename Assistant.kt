package com.mecnun.assistant

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

enum class Status { STOPPED, LOADING, LISTENING, AWAITING, QUIET, ERROR }

object ServiceState {
    val status = MutableStateFlow(Status.STOPPED)
    val note = MutableStateFlow("")
}

interface Host {
    fun startRecording(): Boolean
    fun stopRecording(): Boolean
    fun isRecording(): Boolean
    fun setStatus(s: Status, text: String)
}

data class Res(val ok: Boolean, val msg: String)

/**
 * Komut beyni: duyulan metni alır, uyandırma kelimesini bulur, komutu çözer, çalıştırır.
 * Çok adımlı akışlar (mesaj, not, onay) burada küçük bir durum makinesiyle yönetilir.
 * Durum sadece tek bir "worker" iş parçacığında değişir.
 */
class Assistant private constructor(private val ctx: Context) {

    companion object {
        @Volatile private var inst: Assistant? = null
        fun get(context: Context): Assistant =
            inst ?: synchronized(this) {
                inst ?: Assistant(context.applicationContext).also { inst = it }
            }

        private const val SAMSUNG_NOTES = "com.samsung.android.app.notes"
        private const val KEEP = "com.google.android.keep"
        private val yesWords = setOf("evet", "tamam", "olur", "gonder", "ara", "onayla", "dogru", "aynen", "peki", "okey", "tamamdir")
        private val noWords = setOf("hayir", "vazgec", "iptal", "yok", "dur", "yanlis", "olmaz", "vazgectim")
        private val finishWords = setOf("bitti", "bitir", "tamam", "kaydet", "gonder", "tamamdir")
    }

    @Volatile var host: Host? = null
    val speaker = Speaker(ctx)
    private val worker = Executors.newSingleThreadExecutor()
    private val tickQueued = AtomicBoolean(false)

    private enum class St { IDLE, AWAIT, DICT_NOTE, DICT_SMS, CONFIRM, LEARN }

    @Volatile private var st = St.IDLE
    private var stLast = 0L
    private var stTimeout = 8000L
    private val dictBuf = ArrayList<String>()
    private var pendingContact: ContactEntry? = null
    private var pendingYes: (() -> Unit)? = null
    private var learnApp: AppEntry? = null
    @Volatile private var lastPartialAt = 0L

    val needsPartial: Boolean get() = st != St.IDLE

    // ---- fener ----
    private val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchId: String? = null
    @Volatile private var torchOn = false

    init {
        try {
            torchId = cm.cameraIdList.firstOrNull {
                val c = cm.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) torchOn = enabled
                }
            }, null)
        } catch (_: Exception) {
        }
    }

    // ================= Dışarıya açılan kapılar =================

    /** Servis her final sonucunda bunu çağırır. */
    fun submit(raw: String) {
        worker.execute {
            try {
                onFinal(raw)
            } catch (e: Exception) {
                log(raw, "Hata", "İç hata: ${e.javaClass.simpleName}", false)
            }
        }
    }

    fun notePartial() {
        lastPartialAt = System.currentTimeMillis()
    }

    fun onAudioTick() {
        if (st == St.IDLE) return
        if (tickQueued.compareAndSet(false, true)) {
            worker.execute {
                tickQueued.set(false)
                try {
                    tick()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun startLearn(app: AppEntry) {
        worker.execute {
            endState()
            learnApp = app
            st = St.LEARN
            stLast = System.currentTimeMillis()
            stTimeout = 15000L
            host?.setStatus(Status.AWAITING, "${app.label} için adı söyleyin…")
            sayRaw("${app.label} uygulamasının adını söyleyin.", question = true)
        }
    }

    fun speakSample() {
        speaker.speak(Responses.pick("awake"))
    }

    fun lowBattery(level: Int) {
        worker.execute {
            val t = Responses.pick("battery_low", mapOf("n" to Tr.words(level)))
            speaker.speak(t)
            log("(pil)", "Şarj uyarısı", t, true)
        }
    }

    /** "Dene" ekranı için: sadece ne anlaşıldığını söyler, çalıştırmaz. */
    fun interpretOnly(text: String): String {
        val pairs = pairsOf(text)
        var toks = pairs.map { it.first }
        var words = pairs.map { it.second }
        val w = Wake.find(toks)
        if (w != null) {
            toks = toks.drop(w.first + w.second)
            words = words.drop(w.first + w.second)
        }
        if (toks.isEmpty()) return "Sadece uyandırma kelimesi: komut beklerdi"
        return Interpreter.parse(ctx, toks, words).label
    }

    // ================= Durum makinesi =================

    private fun pairsOf(raw: String): List<Pair<String, String>> =
        raw.trim().split(Regex("\\s+")).mapNotNull { w ->
            val n = Tr.norm(w).replace(" ", "")
            if (n.isEmpty()) null else n to w
        }

    private fun onFinal(raw: String) {
        val pairs = pairsOf(raw)
        if (pairs.isEmpty()) return
        val toks = pairs.map { it.first }
        val words = pairs.map { it.second }
        val wake = Wake.find(toks)
        val now = System.currentTimeMillis()

        when (st) {
            St.IDLE -> {
                if (wake == null) {
                    if (Prefs.logIgnored) log(raw, "Komut değil (uyanma kelimesi yok)", "yok sayıldı", true)
                    return
                }
                val cut = wake.first + wake.second
                val rt = toks.drop(cut)
                val rw = words.drop(cut)
                if (rt.isEmpty()) enterAwait() else runCommand(rt, rw, raw)
            }

            St.AWAIT -> {
                var rt = toks
                var rw = words
                if (wake != null && wake.first == 0) {
                    rt = toks.drop(wake.second)
                    rw = words.drop(wake.second)
                }
                if (rt.isEmpty()) {
                    stLast = now
                    return
                }
                runCommand(rt, rw, raw)
            }

            St.CONFIRM -> {
                var rt = toks
                if (wake != null && wake.first == 0) rt = toks.drop(wake.second)
                if (rt.any { it in noWords }) {
                    endState()
                    val t = say("cancelled")
                    log(raw, "Onay: hayır", t, true)
                } else if (rt.any { it in yesWords }) {
                    val a = pendingYes
                    endState()
                    a?.invoke()
                } else {
                    stLast = now
                }
            }

            St.DICT_NOTE, St.DICT_SMS -> {
                var rt = toks
                var rw = words
                if (wake != null && wake.first == 0) {
                    rt = toks.drop(wake.second)
                    rw = words.drop(wake.second)
                }
                if (rt.isEmpty()) return
                if (rt.size <= 3 && rt.any { it == "iptal" || it == "vazgec" }) {
                    endState()
                    val t = say("cancelled")
                    log(raw, "Yazdırma iptal", t, true)
                    return
                }
                var finish = false
                if (rt.last() in finishWords) {
                    finish = true
                    rw = rw.dropLast(1)
                }
                if (rw.isNotEmpty()) dictBuf.add(rw.joinToString(" "))
                stLast = now
                if (finish) finishDictation()
            }

            St.LEARN -> {
                var rt = toks
                if (wake != null && wake.first == 0) rt = toks.drop(wake.second)
                val app = learnApp
                if (rt.isEmpty() || app == null) return
                val alias = rt.joinToString(" ")
                endState()
                Prefs.addAlias(alias, app.pkg)
                val t = say("learned", mapOf("ad" to app.label, "metin" to alias))
                log(raw, "Uygulama adı öğretildi: ${app.label}", t, true)
            }
        }
    }

    private fun tick() {
        if (st == St.IDLE) return
        val now = System.currentTimeMillis()
        if (speaker.speaking || now - lastPartialAt < 800) stLast = now
        when (st) {
            St.DICT_NOTE, St.DICT_SMS -> {
                if (dictBuf.isNotEmpty()) {
                    if (now - stLast > 3500) finishDictation()
                } else if (now - stLast > 12000) {
                    endState()
                    val t = say("timeout")
                    log("(sessizlik)", "Yazdırma zaman aşımı", t, false)
                }
            }
            St.CONFIRM -> if (now - stLast > stTimeout) {
                endState()
                val t = say("timeout")
                log("(sessizlik)", "Onay zaman aşımı", t, false)
            }
            else -> if (now - stLast > stTimeout) endState()
        }
    }

    private fun endState() {
        st = St.IDLE
        dictBuf.clear()
        pendingContact = null
        pendingYes = null
        learnApp = null
        host?.setStatus(Status.LISTENING, "Dinliyor")
    }

    private fun enterAwait() {
        st = St.AWAIT
        stLast = System.currentTimeMillis()
        stTimeout = 8000L
        host?.setStatus(Status.AWAITING, "Dinliyorum…")
        val t = say("awake", question = true)
        log("mecnun", "Uyandı, komut bekliyor", t, true)
    }

    private fun askConfirm(key: String, vars: Map<String, String>, yes: () -> Unit): String {
        st = St.CONFIRM
        pendingYes = yes
        stLast = System.currentTimeMillis()
        stTimeout = 10000L
        host?.setStatus(Status.AWAITING, "Onay bekliyorum…")
        return say(key, vars, question = true)
    }

    private fun startDictation(kind: St) {
        st = kind
        dictBuf.clear()
        stLast = System.currentTimeMillis()
        host?.setStatus(Status.AWAITING, if (kind == St.DICT_NOTE) "Notu söyleyin…" else "Mesajı söyleyin…")
    }

    private fun finishDictation() {
        val kind = st
        val contact = pendingContact
        val text = formatDictation(dictBuf.joinToString(" "))
        endState()
        if (text.isBlank()) {
            val t = say("cancelled")
            log("(boş)", "Yazdırma iptal", t, false)
            return
        }
        if (kind == St.DICT_NOTE) {
            saveNote(text)
            val t = say("note_saved")
            log(text, "Not kaydedildi", t, true)
        } else if (contact != null) {
            if (Prefs.confirmCritical) {
                val t = askConfirm("sms_confirm", mapOf("metin" to text, "ad" to contact.name)) { sendSms(contact, text) }
                log(text, "Mesaj onayı bekleniyor: ${contact.name}", t, true)
            } else {
                sendSms(contact, text)
            }
        }
    }

    private fun formatDictation(s: String): String {
        val t = Tr.capitalize(s.trim())
        if (t.isEmpty()) return t
        return if (t.last() in ".!?") t else "$t."
    }

    // ================= Komutları çalıştır =================

    private fun runCommand(toks: List<String>, words: List<String>, raw: String) {
        endState()
        val cmd = Interpreter.parse(ctx, toks, words)
        val res = try {
            execute(cmd)
        } catch (e: SecurityException) {
            fail("need_permission")
        } catch (e: Exception) {
            fail("error")
        }
        log(raw, cmd.label, res.msg, res.ok)
    }

    private fun ok(key: String, vars: Map<String, String> = emptyMap()) = Res(true, say(key, vars))
    private fun fail(key: String, vars: Map<String, String> = emptyMap()) = Res(false, say(key, vars))

    private fun say(key: String, vars: Map<String, String> = emptyMap(), question: Boolean = false): String {
        val text = Responses.pick(key, vars)
        speakByMode(text, question)
        return text
    }

    private fun sayRaw(text: String, question: Boolean): String {
        speakByMode(text, question)
        return text
    }

    private fun speakByMode(text: String, question: Boolean) {
        val mode = Prefs.voiceMode
        if (mode == 2 || (mode == 1 && question)) speaker.speak(text)
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun launch(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    private fun execute(cmd: Cmd): Res {
        return when (cmd) {
            is Cmd.Lock -> {
                val s = MecnunAccessibilityService.instance ?: return fail("need_accessibility")
                val t = Responses.pick("lock")
                speakByMode(t, false)
                s.lockScreen()
                return Res(true, t)
            }

            is Cmd.Screenshot -> {
                val s = MecnunAccessibilityService.instance ?: return fail("need_accessibility")
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                val kg = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!pm.isInteractive || kg.isKeyguardLocked) return fail("screen_off")
                s.screenshot()
                return ok("screenshot")
            }

            is Cmd.Torch -> {
                val id = torchId ?: return fail("error")
                val on = cmd.on ?: !torchOn
                cm.setTorchMode(id, on)
                return ok(if (on) "torch_on" else "torch_off")
            }

            is Cmd.Volume -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val step = (max / 6).coerceAtLeast(1)
                return when (cmd.kind) {
                    VolKind.DOWN -> {
                        repeat(step) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0) }
                        ok("volume_down")
                    }
                    VolKind.UP -> {
                        repeat(step) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0) }
                        ok("volume_up")
                    }
                    VolKind.MUTE -> {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        ok("mute")
                    }
                    VolKind.SET -> {
                        val p = cmd.percent ?: 50
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (p * max / 100.0).roundToInt(), 0)
                        ok("volume_set", mapOf("n" to Tr.words(p)))
                    }
                }
            }

            is Cmd.Time -> {
                val c = Calendar.getInstance()
                val s = Tr.clockWords(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                return ok("time", mapOf("saat" to s))
            }

            is Cmd.Battery -> {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                return ok(if (bm.isCharging) "battery_charging" else "battery", mapOf("n" to Tr.words(level)))
            }

            is Cmd.Media -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val code = when (cmd.act) {
                    MediaAct.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    MediaAct.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
                    MediaAct.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    MediaAct.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                }
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                return ok("media")
            }

            is Cmd.OpenApp -> {
                val app = cmd.app ?: return fail("app_not_found", mapOf("ad" to cmd.heard))
                val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return fail("app_not_found", mapOf("ad" to cmd.heard))
                val okLaunch = launch(i)
                return if (okLaunch) ok("app_open", mapOf("ad" to app.label)) else fail("error")
            }

            is Cmd.RecStart -> {
                val h = host ?: return fail("error")
                if (!granted(Manifest.permission.RECORD_AUDIO)) return fail("need_permission")
                if (h.isRecording()) return ok("rec_already")
                return if (h.startRecording()) ok("rec_start") else fail("error")
            }

            is Cmd.RecStop -> {
                val h = host ?: return fail("error")
                if (!h.isRecording()) return fail("rec_none")
                return if (h.stopRecording()) ok("rec_stop") else fail("error")
            }

            is Cmd.Call -> {
                if (!granted(Manifest.permission.CALL_PHONE)) return fail("need_permission")
                val number = cmd.contact?.number ?: cmd.number ?: return fail("contact_not_found")
                val name = cmd.contact?.name ?: cmd.number ?: ""
                if (Prefs.confirmCritical) {
                    val t = askConfirm("call_confirm", mapOf("ad" to name)) { doCall(number, name) }
                    return Res(true, t)
                }
                return doCall(number, name)
            }

            is Cmd.Sms -> {
                if (!granted(Manifest.permission.SEND_SMS)) return fail("need_permission")
                val c = cmd.contact ?: return fail("contact_not_found")
                startDictation(St.DICT_SMS)
                pendingContact = c
                val t = say("sms_ask", mapOf("ad" to c.name), question = true)
                return Res(true, t)
            }

            is Cmd.NoteNew -> {
                val txt = cmd.text
                if (!txt.isNullOrBlank()) {
                    val f = formatDictation(txt)
                    saveNote(f)
                    return ok("note_saved")
                }
                startDictation(St.DICT_NOTE)
                return Res(true, say("note_ask", question = true))
            }

            is Cmd.NoteRead -> {
                val list = Notes.items.value
                if (list.isEmpty()) return fail("note_none")
                val take = if (cmd.all) list.take(3).reversed() else list.take(1)
                val text = take.joinToString(" ") { it.text }
                speaker.speak(text)
                return Res(true, text)
            }

            is Cmd.Alarm -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, cmd.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, cmd.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "Mecnun")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                return if (launch(i)) ok("alarm_set", mapOf("saat" to Tr.clockWords(cmd.hour, cmd.minute))) else fail("error")
            }

            is Cmd.AlarmFail -> return fail("unknown")
            is Cmd.Unknown -> return fail("unknown")
        }
    }

    private fun doCall(number: String, name: String): Res {
        val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
        val t = Responses.pick("call", mapOf("ad" to name))
        return if (launch(i)) {
            speakByMode(t, false)
            Res(true, t)
        } else fail("error")
    }

    private fun sendSms(c: ContactEntry, text: String) {
        val res = try {
            val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(c.number, null, parts, null, null)
            ok("sms_sent")
        } catch (e: Exception) {
            fail("error")
        }
        log(text, "Mesaj gönder: ${c.name}", res.msg, res.ok)
    }

    // ================= Not alma =================

    private fun saveNote(text: String) {
        Notes.add(text)
        sendToNoteApp(text)
    }

    private fun installed(pkg: String) = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun sendToNoteApp(text: String): Boolean {
        val mode = Prefs.noteApp
        if (mode == 3) return true
        fun mk(pkg: String?): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (pkg != null) setPackage(pkg)
        }
        val order = when (mode) {
            1 -> listOf(SAMSUNG_NOTES)
            2 -> listOf(KEEP)
            else -> listOf(SAMSUNG_NOTES, KEEP)
        }
        for (p in order) if (installed(p) && launch(mk(p))) return true
        if (mode == 0) return launch(Intent.createChooser(mk(null), "Notu kaydet"))
        return false
    }

    private fun log(heard: String, understood: String, result: String, ok: Boolean) {
        History.add(HistoryItem(System.currentTimeMillis(), heard, understood, result, ok))
    }
}
