package com.mecnun.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Android 14+ mikrofonlu servisin telefon açılırken arka plandan başlatılmasına izin vermez.
 * Bu yüzden açılışta "Mecnun'u başlat" bildirimi gösteririz; dokununca servis başlar.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("boot", "Açılış hatırlatması", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START_SERVICE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, "boot")
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Mecnun kapalı")
            .setContentText("Dinlemeyi başlatmak için dokunun")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(2, n)
        } catch (_: SecurityException) {
        }
    }
}
