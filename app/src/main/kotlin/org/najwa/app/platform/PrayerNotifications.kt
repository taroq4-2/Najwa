package org.najwa.app.platform

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.graphics.Color
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.najwa.app.MainActivity
import org.najwa.app.R
import org.najwa.core.*
import java.time.Instant

enum class NotificationDelivery { POSTED, POSTED_HISTORY_UNAVAILABLE, BLOCKED, ALREADY_POSTED }

class PrayerNotifications(
    private val context: Context,
    private val clock: ()->Instant=Instant::now,
    private val post: (NotificationManager,Int,Notification)->Unit={manager,id,notification->manager.notify(id,notification)}
) {
    companion object {
        const val ADHAN="adhan_sound_v1"
        const val IQAMA="iqama_visual_v1"
        const val FRIDAY="friday_reminder_v1"
        /** Quiet card shown after the user stops the adhan sound; keeps the iqama countdown. */
        const val STATUS="prayer_status_v1"
        const val STOP_ADHAN="org.najwa.notification.STOP_ADHAN"
        private const val TEST_ID=499
    }
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val history=JsonFile(context,"notification-history.json")
    val sounds=AdhanSounds(context)
    val adhanChannel get()=sounds.selected().channelId
    fun cancelTest() {manager.cancel(TEST_ID)}
    fun hidePreviouslyPublicAlerts() {
        manager.activeNotifications.filter {it.notification.visibility==Notification.VISIBILITY_PUBLIC}
            .forEach {manager.cancel(it.tag,it.id)}
    }
    val granted get() = runCatching {(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled()}.getOrDefault(false)
    fun enabled(channel: String): Boolean {
        if(!granted)return false
        val current=manager.getNotificationChannel(if(channel==ADHAN)adhanChannel else channel) ?: return false
        return current.importance!=NotificationManager.IMPORTANCE_NONE
    }
    init { updateChannels(updateSelection=false) }
    fun updateChannels(updateSelection: Boolean=true) {
        val strings=context.prayerLanguage()
        // Re-creating an existing channel only updates its name and description;
        // user-chosen importance, sound and vibration are preserved by Android.
        manager.createNotificationChannel(NotificationChannel(ADHAN,strings.getString(R.string.system_adhan),NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(false)
            description=strings.getString(R.string.channel_adhan_description)
        })
        manager.createNotificationChannel(NotificationChannel(IQAMA,strings.getString(R.string.system_iqama),NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null,null);enableVibration(false)
            description=strings.getString(R.string.channel_iqama_description)
        })
        manager.createNotificationChannel(NotificationChannel(FRIDAY,strings.getString(R.string.system_friday_title),NotificationManager.IMPORTANCE_DEFAULT).apply {
            description=strings.getString(R.string.channel_friday_description)
        })
        manager.createNotificationChannel(NotificationChannel(STATUS,strings.getString(R.string.channel_status_name),NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null,null);enableVibration(false);setShowBadge(false)
            description=strings.getString(R.string.channel_status_description)
        })
        val selected=if(updateSelection)adhanChannel else ADHAN
        if(selected!=ADHAN) manager.getNotificationChannel(selected)?.let {channel->
            channel.name=strings.getString(R.string.system_adhan)
            channel.description=strings.getString(R.string.channel_adhan_description)
            manager.createNotificationChannel(channel)
        }
    }
    fun show(task: ScheduledPrayer,p: Preferences,test: Boolean=false): NotificationDelivery {
        val adhan=task.action==PrayerAction.ADHAN
        val channel=when(task.action) {
            PrayerAction.ADHAN -> adhanChannel
            PrayerAction.IQAMA -> IQAMA
            PrayerAction.FRIDAY -> FRIDAY
            PrayerAction.SILENCE -> return NotificationDelivery.BLOCKED
        }
        if(!enabled(channel))return NotificationDelivery.BLOCKED
        val ids=if(test)emptyList() else {
            val entries=try {history.read()?.optJSONArray("delivered")} catch(_: JSONException) {null}
            if(entries==null)emptyList() else (0 until entries.length()).mapNotNull {entries.opt(it) as? String}
        }
        if(task.id in ids)return NotificationDelivery.ALREADY_POSTED
        val now=clock()
        val notificationId=notificationId(task,test)
        val notification=build(task,p,test,channel,now,stoppable=adhan)
        try {
            post(manager,notificationId,notification)
        } catch(_: RuntimeException) {
            if(!enabled(channel))return NotificationDelivery.BLOCKED
            post(manager,notificationId,notification)
        }
        val historySaved=test || runCatching {
            history.write(JSONObject().put("delivered",JSONArray((ids+task.id).takeLast(128))))
        }.isSuccess
        if(task.action==PrayerAction.IQAMA && !test)runCatching {manager.cancel(300+task.event.prayer.ordinal*2)}
        return if(historySaved)NotificationDelivery.POSTED else NotificationDelivery.POSTED_HISTORY_UNAVAILABLE
    }

    /** "Stop adhan" action: cancelling the sounding notification ends its audio. The same
     * card is then re-posted on the quiet status channel so the iqama countdown stays. */
    fun stopAdhan(intent: Intent,p: Preferences) {
        val id=intent.getIntExtra("notification",-1)
        if(id<0)return
        manager.cancel(id)
        val prayer=runCatching {Prayer.valueOf(intent.getStringExtra("prayer") ?: "")}.getOrNull() ?: return
        val eventAt=intent.getLongExtra("eventAt",0L)
        val at=intent.getLongExtra("at",0L)
        if(eventAt<=0 || at<=0)return
        val test=intent.getBooleanExtra("test",false)
        val task=ScheduledPrayer(PrayerEvent(prayer,Instant.ofEpochMilli(eventAt)),PrayerAction.ADHAN,Instant.ofEpochMilli(at))
        val now=clock()
        if(iqamaDeadline(task,p)<=now.toEpochMilli() || !enabled(STATUS))return
        runCatching {post(manager,id,build(task,p,test,STATUS,now,stoppable=false))}
    }

    private fun notificationId(task: ScheduledPrayer,test: Boolean)=when {
        test -> TEST_ID
        task.action==PrayerAction.FRIDAY -> 490
        else -> 300+task.event.prayer.ordinal*2+if(task.action==PrayerAction.ADHAN)0 else 1
    }

    private fun build(task: ScheduledPrayer,p: Preferences,test: Boolean,channel: String,now: Instant,stoppable: Boolean): Notification {
        val adhan=task.action==PrayerAction.ADHAN
        val friday=task.action==PrayerAction.FRIDAY
        val strings=context.prayerLanguage()
        val prayer=strings.prayerName(task.event.prayer)
        val time=strings.prayerTime(task.event.time,p)
        val title=when {
            friday -> strings.getString(R.string.system_friday_title)
            test -> strings.getString(if(adhan)R.string.system_adhan_test else R.string.system_iqama_test)
            adhan -> strings.getString(R.string.system_adhan_title,prayer)
            else -> strings.getString(R.string.system_iqama_title,prayer)
        }
        val iqamaAt=Instant.ofEpochMilli(iqamaDeadline(task,p))
        val iqamaTime=strings.prayerTime(iqamaAt,p)
        val adhanLabel=strings.getString(R.string.system_adhan)
        val iqamaLabel=strings.getString(R.string.system_iqama)
        // Native template: fills the whole card, follows the phone's light/dark theme,
        // and the collapsed line always carries the essential information.
        val text=when {
            friday -> "${strings.prayerName(Prayer.DHUHR)} ≈ $time · ${p.city.name}"
            adhan -> "$adhanLabel $time · $iqamaLabel $iqamaTime"
            else -> "$iqamaLabel ${strings.prayerTime(task.at,p)} · ${p.city.name}"
        }
        val message=when {
            friday -> strings.getString(R.string.system_friday_detail)
            strings.usesArabic -> prayerMessage(task.event.prayer,adhan)
            else -> ""
        }
        val details=buildList {
            add(text)
            if(adhan)add(p.city.name)
            if(!friday && !adhan)add(strings.getString(R.string.system_iqama_detail))
            if(message.isNotBlank()) {add("");add(message)}
        }.joinToString("\n")
        val remaining=iqamaAt.toEpochMilli()-now.toEpochMilli()
        val countdown=adhan && remaining>0
        val open=PendingIntent.getActivity(context,201,Intent(context,MainActivity::class.java).putExtra("open_home",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val icon=if(adhan)R.drawable.ic_status_adhan else R.drawable.ic_status_iqama
        val publicVersion=NotificationCompat.Builder(context,channel)
            .setSmallIcon(icon).setContentTitle(strings.getString(R.string.system_brand)).setContentText(strings.getString(R.string.system_private_alert))
            .setShowWhen(false).build()
        val builder=NotificationCompat.Builder(context,channel).setSmallIcon(icon)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(details))
                .setColor(Color.parseColor(when {
                    adhan -> "#1F9E74"
                    friday -> "#9F7535"
                    else -> "#126D53"
                })).setSubText(when {
                    countdown -> strings.getString(R.string.system_to_iqama)
                    friday -> strings.getString(R.string.system_friday)
                    adhan -> adhanLabel
                    else -> iqamaLabel
                })
                .setContentIntent(open).setAutoCancel(true).setCategory(if(adhan)NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(if(p.showLockScreenDetails)NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setOnlyAlertOnce(true).setShowWhen(true)
                // Live countdown to iqama in the header while waiting; otherwise the alert time.
                .setWhen(if(countdown)iqamaAt.toEpochMilli() else task.at.toEpochMilli())
                .setUsesChronometer(countdown).setChronometerCountDown(countdown)
                .setTimeoutAfter(when {
                    friday -> (task.event.time.toEpochMilli()-now.toEpochMilli()).coerceAtLeast(60_000L)
                    adhan -> (iqamaDeadline(task,p)-now.toEpochMilli()).let {if(it>0)it else 60_000L}
                    else -> 15*60_000L
                })
        if(stoppable) {
            val id=notificationId(task,test)
            val stop=Intent(context,NotificationActionReceiver::class.java).setAction(STOP_ADHAN)
                .putExtra("notification",id).putExtra("prayer",task.event.prayer.name)
                .putExtra("eventAt",task.event.time.toEpochMilli()).putExtra("at",task.at.toEpochMilli()).putExtra("test",test)
            val pending=PendingIntent.getBroadcast(context,600+id,stop,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_status_adhan,strings.getString(R.string.notification_stop_adhan),pending)
        }
        return builder.build()
    }
}

private fun iqamaDeadline(task: ScheduledPrayer,p: Preferences) =
    task.event.time.toEpochMilli()+p.iqamaMinutes.getValue(task.event.prayer)*60_000L

/** Legacy custom card. Posted notifications now use the native template above;
 * kept for the existing layout/snapshot tests until those are migrated. */
fun notificationViews(context: Context,task: ScheduledPrayer,p: Preferences,title: String,detail: String,expanded: Boolean,now: Instant=task.at): RemoteViews {
    val adhan=task.action==PrayerAction.ADHAN
    val friday=task.action==PrayerAction.FRIDAY
    val strings=context.prayerLanguage()
    val brand=strings.getString(R.string.system_brand)
    val phase=strings.getString(when {
        friday->R.string.system_friday
        adhan->R.string.system_adhan
        else->R.string.system_iqama
    })
    return RemoteViews(context.packageName,if(expanded)R.layout.prayer_notification_expanded else R.layout.prayer_notification_compact).apply {
        setInt(R.id.notification_root,"setLayoutDirection",strings.prayerLayoutDirection)
        val primary=Color.parseColor(if(adhan)"#F6F4EC" else "#19352E")
        val secondary=Color.parseColor(when {
            adhan -> "#B8CBC0"
            friday -> "#66523A"
            else -> "#58756A"
        })
        val accent=Color.parseColor(when {
            adhan -> "#A5E8C4"
            friday -> "#865F2A"
            else -> "#126D53"
        })
        setInt(R.id.notification_root,"setBackgroundResource",when {
            adhan -> R.drawable.notification_surface_adhan
            friday -> R.drawable.notification_surface_friday
            else -> R.drawable.notification_surface_iqama
        })
        setImageViewResource(R.id.notification_icon,when {
            adhan -> R.drawable.ic_adhan
            friday -> R.drawable.ic_notification_friday
            else -> R.drawable.ic_iqama
        })
        setTextColor(R.id.notification_title,primary)
        setTextColor(R.id.notification_detail,secondary)
        setTextViewText(R.id.notification_title,title)
        setTextViewText(R.id.notification_detail,when {
            friday -> detail
            adhan -> {
                val iqamaTime=strings.prayerTime(Instant.ofEpochMilli(iqamaDeadline(task,p)),p)
                val schedule="${strings.getString(R.string.system_iqama)} $iqamaTime"
                if(expanded)"$schedule · ${p.city.name}" else schedule
            }
            !expanded -> strings.prayerTime(task.at,p)
            else -> "${p.city.name} · ${strings.getString(R.string.system_iqama_detail)}"
        })
        // Keep the live countdown visible even when Android collapses the card.
        // SystemUI owns expansion; a compact RemoteViews must stay within 48dp.
        val remaining=iqamaDeadline(task,p)-now.toEpochMilli()
        val showTimer=adhan && remaining>0
        setViewVisibility(R.id.notification_timer_box,if(showTimer)View.VISIBLE else View.GONE)
        setChronometerCountDown(R.id.notification_timer,true)
        setChronometer(R.id.notification_timer,SystemClock.elapsedRealtime()+remaining.coerceAtLeast(0L),null,showTimer)
        if(expanded) {
            setTextViewText(R.id.notification_brand,"$brand · $phase")
            setTextColor(R.id.notification_brand,accent)
            setTextColor(R.id.notification_time,secondary)
            setTextColor(R.id.notification_signature,secondary)
            setTextColor(R.id.notification_message,accent)
            setTextViewText(R.id.notification_signature,strings.getString(R.string.system_signature))
            setTextViewText(R.id.notification_timer_label,strings.getString(R.string.system_to_iqama))
            val rtl=strings.prayerLayoutDirection==View.LAYOUT_DIRECTION_RTL
            if(android.os.Build.VERSION.SDK_INT>=31) {
                setInt(R.id.notification_timer,"setGravity",android.view.Gravity.CENTER_VERTICAL or
                    if(rtl)android.view.Gravity.LEFT else android.view.Gravity.RIGHT)
            }
            setTextViewText(R.id.notification_message,when {
                friday -> strings.getString(R.string.system_friday_detail)
                !strings.usesArabic -> strings.getString(if(adhan)R.string.system_adhan_title else R.string.system_iqama_title,strings.prayerName(task.event.prayer))
                else -> prayerMessage(task.event.prayer,adhan)
            })
            setTextViewText(R.id.notification_time,strings.prayerTime(task.at,p))
            setViewVisibility(R.id.notification_message,View.VISIBLE)

        }
    }
}
