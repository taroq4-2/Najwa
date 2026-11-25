package org.najwa.app.platform

import android.content.Context
import java.time.LocalDate

/** Day-by-day counts kept as "2026-11-24=240;2026-11-23=99" (a few weeks only). */
internal object DayCounts {
    fun parse(raw: String?): Map<LocalDate,Int> = raw.orEmpty().split(';').mapNotNull {
        val (d,n)=it.split('=').takeIf {p->p.size==2} ?: return@mapNotNull null
        runCatching {LocalDate.parse(d) to n.toInt()}.getOrNull()
    }.toMap()
    fun format(days: Map<LocalDate,Int>,today: LocalDate,keep: Long=60) =
        days.filterKeys {!it.isBefore(today.minusDays(keep))}.entries.sortedByDescending {it.key}.joinToString(";") {"${it.key}=${it.value}"}
    /** Consecutive days with activity, ending today (or yesterday when today has none yet). */
    fun streak(active: Set<LocalDate>,today: LocalDate): Int {
        var day=if(today in active) today else today.minusDays(1)
        var n=0
        while(day in active) {n++;day=day.minusDays(1)}
        return n
    }
}

/** The tasbeeh counter, shared by the app screen and the home-screen widget. */
object TasbeehStore {
    /** After-prayer tasbeeh: SubhanAllah 33, Alhamdulillah 33, Allahu akbar 34 (Muslim). Indices into the phrase list. */
    val sequence=listOf(0 to 33,1 to 33,2 to 34)

    data class State(val phrase: Int=0,val target: Int=33,val count: Int=0,val total: Int=0,val rounds: Int=0,
                     val sequenceMode: Boolean=false,val step: Int=0,val haptics: Boolean=true,val volumeKeys: Boolean=false,val keepAwake: Boolean=false) {
        val currentPhrase get()=if(sequenceMode) sequence[step].first else phrase
        val currentTarget get()=if(sequenceMode) sequence[step].second else target
    }
    /** What a tap finished: nothing, one round of the target, or the whole after-prayer sequence. */
    enum class Outcome {COUNTED,ROUND,SEQUENCE}

    private fun prefs(context: Context)=context.getSharedPreferences("tasbeeh",Context.MODE_PRIVATE)

    fun read(context: Context): State = prefs(context).let {s->
        State(s.getInt("phrase",0).coerceAtLeast(0),s.getInt("target",33).coerceAtLeast(0),s.getInt("count",0).coerceAtLeast(0),
            s.getInt("total",0).coerceAtLeast(0),s.getInt("rounds",0),s.getBoolean("sequence",false),s.getInt("step",0).coerceIn(0,sequence.lastIndex),
            s.getBoolean("haptics",true),s.getBoolean("volumeKeys",false),s.getBoolean("keepAwake",false))
    }
    fun write(context: Context,s: State) {
        prefs(context).edit().putInt("phrase",s.phrase).putInt("target",s.target).putInt("count",s.count).putInt("total",s.total)
            .putInt("rounds",s.rounds).putBoolean("sequence",s.sequenceMode).putInt("step",s.step).putBoolean("haptics",s.haptics)
            .putBoolean("volumeKeys",s.volumeKeys).putBoolean("keepAwake",s.keepAwake).apply()
    }

    @Synchronized fun tap(context: Context,today: LocalDate=LocalDate.now()): Pair<State,Outcome> {
        val s=read(context)
        val count=s.count+1
        val target=s.currentTarget
        val (next,outcome)=when {
            target<=0 || count<target -> s.copy(count=count) to Outcome.COUNTED
            s.sequenceMode && s.step<sequence.lastIndex -> s.copy(count=0,step=s.step+1) to Outcome.ROUND
            s.sequenceMode -> s.copy(count=0,step=0,rounds=s.rounds+1) to Outcome.SEQUENCE
            else -> s.copy(count=0,rounds=s.rounds+1) to Outcome.ROUND
        }
        val result=next.copy(total=s.total+1)
        write(context,result)
        addToday(context,today,1)
        // A completed round of a phrase with linked evidence becomes one worship record (the sequence is not mapped).
        if(outcome==Outcome.ROUND && !s.sequenceMode && WorshipSettingsStore.read(context).enabled)
            WorshipRecorder.onTasbeehRound(context,s.phrase,target,today)
        return result to outcome
    }
    /** Takes back the last tap of the current round (an accidental double tap). */
    @Synchronized fun undo(context: Context,today: LocalDate=LocalDate.now()): State {
        val s=read(context)
        if(s.count==0)return s
        val result=s.copy(count=s.count-1,total=(s.total-1).coerceAtLeast(0))
        write(context,result)
        addToday(context,today,-1)
        return result
    }

    private fun addToday(context: Context,today: LocalDate,delta: Int) {
        val days=DayCounts.parse(prefs(context).getString("days",null)).toMutableMap()
        days[today]=((days[today] ?: 0)+delta).coerceAtLeast(0)
        prefs(context).edit().putString("days",DayCounts.format(days,today)).apply()
    }
    fun today(context: Context,today: LocalDate=LocalDate.now())=DayCounts.parse(prefs(context).getString("days",null))[today] ?: 0
    fun week(context: Context,today: LocalDate=LocalDate.now())=
        DayCounts.parse(prefs(context).getString("days",null)).filterKeys {!it.isBefore(today.minusDays(6)) && !it.isAfter(today)}.values.sum()
}

/** Today's adhkar progress per category, and the days each category was completed. */
object AdhkarProgressStore {
    private fun prefs(context: Context)=context.getSharedPreferences("adhkar",Context.MODE_PRIVATE)

    /** Remaining counts for today, or null when this category has not been started today. */
    fun remaining(context: Context,category: String,size: Int,today: LocalDate=LocalDate.now()): List<Int>? {
        val raw=prefs(context).getString("p_$category",null) ?: return null
        val (date,values)=raw.split('|').takeIf {it.size==2} ?: return null
        if(date!=today.toString())return null
        return values.split(',').mapNotNull {it.toIntOrNull()}.takeIf {it.size==size}
    }
    fun saveRemaining(context: Context,category: String,remaining: List<Int>,today: LocalDate=LocalDate.now()) {
        prefs(context).edit().putString("p_$category","$today|${remaining.joinToString(",")}").apply()
        if(remaining.all {it==0})markDone(context,category,today)
    }
    private fun markDone(context: Context,category: String,today: LocalDate) {
        val days=doneDays(context,category)+today
        prefs(context).edit().putString("done_$category",days.filter {!it.isBefore(today.minusDays(400))}.sorted().joinToString(",")).apply()
    }
    fun doneDays(context: Context,category: String): Set<LocalDate> =
        prefs(context).getString("done_$category",null).orEmpty().split(',').mapNotNull {runCatching {LocalDate.parse(it)}.getOrNull()}.toSet()
    fun streak(context: Context,category: String,today: LocalDate=LocalDate.now())=DayCounts.streak(doneDays(context,category),today)

    fun textScale(context: Context)=prefs(context).getFloat("textScale",1f).coerceIn(.8f,1.6f)
    fun setTextScale(context: Context,scale: Float)=prefs(context).edit().putFloat("textScale",scale.coerceIn(.8f,1.6f)).apply()
}

/** Quran reading: where you reached in the 604-page Madinah mushaf, a daily goal and completed khatmas. */
object QuranStore {
    const val PAGES=604
    data class State(val page: Int=0,val goal: Int=4,val khatmas: Int=0)

    private fun prefs(context: Context)=context.getSharedPreferences("quran",Context.MODE_PRIVATE)
    fun read(context: Context)=prefs(context).let {State(it.getInt("page",0).coerceIn(0,PAGES),it.getInt("goal",4).coerceIn(1,60),it.getInt("khatmas",0).coerceAtLeast(0))}
    private fun write(context: Context,s: State)=prefs(context).edit().putInt("page",s.page).putInt("goal",s.goal).putInt("khatmas",s.khatmas).apply()
    fun setGoal(context: Context,goal: Int)=write(context,read(context).copy(goal=goal.coerceIn(1,60)))

    /** Records pages read today; passing the last page completes a khatma and starts again. */
    @Synchronized fun addPages(context: Context,pages: Int,today: LocalDate=LocalDate.now()): State {
        if(pages<=0)return read(context)
        val s=read(context)
        var page=s.page+pages
        var khatmas=s.khatmas
        if(page>=PAGES) {khatmas++;page-=PAGES}
        val result=s.copy(page=page.coerceIn(0,PAGES-1),khatmas=khatmas)
        write(context,result)
        addToday(context,today,pages)
        if(WorshipSettingsStore.read(context).enabled)WorshipRecorder.onQuranRead(context,s.page+1,pages,today)
        return result
    }
    /** Takes back the last reading recorded today: the page, the day's count and its worship record. */
    @Synchronized fun undoLast(context: Context,today: LocalDate=LocalDate.now()): State? {
        val last=WorshipRecorder.lastQuranRange(context,today) ?: return null
        val s=read(context)
        val pages=last.count.toInt()
        val wrapped=last.from-1+pages>=PAGES
        val result=s.copy(page=(last.from-1).coerceIn(0,PAGES-1),khatmas=if(wrapped)(s.khatmas-1).coerceAtLeast(0) else s.khatmas)
        write(context,result)
        addToday(context,today,-pages)
        WorshipStore.remove(context,today,last.id)
        DayWidget.update(context)
        return result
    }
    /** Jumps to the page you reached (counted as read today when it moves forward). */
    @Synchronized fun moveTo(context: Context,page: Int,today: LocalDate=LocalDate.now()): State {
        val s=read(context)
        val target=page.coerceIn(0,PAGES)
        if(target>s.page)return addPages(context,target-s.page,today)
        val result=s.copy(page=target)
        write(context,result)
        return result
    }
    private fun addToday(context: Context,today: LocalDate,pages: Int) {
        val days=DayCounts.parse(prefs(context).getString("days",null)).toMutableMap()
        days[today]=((days[today] ?: 0)+pages).coerceAtLeast(0)
        prefs(context).edit().putString("days",DayCounts.format(days,today,400)).apply()
    }
    fun days(context: Context)=DayCounts.parse(prefs(context).getString("days",null))
    fun today(context: Context,today: LocalDate=LocalDate.now())=days(context)[today] ?: 0
    fun streak(context: Context,today: LocalDate=LocalDate.now())=DayCounts.streak(days(context).filterValues {it>0}.keys,today)
}

/** Juz (1–30) of a page in the Madinah mushaf: each juz after the first starts 20 pages later, from page 22. */
fun juzOfPage(page: Int): Int = if(page<22) 1 else ((page-2)/20+1).coerceAtMost(30)
