package org.najwa.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.najwa.app.MainActivity
import org.najwa.app.NajwaApplication
import org.najwa.app.R
import org.najwa.core.EvidenceStatus
import org.najwa.core.GoalKind
import org.najwa.core.PrayerCalculator
import org.najwa.core.PrayerLog
import org.najwa.core.PrayerStatus
import org.najwa.core.Preferences
import org.najwa.core.StepKind
import org.najwa.core.VirtueKind
import org.najwa.core.WorshipCatalog
import org.najwa.core.widgetPrayer
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale

/**
 * "My day": one card for the whole day. The prayer part is filled by [PrayerWidget] with the classic widget's own
 * logic (the layout shares its ids); below it, a mark under each prayer you recorded, the hasanat of the day with
 * what is left to the goal, and three tiles: prayers, the adhkar of the moment and the Quran portion.
 * The card's background follows the transparency chosen in the widget settings. Only confirmed records are shown.
 */
class DayWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {PrayerWidget.updateAll(context)}
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) {PrayerWidget.updateAll(context)}

    companion object {
        private fun component(context: Context)=ComponentName(context,DayWidget::class.java)
        fun present(context: Context)=runCatching {AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()}.getOrDefault(false)

        // Rendering rebuilds the day (stores, prayer times, letters): never on the UI thread, and a burst of taps
        // (counting adhkar) becomes a single refresh.
        private val worker=java.util.concurrent.Executors.newSingleThreadExecutor {r->Thread(r,"najwa-day-widget").apply {isDaemon=true}}
        private val pending=java.util.concurrent.atomic.AtomicBoolean(false)

        /** Re-renders after a worship record changes: the snapshot is dropped now, the widgets are redrawn in the background. */
        fun update(context: Context) {
            DaySnapshot.invalidate()
            val app=context.applicationContext
            if(!pending.compareAndSet(false,true))return
            worker.execute {
                runCatching {Thread.sleep(150)}
                pending.set(false)
                refresh(app)
            }
        }

        /** Draws the Quran widget and every placed day card now, on the calling thread. */
        fun refresh(context: Context) {
            QuranWidget.update(context)
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(component(context))
                if(ids.isEmpty())return
                val p=PreferencesStore(context).read()
                val now=Instant.now()
                // Each placed card gets the rows its height allows (portrait phones report the height as the max).
                ids.forEach {id->
                    val height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,0)
                    manager.updateAppWidget(id,render(context,p,now,if(height>0) height else 400))
                }
            }
        }

        private fun openApp(context: Context,code: Int,action: String?,tab: Int?=null)=PendingIntent.getActivity(context,code,
            Intent(context,MainActivity::class.java).apply {if(action!=null)setAction(action) else putExtra("open_home",true);if(tab!=null)putExtra("adhkar",tab)}
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private val cellIds=listOf(
            Triple(R.id.widget_p0,R.id.widget_p0_name,R.id.widget_p0_time),Triple(R.id.widget_p1,R.id.widget_p1_name,R.id.widget_p1_time),
            Triple(R.id.widget_p2,R.id.widget_p2_name,R.id.widget_p2_time),Triple(R.id.widget_p3,R.id.widget_p3_name,R.id.widget_p3_time),
            Triple(R.id.widget_p4,R.id.widget_p4_name,R.id.widget_p4_time))
        private val marks=listOf(R.id.widget_p0_done,R.id.widget_p1_done,R.id.widget_p2_done,R.id.widget_p3_done,R.id.widget_p4_done)

        /**
         * The whole card for [p] at [now], [height] dp tall; also used by the live preview in the widget settings.
         * A short card drops the tile row first, then the hasanat tile, so the prayer part is never cut.
         */
        fun render(context: Context,p: Preferences,now: Instant,height: Int=400): RemoteViews {
            val colors=widgetColors(context,p)
            val v=PrayerWidget.views(context,p,now,colors=colors,into=R.layout.day_widget)
            runCatching {fill(context,v,p,now,colors)}
            if(height<COMPACT)v.setViewVisibility(R.id.day_chips,View.GONE)
            if(height<SMALL)v.setViewVisibility(R.id.day_stats,View.GONE)
            return v
        }
        private const val COMPACT=340
        private const val SMALL=250

        private fun fill(context: Context,v: RemoteViews,p: Preferences,now: Instant,colors: WidgetColors) {
            val strings=context.prayerLanguage()
            val settings=WorshipSettingsStore.read(context)
            val date=WorshipDay.today(context)
            val runtime=(context.applicationContext as? NajwaApplication)?.runtime
            val log=if(p.prayerLog) runtime?.log() ?: PrayerLog() else null
            v.setTextColor(R.id.day_dot,colors.secondary)
            val tile=if(colors.light)R.drawable.day_tile_light else R.drawable.day_tile
            listOf(R.id.day_a,R.id.day_p,R.id.day_b,R.id.day_c).forEach {v.setInt(it,"setBackgroundResource",tile)}
            listOf(R.id.day_a_title,R.id.day_a_note,R.id.day_p_title,R.id.day_b_title,R.id.day_c_title).forEach {v.setTextColor(it,colors.secondary)}
            listOf(R.id.day_p_value,R.id.day_b_value,R.id.day_c_value).forEach {v.setTextColor(it,colors.text)}
            v.setTextColor(R.id.day_a_value,colors.iqama)
            v.setTextColor(R.id.day_a_badge,colors.iqama)

            // A mark under each prayer of today's list: prayed (gold in congregation), missed, or passed unrecorded.
            val zone=p.city.zoneId
            val data=widgetPrayer(now,p)
            var listDate=(data?.time ?: now).atZone(zone).toLocalDate()
            var events=runCatching {PrayerCalculator().calculate(listDate,p).events.filter {it.prayer.isPrayer}}.getOrDefault(emptyList())
            // After Isha (and until Fajr) the classic list moves to the next day. The day card keeps the worship day's
            // prayers with their marks instead, and shows the coming Fajr in its cell.
            if(listDate!=date && log!=null) {
                val today=runCatching {PrayerCalculator().calculate(date,p).events.filter {it.prayer.isPrayer}}.getOrDefault(emptyList())
                if(today.size==5) {
                    val highlight=if(colors.light)R.drawable.widget_cell_adhan_light else R.drawable.widget_cell_adhan
                    today.forEachIndexed {i,event->
                        val (cell,name,time)=cellIds[i]
                        val next=event.prayer==data?.prayer
                        v.setTextViewText(name,strings.prayerName(event,p))
                        v.setTextViewText(time,strings.prayerTime(if(next && data!=null) data.time else event.time,p))
                        v.setTextColor(name,if(next)colors.adhan else colors.secondary)
                        v.setTextColor(time,if(next)colors.adhan else colors.secondary)
                        v.setInt(cell,"setBackgroundResource",if(next)highlight else 0)
                    }
                    events=today;listDate=date
                }
            }
            events.forEachIndexed {i,event->
                val id=marks.getOrNull(i) ?: return@forEachIndexed
                val status=if(log!=null && listDate==date) log.status(date,event.prayer) else null
                val passed=event.time<=now && event.prayer!=data?.prayer
                when {
                    log==null -> v.setViewVisibility(id,View.GONE)
                    status?.prayed==true -> {
                        v.setViewVisibility(id,View.VISIBLE)
                        v.setImageViewResource(id,R.drawable.ic_widget_done)
                        v.setInt(id,"setColorFilter",if(status==PrayerStatus.JAMAAH)colors.iqama else colors.adhan)
                    }
                    status==PrayerStatus.MISSED || (passed && listDate==date) -> {
                        v.setViewVisibility(id,View.VISIBLE)
                        v.setImageViewResource(id,R.drawable.ic_widget_pending)
                        v.setInt(id,"setColorFilter",if(status==PrayerStatus.MISSED)colors.soon else colors.secondary)
                    }
                    else -> v.setViewVisibility(id,View.INVISIBLE)
                }
            }

            if(!settings.enabled) {v.setViewVisibility(R.id.day_stats,View.GONE);return}
            v.setViewVisibility(R.id.day_stats,View.VISIBLE)
            val s=DaySnapshot.get(context,settings)
            // Counts follow the digits chosen in Appearance, like the rest of the worship pages.
            val num=NumberFormat.getIntegerInstance(if(Counts.arabicDigits(context)) Locale.forLanguageTag("ar-EG") else Locale.US)

            // Hasanat of the day and what is left to the goal (the personal goal when it counts hasanat, else today's portion).
            val goal=s.goal
            val hasanat=s.virtues.hasanat
            // Without hasanat or a goal (the official build until the rules are approved), the wide tile shows today's
            // Quran portion with its bar, so the card never has an empty gap.
            val wirdInA=hasanat==null && goal==null
            val showA=settings.widgetReward
            v.setViewVisibility(R.id.day_a,if(showA)View.VISIBLE else View.GONE)
            if(showA) {
                v.setViewVisibility(R.id.day_a_badge,if(s.preview)View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.day_a_badge,strings.getString(R.string.worship_preview_badge))
                if(hasanat!=null) {
                    val target=if(goal!=null && s.goalKind==GoalKind.HASANAT) goal.target else maxOf(s.hasanatTarget ?: hasanat,hasanat)
                    val left=(target-hasanat).coerceAtLeast(0)
                    v.setTextViewText(R.id.day_a_title,strings.getString(R.string.day_hasanat_title))
                    v.setTextViewText(R.id.day_a_value,num.format(hasanat))
                    v.setProgressBar(R.id.day_a_bar,1000,if(target<=0) 1000 else (hasanat.coerceAtMost(target)*1000/target).toInt(),false)
                    v.setTextViewText(R.id.day_a_note,when {
                        left>0 -> strings.getString(R.string.day_goal_remaining,num.format(left),num.format(target))
                        goal!=null && s.goalKind==GoalKind.HASANAT -> strings.getString(R.string.day_goal_reached)
                        else -> strings.getString(R.string.day_reward_note)
                    })
                } else if(goal!=null) {
                    val unit=when(s.goalKind) {GoalKind.HASANAT->R.string.day_unit_hasanat;GoalKind.QURAN_PAGES->R.string.day_unit_pages;GoalKind.ADHKAR_ITEMS->R.string.day_unit_adhkar}
                    v.setTextViewText(R.id.day_a_title,strings.getString(R.string.day_goal_title))
                    v.setTextViewText(R.id.day_a_value,strings.getString(unit,num.format(goal.value)))
                    v.setProgressBar(R.id.day_a_bar,1000,goal.permille,false)
                    v.setTextViewText(R.id.day_a_note,if(goal.reached)strings.getString(R.string.day_goal_reached)
                        else strings.getString(R.string.day_goal_remaining,num.format(goal.remaining),num.format(goal.target)))
                } else {
                    val left=(s.pageGoal-s.pages).coerceAtLeast(0)
                    v.setTextViewText(R.id.day_a_title,strings.getString(R.string.day_wird_title))
                    v.setTextViewText(R.id.day_a_value,strings.getString(R.string.day_of_five,num.format(s.pages),num.format(s.pageGoal)))
                    v.setProgressBar(R.id.day_a_bar,1000,(s.pages.coerceAtMost(s.pageGoal)*1000/s.pageGoal.coerceAtLeast(1)),false)
                    v.setTextViewText(R.id.day_a_note,if(left==0)strings.getString(R.string.day_wird_done)
                        else strings.getString(R.string.next_left,Counts.pages(strings,left)))
                }
                v.setOnClickPendingIntent(R.id.day_a,if(wirdInA)openApp(context,256,ACTION_QURAN) else openApp(context,250,null))
            }

            // Prayers done today.
            v.setViewVisibility(R.id.day_p,if(p.prayerLog)View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.day_p_title,strings.getString(R.string.day_prayers_title))
            v.setTextViewText(R.id.day_p_value,strings.getString(R.string.day_of_five,num.format(s.prayed),num.format(5)))
            v.setTextColor(R.id.day_p_value,if(s.prayed>=5)colors.adhan else colors.text)
            v.setOnClickPendingIntent(R.id.day_p,openApp(context,254,ACTION_PRAYER_LOG))

            // The adhkar of this moment, from the day engine: morning from Fajr until Asr, evening after.
            v.setViewVisibility(R.id.day_c,if(settings.widgetAdhkar)View.VISIBLE else View.GONE)
            val morning=s.adhkarNow==StepKind.MORNING_ADHKAR
            val (title,routine)=if(morning) R.string.adhkar_morning to s.morning else R.string.adhkar_evening to s.evening
            v.setTextViewText(R.id.day_c_title,strings.getString(title))
            v.setTextViewText(R.id.day_c_value,if(routine.complete)strings.getString(R.string.day_routine_done_mark)
                else strings.getString(R.string.next_left,Counts.adhkar(context,routine.left)))
            v.setTextColor(R.id.day_c_value,if(routine.complete)colors.adhan else colors.text)
            v.setOnClickPendingIntent(R.id.day_c,openApp(context,if(morning)252 else 253,ACTION_ADHKAR,if(morning)0 else 1))

            // Actions linked to forgiveness, when such rules are active; otherwise today's Quran portion.
            v.setViewVisibility(R.id.day_b,if(settings.widgetExpiation)View.VISIBLE else View.GONE)
            val expiationActive=WorshipCatalog.rules.any {it.has(VirtueKind.EXPIATION) && (s.preview || it.status==EvidenceStatus.APPROVED)}
            if(expiationActive) {
                v.setTextViewText(R.id.day_b_title,strings.getString(R.string.day_forgiveness_title))
                v.setTextViewText(R.id.day_b_value,num.format(s.virtues.expiationActions))
                v.setOnClickPendingIntent(R.id.day_b,openApp(context,251,null))
            } else if(wirdInA && settings.widgetReward) {
                // The portion is already in the wide tile: this one counts the adhkar completed today.
                v.setTextViewText(R.id.day_b_title,strings.getString(R.string.day_adhkar_today))
                v.setTextViewText(R.id.day_b_value,Counts.adhkar(strings,s.adhkarItems))
                v.setOnClickPendingIntent(R.id.day_b,openApp(context,251,ACTION_ADHKAR,0))
            } else {
                v.setTextViewText(R.id.day_b_title,strings.getString(R.string.day_wird_title))
                v.setTextViewText(R.id.day_b_value,if(s.pages>=s.pageGoal) strings.getString(R.string.day_wird_done)
                    else strings.getString(R.string.day_of_five,num.format(s.pages),num.format(s.pageGoal)))
                v.setTextColor(R.id.day_b_value,if(s.pages>=s.pageGoal)colors.adhan else colors.text)
                v.setOnClickPendingIntent(R.id.day_b,openApp(context,251,ACTION_QURAN))
            }
            val anyTile=p.prayerLog || settings.widgetAdhkar || settings.widgetExpiation
            v.setViewVisibility(R.id.day_chips,if(anyTile)View.VISIBLE else View.GONE)
        }
    }
}
