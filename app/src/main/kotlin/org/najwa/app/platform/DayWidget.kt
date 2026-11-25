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
import org.najwa.app.R
import org.najwa.core.GoalKind
import org.najwa.core.VirtueKind
import org.najwa.core.WorshipCatalog
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * "My day": the large prayer widget, untouched, with an optional worship strip underneath.
 * The prayer block is the very RemoteViews the classic widget renders at its large size, so prayer, iqama,
 * countdown and phase look and behave exactly the same. The strip shows only confirmed records.
 */
class DayWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {PrayerWidget.updateAll(context)}
    override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) {PrayerWidget.updateAll(context)}

    companion object {
        private fun component(context: Context)=ComponentName(context,DayWidget::class.java)
        fun present(context: Context)=runCatching {AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()}.getOrDefault(false)

        /** Re-renders after a worship record changes; the prayer block is rebuilt with the same inputs as the classic widget. */
        fun update(context: Context) {
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(component(context))
                if(ids.isEmpty())return
                val p=PreferencesStore(context).read()
                val now=Instant.now()
                val prayer=PrayerWidget.views(context,p,now,360,240)
                manager.updateAppWidget(ids,views(context,prayer,LocalDate.now()))
            }
        }

        private fun openApp(context: Context,code: Int,action: String?,tab: Int?=null)=PendingIntent.getActivity(context,code,
            Intent(context,MainActivity::class.java).apply {if(action!=null)setAction(action) else putExtra("open_home",true);if(tab!=null)putExtra("adhkar",tab)}
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun views(context: Context,prayer: RemoteViews,date: LocalDate): RemoteViews {
            val p=PreferencesStore(context).read()
            val strings=context.prayerLanguage()
            val colors=widgetColors(context,p)
            val settings=WorshipSettingsStore.read(context)
            val v=RemoteViews(context.packageName,R.layout.day_widget)
            v.removeAllViews(R.id.day_prayer)
            v.addView(R.id.day_prayer,prayer)
            v.setInt(R.id.day_root,"setLayoutDirection",strings.prayerLayoutDirection)
            val show=settings.enabled && (settings.widgetReward || settings.widgetExpiation || settings.widgetAdhkar)
            v.setViewVisibility(R.id.day_strip,if(show)View.VISIBLE else View.GONE)
            if(!show)return v
            v.setInt(R.id.day_strip,"setBackgroundResource",if(colors.light)R.drawable.day_strip_background_light else R.drawable.day_strip_background)
            val s=WorshipSummary.build(context,settings,date)
            val num=NumberFormat.getIntegerInstance(Locale.US)
            fun cell(visible: Boolean,id: Int)=v.setViewVisibility(id,if(visible)View.VISIBLE else View.GONE)
            listOf(R.id.day_a_title,R.id.day_b_title,R.id.day_c_title,R.id.day_a_note,R.id.day_b_note,R.id.day_c_note).forEach {v.setTextColor(it,colors.secondary)}
            listOf(R.id.day_a_value,R.id.day_b_value,R.id.day_c_value).forEach {v.setTextColor(it,colors.text)}

            // A: the personal goal, or the hoped-for reward, or today's recorded prayers.
            cell(settings.widgetReward,R.id.day_a)
            val goal=s.goal
            val unit=when(s.goalKind) {GoalKind.HASANAT->R.string.day_unit_hasanat;GoalKind.QURAN_PAGES->R.string.day_unit_pages;GoalKind.ADHKAR_ITEMS->R.string.day_unit_adhkar}
            when {
                goal!=null -> {
                    v.setTextViewText(R.id.day_a_title,strings.getString(R.string.day_goal_title))
                    v.setTextViewText(R.id.day_a_value,strings.getString(unit,num.format(goal.value)))
                    v.setViewVisibility(R.id.day_a_bar,View.VISIBLE)
                    v.setProgressBar(R.id.day_a_bar,1000,goal.permille,false)
                    v.setTextViewText(R.id.day_a_note,if(goal.reached)strings.getString(R.string.day_goal_reached)
                        else strings.getString(R.string.day_goal_left,num.format(goal.remaining),num.format(goal.target)))
                }
                s.virtues.hasanat!=null -> {
                    v.setTextViewText(R.id.day_a_title,strings.getString(R.string.day_reward_title))
                    v.setTextViewText(R.id.day_a_value,strings.getString(R.string.day_unit_hasanat,num.format(s.virtues.hasanat)))
                    v.setViewVisibility(R.id.day_a_bar,View.GONE)
                    v.setTextViewText(R.id.day_a_note,strings.getString(R.string.day_reward_note))
                }
                else -> {
                    v.setTextViewText(R.id.day_a_title,strings.getString(R.string.day_prayers_title))
                    v.setTextViewText(R.id.day_a_value,String.format(Locale.ROOT,"%d/5",s.prayed))
                    v.setViewVisibility(R.id.day_a_bar,View.VISIBLE)
                    v.setProgressBar(R.id.day_a_bar,5,s.prayed,false)
                    v.setTextViewText(R.id.day_a_note,if(s.jamaah>0)strings.getString(R.string.day_jamaah,s.jamaah) else "")
                }
            }
            if(s.preview)v.setTextViewText(R.id.day_a_note,strings.getString(R.string.worship_preview_badge))
            v.setOnClickPendingIntent(R.id.day_a,openApp(context,250,null))

            // B: actions linked to forgiveness, when such rules are active; otherwise the Quran reading.
            cell(settings.widgetExpiation,R.id.day_b)
            val expiationActive=WorshipCatalog.rules.any {it.has(VirtueKind.EXPIATION) && (s.preview || it.status==org.najwa.core.EvidenceStatus.APPROVED)}
            if(expiationActive) {
                v.setTextViewText(R.id.day_b_title,strings.getString(R.string.day_forgiveness_title))
                v.setTextViewText(R.id.day_b_value,strings.getString(R.string.day_actions,num.format(s.virtues.expiationActions)))
                v.setTextViewText(R.id.day_b_note,strings.getString(R.string.day_forgiveness_note))
                v.setOnClickPendingIntent(R.id.day_b,openApp(context,251,null))
            } else {
                v.setTextViewText(R.id.day_b_title,strings.getString(R.string.day_quran_title))
                v.setTextViewText(R.id.day_b_value,String.format(Locale.ROOT,"%d/%d",s.pages,s.pageGoal))
                v.setTextViewText(R.id.day_b_note,if(s.letters!=null && s.letters>0)strings.getString(R.string.day_letters,num.format(s.letters)) else strings.getString(R.string.day_pages_note))
                v.setOnClickPendingIntent(R.id.day_b,openApp(context,251,ACTION_QURAN))
            }

            // C: morning adhkar until complete, then evening.
            cell(settings.widgetAdhkar,R.id.day_c)
            val morning=!s.morning.complete
            val (title,routine)=if(morning) R.string.adhkar_morning to s.morning else R.string.adhkar_evening to s.evening
            v.setTextViewText(R.id.day_c_title,strings.getString(title))
            v.setTextViewText(R.id.day_c_value,if(routine.complete)strings.getString(R.string.day_routine_done) else strings.getString(R.string.day_routine_left,routine.left))
            v.setTextViewText(R.id.day_c_note,if(routine.complete)strings.getString(R.string.day_routine_blessing) else "")
            v.setOnClickPendingIntent(R.id.day_c,openApp(context,if(morning)252 else 253,ACTION_ADHKAR,if(morning)0 else 1))
            return v
        }
    }
}
