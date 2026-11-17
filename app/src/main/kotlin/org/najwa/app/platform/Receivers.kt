package org.najwa.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.najwa.app.NajwaApplication
import org.najwa.app.prayerExecutor

internal fun BroadcastReceiver.runPrayerWork(block: ()->Unit) {
    val pending=goAsync()
    prayerExecutor.execute {
        try { block() } finally { pending.finish() }
    }
}

class AlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val runtime=(context.applicationContext as NajwaApplication).runtime
        if(android.os.Build.VERSION.SDK_INT>=37) {
            try {
                context.startForegroundService(Intent(context,PrayerExecutionService::class.java).setAction(intent.action).putExtras(intent))
            } catch(_: IllegalStateException) {
                runPrayerWork { runtime.receive(intent.action,intent.getStringExtra("id") ?: "",intent.getLongExtra("at",0),allowRinger=false) }
            } catch(_: SecurityException) {
                runPrayerWork { runtime.receive(intent.action,intent.getStringExtra("id") ?: "",intent.getLongExtra("at",0),allowRinger=false) }
            }
        } else runPrayerWork { runtime.receive(intent.action,intent.getStringExtra("id") ?: "",intent.getLongExtra("at",0)) }
    }
}
class SystemReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        when (intent.action) {
            Intent.ACTION_WALLPAPER_CHANGED -> runPrayerWork { PrayerWidget.updateAll(context) }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            "android.app.action.NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED" ->
                runPrayerWork {
                    (context.applicationContext as NajwaApplication).runtime.reconcile(refreshAlarms=true)
                    PrayerWidget.updateAll(context)
                }
        }
    }
}

/** Handles buttons on prayer notifications (currently "Stop adhan"). */
class NotificationActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action!=PrayerNotifications.STOP_ADHAN)return
        val runtime=(context.applicationContext as NajwaApplication).runtime
        runPrayerWork { runtime.notifications.stopAdhan(intent,runtime.preferences) }
    }
}
