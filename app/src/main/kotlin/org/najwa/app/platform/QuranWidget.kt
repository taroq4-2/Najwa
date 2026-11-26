package org.najwa.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.najwa.app.MainActivity
import org.najwa.app.R

/** The mushaf on the home screen: the surah at your khatma bookmark, the page, and today's portion as a bar. */
class QuranWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {update(context)}

    companion object {
        fun update(context: Context) {
            runCatching {
                val manager=AppWidgetManager.getInstance(context)
                val ids=manager.getAppWidgetIds(ComponentName(context,QuranWidget::class.java))
                if(ids.isEmpty())return
                val p=PreferencesStore(context).read()
                val colors=widgetColors(context,p)
                val strings=context.prayerLanguage()
                val q=QuranStore.read(context)
                val wird=QuranStore.wird(context)
                val page=(q.page+1).coerceIn(1,QuranStore.PAGES)
                val d=Mushaf.load(context)
                val v=RemoteViews(context.packageName,R.layout.quran_widget)
                v.setInt(R.id.widget_root,"setLayoutDirection",strings.prayerLayoutDirection)
                v.setInt(R.id.widget_root,"setBackgroundResource",if(colors.light)R.drawable.widget_background_light else R.drawable.widget_background)
                v.setTextViewText(R.id.quran_label,strings.getString(R.string.mushaf_khatma_mark))
                v.setTextViewText(R.id.quran_surah,d.surahsOn(page).joinToString(" · ") {"سورة ${it.name}"})
                v.setTextViewText(R.id.quran_where,Counts.digits(context,strings.getString(R.string.mushaf_where,d.juzOf(page),page)))
                v.setProgressBar(R.id.quran_bar,1000,(wird.read.coerceAtMost(wird.goal)*1000/wird.goal.coerceAtLeast(1)),false)
                v.setTextViewText(R.id.quran_note,if(wird.done) strings.getString(R.string.day_wird_done)
                    else strings.getString(R.string.next_left,Counts.pages(context,wird.left)))
                listOf(R.id.quran_label,R.id.quran_where,R.id.quran_note).forEach {v.setTextColor(it,colors.secondary)}
                v.setTextColor(R.id.quran_surah,colors.text)
                val open=PendingIntent.getActivity(context,254,Intent(context,MainActivity::class.java).setAction(ACTION_QURAN).putExtra("page",page)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                v.setOnClickPendingIntent(R.id.widget_root,open)
                manager.updateAppWidget(ids,v)
            }
        }
    }
}
