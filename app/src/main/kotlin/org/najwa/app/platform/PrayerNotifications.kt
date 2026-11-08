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
import org.json.JSONObject
import org.najwa.app.MainActivity
import org.najwa.app.R
import org.najwa.core.*

class PrayerNotifications(private val context: Context) {
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
    fun show(task: ScheduledPrayer,p: Preferences,test: Boolean=false) {
        val adhan=task.action==PrayerAction.ADHAN
        val friday=task.action==PrayerAction.FRIDAY
        val channel=when(task.action) {
            PrayerAction.ADHAN -> adhanChannel
            PrayerAction.IQAMA -> IQAMA
            PrayerAction.FRIDAY -> FRIDAY
            PrayerAction.SILENCE -> return
        }
        if(!enabled(channel))return
        if(!test) {
            val a=history.read()?.optJSONArray("delivered") ?: JSONArray()
            val ids=(0 until a.length()).map {a.getString(it)}
            if(task.id in ids)return
            // Claim before posting to prevent duplicate sound after a process restart.
            history.write(JSONObject().put("delivered",JSONArray((ids+task.id).takeLast(128))))
        }
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
        val compact=notificationViews(context,task,p,title,text,false)
        val expanded=notificationViews(context,task,p,title,text,true)
        if(task.action==PrayerAction.IQAMA && !test)manager.cancel(300+task.event.prayer.ordinal*2)
        val open=PendingIntent.getActivity(context,201,Intent(context,MainActivity::class.java).putExtra("open_home",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notificationId=when {
            test -> 499
            friday -> 490
            else -> 300+task.event.prayer.ordinal*2+if(adhan)0 else 1
        }
        manager.notify(notificationId,
            NotificationCompat.Builder(context,channel).setSmallIcon(if(adhan)R.drawable.ic_status_adhan else R.drawable.ic_status_iqama)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(compact).setCustomBigContentView(expanded).setCustomHeadsUpContentView(compact)
                .setColor(Color.parseColor(if(adhan)"#16C784" else "#087A53")).setSubText("صلاتك أولاً")
                .setContentIntent(open).setAutoCancel(true).setCategory(if(adhan)NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setWhen(task.at.toEpochMilli())
                .setTimeoutAfter(when {
                    friday -> (task.event.time.toEpochMilli()-task.at.toEpochMilli()).coerceAtLeast(60_000L)
                    adhan -> p.iqamaMinutes.getValue(task.event.prayer)*60_000L
                    else -> 15*60_000L
                }).build())
    }
}

/** Both native notifications and preview tests use these exact RemoteViews. */
fun notificationViews(context: Context,task: ScheduledPrayer,p: Preferences,title: String,detail: String,expanded: Boolean): RemoteViews {
    val adhan=task.action==PrayerAction.ADHAN
    val friday=task.action==PrayerAction.FRIDAY
    return RemoteViews(context.packageName,if(expanded)R.layout.prayer_notification_expanded else R.layout.prayer_notification_compact).apply {
        val primary=Color.parseColor(if(adhan)"#F5F8FC" else "#172A34")
        val secondary=Color.parseColor(if(adhan)"#B5C9D6" else "#395A57")
        val accent=Color.parseColor(if(adhan)"#60DFBA" else "#087A53")
        setInt(R.id.notification_root,"setBackgroundResource",if(adhan)R.drawable.widget_background else R.drawable.widget_background_light)
        setImageViewResource(R.id.notification_icon,if(adhan)R.drawable.ic_adhan else R.drawable.ic_iqama)
        setTextColor(R.id.notification_title,primary)
        setTextColor(R.id.notification_detail,secondary)
        setTextViewText(R.id.notification_title,title)
        val iqamaTime=displayTime(task.event.time.plusSeconds(p.iqamaMinutes.getValue(task.event.prayer)*60L),p)
        setTextViewText(R.id.notification_detail,when {
            friday -> detail
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
            setViewVisibility(R.id.notification_timer_box,if(adhan)View.VISIBLE else View.GONE)
            if(adhan) {
                val deadline=task.event.time.plusSeconds(p.iqamaMinutes.getValue(task.event.prayer)*60L).toEpochMilli()
                setChronometerCountDown(R.id.notification_timer,true)
                setChronometer(R.id.notification_timer,SystemClock.elapsedRealtime()+deadline-System.currentTimeMillis(),null,true)
            }
        }
    }
}
