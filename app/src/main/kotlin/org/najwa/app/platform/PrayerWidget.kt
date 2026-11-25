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
import android.view.Gravity
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
        runPrayerWork {
            if(intent.action==REFRESH)updateAll(context) else super.onReceive(context,intent)
        }
    }
    companion object {
        private const val REFRESH="org.najwa.widget.REFRESH"
        private val SOON: java.time.Duration=java.time.Duration.ofMinutes(10)
        private fun component(context: Context)=ComponentName(context,PrayerWidget::class.java)
        private fun refreshIntent(context: Context)=PendingIntent.getBroadcast(context,401,Intent(context,PrayerWidget::class.java).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun cancelRefresh(context: Context) {runCatching {context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))}}
        fun requestPin(context: Context): Boolean = runCatching {
            val manager=AppWidgetManager.getInstance(context)
            manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(component(context),null,null)
        }.getOrDefault(false)
        fun views(context: Context,p: Preferences,now: Instant=Instant.now(),width: Int=360,height: Int=240,data: WidgetPrayer?=widgetPrayer(now,p),colors: WidgetColors=widgetColors(context,p)): RemoteViews {
            val strings=context.prayerLanguage()
            val layout=when {
                width<110 || height<100 -> R.layout.prayer_widget_tiny
                width>=360 && height>=240 -> R.layout.prayer_widget_large
                width>=280 && height>=180 -> R.layout.prayer_widget_medium
                width>=260 && height<180 -> R.layout.prayer_widget_wide
                width>=180 && height>=260 -> R.layout.prayer_widget_tall
                width in 110..179 && height>=250 -> R.layout.prayer_widget_slim
                width>=180 && height>=170 -> R.layout.prayer_widget_compact
                else -> R.layout.prayer_widget_mini
            }
            return RemoteViews(context.packageName,layout).apply {
                val direction=strings.prayerLayoutDirection
                val rtl=direction==View.LAYOUT_DIRECTION_RTL
                setInt(R.id.widget_root,"setLayoutDirection",direction)
                setImageViewResource(R.id.widget_backdrop,if(colors.light)R.drawable.widget_background_light else R.drawable.widget_background)
                setInt(R.id.widget_backdrop,"setImageAlpha",p.widgetOpacity*255/100)
                setInt(R.id.widget_mark,"setColorFilter",colors.adhan)
                setInt(R.id.widget_refresh,"setColorFilter",colors.secondary)
                setInt(R.id.widget_city,"setBackgroundResource",if(colors.light)R.drawable.widget_pill_light else R.drawable.widget_pill)
                listOf(R.id.widget_brand,R.id.widget_prayer).forEach {setTextColor(it,colors.text)}
                listOf(R.id.widget_city,R.id.widget_phase,R.id.widget_time,R.id.widget_countdown_label,R.id.widget_hijri).forEach {setTextColor(it,colors.secondary)}
                setTextViewText(R.id.widget_city,p.city.name)
                val date=strings.hijriLabel(now.atZone(p.city.zoneId).toLocalDate(),p.hijriOffset)
                setTextViewText(R.id.widget_hijri,date)
                val iqama=data?.phase==CountdownPhase.IQAMA
                val praying=data?.phase==CountdownPhase.PRAYER
                if(layout==R.layout.prayer_widget_compact)
                    setViewVisibility(R.id.widget_compact_footer,if(height>=220)View.VISIBLE else View.GONE)
                if(Build.VERSION.SDK_INT>=31 && layout!=R.layout.prayer_widget_mini && layout!=R.layout.prayer_widget_slim && layout!=R.layout.prayer_widget_tiny) {
                    setInt(R.id.widget_countdown,"setGravity",Gravity.CENTER_VERTICAL or if(rtl)Gravity.LEFT else Gravity.RIGHT)
                }
                val brand=strings.getString(R.string.system_brand)
                val heading=strings.getString(when {iqama->R.string.system_iqama_remaining;praying->R.string.widget_prayer_now;else->R.string.system_next})
                val prayer=data?.let {strings.prayerName(PrayerEvent(it.prayer,it.time),p)}
                val time=data?.time?.let {strings.prayerTime(it,p)}
                setTextViewText(R.id.widget_brand,brand)
                setTextViewText(R.id.widget_phase,if(layout==R.layout.prayer_widget_mini)strings.getString(
                    if(iqama)R.string.system_iqama else if(praying)R.string.widget_prayer_now else R.string.system_next) else heading)
                setTextViewText(R.id.widget_countdown_label,strings.getString(when {
                    iqama->R.string.system_to_iqama
                    praying->if(p.autoSilent && data!!.prayer in p.enabled)R.string.widget_ringer_back else R.string.widget_prayer_ends
                    else->R.string.system_to_adhan
                }))
                // Ten minutes before adhan the countdown switches to the alert colour.
                val soon=data?.phase==CountdownPhase.ADHAN && java.time.Duration.between(now,data.time)<=SOON
                val phaseColor=when {iqama->colors.iqama;praying->colors.prayer;soon->colors.soon;else->colors.adhan}
                // Every stage has one colour: countdown, its label, the bar and the highlighted prayer.
                if(soon || praying || iqama) {
                    setTextColor(R.id.widget_countdown_label,phaseColor)
                    setTextColor(R.id.widget_phase,phaseColor)
                }
                val highlight=when {
                    iqama -> if(colors.light)R.drawable.widget_cell_iqama_light else R.drawable.widget_cell_iqama
                    praying -> if(colors.light)R.drawable.widget_cell_prayer_light else R.drawable.widget_cell_prayer
                    soon -> if(colors.light)R.drawable.widget_cell_soon_light else R.drawable.widget_cell_soon
                    else -> if(colors.light)R.drawable.widget_cell_adhan_light else R.drawable.widget_cell_adhan
                }
                setTextColor(R.id.widget_countdown,phaseColor)
                if(Build.VERSION.SDK_INT>=31) {
                    setColorStateList(R.id.widget_progress,"setProgressTintList",ColorStateList.valueOf(phaseColor))
                }
                setTextViewText(R.id.widget_prayer,prayer ?: strings.getString(R.string.system_unavailable))
                setTextViewText(R.id.widget_time,when {
                    time==null -> strings.getString(R.string.system_check_calculation)
                    iqama -> "${strings.getString(R.string.system_iqama)} $time"
                    praying -> strings.getString(R.string.widget_prayer_until,time)
                    // Roomy sizes also show when the iqama of the next prayer will be.
                    data.iqamaLabel!=null && (layout==R.layout.prayer_widget_large || layout==R.layout.prayer_widget_medium) ->
                        "$time · ${strings.getString(R.string.system_iqama)} ${strings.prayerTime(data.time.plusSeconds(p.iqamaMinutes.getValue(data.prayer)*60L),p)}"
                    else -> time
                })
                setViewVisibility(R.id.widget_countdown,if(data!=null)View.VISIBLE else View.GONE)
                val duration=data?.start?.let {java.time.Duration.between(it,data.time).toMillis()} ?: 0L
                val remaining=data?.let {java.time.Duration.between(now,it.time).toMillis()} ?: 0L
                setViewVisibility(R.id.widget_progress,if(duration>0)View.VISIBLE else View.GONE)
                // Fills up as the prayer (or iqama) approaches: elapsed share of the waiting period.
                if(duration>0)setProgressBar(R.id.widget_progress,100,((1-remaining.toDouble()/duration)*100).toInt().coerceIn(0,100),false)
                if(data!=null) {
                    setChronometerCountDown(R.id.widget_countdown,true)
                    setChronometer(R.id.widget_countdown,SystemClock.elapsedRealtime()+data.time.toEpochMilli()-now.toEpochMilli(),null,true)
                }
                val day=fillDay(this,strings,p,now,data,colors,phaseColor,highlight)
                setContentDescription(R.id.widget_root,listOfNotNull(brand,date,heading,prayer,time,p.city.name,day).joinToString(" · "))
                val open=PendingIntent.getActivity(context,400,Intent(context,MainActivity::class.java).putExtra("open_home",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setOnClickPendingIntent(R.id.widget_root,open)
                setOnClickPendingIntent(R.id.widget_refresh,refreshIntent(context))
            }
        }
        private val cells=listOf(
            Triple(R.id.widget_p0,R.id.widget_p0_name,R.id.widget_p0_time),
            Triple(R.id.widget_p1,R.id.widget_p1_name,R.id.widget_p1_time),
            Triple(R.id.widget_p2,R.id.widget_p2_name,R.id.widget_p2_time),
            Triple(R.id.widget_p3,R.id.widget_p3_name,R.id.widget_p3_time),
            Triple(R.id.widget_p4,R.id.widget_p4_name,R.id.widget_p4_time))

        /** Fills the five daily prayers (layouts without these cells ignore the calls).
         * Shows the day of the highlighted prayer, so after Isha it is tomorrow's list. */
        private fun fillDay(views: RemoteViews,strings: Context,p: Preferences,now: Instant,data: WidgetPrayer?,colors: WidgetColors,accent: Int,highlight: Int): String? {
            val zone=p.city.zoneId
            val date=(data?.time ?: now).atZone(zone).toLocalDate()
            val events=runCatching {PrayerCalculator().calculate(date,p).events.filter {it.prayer.isPrayer}}.getOrDefault(emptyList())
            if(events.size!=5) {views.setViewVisibility(R.id.widget_day,View.GONE);return null}
            views.setViewVisibility(R.id.widget_day,View.VISIBLE)
            events.forEachIndexed {index,event->
                val (cell,name,time)=cells[index]
                val active=event.prayer==data?.prayer
                val passed=!active && event.time<=now
                views.setTextViewText(name,strings.prayerName(event,p))
                views.setTextViewText(time,strings.prayerTime(event.time,p))
                views.setTextColor(name,if(active)accent else colors.secondary)
                views.setTextColor(time,when {active->accent;passed->colors.secondary;else->colors.text})
                views.setInt(cell,"setBackgroundResource",if(active)highlight else 0)
            }
            return events.joinToString("، ") {"${strings.prayerName(it,p)} ${strings.prayerTime(it.time,p)}"}
        }
        private fun openApp(context: Context)=PendingIntent.getActivity(context,400,Intent(context,MainActivity::class.java).putExtra("open_home",true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        /** The five prayers of the day, with the next one highlighted. */
        fun listViews(context: Context,p: Preferences,now: Instant,data: WidgetPrayer?,colors: WidgetColors): RemoteViews {
            val strings=context.prayerLanguage()
            return RemoteViews(context.packageName,R.layout.prayer_widget_list).apply {
                setInt(R.id.widget_root,"setLayoutDirection",strings.prayerLayoutDirection)
                setImageViewResource(R.id.widget_backdrop,if(colors.light)R.drawable.widget_background_light else R.drawable.widget_background)
                setInt(R.id.widget_backdrop,"setImageAlpha",p.widgetOpacity*255/100)
                setInt(R.id.widget_mark,"setColorFilter",colors.adhan)
                setTextViewText(R.id.widget_city,p.city.name);setTextColor(R.id.widget_city,colors.text)
                setTextViewText(R.id.widget_hijri,strings.hijriLabel(now.atZone(p.city.zoneId).toLocalDate(),p.hijriOffset));setTextColor(R.id.widget_hijri,colors.secondary)
                val highlight=if(colors.light)R.drawable.widget_cell_adhan_light else R.drawable.widget_cell_adhan
                val summary=fillDay(this,strings,p,now,data,colors,colors.adhan,highlight)
                setContentDescription(R.id.widget_root,listOfNotNull(p.city.name,summary).joinToString(" · "))
                setOnClickPendingIntent(R.id.widget_root,openApp(context))
            }
        }
        /** Transparent: just the next prayer and its countdown, legible on any wallpaper. */
        fun minimalViews(context: Context,p: Preferences,now: Instant,data: WidgetPrayer?): RemoteViews {
            val strings=context.prayerLanguage()
            return RemoteViews(context.packageName,R.layout.prayer_widget_minimal).apply {
                setInt(R.id.widget_root,"setLayoutDirection",strings.prayerLayoutDirection)
                val phase=strings.getString(when(data?.phase) {CountdownPhase.IQAMA->R.string.system_iqama_remaining;CountdownPhase.PRAYER->R.string.widget_prayer_now;else->R.string.system_next})
                setTextViewText(R.id.widget_phase,phase)
                setTextViewText(R.id.widget_prayer,data?.let {strings.prayerName(PrayerEvent(it.prayer,it.time),p)} ?: strings.getString(R.string.system_unavailable))
                setTextViewText(R.id.widget_time,data?.let {strings.prayerTime(it.time,p)+" · "+p.city.name} ?: "")
                setViewVisibility(R.id.widget_countdown,if(data!=null)View.VISIBLE else View.GONE)
                if(data!=null) {
                    setChronometerCountDown(R.id.widget_countdown,true)
                    setChronometer(R.id.widget_countdown,SystemClock.elapsedRealtime()+data.time.toEpochMilli()-now.toEpochMilli(),null,true)
                }
                setOnClickPendingIntent(R.id.widget_root,openApp(context))
            }
        }
        fun updateAll(context: Context) {
            // The companion widgets follow the same theme and day changes.
            PrayerLogWidget.update(context);TasbeehWidget.update(context)
            // A launcher or vendor widget error must never crash the main activity.
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(component(context))
                val listIds=manager.getAppWidgetIds(ComponentName(context,PrayerListWidget::class.java))
                val minimalIds=manager.getAppWidgetIds(ComponentName(context,MinimalWidget::class.java))
                if(ids.isEmpty() && listIds.isEmpty() && minimalIds.isEmpty()) {cancelRefresh(context);return}
                val p=PreferencesStore(context).read()
                val now=Instant.now()
                val next=widgetPrayer(now,p)
                val colors=widgetColors(context,p)
                fun render(width: Int,height: Int)=views(context,p,now,width,height,next,colors)
                if(listIds.isNotEmpty())manager.updateAppWidget(listIds,listViews(context,p,now,next,colors))
                if(minimalIds.isNotEmpty())manager.updateAppWidget(minimalIds,minimalViews(context,p,now,next))
                if(ids.isEmpty()) {} else if(Build.VERSION.SDK_INT>=31) {
                    val content=RemoteViews(mapOf(
                        SizeF(50f,50f) to render(50,50),
                        SizeF(110f,100f) to render(110,100),
                        SizeF(110f,250f) to render(110,250),
                        SizeF(260f,110f) to render(260,110),
                        SizeF(180f,210f) to render(180,210),
                        SizeF(180f,260f) to render(180,260),
                        SizeF(280f,180f) to render(280,180),
                        SizeF(360f,240f) to render(360,240)))
                    manager.updateAppWidget(ids,content)
                } else {
                    ids.forEach {id->
                        val options=manager.getAppWidgetOptions(id)
                        manager.updateAppWidget(id,RemoteViews(
                            render(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,280),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,180)),
                            render(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,180),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,210))))
                    }
                }
                val alarm=context.getSystemService(AlarmManager::class.java)
                val midnight=now.atZone(p.city.zoneId).toLocalDate().plusDays(1).atStartOfDay(p.city.zoneId).toInstant()
                val soonAt=next?.takeIf {it.phase==CountdownPhase.ADHAN}?.time?.minus(SOON)
                val progressInterval=if(next?.phase!=CountdownPhase.ADHAN || (soonAt!=null && soonAt<=now))60L else 900L
                val at=listOfNotNull(next?.time,soonAt?.takeIf {it>now},midnight,now.plusSeconds(progressInterval)).min().toEpochMilli()
                // Non-wakeup: the widget must not wake a sleeping phone or contend
                // with the idle quota reserved for sound restoration.
                if(Build.VERSION.SDK_INT<31 || alarm.canScheduleExactAlarms())alarm.setExact(AlarmManager.RTC,at,refreshIntent(context))
                else alarm.set(AlarmManager.RTC,at,refreshIntent(context))
            }
        }
    }
}

/** Five daily prayers in a list (home screen or lock screen). */
class PrayerListWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {PrayerWidget.updateAll(context)}
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) {PrayerWidget.updateAll(context)}
}
/** Transparent next-prayer countdown. */
class MinimalWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {PrayerWidget.updateAll(context)}
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) {PrayerWidget.updateAll(context)}
}
