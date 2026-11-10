package org.najwa.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.najwa.app.NajwaApplication

class AlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val runtime=(context.applicationContext as NajwaApplication).runtime
        if(android.os.Build.VERSION.SDK_INT>=37) {
            try {
                context.startForegroundService(Intent(context,PrayerExecutionService::class.java).setAction(intent.action).putExtras(intent))
            } catch(_: IllegalStateException) {
                runtime.receive(intent.action,intent.getStringExtra("id") ?: "",intent.getLongExtra("at",0),allowRinger=false)
            } catch(_: SecurityException) {
                runtime.receive(intent.action,intent.getStringExtra("id") ?: "",intent.getLongExtra("at",0),allowRinger=false)
            }
        } else runtime.receive(intent.action,intent.getStringExtra("id") ?: "",intent.getLongExtra("at",0))
    }
}
class SystemReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        when (intent.action) {
            Intent.ACTION_WALLPAPER_CHANGED -> PrayerWidget.updateAll(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            "android.app.action.NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED" ->
                { (context.applicationContext as NajwaApplication).runtime.reconcile();PrayerWidget.updateAll(context) }
        }
    }
}
