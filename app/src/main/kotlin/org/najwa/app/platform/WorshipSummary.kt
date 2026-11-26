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
import org.najwa.core.silenceFor
import org.najwa.core.DayEngine
import org.najwa.core.DayProgress
import org.najwa.core.NextStep
import org.najwa.core.Prayer
import org.najwa.core.PrayerCalculator
import org.najwa.core.StepKind
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
    /** Letters of today's whole portion: what was read plus the pages still due after your khatma position. */
    val wirdLetters: Long?=null,
    /** Hoped-for hasanat if today's portion is completed (null whenever [DayVirtues.hasanat] is null). */
    val hasanatTarget: Long?=null,
    /** The day engine's answer to "what now?", shared by the home card, the widget and the reminders. */
    val next: NextStep=NextStep(StepKind.DONE,0),
    /** Morning adhkar from Fajr until Asr, evening adhkar after. */
    val adhkarNow: StepKind=StepKind.MORNING_ADHKAR,
    val sleep: RoutineState=RoutineState(0,0),
) {
    val wirdLeft get()=(pageGoal-pages).coerceAtLeast(0)
}

/**
 * One snapshot of the day for every surface (home, widget, reminders): rebuilt at most once a minute, and at once
 * after any record changes ([invalidate] is called from [DayWidget.update]).
 */
object DaySnapshot {
    @Volatile private var cache: Pair<String,DaySummary>?=null
    @Volatile private var version=0
    fun invalidate() {version++;cache=null}
    fun get(context: Context,settings: WorshipSettings=WorshipSettingsStore.read(context)): DaySummary {
        val key="${System.currentTimeMillis()/60_000}:$version:${settings.hashCode()}:${WorshipDay.today(context)}"
        cache?.let {(k,s)->if(k==key)return s}
        return WorshipSummary.build(context,settings).also {cache=key to it}
    }
}

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
    fun build(context: Context,settings: WorshipSettings,date: LocalDate=WorshipDay.today(context)): DaySummary {
        val runtime=(context.applicationContext as NajwaApplication).runtime
        val log=if(runtime.preferences.prayerLog) runtime.log() else PrayerLog()
        // The worship day (Fajr to Fajr) is also the prayer log's day.
        val prayerDate=date
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
        // Target: the same rules applied as if the rest of today's portion were read too.
        val quran=QuranStore.read(context)
        val due=(quran.goal-pages).coerceAtLeast(0)
        val dueLetters=if(due==0) 0L else QuranPageLetters.letters(context,quran.page+1,due)
        val wirdLetters=if(letters==null || dueLetters==null) null else letters+dueLetters
        val hasanatTarget=when {
            virtues.hasanat==null -> null
            dueLetters==null || dueLetters==0L -> virtues.hasanat
            else -> WorshipEngine.evaluate(records+WorshipRecord("wird.target",WorshipActions.QURAN_LETTER,dueLetters,TimeWindow.ANY_TIME,date),
                WorshipCatalog.rules,policy).hasanat
        }
        // What now: from today's prayer times (the city's) and what is still open.
        val morning=routine("morning");val evening=routine("evening");val sleep=routine("sleep")
        val now=java.time.Instant.now()
        val (prayerDay,nextFajr)=WorshipDay.times(context,prayerDate)
        val times=prayerDay.events.associate {it.prayer to it.time}
        val (fajr,asr,isha)=Triple(times[Prayer.FAJR],times[Prayer.ASR],times[Prayer.ISHA])
        // The prayers themselves, when the log is on: the one running now, its after-prayer adhkar, earlier ones unrecorded.
        val prefs=runtime.preferences
        val current=if(prefs.prayerLog) DayEngine.currentPrayer(now,prayerDay,nextFajr) else null
        val duePrayer=current?.takeIf {log.status(prayerDate,it)==null}
        val recent=current?.takeIf {log.prayed(prayerDate,it)}?.takeIf {p->
            val adhan=times[p] ?: return@takeIf false
            val event=prayerDay.events.first {it.prayer==p}
            now.isBefore(adhan.plusSeconds((prefs.silenceFor(event)+20)*60L)) && !AfterPrayerMarks.done(context,prayerDate,p)
        }
        val after=routine("after")
        val unrecorded=if(!prefs.prayerLog) 0 else dailyPrayers.count {p->
            p!=current && log.status(prayerDate,p)==null && DayEngine.prayerWindowEnd(p,prayerDay,nextFajr)?.let {now.isAfter(it)}==true
        }
        val progress=DayProgress(morning.left,evening.left,sleep.left,(quran.goal-pages).coerceAtLeast(0),
            duePrayer=duePrayer,afterPrayer=recent,afterLeft=if(after.complete && recent!=null) after.total else after.left,
            qadaLeft=QadaPlan.leftToday(context,log.qada),unrecordedEarlier=unrecorded)
        val next=if(fajr==null || asr==null || isha==null) NextStep(if(progress.wirdLeft>0)StepKind.WIRD else StepKind.DONE,progress.wirdLeft)
            else DayEngine.next(now,fajr,asr,isha,progress)
        val adhkarNow=if(fajr==null || asr==null) (if(morning.complete)StepKind.EVENING_ADHKAR else StepKind.MORNING_ADHKAR)
            else DayEngine.adhkarOfTheMoment(now,fajr,asr)
        return DaySummary(date,dailyPrayers.count {log.prayed(prayerDate,it)},dailyPrayers.count {log.status(prayerDate,it)==PrayerStatus.JAMAAH},
            pages,quran.goal,letters,morning,evening,adhkarItems,virtues,
            settings.previewActive,settings.goalKind,DailyGoal.progress(settings.goal,value),wirdLetters,hasanatTarget,next,adhkarNow,sleep)
    }
}
