package com.mecnun.assistant

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ses kaydı. Dinleme akışıyla aynı mikrofondan beslenir, bu yüzden kayıt sırasında da komut dinlemeye devam eder.
 * Dosya: Müzik/<klasör>/Mecnun_tarih.wav (MediaStore, ek izin gerekmez).
 */
class WavRecorder(private val ctx: Context, private val sampleRate: Int, private val folder: String) {
    private var uri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null
    private var out: FileOutputStream? = null
    private var bytes = 0L
    var fileName: String = ""
        private set

    @Synchronized
    fun start(): Boolean {
        return try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            fileName = "Mecnun_$stamp.wav"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$folder")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val u = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val p = ctx.contentResolver.openFileDescriptor(u, "rw") ?: return false
            val o = FileOutputStream(p.fileDescriptor)
            o.write(header(0))
            uri = u; pfd = p; out = o; bytes = 0
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun write(buf: ShortArray, n: Int) {
        val o = out ?: return
        try {
            val b = ByteArray(n * 2)
            for (i in 0 until n) {
                b[2 * i] = (buf[i].toInt() and 0xff).toByte()
                b[2 * i + 1] = ((buf[i].toInt() shr 8) and 0xff).toByte()
            }
            o.write(b)
            bytes += b.size
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun stop(): Boolean {
        val o = out ?: return false
        return try {
            o.channel.position(0)
            o.write(header(bytes))
            o.flush()
            o.close()
            pfd?.close()
            val v = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            uri?.let { ctx.contentResolver.update(it, v, null, null) }
            true
        } catch (e: Exception) {
            false
        } finally {
            out = null; pfd = null; uri = null
        }
    }

    private fun header(dataLen: Long): ByteArray {
        val total = dataLen + 36
        val byteRate = sampleRate * 2
        val h = ByteArray(44)
        fun w32(off: Int, v: Long) {
            h[off] = (v and 0xff).toByte(); h[off + 1] = ((v shr 8) and 0xff).toByte()
            h[off + 2] = ((v shr 16) and 0xff).toByte(); h[off + 3] = ((v shr 24) and 0xff).toByte()
        }
        fun w16(off: Int, v: Int) {
            h[off] = (v and 0xff).toByte(); h[off + 1] = ((v shr 8) and 0xff).toByte()
        }
        "RIFF".toByteArray().copyInto(h, 0)
        w32(4, total)
        "WAVE".toByteArray().copyInto(h, 8)
        "fmt ".toByteArray().copyInto(h, 12)
        w32(16, 16)
        w16(20, 1)
        w16(22, 1)
        w32(24, sampleRate.toLong())
        w32(28, byteRate.toLong())
        w16(32, 2)
        w16(34, 16)
        "data".toByteArray().copyInto(h, 36)
        w32(40, dataLen)
        return h
    }
}
