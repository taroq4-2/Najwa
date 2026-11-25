package org.najwa.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.RemoteViews
import org.najwa.app.MainActivity
import org.najwa.app.NajwaApplication
import org.najwa.app.R
import org.najwa.app.ui.Adhkar
import org.najwa.core.Prayer
import org.najwa.core.PrayerStatus
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

private fun openApp(context: Context,code: Int,action: String)=PendingIntent.getActivity(context,code,
    Intent(context,MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Today's five prayers: one tap marks a prayer as prayed, a second tap as prayed in congregation, a third clears it. */
class PrayerLogWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {update(context)}
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action!=TOGGLE) {super.onReceive(context,intent);return}
        val prayer=runCatching {Prayer.valueOf(intent.getStringExtra("prayer") ?: "")}.getOrNull() ?: return
        runPrayerWork {
            val runtime=(context.applicationContext as NajwaApplication).runtime
            val p=runtime.preferences
            if(!p.prayerLog)return@runPrayerWork
            val today=LocalDate.now(p.city.zoneId)
            val next=when(runtime.log().status(today,prayer)) {
                null,PrayerStatus.MISSED->PrayerStatus.ON_TIME
                PrayerStatus.ON_TIME,PrayerStatus.LATE->PrayerStatus.JAMAAH
                PrayerStatus.JAMAAH->null
            }
            runtime.setStatus(today,prayer,next)
            runCatching {context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(20,120))}
        }
    }
    companion object {
        const val TOGGLE="org.najwa.widget.LOG_TOGGLE"
        private val order=listOf(Prayer.FAJR,Prayer.DHUHR,Prayer.ASR,Prayer.MAGHRIB,Prayer.ISHA)
        private val marks=listOf(R.id.mark_0,R.id.mark_1,R.id.mark_2,R.id.mark_3,R.id.mark_4)
        private val names=listOf(R.id.name_0,R.id.name_1,R.id.name_2,R.id.name_3,R.id.name_4)
        private val cells=listOf(R.id.cell_0,R.id.cell_1,R.id.cell_2,R.id.cell_3,R.id.cell_4)

        fun update(context: Context) {
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(ComponentName(context,PrayerLogWidget::class.java))
                if(ids.isEmpty())return
                manager.updateAppWidget(ids,views(context))
            }
        }

        private fun views(context: Context): RemoteViews {
            val runtime=(context.applicationContext as NajwaApplication).runtime
            val p=runtime.preferences
            val strings=context.prayerLanguage()
            val colors=widgetColors(context,p)
            val now=Instant.now()
            val today=LocalDate.now(p.city.zoneId)
            val log=runtime.log()
            val times=runCatching {runtime.calculator.calculate(today,p).events.associate {it.prayer to it.time}}.getOrDefault(emptyMap())
            // The prayer whose time is running now gets a ring until it is marked.
            val current=order.lastOrNull {times[it]?.let {t->t<=now}==true}
            val v=RemoteViews(context.packageName,R.layout.prayer_log_widget)
            // Follow Najwa's language, not the launcher's (an Arabic week reads right to left).
            v.setInt(R.id.widget_root,"setLayoutDirection",strings.prayerLayoutDirection)
            v.setInt(R.id.widget_root,"setBackgroundResource",if(colors.light)R.drawable.widget_background_light else R.drawable.widget_background)
            v.setOnClickPendingIntent(R.id.widget_root,openApp(context,240,ACTION_PRAYER_LOG))
            val done=order.count {log.prayed(today,it)}
            v.setTextViewText(R.id.log_title,strings.getString(R.string.widget_log_title))
            v.setTextColor(R.id.log_title,colors.text)
            v.setTextViewText(R.id.log_count,String.format(Locale.ROOT,"%d/5",done))
            v.setTextColor(R.id.log_count,if(done==5)colors.adhan else colors.secondary)
            order.forEachIndexed {i,prayer->
                val status=log.status(today,prayer)
                val future=times[prayer]?.let {it>now}==true
                v.setInt(marks[i],"setBackgroundResource",when(status) {
                    PrayerStatus.JAMAAH->R.drawable.log_cell_jamaah
                    PrayerStatus.ON_TIME->R.drawable.log_cell_done
                    PrayerStatus.LATE->R.drawable.log_cell_late
                    PrayerStatus.MISSED->R.drawable.log_cell_missed
                    null->if(prayer==current) R.drawable.log_cell_now else if(colors.light) R.drawable.log_cell_open_light else R.drawable.log_cell_open
                })
                v.setTextViewText(marks[i],when(status) {PrayerStatus.JAMAAH->"✓✓";PrayerStatus.MISSED->"";null->"";else->"✓"})
                v.setTextViewText(names[i],strings.prayerName(prayer))
                v.setTextColor(names[i],if(future)colors.secondary else colors.text)
                if(p.prayerLog && !future)v.setOnClickPendingIntent(cells[i],PendingIntent.getBroadcast(context,810+i,
                    Intent(context,PrayerLogWidget::class.java).setAction(TOGGLE).putExtra("prayer",prayer.name).setData(Uri.parse("najwa://log/${prayer.name}")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                else v.setOnClickPendingIntent(cells[i],openApp(context,240,ACTION_PRAYER_LOG))
            }
            val streak=log.streak(today)
            v.setTextViewText(R.id.log_footer,when {
                !p.prayerLog->strings.getString(R.string.widget_log_disabled)
                done==5->strings.getString(R.string.widget_log_complete,streak)
                else->strings.getString(R.string.widget_log_hint)
            })
            v.setTextColor(R.id.log_footer,colors.secondary)
            return v
        }
    }
}

/** A tasbeeh counter on the home screen: tap the circle to count; it shares its count with the app. */
class TasbeehWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {update(context)}
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action!=TAP) {super.onReceive(context,intent);return}
        val (state,outcome)=TasbeehStore.tap(context)
        if(state.haptics)runCatching {
            context.getSystemService(Vibrator::class.java)?.vibrate(if(outcome!=TasbeehStore.Outcome.COUNTED)
                VibrationEffect.createWaveform(longArrayOf(0,60,70,140),-1) else VibrationEffect.createOneShot(16,90))
        }
        update(context)
    }
    companion object {
        const val TAP="org.najwa.widget.TASBEEH_TAP"
        fun update(context: Context) {
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(ComponentName(context,TasbeehWidget::class.java))
                if(ids.isEmpty())return
                val p=PreferencesStore(context).read()
                val colors=widgetColors(context,p)
                val strings=context.prayerLanguage()
                val s=TasbeehStore.read(context)
                val v=RemoteViews(context.packageName,R.layout.tasbeeh_widget)
                v.setInt(R.id.widget_root,"setLayoutDirection",strings.prayerLayoutDirection)
                v.setInt(R.id.widget_root,"setBackgroundResource",if(colors.light)R.drawable.widget_background_light else R.drawable.widget_background)
                v.setTextViewText(R.id.tasbeeh_phrase,Adhkar.tasbeeh[s.currentPhrase.coerceIn(0,Adhkar.tasbeeh.lastIndex)])
                v.setTextColor(R.id.tasbeeh_phrase,colors.text)
                v.setTextViewText(R.id.tasbeeh_count,String.format(Locale.ROOT,"%d",s.count))
                v.setTextColor(R.id.tasbeeh_count,colors.adhan)
                v.setTextViewText(R.id.tasbeeh_target,if(s.currentTarget>0)strings.getString(R.string.tasbeeh_of,String.format(Locale.ROOT,"%d",s.currentTarget))
                    else strings.getString(R.string.tasbeeh_open))
                v.setTextColor(R.id.tasbeeh_target,colors.secondary)
                v.setTextViewText(R.id.tasbeeh_open,strings.getString(R.string.widget_tasbeeh_open))
                v.setTextColor(R.id.tasbeeh_open,colors.secondary)
                v.setOnClickPendingIntent(R.id.tasbeeh_button,PendingIntent.getBroadcast(context,830,Intent(context,TasbeehWidget::class.java).setAction(TAP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                v.setOnClickPendingIntent(R.id.tasbeeh_open,openApp(context,241,ACTION_TASBEEH))
                v.setOnClickPendingIntent(R.id.tasbeeh_phrase,openApp(context,241,ACTION_TASBEEH))
                manager.updateAppWidget(ids,v)
            }
        }
    }
}
