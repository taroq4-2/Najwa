package org.najwa.app.platform

import android.content.Context
import org.najwa.core.dailyPrayers

/** The last seven worship days in a few numbers, for the Friday summary. */
object WeeklySummary {
    data class Week(val prayers: Int,val possible: Int,val pages: Int,val adhkarDays: Int,val khatmaPercent: Int)

    fun build(context: Context): Week {
        val today=WorshipDay.today(context)
        val days=(0L..6L).map {today.minusDays(it)}
        val log=PrayerLogStore(context).read()
        val prayers=days.sumOf {d->dailyPrayers.count {log.prayed(d,it)}}
        val pages=QuranStore.days(context).filterKeys {it in days}.values.sum()
        val morning=AdhkarProgressStore.doneDays(context,"morning");val evening=AdhkarProgressStore.doneDays(context,"evening")
        val adhkarDays=days.count {it in morning && it in evening}
        val q=QuranStore.read(context)
        return Week(prayers,days.size*dailyPrayers.size,pages,adhkarDays,q.page*100/QuranStore.PAGES)
    }
}
