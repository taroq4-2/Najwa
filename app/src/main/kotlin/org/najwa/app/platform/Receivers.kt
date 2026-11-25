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
            Intent.ACTION_TIMEZONE_CHANGED -> runPrayerWork {
                val runtime=(context.applicationContext as NajwaApplication).runtime
                runtime.reconcile(refreshAlarms=true)
                PrayerWidget.updateAll(context)
                // Travel: the phone now keeps a different clock from the chosen city.
                val city=runtime.preferences.city
                val zone=java.util.TimeZone.getDefault().id
                val now=java.time.Instant.now()
                if(java.time.ZoneId.of(zone).rules.getOffset(now)!=city.zoneId.rules.getOffset(now))
                    runtime.notifications.showTravel(context,zone,city.name)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            "android.app.action.NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED" ->
                runPrayerWork {
                    val runtime=(context.applicationContext as NajwaApplication).runtime
                    runtime.reconcile(refreshAlarms=true)
                    PrayerWidget.updateAll(context)
                    runtime.checkHealthInBackground()
                }
        }
    }
}

/** Handles buttons on prayer notifications (currently "Stop adhan"). */
class NotificationActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val runtime=(context.applicationContext as NajwaApplication).runtime
        when(intent.action) {
            PrayerNotifications.STOP_ADHAN -> runPrayerWork { runtime.notifications.stopAdhan(intent,runtime.preferences) }
            PrayerNotifications.MARK_PRAYED -> runPrayerWork {
                val prayer=runCatching {org.najwa.core.Prayer.valueOf(intent.getStringExtra("prayer") ?: "")}.getOrNull() ?: return@runPrayerWork
                val at=java.time.Instant.ofEpochMilli(intent.getLongExtra("eventAt",System.currentTimeMillis()))
                runtime.setPrayed(at.atZone(runtime.preferences.city.zoneId).toLocalDate(),prayer,true)
                AdhanPlayer.stop(context)
                val manager=context.getSystemService(android.app.NotificationManager::class.java)
                intent.getIntExtra("notification",-1).takeIf {it>=0}?.let {manager.cancel(it)}
                manager.cancel(520+prayer.ordinal)
                PrayerWidget.updateAll(context)
            }
        }
    }
}
