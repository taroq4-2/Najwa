package org.najwa.app.platform

import android.content.Context
import org.najwa.app.NajwaApplication
import org.najwa.app.ui.Adhkar
import org.najwa.core.DailyGoal
import org.najwa.core.DayVirtues
import org.najwa.core.GoalKind
import org.najwa.core.GoalProgress
import org.najwa.core.PrayerLog
import org.najwa.core.PrayerStatus
import org.najwa.core.RoutineState
import org.najwa.core.RulePolicy
import org.najwa.core.TimeWindow
import org.najwa.core.WorshipActions
import org.najwa.core.WorshipCatalog
import org.najwa.core.WorshipEngine
import org.najwa.core.WorshipRecord
import org.najwa.core.dailyPrayers
import java.time.LocalDate

/**
 * Letters per page of the 604-page Madinah mushaf, built offline with [QuranLetters] (method version in the file header).
 * The app ships no Quran text; until assets/quran/page_letters.txt is added, letter counts are simply unavailable.
 */
object QuranPageLetters {
    @Volatile private var cache: IntArray?=null
    @Volatile private var loaded=false

    fun table(context: Context): IntArray? {
        if(loaded)return cache
        synchronized(this) {
            if(!loaded) {
                cache=runCatching {
                    context.assets.open("quran/page_letters.txt").bufferedReader().useLines {lines->
                        val rows=lines.filterNot {it.isBlank() || it.startsWith("#")}.map {it.trim().toInt()}.toList()
                        // Only a complete table built with the current method is trusted.
                        rows.takeIf {it.size==QuranStore.PAGES && it.all {n->n>0}}?.toIntArray()
                    }
                }.getOrNull()
                loaded=true
            }
        }
        return cache
    }

    /** Letters on pages [from]..from+pages-1, wrapping after the last page; null without a table. */
    fun letters(context: Context,from: Int,pages: Int): Long? {
        val t=table(context) ?: return null
        var sum=0L
        for(i in 0 until pages.coerceIn(0,QuranStore.PAGES)) sum+=t[(from-1+i).mod(QuranStore.PAGES)]
        return sum
    }
}

/** Everything the home card and the day widget show, rebuilt from the records each time. */
data class DaySummary(
    val date: LocalDate,
    val prayed: Int,
    val jamaah: Int,
    val pages: Int,
    val pageGoal: Int,
    val letters: Long?,
    val morning: RoutineState,
    val evening: RoutineState,
    val adhkarItems: Int,
    val virtues: DayVirtues,
    val preview: Boolean,
    val goalKind: GoalKind,
    val goal: GoalProgress?,
)

object WorshipSummary {
    private val routineSizes=mapOf("morning" to Adhkar.morning.size,"evening" to Adhkar.evening.size,"after" to Adhkar.afterPrayer.size,
        "sleep" to Adhkar.sleep.size,"waking" to Adhkar.waking.size,"mosque" to Adhkar.mosque.size)

    /** Prayers come straight from the prayer log; marking, correcting or clearing a prayer there is reflected here. */
    fun prayerRecords(log: PrayerLog,date: LocalDate): List<WorshipRecord> = dailyPrayers.flatMap {prayer->
        val status=log.status(date,prayer)
        buildList {
            if(status?.prayed==true)add(WorshipRecord("p:$prayer",WorshipActions.PRAYER,1,TimeWindow.ANY_TIME,date))
            if(status==PrayerStatus.JAMAAH)add(WorshipRecord("j:$prayer",WorshipActions.PRAYER_JAMAAH,1,TimeWindow.ANY_TIME,date))
        }
    }

    /**
     * [date] is the phone's date, like the adhkar, tasbeeh and Quran stores; prayers use the city's date, like the prayer log.
     */
    fun build(context: Context,settings: WorshipSettings,date: LocalDate=LocalDate.now()): DaySummary {
        val runtime=(context.applicationContext as NajwaApplication).runtime
        val log=if(runtime.preferences.prayerLog) runtime.log() else PrayerLog()
        val prayerDate=if(date==LocalDate.now()) LocalDate.now(runtime.preferences.city.zoneId) else date
        val entries=WorshipStore.entries(context,date)
        val ranges=entries.filter {it.action==WorshipStore.QURAN_RANGE}
        val rangeLetters=ranges.map {it to QuranPageLetters.letters(context,it.from,it.count.toInt())}
        val letters=if(QuranPageLetters.table(context)==null) null else rangeLetters.sumOf {it.second ?: 0L}
        val records=entries.filter {it.action!=WorshipStore.QURAN_RANGE}.map {it.record(date)} +
            rangeLetters.mapNotNull {(e,n)->n?.let {WorshipRecord(e.id,WorshipActions.QURAN_LETTER,it,TimeWindow.ANY_TIME,date)}} +
            prayerRecords(log,prayerDate)
        val policy=if(settings.previewActive) RulePolicy.REVIEW else RulePolicy.RELEASE
        val virtues=WorshipEngine.evaluate(records,WorshipCatalog.rules,policy)
        fun routine(key: String)=routineSizes.getValue(key).let {size->
            RoutineState(AdhkarProgressStore.remaining(context,key,size,date)?.count {it==0} ?: 0,size)
        }
        val adhkarItems=routineSizes.keys.sumOf {key->AdhkarProgressStore.remaining(context,key,routineSizes.getValue(key),date)?.count {it==0} ?: 0}
        val pages=QuranStore.today(context,date)
        val value: Long?=when(settings.goalKind) {
            GoalKind.HASANAT->virtues.hasanat
            GoalKind.QURAN_PAGES->pages.toLong()
            GoalKind.ADHKAR_ITEMS->adhkarItems.toLong()
        }
        return DaySummary(date,dailyPrayers.count {log.prayed(prayerDate,it)},dailyPrayers.count {log.status(prayerDate,it)==PrayerStatus.JAMAAH},
            pages,QuranStore.read(context).goal,letters,routine("morning"),routine("evening"),adhkarItems,virtues,
            settings.previewActive,settings.goalKind,DailyGoal.progress(settings.goal,value))
    }
}
