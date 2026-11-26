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
                val phone=java.time.ZoneId.of(zone).rules.getOffset(now)
                val home=TravelHome.read(context)
                if(phone!=city.zoneId.rules.getOffset(now)) {
                    // Leaving: remember the home city (once) and say so.
                    if(home==null)TravelHome.save(context,city)
                    runtime.notifications.showTravel(context,zone,city.name)
                } else if(home!=null && home!=city && phone==home.zoneId.rules.getOffset(now)) {
                    runtime.notifications.showHomeAgain(context,home.name)
                } else if(home==city) TravelHome.clear(context)
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
            PrayerNotifications.SNOOZE_WIRD -> runtime.notifications.snoozeWird()
            PrayerNotifications.RETURN_HOME -> runPrayerWork {
                TravelHome.read(context)?.let {home->runtime.update(runtime.preferences.copy(city=home));TravelHome.clear(context)}
                PrayerWidget.updateAll(context)
            }
            PrayerNotifications.WIRD_AGAIN -> runPrayerWork {runtime.notifications.showWird(runtime.preferences,again=true)}
            PrayerNotifications.ENABLE_RAMADAN -> runPrayerWork {runtime.notifications.enableRamadan(runtime)}
            PrayerNotifications.MARK_PRAYED -> runPrayerWork {
                val prayer=runCatching {org.najwa.core.Prayer.valueOf(intent.getStringExtra("prayer") ?: "")}.getOrNull() ?: return@runPrayerWork
                val at=java.time.Instant.ofEpochMilli(intent.getLongExtra("eventAt",System.currentTimeMillis()))
                val date=at.atZone(runtime.preferences.city.zoneId).toLocalDate()
                // "Prayed in congregation" from the after-prayer card; otherwise simply prayed.
                val status=runCatching {org.najwa.core.PrayerStatus.valueOf(intent.getStringExtra("status") ?: "")}.getOrNull()
                if(status!=null)runtime.setStatus(date,prayer,status) else runtime.setPrayed(date,prayer,true)
                AdhanPlayer.stop(context)
                val manager=context.getSystemService(android.app.NotificationManager::class.java)
                intent.getIntExtra("notification",-1).takeIf {it>=0}?.let {manager.cancel(it)}
                manager.cancel(520+prayer.ordinal);manager.cancel(LOG_CHECK_ID+prayer.ordinal);manager.cancel(AFTER_PRAYER_ID+prayer.ordinal)
                DayWidget.update(context)
                PrayerWidget.updateAll(context)
            }
        }
    }
}
