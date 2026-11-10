package org.najwa.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import org.najwa.app.MainActivity
import org.najwa.app.R
import org.najwa.core.*
import java.time.Instant

class PrayerWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {updateAll(context)}
    override fun onEnabled(context: Context) {updateAll(context)}
    override fun onDisabled(context: Context) {updateAll(context)}
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) {updateAll(context)}
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action==REFRESH)updateAll(context) else super.onReceive(context,intent)
    }
    companion object {
        private const val REFRESH="org.najwa.widget.REFRESH"
        private fun component(context: Context)=ComponentName(context,PrayerWidget::class.java)
        private fun refreshIntent(context: Context)=PendingIntent.getBroadcast(context,401,Intent(context,PrayerWidget::class.java).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun cancelRefresh(context: Context) {runCatching {context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))}}
        fun requestPin(context: Context): Boolean = runCatching {
            val manager=AppWidgetManager.getInstance(context)
            manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(component(context),null,null)
        }.getOrDefault(false)
        fun views(context: Context,p: Preferences,now: Instant=Instant.now(),width: Int=360,height: Int=240,data: WidgetPrayer?=widgetPrayer(now,p),colors: WidgetColors=widgetColors(context,p)): RemoteViews {
            val layout=when {
                width>=360 && height>=240 -> R.layout.prayer_widget_large
                width>=280 && height>=180 -> R.layout.prayer_widget_medium
                width>=260 && height<180 -> R.layout.prayer_widget_wide
                width>=180 && height>=260 -> R.layout.prayer_widget_tall
                width in 110..179 && height>=250 -> R.layout.prayer_widget_slim
                width>=180 && height>=210 -> R.layout.prayer_widget_compact
                else -> R.layout.prayer_widget_mini
            }
            return RemoteViews(context.packageName,layout).apply {
                setImageViewResource(R.id.widget_backdrop,if(colors.light)R.drawable.widget_background_light else R.drawable.widget_background)
                setInt(R.id.widget_backdrop,"setImageAlpha",p.widgetOpacity*255/100)
                listOf(R.id.widget_brand,R.id.widget_prayer).forEach {setTextColor(it,colors.text)}
                listOf(R.id.widget_city,R.id.widget_phase,R.id.widget_time,R.id.widget_countdown_label,R.id.widget_footer).forEach {setTextColor(it,colors.secondary)}
                setTextColor(R.id.widget_refresh,colors.adhan)
                setTextViewText(R.id.widget_city,p.city.name)
                val date=hijriLabel(now.atZone(p.city.zoneId).toLocalDate())
                setTextViewText(R.id.widget_hijri,date)
                setTextColor(R.id.widget_hijri,colors.secondary)
                val iqama=data?.phase==CountdownPhase.IQAMA
                val mini=layout==R.layout.prayer_widget_mini
                val wide=layout==R.layout.prayer_widget_wide
                val slim=layout==R.layout.prayer_widget_slim
                if(mini || wide)setViewVisibility(R.id.widget_hijri,View.GONE)
                if(mini)setViewVisibility(R.id.widget_brand,View.GONE)
                if(slim && data!=null)setViewVisibility(R.id.widget_phase,View.GONE)
                setTextViewText(R.id.widget_phase,data?.heading ?: "الصلاة القادمة")
                setTextViewText(R.id.widget_countdown_label,if(iqama)"إلى الإقامة" else "إلى الأذان")
                val phaseColor=if(iqama)colors.iqama else colors.adhan
                setTextColor(R.id.widget_countdown,phaseColor)
                if(Build.VERSION.SDK_INT>=31) {
                    setColorStateList(R.id.widget_progress,"setProgressTintList",ColorStateList.valueOf(phaseColor))
                }
                setInt(R.id.widget_counter_box,"setBackgroundResource",when {
                    colors.light && iqama->R.drawable.widget_counter_gold_light
                    colors.light->R.drawable.widget_counter_mint_light
                    iqama->R.drawable.widget_counter_gold
                    else->R.drawable.widget_counter_mint
                })
                setTextViewText(R.id.widget_prayer,data?.prayer?.label ?: "غير متاح")
                setTextViewText(R.id.widget_time,data?.timeLabel ?: "راجع إعدادات الحساب")
                setViewVisibility(R.id.widget_countdown,if(data!=null)View.VISIBLE else View.GONE)
                val duration=data?.start?.let {java.time.Duration.between(it,data.time).toMillis()} ?: 0L
                val remaining=data?.let {java.time.Duration.between(now,it.time).toMillis()} ?: 0L
                setViewVisibility(R.id.widget_progress,if(duration>0)View.VISIBLE else View.GONE)
                if(duration>0)setProgressBar(R.id.widget_progress,100,(remaining.toDouble()/duration*100).toInt().coerceIn(0,100),false)
                if(data!=null) {
                    setChronometerCountDown(R.id.widget_countdown,true)
                    setChronometer(R.id.widget_countdown,SystemClock.elapsedRealtime()+data.time.toEpochMilli()-now.toEpochMilli(),null,true)
                }
                val footer=when {
                    slim -> p.city.name
                    iqama -> "إقامة ${data?.prayer?.label} · حسب إعدادك"
                    else -> "صلاتك أولاً، وضجيجك لاحقاً"
                }
                setTextViewText(R.id.widget_footer,footer)
                setContentDescription(R.id.widget_root,listOfNotNull("نجوى",date,data?.heading,data?.prayer?.label,data?.timeLabel,p.city.name).joinToString(" · "))
                val open=PendingIntent.getActivity(context,400,Intent(context,MainActivity::class.java).putExtra("open_home",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setOnClickPendingIntent(R.id.widget_root,open)
                setOnClickPendingIntent(R.id.widget_refresh,refreshIntent(context))
            }
        }
        fun updateAll(context: Context) {
            // A launcher or vendor widget error must never crash the main activity.
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(component(context))
                if(ids.isEmpty()) {cancelRefresh(context);return}
                val p=PreferencesStore(context).read()
                val now=Instant.now()
                val next=widgetPrayer(now,p)
                val colors=widgetColors(context,p)
                fun render(width: Int,height: Int)=views(context,p,now,width,height,next,colors)
                ids.forEach {id->
                    val options=manager.getAppWidgetOptions(id)
                    val content=if(Build.VERSION.SDK_INT>=31)RemoteViews(mapOf(
                        SizeF(110f,100f) to render(110,100),
                        SizeF(110f,250f) to render(110,250),
                        SizeF(260f,110f) to render(260,110),
                        SizeF(180f,210f) to render(180,210),
                        SizeF(180f,260f) to render(180,260),
                        SizeF(280f,180f) to render(280,180),
                        SizeF(360f,240f) to render(360,240)))
                    else RemoteViews(
                        render(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,280),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,180)),
                        render(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,210)))
                    manager.updateAppWidget(id,content)
                }
                val alarm=context.getSystemService(AlarmManager::class.java)
                val midnight=now.atZone(p.city.zoneId).toLocalDate().plusDays(1).atStartOfDay(p.city.zoneId).toInstant()
                val progressInterval=if(next?.phase==CountdownPhase.IQAMA)60L else 300L
                val at=listOfNotNull(next?.time,midnight,now.plusSeconds(progressInterval)).min().toEpochMilli()
                // Non-wakeup: the widget must not wake a sleeping phone or contend
                // with the idle quota reserved for sound restoration.
                if(Build.VERSION.SDK_INT<31 || alarm.canScheduleExactAlarms())alarm.setExact(AlarmManager.RTC,at,refreshIntent(context))
                else alarm.set(AlarmManager.RTC,at,refreshIntent(context))
            }
        }
    }
}

