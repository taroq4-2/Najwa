package org.najwa.app.platform

import android.app.*
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.najwa.app.MainActivity
import org.najwa.app.NajwaApplication
import org.najwa.app.R

/** Android 17 requires a foreground lifecycle for scheduled ringer changes. */
class PrayerExecutionService: Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("prayer_execution_v1","تنفيذ وضع الصلاة",NotificationManager.IMPORTANCE_LOW).apply {setSound(null,null)})
        val open=PendingIntent.getActivity(this,702,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(this,"prayer_execution_v1")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("نجوى · ضبط وضع الصلاة")
            .setContentText("تنفيذ موعد الصمت أو استعادة الرنين الذي اخترته")
            .setContentIntent(open).setOngoing(true).setSilent(true).build()
        val foreground=try {
            startForeground(702,notification)
            true
        } catch(_: IllegalStateException) {
            false
        } catch(_: SecurityException) {
            false
        }
        Handler(Looper.getMainLooper()).post {
            try {
                (application as NajwaApplication).runtime.receive(intent?.action,intent?.getStringExtra("id") ?: "",
                    intent?.getLongExtra("at",0) ?: 0,allowRinger=foreground)
            } finally { stopSelfResult(startId) }
        }
        return START_NOT_STICKY
    }
}
