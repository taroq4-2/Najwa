package org.najwa.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.najwa.app.MainActivity
import org.najwa.app.R
import org.najwa.core.CountdownPhase
import org.najwa.core.EvidenceStatus
import org.najwa.core.GoalKind
import org.najwa.core.Preferences
import org.najwa.core.PrayerCalculator
import org.najwa.core.PrayerLog
import org.najwa.core.VirtueKind
import org.najwa.core.WidgetPrayer
import org.najwa.core.WorshipCatalog
import org.najwa.core.widgetPrayer
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * "My day": the Najwa widget, modernised. Same card, orbit art, mark, city pill and stage colours as the classic
 * widget, with everything in view: both dates, the countdown to adhan and to iqama, the five prayers with their iqama
 * times and what was prayed, the day's orbit, the worship summary and the race with yourself.
 */
class DayWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {PrayerWidget.updateAll(context)}
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) {PrayerWidget.updateAll(context)}

    companion object {
        private val SOON: Duration=Duration.ofMinutes(10)
        private fun component(context: Context)=ComponentName(context,DayWidget::class.java)
        fun present(context: Context)=runCatching {AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()}.getOrDefault(false)

        fun update(context: Context) {
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(component(context))
                if(ids.isEmpty())return
                manager.updateAppWidget(ids,views(context,Instant.now()))
            }
        }

        private fun openApp(context: Context,code: Int,action: String?,tab: Int?=null)=PendingIntent.getActivity(context,code,
            Intent(context,MainActivity::class.java).apply {if(action!=null)setAction(action) else putExtra("open_home",true);if(tab!=null)putExtra("adhkar",tab)}
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private val cells=listOf(
            arrayOf(R.id.dw_p0,R.id.dw_p0_name,R.id.dw_p0_time,R.id.dw_p0_iqama),
            arrayOf(R.id.dw_p1,R.id.dw_p1_name,R.id.dw_p1_time,R.id.dw_p1_iqama),
            arrayOf(R.id.dw_p2,R.id.dw_p2_name,R.id.dw_p2_time,R.id.dw_p2_iqama),
            arrayOf(R.id.dw_p3,R.id.dw_p3_name,R.id.dw_p3_time,R.id.dw_p3_iqama),
            arrayOf(R.id.dw_p4,R.id.dw_p4_name,R.id.dw_p4_time,R.id.dw_p4_iqama))

        fun views(context: Context,now: Instant): RemoteViews {
            val p=PreferencesStore(context).read()
            val strings=context.prayerLanguage()
            val colors=widgetColors(context,p)
            val runtime=(context.applicationContext as org.najwa.app.NajwaApplication).runtime
            val log=if(p.prayerLog) runtime.log() else PrayerLog()
            val data=widgetPrayer(now,p)
            val v=RemoteViews(context.packageName,R.layout.day_widget)
            val direction=strings.prayerLayoutDirection
            val rtl=direction==View.LAYOUT_DIRECTION_RTL
            v.setInt(R.id.dw_root,"setLayoutDirection",direction)
            v.setImageViewResource(R.id.dw_backdrop,if(colors.light)R.drawable.widget_background_light else R.drawable.widget_background)
            v.setInt(R.id.dw_backdrop,"setImageAlpha",p.widgetOpacity*255/100)
            v.setInt(R.id.dw_mark,"setColorFilter",colors.adhan)
            v.setInt(R.id.dw_refresh,"setColorFilter",colors.secondary)
            v.setInt(R.id.dw_city,"setBackgroundResource",if(colors.light)R.drawable.widget_pill_light else R.drawable.widget_pill)
            listOf(R.id.dw_brand,R.id.dw_prayer).forEach {v.setTextColor(it,colors.text)}
            listOf(R.id.dw_city,R.id.dw_date,R.id.dw_phase,R.id.dw_time,R.id.dw_count_label).forEach {v.setTextColor(it,colors.secondary)}
            v.setTextViewText(R.id.dw_brand,strings.getString(R.string.system_brand))
            v.setTextViewText(R.id.dw_city,p.city.name)

            // Both dates: the weekday and Gregorian date in the interface language, then the Hijri date.
            val today=now.atZone(p.city.zoneId).toLocalDate()
            val locale=strings.resources.configuration.locales[0]
            val gregorian=today.format(DateTimeFormatter.ofPattern("EEEE d MMMM",locale))
            v.setTextViewText(R.id.dw_date,"$gregorian · ${strings.hijriLabel(today,p.hijriOffset)}")

            // Next prayer, its stage colour and the main countdown, decided exactly as in the classic widget.
            val iqama=data?.phase==CountdownPhase.IQAMA
            val praying=data?.phase==CountdownPhase.PRAYER
            val soon=data?.phase==CountdownPhase.ADHAN && Duration.between(now,data.time)<=SOON
            val phaseColor=when {iqama->colors.iqama;praying->colors.prayer;soon->colors.soon;else->colors.adhan}
            v.setTextViewText(R.id.dw_phase,strings.getString(when {iqama->R.string.system_iqama_remaining;praying->R.string.widget_prayer_now;else->R.string.system_next}))
            v.setTextColor(R.id.dw_phase,phaseColor)
            val prayerName=data?.let {strings.prayerName(it.prayer)} ?: strings.getString(R.string.system_unavailable)
            v.setTextViewText(R.id.dw_prayer,prayerName)
            val iqamaAt=data?.let {iqamaInstant(it,p)}
            val time=data?.let {strings.prayerTime(it.time,p)}
            v.setTextViewText(R.id.dw_time,when {
                data==null -> strings.getString(R.string.system_check_calculation)
                iqama -> "${strings.getString(R.string.system_iqama)} $time"
                praying -> strings.getString(R.string.widget_prayer_until,time)
                else -> "$time · ${strings.getString(R.string.system_iqama)} ${strings.prayerTime(iqamaAt!!,p)}"
            })
            v.setTextViewText(R.id.dw_count_label,strings.getString(when {
                iqama->R.string.system_to_iqama
                praying->if(p.autoSilent && data!!.prayer in p.enabled)R.string.widget_ringer_back else R.string.widget_prayer_ends
                else->R.string.system_to_adhan
            }))
            v.setTextColor(R.id.dw_count_label,phaseColor)
            v.setTextColor(R.id.dw_countdown,phaseColor)
            v.setViewVisibility(R.id.dw_countdown,if(data!=null)View.VISIBLE else View.GONE)
            if(data!=null) {
                v.setChronometerCountDown(R.id.dw_countdown,true)
                v.setChronometer(R.id.dw_countdown,SystemClock.elapsedRealtime()+data.time.toEpochMilli()-now.toEpochMilli(),null,true)
            }
            // The second clock runs only while the adhan is still ahead: time left to the iqama of that prayer.
            val showIqama=data!=null && data.phase==CountdownPhase.ADHAN && iqamaAt!=null
            v.setViewVisibility(R.id.dw_iqama_box,if(showIqama)View.VISIBLE else View.GONE)
            if(showIqama) {
                v.setTextViewText(R.id.dw_iqama_label,strings.getString(R.string.system_to_iqama))
                v.setChronometerCountDown(R.id.dw_iqama_countdown,true)
                v.setChronometer(R.id.dw_iqama_countdown,SystemClock.elapsedRealtime()+iqamaAt!!.toEpochMilli()-now.toEpochMilli(),null,true)
            }

            // The five prayers of the highlighted prayer's day, with iqama times and what was prayed.
            val dayDate=(data?.time ?: now).atZone(p.city.zoneId).toLocalDate()
            val events=runCatching {PrayerCalculator().calculate(dayDate,p).events.filter {it.prayer.isPrayer}}.getOrDefault(emptyList())
            val highlight=when {
                iqama -> if(colors.light)R.drawable.widget_cell_iqama_light else R.drawable.widget_cell_iqama
                praying -> if(colors.light)R.drawable.widget_cell_prayer_light else R.drawable.widget_cell_prayer
                soon -> if(colors.light)R.drawable.widget_cell_soon_light else R.drawable.widget_cell_soon
                else -> if(colors.light)R.drawable.widget_cell_adhan_light else R.drawable.widget_cell_adhan
            }
            val prayedFlags=events.map {log.status(dayDate,it.prayer)?.prayed==true}
            if(events.size==5) {
                v.setViewVisibility(R.id.dw_day,View.VISIBLE)
                v.setViewVisibility(R.id.dw_orbit,View.VISIBLE)
                events.forEachIndexed {i,event->
                    val (cell,name,timeId,iqamaId)=cells[i]
                    val active=event.prayer==data?.prayer
                    val passed=!active && event.time<=now
                    val prayed=prayedFlags[i]
                    v.setTextViewText(name,(if(prayed)"✓ " else "")+strings.prayerName(event,p))
                    v.setTextViewText(timeId,strings.prayerTime(event.time,p))
                    v.setTextViewText(iqamaId,strings.prayerTime(event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L),p))
                    v.setTextColor(name,if(active)phaseColor else if(prayed)colors.adhan else colors.secondary)
                    v.setTextColor(timeId,when {active->phaseColor;passed->colors.secondary;else->colors.text})
                    v.setTextColor(iqamaId,if(active)phaseColor else colors.secondary)
                    v.setInt(cell,"setBackgroundResource",if(active)highlight else 0)
                }
                v.setImageViewBitmap(R.id.dw_orbit,orbit(events.map {it.time},prayedFlags,events.indexOfFirst {it.prayer==data?.prayer},now,colors,phaseColor,rtl))
            } else {
                v.setViewVisibility(R.id.dw_day,View.GONE)
                v.setViewVisibility(R.id.dw_orbit,View.GONE)
            }

            fillWorship(context,v,strings,colors,log,today,p)
            v.setContentDescription(R.id.dw_root,listOfNotNull(strings.getString(R.string.system_brand),p.city.name,gregorian,
                strings.hijriLabel(today,p.hijriOffset),prayerName,time).joinToString(" · "))
            v.setOnClickPendingIntent(R.id.dw_root,openApp(context,250,null))
            v.setOnClickPendingIntent(R.id.dw_refresh,PendingIntent.getBroadcast(context,401,Intent(context,PrayerWidget::class.java).setAction("org.najwa.widget.REFRESH"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return v
        }

        private fun iqamaInstant(data: WidgetPrayer,p: Preferences): Instant? = when(data.phase) {
            CountdownPhase.ADHAN -> data.time.plusSeconds(p.iqamaMinutes.getValue(data.prayer)*60L)
            CountdownPhase.IQAMA -> data.time
            CountdownPhase.PRAYER -> null
        }

        /** Hoped-for reward and goal, hope of forgiveness, adhkar, and the race with yourself. */
        private fun fillWorship(context: Context,v: RemoteViews,strings: Context,colors: WidgetColors,log: PrayerLog,today: LocalDate,p: Preferences) {
            val settings=WorshipSettingsStore.read(context)
            val show=settings.enabled && (settings.widgetReward || settings.widgetExpiation || settings.widgetAdhkar)
            v.setViewVisibility(R.id.dw_worship,if(show)View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.dw_race,if(settings.enabled && p.prayerLog)View.VISIBLE else View.GONE)
            val num=NumberFormat.getIntegerInstance(Locale.US)
            val card=if(colors.light)R.drawable.dw_card_light else R.drawable.dw_card
            listOf(R.id.dw_a,R.id.dw_b,R.id.dw_c).forEach {v.setInt(it,"setBackgroundResource",card)}
            listOf(R.id.dw_a_title,R.id.dw_b_title,R.id.dw_c_title,R.id.dw_a_note,R.id.dw_b_note,R.id.dw_c_note).forEach {v.setTextColor(it,colors.secondary)}
            listOf(R.id.dw_a_value,R.id.dw_b_value,R.id.dw_c_value).forEach {v.setTextColor(it,colors.text)}
            if(settings.enabled && p.prayerLog) {
                // The race with yourself: your run of complete days against your record, and this week against last.
                val streak=log.streak(today);val best=maxOf(log.bestStreak(),streak)
                val week=log.total(today,7);val last=log.total(today.minusDays(7),7)
                val run=when {
                    best==0 -> strings.getString(R.string.dw_race_start)
                    streak>0 && streak>=best -> strings.getString(R.string.dw_race_record,streak)
                    else -> strings.getString(R.string.dw_race_streak,streak,best)
                }
                val weekText=if(week>last)strings.getString(R.string.dw_race_up,week-last) else strings.getString(R.string.dw_race_week,week)
                v.setTextViewText(R.id.dw_race,"★  $run · $weekText")
                v.setOnClickPendingIntent(R.id.dw_race,openApp(context,254,ACTION_PRAYER_LOG))
            }
            if(!show)return
            val s=WorshipSummary.build(context,settings)
            v.setViewVisibility(R.id.dw_a,if(settings.widgetReward)View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.dw_b,if(settings.widgetExpiation)View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.dw_c,if(settings.widgetAdhkar)View.VISIBLE else View.GONE)

            // A: hoped-for reward with the day's goal; without an active rule, the goal or the Quran reading.
            val goal=s.goal
            val hasanat=s.virtues.hasanat
            when {
                hasanat!=null -> {
                    v.setTextViewText(R.id.dw_a_title,strings.getString(R.string.dw_reward_title))
                    v.setTextViewText(R.id.dw_a_value,strings.getString(R.string.dw_hasanat,num.format(hasanat)))
                    if(goal!=null && s.goalKind==GoalKind.HASANAT) {
                        v.setViewVisibility(R.id.dw_a_bar,View.VISIBLE)
                        v.setProgressBar(R.id.dw_a_bar,1000,goal.permille,false)
                        val line=if(goal.reached)strings.getString(R.string.dw_goal_reached,num.format(goal.target))
                            else strings.getString(R.string.dw_goal_line,goal.permille/10,num.format(goal.remaining),num.format(goal.target))
                        // Unapproved figures always carry their label.
                        v.setTextViewText(R.id.dw_a_note,if(s.preview)line+"\n"+strings.getString(R.string.worship_preview_badge) else line)
                    } else {
                        v.setViewVisibility(R.id.dw_a_bar,View.GONE)
                        v.setTextViewText(R.id.dw_a_note,strings.getString(if(s.preview)R.string.worship_preview_badge else R.string.day_reward_note))
                    }
                }
                goal!=null -> {
                    v.setTextViewText(R.id.dw_a_title,strings.getString(R.string.day_goal_title))
                    v.setTextViewText(R.id.dw_a_value,strings.getString(when(s.goalKind) {GoalKind.HASANAT->R.string.day_unit_hasanat;GoalKind.QURAN_PAGES->R.string.day_unit_pages;GoalKind.ADHKAR_ITEMS->R.string.day_unit_adhkar},num.format(goal.value)))
                    v.setViewVisibility(R.id.dw_a_bar,View.VISIBLE)
                    v.setProgressBar(R.id.dw_a_bar,1000,goal.permille,false)
                    v.setTextViewText(R.id.dw_a_note,if(goal.reached)strings.getString(R.string.dw_goal_reached,num.format(goal.target))
                        else strings.getString(R.string.dw_goal_line,goal.permille/10,num.format(goal.remaining),num.format(goal.target)))
                }
                else -> {
                    v.setTextViewText(R.id.dw_a_title,strings.getString(R.string.day_quran_title))
                    v.setTextViewText(R.id.dw_a_value,strings.getString(R.string.day_unit_pages,String.format(Locale.ROOT,"%d/%d",s.pages,s.pageGoal)))
                    v.setViewVisibility(R.id.dw_a_bar,View.VISIBLE)
                    v.setProgressBar(R.id.dw_a_bar,1000,if(s.pageGoal>0)(s.pages*1000/s.pageGoal).coerceAtMost(1000) else 0,false)
                    v.setTextViewText(R.id.dw_a_note,if(s.letters!=null && s.letters>0)strings.getString(R.string.day_letters,num.format(s.letters)) else strings.getString(R.string.day_pages_note))
                }
            }
            v.setOnClickPendingIntent(R.id.dw_a,openApp(context,251,if(hasanat==null && goal==null)ACTION_QURAN else null))

            // B: hope of forgiveness when its rules are active, otherwise today's prayers.
            val expiationActive=WorshipCatalog.rules.any {it.has(VirtueKind.EXPIATION) && (s.preview || it.status==EvidenceStatus.APPROVED)}
            if(expiationActive) {
                v.setTextViewText(R.id.dw_b_title,strings.getString(R.string.dw_forgiveness_title))
                v.setTextViewText(R.id.dw_b_value,strings.getString(R.string.day_actions,num.format(s.virtues.expiationActions)))
                v.setTextViewText(R.id.dw_b_note,strings.getString(if(s.preview)R.string.worship_preview_badge else R.string.day_forgiveness_note))
                v.setOnClickPendingIntent(R.id.dw_b,openApp(context,252,null))
            } else {
                v.setTextViewText(R.id.dw_b_title,strings.getString(R.string.day_prayers_title))
                v.setTextViewText(R.id.dw_b_value,String.format(Locale.ROOT,"%d/5",s.prayed))
                v.setTextViewText(R.id.dw_b_note,if(s.jamaah>0)strings.getString(R.string.day_jamaah,s.jamaah) else strings.getString(R.string.log_title))
                v.setOnClickPendingIntent(R.id.dw_b,openApp(context,252,ACTION_PRAYER_LOG))
            }

            // C: morning adhkar until complete, then evening; a shield marks a completed routine.
            val morning=!s.morning.complete
            val routine=if(morning)s.morning else s.evening
            v.setTextViewText(R.id.dw_c_title,(if(routine.complete)"⛉ " else "")+strings.getString(if(morning)R.string.adhkar_morning else R.string.adhkar_evening))
            v.setTextViewText(R.id.dw_c_value,if(routine.complete)strings.getString(R.string.day_routine_done) else strings.getString(R.string.day_routine_left,routine.left))
            v.setTextViewText(R.id.dw_c_note,when {
                routine.complete -> strings.getString(R.string.day_routine_blessing)
                !morning -> strings.getString(R.string.dw_morning_done)
                else -> strings.getString(R.string.dw_evening_left,s.evening.left)
            })
            v.setOnClickPendingIntent(R.id.dw_c,openApp(context,if(morning)253 else 255,ACTION_ADHKAR,if(morning)0 else 1))
        }

        /**
         * The day's orbit: an arc through the five prayers (one under each prayer column), lit where you prayed,
         * with the sun where the day is now. Mirrored for right-to-left languages.
         */
        internal fun orbit(times: List<Instant>,prayed: List<Boolean>,current: Int,now: Instant,colors: WidgetColors,accent: Int,rtl: Boolean,
                           width: Int=720,height: Int=96): Bitmap {
            val bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val c=Canvas(bmp)
            val w=width.toFloat();val h=height.toFloat()
            fun xOf(i: Float)=(if(rtl)(4.5f-i) else (i+.5f))*w/5f
            fun yOf(x: Float)=h*.82f-h*.58f*sin(PI*(x/w)).toFloat()
            val track=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=3f;color=colors.secondary;alpha=110
                pathEffect=DashPathEffect(floatArrayOf(6f,10f),0f)}
            val path=Path()
            for(px in 0..width step 6) {val x=px.toFloat();if(px==0)path.moveTo(x,yOf(x)) else path.lineTo(x,yOf(x))}
            c.drawPath(path,track)
            // Where the day is: between two prayers the sun moves in proportion to the time between them.
            val position=when {
                now<times.first() -> -.35f
                now>=times.last() -> 4f+(Duration.between(times.last(),now).toMinutes()/240f).coerceAtMost(.45f)
                else -> {
                    val i=times.indexOfLast {it<=now}
                    i+Duration.between(times[i],now).toMillis().toFloat()/Duration.between(times[i],times[i+1]).toMillis().coerceAtLeast(1)
                }
            }
            // The travelled part of the day in dawn-gold.
            val lit=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=5f;strokeCap=Paint.Cap.ROUND;color=colors.iqama}
            val from=xOf(-.5f);val to=xOf(position.coerceIn(-.5f,4.5f))
            val litPath=Path();var first=true
            val step=if(to>=from)6f else -6f
            var x=from
            while(if(step>0)x<=to else x>=to) {if(first) {litPath.moveTo(x,yOf(x));first=false} else litPath.lineTo(x,yOf(x));x+=step}
            if(!first)c.drawPath(litPath,lit)
            val fill=Paint(Paint.ANTI_ALIAS_FLAG)
            val ring=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=3f}
            val tick=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeWidth=3f;strokeCap=Paint.Cap.ROUND;color=0xFF0B1F1A.toInt()}
            for(i in 0 until 5) {
                val cx=xOf(i.toFloat());val cy=yOf(cx)
                when {
                    i==current -> {fill.color=accent;fill.alpha=70;c.drawCircle(cx,cy,17f,fill);fill.alpha=255;c.drawCircle(cx,cy,9f,fill)}
                    prayed.getOrElse(i) {false} -> {
                        fill.color=colors.adhan;fill.alpha=255;c.drawCircle(cx,cy,10f,fill)
                        c.drawPath(Path().apply {moveTo(cx-5f,cy);lineTo(cx-1f,cy+4f);lineTo(cx+5f,cy-4f)},tick)
                    }
                    else -> {ring.color=colors.secondary;c.drawCircle(cx,cy,8f,ring)}
                }
            }
            // The sun: a dawn-gold disc with a soft halo.
            val sx=xOf(position.coerceIn(-.45f,4.45f));val sy=yOf(sx)-2f
            fill.color=colors.iqama;fill.alpha=60;c.drawCircle(sx,sy,15f,fill)
            fill.alpha=255;c.drawCircle(sx,sy,7f,fill)
            return bmp
        }
    }
}
