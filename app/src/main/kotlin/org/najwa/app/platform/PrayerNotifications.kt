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
    }
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val history=JsonFile(context,"notification-history.json")
    val sounds=AdhanSounds(context)
    val adhanChannel get()=sounds.selected().channelId
    fun cancelTest() {manager.cancel(499)}
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
    init {
        manager.createNotificationChannel(NotificationChannel(ADHAN,"تنبيه الأذان",NotificationManager.IMPORTANCE_HIGH).apply {
            description="تنبيه صوتي عند دخول وقت الصلاة؛ يمكن اختيار الصوت من إعدادات الهاتف"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(IQAMA,"تذكير الإقامة",NotificationManager.IMPORTANCE_HIGH).apply {
            description="تذكير مرئي حسب المدة التي تحددها لكل صلاة"
            setSound(null,null);enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(FRIDAY,"تذكير صلاة الجمعة",NotificationManager.IMPORTANCE_DEFAULT).apply {
            description="تذكير أسبوعي قبل وقت الظهر المحسوب في مدينتك"
        })
    }
    fun show(task: ScheduledPrayer,p: Preferences,test: Boolean=false): NotificationDelivery {
        val adhan=task.action==PrayerAction.ADHAN
        val friday=task.action==PrayerAction.FRIDAY
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
        val title=when {
            friday -> "تذكير بصلاة الجمعة"
            test -> if(adhan)"تجربة تنبيه الأذان" else "تجربة تذكير الإقامة"
            adhan -> "حان وقت أذان ${task.event.prayer.label}"
            else -> "حان وقت إقامة ${task.event.prayer.label}"
        }
        val text=when {
            friday -> "${p.city.name} · الظهر ${displayTime(task.event.time,p)} تقريبًا"
            adhan -> "${p.city.name} · ${displayTime(task.event.time,p)} · الإقامة بعد ${p.iqamaMinutes.getValue(task.event.prayer)} دقيقة"
            else -> "${p.city.name} · بعد الأذان بـ${p.iqamaMinutes.getValue(task.event.prayer)} دقيقة · حسب إعدادك"
        }
        val compactTitle=when {
            friday || test -> title
            adhan -> "أذان ${task.event.prayer.label}"
            else -> "إقامة ${task.event.prayer.label}"
        }
        val compact=notificationViews(context,task,p,compactTitle,text,false,now)
        val expanded=notificationViews(context,task,p,title,text,true,now)
        val open=PendingIntent.getActivity(context,201,Intent(context,MainActivity::class.java).putExtra("open_home",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notificationId=when {
            test -> 499
            friday -> 490
            else -> 300+task.event.prayer.ordinal*2+if(adhan)0 else 1
        }
        val icon=if(adhan)R.drawable.ic_status_adhan else R.drawable.ic_status_iqama
        val publicVersion=NotificationCompat.Builder(context,channel)
            .setSmallIcon(icon).setContentTitle("نجوى").setContentText("لديك تنبيه للصلاة")
            .setShowWhen(false).build()
        val notification=NotificationCompat.Builder(context,channel).setSmallIcon(icon)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(compact).setCustomBigContentView(expanded).setCustomHeadsUpContentView(compact)
                .setColor(Color.parseColor(when {
                    adhan -> "#16C784"
                    friday -> "#9D7526"
                    else -> "#087A53"
                })).setSubText("صلاتك أولاً")
                .setContentIntent(open).setAutoCancel(true).setCategory(if(adhan)NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(if(p.showLockScreenDetails)NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setWhen(task.at.toEpochMilli()).setOnlyAlertOnce(true)
                .setTimeoutAfter(when {
                    friday -> (task.event.time.toEpochMilli()-now.toEpochMilli()).coerceAtLeast(60_000L)
                    adhan -> (iqamaDeadline(task,p)-now.toEpochMilli()).let {if(it>0)it else 60_000L}
                    else -> 15*60_000L
                }).build()
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
}

private fun iqamaDeadline(task: ScheduledPrayer,p: Preferences) =
    task.event.time.toEpochMilli()+p.iqamaMinutes.getValue(task.event.prayer)*60_000L

/** Both native notifications and preview tests use these exact RemoteViews. */
fun notificationViews(context: Context,task: ScheduledPrayer,p: Preferences,title: String,detail: String,expanded: Boolean,now: Instant=task.at): RemoteViews {
    val adhan=task.action==PrayerAction.ADHAN
    val friday=task.action==PrayerAction.FRIDAY
    return RemoteViews(context.packageName,if(expanded)R.layout.prayer_notification_expanded else R.layout.prayer_notification_compact).apply {
        val primary=Color.parseColor(if(adhan)"#F4F8FC" else "#172C2D")
        val secondary=Color.parseColor(when {
            adhan -> "#BCD1D8"
            friday -> "#624F2D"
            else -> "#365852"
        })
        val accent=Color.parseColor(when {
            adhan -> "#71E0BB"
            friday -> "#755419"
            else -> "#086548"
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
        val iqamaTime=displayTime(task.event.time.plusSeconds(p.iqamaMinutes.getValue(task.event.prayer)*60L),p)
        setTextViewText(R.id.notification_detail,when {
            friday -> detail
            !expanded && adhan -> "الإقامة $iqamaTime"
            !expanded -> displayTime(task.at,p)
            adhan -> "${p.city.name} · الإقامة $iqamaTime"
            else -> "${p.city.name} · حسب موعد الإقامة المحدد"
        })
        if(expanded) {
            setTextViewText(R.id.notification_brand,when {
                friday -> "نجوى · الجمعة"
                adhan -> "نجوى · الأذان"
                else -> "نجوى · الإقامة"
            })
            setTextColor(R.id.notification_brand,accent)
            setTextColor(R.id.notification_time,secondary)
            setTextColor(R.id.notification_signature,secondary)
            setTextColor(R.id.notification_message,accent)
            setTextViewText(R.id.notification_message,if(friday)"استعد لصلاة الجمعة. يُحدّد كل مسجد موعد الخطبة والصلاة." else prayerMessage(task.event.prayer,adhan))
            setTextViewText(R.id.notification_time,displayTime(task.at,p))
            setViewVisibility(R.id.notification_message,View.VISIBLE)
            val remaining=iqamaDeadline(task,p)-now.toEpochMilli()
            val showTimer=adhan && remaining>0
            setViewVisibility(R.id.notification_timer_box,if(showTimer)View.VISIBLE else View.GONE)
            if(showTimer) {
                setChronometerCountDown(R.id.notification_timer,true)
                setChronometer(R.id.notification_timer,SystemClock.elapsedRealtime()+remaining,null,true)
            }
        }
    }
}
