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
        const val PRE_ADHAN="pre_adhan_v1"
        /** Gentle reminders: Ramadan, sunnah, adhkar. */
        const val REMINDERS="reminders_v1"
        /** Ongoing iqama countdown, promoted to Live Updates / Samsung Now Bar on Android 16+. */
        const val LIVE="iqama_live_v1"
        /** Silent heads-up card while Najwa plays the adhan itself (enhanced playback). */
        const val ADHAN_VISUAL="adhan_visual_v1"
        const val MARK_PRAYED="org.najwa.notification.MARK_PRAYED"
        const val SNOOZE_WIRD="org.najwa.notification.SNOOZE_WIRD"
        const val WIRD_AGAIN="org.najwa.notification.WIRD_AGAIN"
        const val ENABLE_RAMADAN="org.najwa.notification.ENABLE_RAMADAN"
        const val RETURN_HOME="org.najwa.notification.RETURN_HOME"
        const val HEALTH_ID=560
        private const val TRAVEL_ID=470
        const val STOP_ADHAN="org.najwa.notification.STOP_ADHAN"
        private const val TEST_ID=499
    }
    private val manager=context.getSystemService(NotificationManager::class.java)
    internal val appContext: Context get()=context
    private val history=JsonFile(context,"notification-history.json")
    val sounds=AdhanSounds(context)
    val fajrSounds=AdhanSounds(context,slot="fajr")
    val adhanChannel get()=sounds.selected().channelId
    /** Fajr has its own channel once a separate Fajr adhan is chosen. */
    fun adhanChannelFor(prayer: Prayer): String = if(prayer==Prayer.FAJR && fajrSounds.hasOwn()) fajrSounds.selected().channelId else adhanChannel
    /** The sound the selected adhan channel plays, for Najwa's own player. */
    fun adhanSoundUri(prayer: Prayer): android.net.Uri {
        val choice=if(prayer==Prayer.FAJR && fajrSounds.hasOwn()) fajrSounds.selected() else sounds.selected()
        return choice.uri?.let {android.net.Uri.parse(it)}
            ?: manager.getNotificationChannel(choice.channelId)?.sound
            ?: AdhanSounds.builtInUri(context,2)
    }
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
            // New installs start with the full bundled adhan; Android keeps an existing
            // channel's sound, so people upgrading keep what they already hear.
            setSound(AdhanSounds.builtInUri(context,2),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
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
        manager.createNotificationChannel(NotificationChannel(PRE_ADHAN,strings.getString(R.string.channel_pre_adhan_name),NotificationManager.IMPORTANCE_DEFAULT).apply {
            description=strings.getString(R.string.channel_pre_adhan_description)
        })
        manager.createNotificationChannel(NotificationChannel(REMINDERS,strings.getString(R.string.channel_reminders_name),NotificationManager.IMPORTANCE_DEFAULT).apply {
            description=strings.getString(R.string.channel_reminders_description)
        })
        // One channel per kind of reminder, so each can be silenced on its own.
        listOf(ReminderChannels.ADHKAR to R.string.channel_adhkar_name,ReminderChannels.QURAN to R.string.channel_quran_name,
            ReminderChannels.SEASON to R.string.channel_season_name,ReminderChannels.SUNNAH to R.string.channel_sunnah_name,
            ReminderChannels.PRAYER to R.string.channel_prayer_cards_name).forEach {(id,name)->
            manager.createNotificationChannel(NotificationChannel(id,strings.getString(name),NotificationManager.IMPORTANCE_DEFAULT).apply {
                description=strings.getString(R.string.channel_reminders_description)
            })
        }
        manager.createNotificationChannel(NotificationChannel(LIVE,strings.getString(R.string.channel_live_name),NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null,null);enableVibration(false);setShowBadge(false)
            description=strings.getString(R.string.channel_live_description)
        })
        manager.createNotificationChannel(NotificationChannel(ADHAN_VISUAL,strings.getString(R.string.channel_adhan_visual_name),NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null,null);enableVibration(false)
            description=strings.getString(R.string.channel_adhan_visual_description)
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
            PrayerAction.ADHAN -> adhanChannelFor(task.event.prayer)
            PrayerAction.IQAMA -> IQAMA
            PrayerAction.FRIDAY -> FRIDAY
            PrayerAction.PRE_ADHAN -> PRE_ADHAN
            PrayerAction.LIVE -> return showLive(task,p)
            PrayerAction.REMINDER -> return showReminder(task,p)
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
        // Enhanced playback: a silent heads-up card while Najwa's own player sounds the adhan.
        // If the player cannot start (background limits), the channel's sound is used as before.
        val played=adhan && p.enhancedAdhan && enabled(ADHAN_VISUAL) && runCatching {
            AdhanPlayer.start(context,adhanSoundUri(task.event.prayer),notificationId,build(task,p,test,ADHAN_VISUAL,now,stoppable=true),p,
                spokenAnnouncement(task,p))
        }.getOrDefault(false)
        if(!played) {
            val notification=build(task,p,test,channel,now,stoppable=adhan)
            try {
                post(manager,notificationId,notification)
            } catch(_: RuntimeException) {
                if(!enabled(channel))return NotificationDelivery.BLOCKED
                post(manager,notificationId,notification)
            }
        }
        if(adhan && p.liveIqama && p.showIqamaCountdown)runCatching {showLive(task,p,test)}
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
        AdhanPlayer.stop(context)
        manager.cancel(id)
        // The live countdown card already shows the time left until iqama.
        if(p.liveIqama && p.showIqamaCountdown && manager.activeNotifications.any {it.notification.channelId==LIVE})return
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
        task.action==PrayerAction.PRE_ADHAN -> 480+task.event.prayer.ordinal
        task.action==PrayerAction.LIVE -> 520+task.event.prayer.ordinal
        task.action==PrayerAction.REMINDER -> 540+(task.reminder?.ordinal ?: 0)
        else -> 300+task.event.prayer.ordinal*2+if(task.action==PrayerAction.ADHAN)0 else 1
    }

    private fun build(task: ScheduledPrayer,p: Preferences,test: Boolean,channel: String,now: Instant,stoppable: Boolean): Notification {
        val adhan=task.action==PrayerAction.ADHAN
        val friday=task.action==PrayerAction.FRIDAY
        val before=task.action==PrayerAction.PRE_ADHAN
        val strings=context.prayerLanguage()
        val prayer=strings.prayerName(task.event,p)
        val time=strings.prayerTime(task.event.time,p)
        val minutesLeft=((task.event.time.toEpochMilli()-task.at.toEpochMilli())/60_000L).toInt().coerceAtLeast(1)
        val title=when {
            friday -> strings.getString(R.string.system_friday_title)
            before -> strings.resources.getQuantityString(R.plurals.pre_adhan_title,minutesLeft,minutesLeft,prayer)
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
            friday -> "${strings.getString(R.string.prayer_jumuah)} ≈ $time · ${p.city.name}"
            before -> "$adhanLabel $time · ${p.city.name}"
            adhan -> "$adhanLabel $time · $iqamaLabel $iqamaTime"
            else -> "$iqamaLabel ${strings.prayerTime(task.at,p)} · ${p.city.name}"
        }
        val message=when {
            friday -> strings.getString(R.string.system_friday_detail)
            before -> ""
            strings.usesArabic -> prayerMessage(task.event.prayer,adhan)
            else -> ""
        }
        val details=buildList {
            add(text)
            if(adhan)add(p.city.name)
            if(!friday && !adhan && !before)add(strings.getString(R.string.system_iqama_detail))
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
                    adhan || before -> "#1F9E74"
                    friday -> "#9F7535"
                    else -> "#126D53"
                })).setSubText(when {
                    countdown -> strings.getString(R.string.system_to_iqama)
                    friday -> strings.getString(R.string.system_friday)
                    before -> strings.getString(R.string.system_to_adhan)
                    adhan -> adhanLabel
                    else -> iqamaLabel
                })
                .setContentIntent(open).setAutoCancel(true).setCategory(if(adhan)NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(if(p.showLockScreenDetails)NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setOnlyAlertOnce(true).setShowWhen(true)
                // Live countdown to iqama in the header while waiting; otherwise the alert time.
                .setWhen(when {countdown->iqamaAt.toEpochMilli();before->task.event.time.toEpochMilli();else->task.at.toEpochMilli()})
                .setUsesChronometer(countdown || before).setChronometerCountDown(countdown || before)
                .setTimeoutAfter(when {
                    friday || before -> (task.event.time.toEpochMilli()-now.toEpochMilli()).coerceAtLeast(60_000L)
                    adhan -> (iqamaDeadline(task,p)-now.toEpochMilli()).let {if(it>0)it else 60_000L}
                    else -> 15*60_000L
                })
        // "I prayed": marks the prayer in the private prayer log.
        if(p.prayerLog && !test && !friday && !before && (adhan || task.action==PrayerAction.IQAMA))builder.addAction(prayedAction(task,notificationId(task,test)))
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

/** Ongoing countdown to iqama. On Android 16+ it asks to be promoted to a Live Update, which
 * One UI shows in the Now Bar and on the lock screen (like the iPhone's Dynamic Island). */
fun PrayerNotifications.showLive(task: ScheduledPrayer,p: Preferences,test: Boolean=false): NotificationDelivery {
    val context=appContext
    if(!enabled(PrayerNotifications.LIVE))return NotificationDelivery.BLOCKED
    val now=Instant.now()
    val iqamaAt=task.event.time.toEpochMilli()+p.iqamaMinutes.getValue(task.event.prayer)*60_000L
    if(iqamaAt<=now.toEpochMilli())return NotificationDelivery.BLOCKED
    val strings=context.prayerLanguage()
    val prayer=strings.prayerName(task.event,p)
    val open=PendingIntent.getActivity(context,205,Intent(context,MainActivity::class.java).putExtra("open_home",true)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder=NotificationCompat.Builder(context,PrayerNotifications.LIVE).setSmallIcon(R.drawable.ic_status_iqama)
        .setContentTitle(strings.getString(R.string.live_title,prayer))
        .setContentText(strings.getString(R.string.live_text,strings.prayerTime(Instant.ofEpochMilli(iqamaAt),p),p.city.name))
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setWhen(iqamaAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        .setTimeoutAfter(iqamaAt-now.toEpochMilli()).setContentIntent(open)
        .setVisibility(if(p.showLockScreenDetails)NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
    if(p.prayerLog && !test)builder.addAction(prayedAction(task,520+task.event.prayer.ordinal))
    var notification=builder.build()
    if(android.os.Build.VERSION.SDK_INT>=36)notification=runCatching {
        val b=Notification.Builder.recoverBuilder(context,notification)
        // Android 16 Live Updates API, called reflectively so older SDK stubs still compile.
        runCatching {b.javaClass.getMethod("setRequestPromotedOngoing",Boolean::class.javaPrimitiveType).invoke(b,true)}
        runCatching {b.javaClass.getMethod("setShortCriticalText",CharSequence::class.java).invoke(b,prayer)}
        b.build()
    }.getOrDefault(notification)
    return runCatching {
        context.getSystemService(NotificationManager::class.java).notify(if(test)498 else 520+task.event.prayer.ordinal,notification)
        NotificationDelivery.POSTED
    }.getOrDefault(NotificationDelivery.BLOCKED)
}

/** Reminder channels, one per kind, so each can be silenced on its own in the system settings. */
object ReminderChannels {
    const val ADHKAR="reminders_adhkar_v1"
    const val QURAN="reminders_quran_v1"
    const val SEASON="reminders_season_v1"
    const val SUNNAH="reminders_sunnah_v1"
    const val PRAYER="reminders_prayer_v1"
    const val GROUP="org.najwa.reminders"
    const val SUMMARY_ID=599
    fun of(r: Reminder)=when(r) {
        Reminder.MORNING_ADHKAR,Reminder.EVENING_ADHKAR,Reminder.SLEEP_ADHKAR -> ADHKAR
        Reminder.WIRD,Reminder.KAHF -> QURAN
        Reminder.SUHOOR,Reminder.TARAWEEH,Reminder.FASTING,Reminder.RAMADAN -> SEASON
        Reminder.SUNRISE,Reminder.DUHA,Reminder.QIYAM -> SUNNAH
        Reminder.LOG_CHECK,Reminder.AFTER_PRAYER,Reminder.WEEKLY -> PRAYER
    }
}

/** Posts a reminder in the reminders bundle: two or more at the same time arrive as one group. */
private fun PrayerNotifications.postReminder(id: Int,builder: NotificationCompat.Builder): NotificationDelivery {
    val context=appContext
    builder.setGroup(ReminderChannels.GROUP)
    return runCatching {
        val nm=context.getSystemService(NotificationManager::class.java)
        nm.notify(id,builder.build())
        val active=nm.activeNotifications.count {it.notification.group==ReminderChannels.GROUP && it.id!=ReminderChannels.SUMMARY_ID}
        if(active>=2) nm.notify(ReminderChannels.SUMMARY_ID,NotificationCompat.Builder(context,PrayerNotifications.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification).setColor(Color.parseColor("#9F7535"))
            .setContentTitle(context.prayerLanguage().getString(R.string.reminder_bundle_title))
            .setGroup(ReminderChannels.GROUP).setGroupSummary(true).setAutoCancel(true).build())
        NotificationDelivery.POSTED
    }.getOrDefault(NotificationDelivery.BLOCKED)
}

private fun PrayerNotifications.openApp(code: Int,action: String?,extras: Intent.()->Unit={})=PendingIntent.getActivity(appContext,code,
    Intent(appContext,MainActivity::class.java).apply {if(action!=null)setAction(action) else putExtra("open_home",true);extras()}
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

private fun PrayerNotifications.reminderBuilder(channel: String,title: String,text: String,open: PendingIntent,p: Preferences)=
    NotificationCompat.Builder(appContext,channel).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setColor(Color.parseColor("#9F7535")).setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(3*3600_000L)
        .setVisibility(if(p.showLockScreenDetails)NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)

/** Ramadan, sunnah, adhkar, Quran and prayer reminders, each on its own channel and checked against the day's records. */
fun PrayerNotifications.showReminder(task: ScheduledPrayer,p: Preferences): NotificationDelivery {
    val context=appContext
    val reminder=task.reminder ?: return NotificationDelivery.BLOCKED
    if(!enabled(ReminderChannels.of(reminder)))return NotificationDelivery.BLOCKED
    val strings=context.prayerLanguage()
    val zone=p.city.zoneId
    when(reminder) {
        Reminder.LOG_CHECK -> return showLogCheck(task,p)
        Reminder.AFTER_PRAYER -> return showAfterPrayer(task,p)
        Reminder.WIRD -> return showWird(p)
        Reminder.RAMADAN -> return showRamadan(task,p)
        Reminder.WEEKLY -> return showWeekly(p)
        else -> {}
    }
    // Adhkar reminders ask the day's records first: nothing to remind once a routine is done.
    val adhkarKey=when(reminder) {Reminder.MORNING_ADHKAR->"morning";Reminder.EVENING_ADHKAR->"evening";Reminder.SLEEP_ADHKAR->"sleep";else->null}
    val adhkarSize=when(reminder) {
        Reminder.MORNING_ADHKAR->org.najwa.app.ui.Adhkar.morning.size
        Reminder.EVENING_ADHKAR->org.najwa.app.ui.Adhkar.evening.size
        Reminder.SLEEP_ADHKAR->org.najwa.app.ui.Adhkar.sleep.size
        else->0
    }
    val adhkarLeft=adhkarKey?.let {AdhkarProgressStore.remaining(context,it,adhkarSize)?.count {n->n>0} ?: adhkarSize} ?: 0
    if(adhkarKey!=null && adhkarLeft==0)return NotificationDelivery.ALREADY_POSTED
    // Al-Kahf already read today (all its pages confirmed): no reminder.
    if(reminder==Reminder.KAHF && (KAHF_PAGE..KAHF_LAST_PAGE).all {it in QuranStore.readToday(context)})return NotificationDelivery.ALREADY_POSTED
    fun adhkarText()=if(adhkarLeft<adhkarSize) strings.getString(R.string.next_left,Counts.adhkar(context,adhkarLeft)) else strings.getString(R.string.reminder_adhkar_text)
    val (title,text)=when(reminder) {
        Reminder.SUHOOR -> strings.getString(R.string.reminder_suhoor_title) to strings.getString(R.string.reminder_suhoor_text,p.suhoorMinutes)
        Reminder.TARAWEEH -> strings.getString(R.string.reminder_taraweeh_title) to strings.getString(R.string.reminder_taraweeh_text)
        Reminder.SUNRISE -> strings.getString(R.string.reminder_sunrise_title,p.sunriseLeadMinutes) to strings.getString(R.string.reminder_sunrise_text)
        Reminder.DUHA -> strings.getString(R.string.reminder_duha_title) to strings.getString(R.string.reminder_duha_text)
        Reminder.QIYAM -> strings.getString(R.string.reminder_qiyam_title) to strings.getString(R.string.reminder_qiyam_text)
        Reminder.MORNING_ADHKAR -> strings.getString(R.string.reminder_morning_title) to adhkarText()
        Reminder.EVENING_ADHKAR -> strings.getString(R.string.reminder_evening_title) to adhkarText()
        Reminder.SLEEP_ADHKAR -> strings.getString(R.string.sleep_adhkar_title) to adhkarText()
        Reminder.KAHF -> strings.getString(R.string.reminder_kahf_title) to strings.getString(R.string.reminder_kahf_text)
        Reminder.FASTING -> {
            val tomorrow=task.event.time.atZone(zone).toLocalDate().plusDays(1)
            val reason=fastingReason(tomorrow,p) ?: return NotificationDelivery.BLOCKED
            strings.getString(R.string.reminder_fasting_title) to strings.getString(when(reason) {
                FastReason.ARAFAH -> R.string.fast_arafah
                FastReason.TASUA -> R.string.fast_tasua
                FastReason.ASHURA -> R.string.fast_ashura
                FastReason.WHITE_DAYS -> R.string.fast_white_days
                FastReason.MONDAY -> R.string.fast_monday
                FastReason.THURSDAY -> R.string.fast_thursday
            })
        }
        else -> return NotificationDelivery.BLOCKED
    }
    // Every reminder opens its own place: the adhkar tab, or the mushaf at Al-Kahf.
    val open=when(reminder) {
        Reminder.MORNING_ADHKAR,Reminder.EVENING_ADHKAR,Reminder.SLEEP_ADHKAR -> openApp(206+reminder.ordinal,ACTION_ADHKAR) {
            putExtra("adhkar",when(reminder) {Reminder.EVENING_ADHKAR->1;Reminder.SLEEP_ADHKAR->3;else->0})}
        Reminder.KAHF -> openApp(206+reminder.ordinal,ACTION_QURAN) {putExtra("page",KAHF_PAGE)}
        else -> openApp(206+reminder.ordinal,null)
    }
    return postReminder(540+reminder.ordinal,reminderBuilder(ReminderChannels.of(reminder),title,text,open,p))
}

/** First and last pages of Surat Al-Kahf in the Madinah mushaf. */
const val KAHF_PAGE=293
const val KAHF_LAST_PAGE=304

/**
 * The day's Quran portion, while pages remain: what is left and when the khatma ends, a snooze of one hour, and
 * gentler words after three days without reading.
 */
fun PrayerNotifications.showWird(p: Preferences,again: Boolean=false): NotificationDelivery {
    val context=appContext
    if(!enabled(ReminderChannels.QURAN))return NotificationDelivery.BLOCKED
    val wird=QuranStore.wird(context)
    if(wird.done)return NotificationDelivery.ALREADY_POSTED
    val strings=context.prayerLanguage()
    val today=WorshipDay.today(context)
    val days=QuranStore.days(context)
    val quiet=(1L..3L).all {(days[today.minusDays(it)] ?: 0)==0}
    val text=if(quiet) strings.getString(R.string.reminder_wird_gentle)
        else strings.getString(R.string.reminder_wird_text,Counts.pages(context,wird.left),
            QuranStore.finishDate(context).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
                .withLocale(strings.resources.configuration.locales[0])))
    val page=(QuranStore.read(context).page+1).coerceIn(1,QuranStore.PAGES)
    val open=openApp(206+Reminder.WIRD.ordinal,ACTION_QURAN) {putExtra("page",page)}
    val snooze=PendingIntent.getBroadcast(context,760,Intent(context,NotificationActionReceiver::class.java).setAction(PrayerNotifications.SNOOZE_WIRD),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder=reminderBuilder(ReminderChannels.QURAN,strings.getString(R.string.reminder_wird_title),text,open,p)
        .addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification,strings.getString(R.string.reminder_wird_open),open).build())
        .addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification,strings.getString(R.string.reminder_snooze_hour),snooze).build())
    if(again)builder.setOnlyAlertOnce(true)
    return postReminder(WIRD_ID,builder)
}
const val WIRD_ID=550

/** Snooze: the wird reminder again in an hour (an inexact alarm; no extra permission). */
fun PrayerNotifications.snoozeWird() {
    val context=appContext
    context.getSystemService(NotificationManager::class.java).cancel(WIRD_ID)
    val again=PendingIntent.getBroadcast(context,761,Intent(context,NotificationActionReceiver::class.java).setAction(PrayerNotifications.WIRD_AGAIN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    context.getSystemService(android.app.AlarmManager::class.java)
        .setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+3600_000L,again)
}

/**
 * Ramadan: the evening before it starts and on its first days, one card that turns on Ramadan mode with suhoor and
 * taraweeh reminders and a khatma in thirty days. Asked once per Hijri year.
 */
private fun PrayerNotifications.showRamadan(task: ScheduledPrayer,p: Preferences): NotificationDelivery {
    val context=appContext
    if(p.ramadanMode)return NotificationDelivery.ALREADY_POSTED
    val date=task.event.time.atZone(p.city.zoneId).toLocalDate()
    val year=runCatching {java.time.chrono.HijrahDate.from(date.plusDays(1)).get(java.time.temporal.ChronoField.YEAR)}.getOrDefault(date.year)
    val plan=context.getSharedPreferences("plan",Context.MODE_PRIVATE)
    if(plan.getInt("ramadanAsked",0)==year)return NotificationDelivery.ALREADY_POSTED
    plan.edit().putInt("ramadanAsked",year).apply()
    val strings=context.prayerLanguage()
    val enable=PendingIntent.getBroadcast(context,762,Intent(context,NotificationActionReceiver::class.java).setAction(PrayerNotifications.ENABLE_RAMADAN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder=reminderBuilder(ReminderChannels.SEASON,strings.getString(R.string.ramadan_prompt_title),strings.getString(R.string.ramadan_prompt_text),
        openApp(206+Reminder.RAMADAN.ordinal,null),p)
        .addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification,strings.getString(R.string.ramadan_prompt_enable),enable).build())
    return postReminder(552,builder)
}

/** Turns on Ramadan mode, its reminders and a thirty-day khatma plan (from the Ramadan card). */
fun PrayerNotifications.enableRamadan(runtime: org.najwa.app.NajwaRuntime) {
    runtime.update(runtime.preferences.copy(ramadanMode=true,suhoorReminder=true,taraweehReminder=true))
    QuranStore.setPlan(appContext,30)
    appContext.getSystemService(NotificationManager::class.java).cancel(552)
    DayWidget.update(appContext)
}

/** After Jumu'ah: the week in one gentle line (prayers recorded, pages, adhkar days, khatma). */
private fun PrayerNotifications.showWeekly(p: Preferences): NotificationDelivery {
    val context=appContext
    if(!p.weeklySummary)return NotificationDelivery.ALREADY_POSTED
    val w=WeeklySummary.build(context)
    val strings=context.prayerLanguage()
    val text=strings.getString(R.string.weekly_text,Counts.format(context,w.prayers.toLong()),Counts.format(context,w.possible.toLong()),
        Counts.pages(context,w.pages),Counts.format(context,w.adhkarDays.toLong()),Counts.format(context,w.khatmaPercent.toLong()))
    return postReminder(553,reminderBuilder(ReminderChannels.PRAYER,strings.getString(R.string.weekly_title),text,openApp(206+Reminder.WEEKLY.ordinal,null),p))
}

/**
 * "May Allah accept it": as a prayer's silence ends (or after the iqama when silence is off), one card to record it,
 * alone or in congregation, and go on to the after-prayer adhkar. Not posted when the prayer is already marked.
 */
private fun PrayerNotifications.showAfterPrayer(task: ScheduledPrayer,p: Preferences): NotificationDelivery {
    val context=appContext
    val date=task.event.time.atZone(p.city.zoneId).toLocalDate()
    if(!p.prayerLog || !p.afterPrayerCard || PrayerLogStore(context).read().status(date,task.event.prayer)!=null)return NotificationDelivery.ALREADY_POSTED
    val strings=context.prayerLanguage()
    val name=strings.prayerName(task.event,p)
    val id=AFTER_PRAYER_ID+task.event.prayer.ordinal
    // Opening the adhkar from the card also closes the card.
    val adhkar=openApp(240+task.event.prayer.ordinal,ACTION_ADHKAR) {putExtra("adhkar",2);putExtra("cancel_notification",id)}
    val text=strings.getString(R.string.after_prayer_text)
    val builder=NotificationCompat.Builder(context,ReminderChannels.PRAYER).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(strings.getString(R.string.after_prayer_title,name)).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setColor(Color.parseColor("#3EB28C")).setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(adhkar).setAutoCancel(true).setTimeoutAfter(60*60_000L)
        .addAction(prayedAction(task,id,org.najwa.core.PrayerStatus.JAMAAH))
        .addAction(prayedAction(task,id))
        .addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification,strings.getString(R.string.action_after_adhkar),adhkar).build())
        .setVisibility(if(p.showLockScreenDetails)NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
    return postReminder(id,builder)
}
const val AFTER_PRAYER_ID=580
const val LOG_CHECK_ID=600

/** "Have you prayed Asr?" shortly before its time ends, only while the prayer is still unmarked; it replaces that prayer's "May Allah accept it" card. */
private fun PrayerNotifications.showLogCheck(task: ScheduledPrayer,p: Preferences): NotificationDelivery {
    val context=appContext
    val date=task.event.time.atZone(p.city.zoneId).toLocalDate()
    if(!p.prayerLog || PrayerLogStore(context).read().status(date,task.event.prayer)!=null)return NotificationDelivery.ALREADY_POSTED
    val strings=context.prayerLanguage()
    val name=strings.prayerName(task.event,p)
    val id=LOG_CHECK_ID+task.event.prayer.ordinal
    context.getSystemService(NotificationManager::class.java).cancel(AFTER_PRAYER_ID+task.event.prayer.ordinal)
    val open=openApp(230+task.event.prayer.ordinal,ACTION_PRAYER_LOG)
    val text=strings.getString(if(task.event.prayer==Prayer.FAJR) R.string.log_check_fajr_text else R.string.log_check_text,name)
    val builder=NotificationCompat.Builder(context,ReminderChannels.PRAYER).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(strings.getString(R.string.log_check_title,name)).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setColor(Color.parseColor("#3EB28C")).setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(40*60_000L)
        .addAction(prayedAction(task,id))
        .setVisibility(if(p.showLockScreenDetails)NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
    return postReminder(id,builder)
}

/** "I prayed" button: records the prayer in the private log and dismisses the card. */
private fun PrayerNotifications.prayedAction(task: ScheduledPrayer,notificationId: Int,status: org.najwa.core.PrayerStatus?=null): NotificationCompat.Action {
    val context=appContext
    val date=task.event.time.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val intent=Intent(context,NotificationActionReceiver::class.java).setAction(PrayerNotifications.MARK_PRAYED)
        .putExtra("prayer",task.event.prayer.name).putExtra("eventAt",task.event.time.toEpochMilli()).putExtra("notification",notificationId)
        .apply {if(status!=null)putExtra("status",status.name)}
        .setData(android.net.Uri.parse("najwa://prayed/${task.event.prayer.name}/$date${if(status!=null)"/"+status.name else ""}"))
    val pending=PendingIntent.getBroadcast(context,700+notificationId+(if(status!=null)50 else 0),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val label=context.prayerLanguage().getString(if(status==org.najwa.core.PrayerStatus.JAMAAH)R.string.action_prayed_jamaah else R.string.action_prayed)
    return NotificationCompat.Action.Builder(R.drawable.ic_status_iqama,label,pending).build()
}

/** "It is now time for the Asr adhan", spoken before the adhan when enabled. */
private fun PrayerNotifications.spokenAnnouncement(task: ScheduledPrayer,p: Preferences): String? =
    if(!p.announceAdhan) null else appContext.prayerLanguage().let {it.getString(R.string.announce_adhan,it.prayerName(task.event,p))}

/** Posted when the phone's time zone no longer matches the chosen city (travel). */
fun PrayerNotifications.showTravel(context: Context,zone: String,city: String) {
    val strings=context.prayerLanguage()
    if(!enabled(PrayerNotifications.STATUS))return
    val open=PendingIntent.getActivity(context,203,Intent(context,MainActivity::class.java).setAction(ACTION_CHOOSE_CITY)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification=NotificationCompat.Builder(context,PrayerNotifications.STATUS).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(strings.getString(R.string.travel_title))
        .setContentText(strings.getString(R.string.travel_text,city,zone))
        // The traveller's concession, pointed out without ruling on it.
        .setStyle(NotificationCompat.BigTextStyle().bigText(strings.getString(R.string.travel_text,city,zone)+"\n"+strings.getString(R.string.travel_rukhsa)))
        .setContentIntent(open).setAutoCancel(true).build()
    runCatching {context.getSystemService(NotificationManager::class.java).notify(470,notification)}
}

/** Back home: the phone's clock matches the home city again while another city is chosen; one tap switches back. */
fun PrayerNotifications.showHomeAgain(context: Context,home: String) {
    val strings=context.prayerLanguage()
    if(!enabled(PrayerNotifications.STATUS))return
    val back=PendingIntent.getBroadcast(context,471,Intent(context,NotificationActionReceiver::class.java).setAction(PrayerNotifications.RETURN_HOME),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification=NotificationCompat.Builder(context,PrayerNotifications.STATUS).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(strings.getString(R.string.travel_home_title,home)).setContentText(strings.getString(R.string.travel_home_text))
        .setContentIntent(back).setAutoCancel(true).build()
    runCatching {context.getSystemService(NotificationManager::class.java).notify(472,notification)}
}
const val ACTION_CHOOSE_CITY="org.najwa.action.CHOOSE_CITY"
const val ACTION_SILENCE_NOW="org.najwa.action.SILENCE_NOW"
const val ACTION_QIBLA="org.najwa.action.QIBLA"
const val ACTION_ADHKAR="org.najwa.action.ADHKAR"
const val ACTION_HEALTH="org.najwa.action.HEALTH"
const val ACTION_PRAYER_LOG="org.najwa.action.PRAYER_LOG"
const val ACTION_TASBEEH="org.najwa.action.TASBEEH"
const val ACTION_QURAN="org.najwa.action.QURAN"

private fun iqamaDeadline(task: ScheduledPrayer,p: Preferences) =
    task.event.time.toEpochMilli()+p.iqamaMinutes.getValue(task.event.prayer)*60_000L
