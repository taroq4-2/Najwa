package org.najwa.app.platform

import android.content.Context
import org.najwa.app.NajwaApplication
import org.najwa.core.DayEngine
import org.najwa.core.Preferences
import org.najwa.core.Prayer
import org.najwa.core.PrayerCalculator
import org.najwa.core.PrayerDay
import java.time.Instant
import java.time.LocalDate

/**
 * The worship day runs from Fajr to the next Fajr in the chosen city, not from midnight. Adhkar, the Quran portion,
 * tasbeeh and the worship records all use it, so evening adhkar finished at 11 pm are still finished at 1 am.
 */
object WorshipDay {
    @Volatile private var cached: Triple<Long,String,LocalDate>?=null

    internal fun preferences(context: Context): Preferences=
        (context.applicationContext as? NajwaApplication)?.runtime?.preferences ?: PreferencesStore(context).read()

    fun today(context: Context,now: Instant=Instant.now()): LocalDate {
        val p=preferences(context)
        val minute=now.epochSecond/60
        val key="${p.city.latitude},${p.city.longitude},${p.city.zone},${p.method},${p.offsets}"
        cached?.let {(m,k,d)->if(m==minute && k==key)return d}
        val date=now.atZone(p.city.zoneId).toLocalDate()
        val fajr=runCatching {PrayerCalculator().calculate(date,p).events.firstOrNull {it.prayer==Prayer.FAJR}?.time}.getOrNull()
        val day=if(fajr!=null && now<fajr) date.minusDays(1) else date
        cached=Triple(minute,key,day)
        return day
    }

    /** The prayer times of a worship day and the next day's Fajr (where that day's Isha ends). */
    fun times(context: Context,date: LocalDate): Pair<PrayerDay,Instant?> {
        val p=preferences(context)
        val calculator=PrayerCalculator()
        val day=calculator.calculate(date,p)
        val nextFajr=calculator.calculate(date.plusDays(1),p).events.firstOrNull {it.prayer==Prayer.FAJR}?.time
        return day to nextFajr
    }

    /** The prayer whose time is running now, if any. */
    fun currentPrayer(context: Context,now: Instant=Instant.now()): Prayer? {
        val (day,nextFajr)=times(context,today(context,now))
        return DayEngine.currentPrayer(now,day,nextFajr)
    }
}

/** After-prayer adhkar completed, per prayer of the worship day (the routine itself is shared by all five). */
object AfterPrayerMarks {
    private fun prefs(context: Context)=context.getSharedPreferences("plan",Context.MODE_PRIVATE)
    fun mark(context: Context,date: LocalDate,prayer: Prayer) {
        val kept=marks(context).filter {runCatching {LocalDate.parse(it.substringBefore(':'))}.getOrNull()?.isAfter(date.minusDays(3))==true}
        prefs(context).edit().putStringSet("afterDone",(kept+"$date:${prayer.name}").toSet()).apply()
    }
    fun done(context: Context,date: LocalDate,prayer: Prayer)="$date:${prayer.name}" in marks(context)
    private fun marks(context: Context)=prefs(context).getStringSet("afterDone",emptySet()).orEmpty()
}

/** A daily share of make-up (qada) prayers, counted when the qada total goes down in the prayer log. */
object QadaPlan {
    private fun prefs(context: Context)=context.getSharedPreferences("plan",Context.MODE_PRIVATE)
    fun perDay(context: Context)=prefs(context).getInt("qadaPerDay",0).coerceIn(0,20)
    fun setPerDay(context: Context,n: Int)=prefs(context).edit().putInt("qadaPerDay",n.coerceIn(0,20)).apply()
    fun doneToday(context: Context,today: LocalDate=WorshipDay.today(context))=DayCounts.parse(prefs(context).getString("qadaDays",null))[today] ?: 0
    /** Called with the change of the qada total: a decrease is prayers made up today, an increase takes them back. */
    fun onTotalChanged(context: Context,before: Int,after: Int,today: LocalDate=WorshipDay.today(context)) {
        if(before==after)return
        val days=DayCounts.parse(prefs(context).getString("qadaDays",null)).toMutableMap()
        days[today]=((days[today] ?: 0)+(before-after)).coerceAtLeast(0)
        prefs(context).edit().putString("qadaDays",DayCounts.format(days,today,400)).apply()
    }
    /** Make-up prayers still due today: the daily share, never more than the total owed. */
    fun leftToday(context: Context,owed: Int)=if(perDay(context)==0) 0 else minOf((perDay(context)-doneToday(context)).coerceAtLeast(0),owed)
}
