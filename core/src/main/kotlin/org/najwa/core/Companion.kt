package org.najwa.core

import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

// ───────────────────────────── Hijri helpers ─────────────────────────────

/** Umm al-Qura date shifted by the person's local correction; null outside the supported range. */
fun hijriDate(date: LocalDate, p: Preferences): HijrahDate? =
    runCatching {HijrahDate.from(date).plus(p.hijriOffset.toLong(),ChronoUnit.DAYS)}.getOrNull()
fun hijriMonth(date: LocalDate, p: Preferences) = hijriDate(date,p)?.get(ChronoField.MONTH_OF_YEAR)
fun hijriDay(date: LocalDate, p: Preferences) = hijriDate(date,p)?.get(ChronoField.DAY_OF_MONTH)
fun isRamadan(date: LocalDate, p: Preferences) = hijriMonth(date,p)==9

/** Why [date] is a recommended (sunnah) fasting day, or null. Eid days and Ramadan are excluded. */
enum class FastReason { ARAFAH, TASUA, ASHURA, WHITE_DAYS, MONDAY, THURSDAY }
fun fastingReason(date: LocalDate, p: Preferences): FastReason? {
    val month=hijriMonth(date,p) ?: return null
    val day=hijriDay(date,p) ?: return null
    if(month==9 || month==10 && day==1 || month==12 && day in 10..13) return null
    return when {
        month==12 && day==9 -> FastReason.ARAFAH
        month==1 && day==9 -> FastReason.TASUA
        month==1 && day==10 -> FastReason.ASHURA
        day in 13..15 -> FastReason.WHITE_DAYS
        date.dayOfWeek==DayOfWeek.MONDAY -> FastReason.MONDAY
        date.dayOfWeek==DayOfWeek.THURSDAY -> FastReason.THURSDAY
        else -> null
    }
}

// ───────────────────────────── Extra times ─────────────────────────────

/** Duha (15 minutes after sunrise), the middle of the night and the start of its last third. */
data class ExtraTimes(val duha: Instant, val midnight: Instant, val lastThird: Instant)
private fun PrayerDay.time(prayer: Prayer) = events.first {it.prayer==prayer}.time
fun extraTimes(today: PrayerDay, tomorrow: PrayerDay?): ExtraTimes? {
    if(!today.complete || tomorrow?.complete!=true) return null
    val maghrib=today.time(Prayer.MAGHRIB)
    val night=Duration.between(maghrib,tomorrow.time(Prayer.FAJR))
    return ExtraTimes(today.time(Prayer.SUNRISE).plusSeconds(15*60),maghrib.plus(night.dividedBy(2)),maghrib.plus(night.multipliedBy(2).dividedBy(3)))
}

// ───────────────────────────── Reminders ─────────────────────────────

enum class Reminder { SUHOOR, TARAWEEH, SUNRISE, DUHA, QIYAM, MORNING_ADHKAR, EVENING_ADHKAR, KAHF, FASTING, LOG_CHECK }

/** Gentle reminders around the prayers (Ramadan, sunnah, adhkar). Each is opt-in. */
fun reminderSchedule(days: List<PrayerDay>, p: Preferences): List<ScheduledPrayer> = buildList {
    val ordered=days.sortedBy {it.date}
    ordered.forEachIndexed {index,day->
        if(!day.complete) return@forEachIndexed
        fun event(prayer: Prayer)=day.events.first {it.prayer==prayer}
        fun add(prayer: Prayer,reminder: Reminder,at: Instant) = add(ScheduledPrayer(event(prayer),PrayerAction.REMINDER,at,reminder))
        val fajr=event(Prayer.FAJR).time; val sunrise=event(Prayer.SUNRISE).time
        val asr=event(Prayer.ASR).time; val maghrib=event(Prayer.MAGHRIB).time; val isha=event(Prayer.ISHA).time
        val tomorrow=ordered.getOrNull(index+1)?.takeIf {it.date==day.date.plusDays(1)}
        if(p.ramadanMode && p.suhoorReminder && isRamadan(day.date,p)) add(Prayer.FAJR,Reminder.SUHOOR,fajr.minusSeconds(p.suhoorMinutes*60L))
        // The taraweeh night belongs to the next Ramadan day (the Hijri date turns at maghrib).
        if(p.ramadanMode && p.taraweehReminder && isRamadan(day.date.plusDays(1),p)) add(Prayer.ISHA,Reminder.TARAWEEH,isha.plusSeconds(p.taraweehMinutes*60L))
        if(p.sunriseReminder) add(Prayer.SUNRISE,Reminder.SUNRISE,sunrise.minusSeconds(p.sunriseLeadMinutes*60L))
        if(p.duhaReminder) add(Prayer.SUNRISE,Reminder.DUHA,sunrise.plusSeconds(15*60))
        if(p.qiyamReminder && tomorrow!=null) extraTimes(day,tomorrow)?.let {add(Prayer.ISHA,Reminder.QIYAM,it.lastThird)}
        if(p.adhkarReminders) {
            val morning=fajr.plusSeconds(30*60).let {if(it>=sunrise) sunrise.minusSeconds(10*60) else it}
            if(morning>fajr) add(Prayer.FAJR,Reminder.MORNING_ADHKAR,morning)
            val evening=asr.plusSeconds(30*60)
            if(evening<maghrib) add(Prayer.ASR,Reminder.EVENING_ADHKAR,evening)
        }
        if(p.kahfReminder && p.isFriday(event(Prayer.DHUHR))) add(Prayer.SUNRISE,Reminder.KAHF,sunrise.plusSeconds(2*3600))
        if(p.fastingReminders && fastingReason(day.date.plusDays(1),p)!=null) add(Prayer.ISHA,Reminder.FASTING,isha.plusSeconds(30*60))
        if(p.prayerLog && p.logCheckIn) {
            // Before each prayer's time runs out: Fajr before sunrise, the others before the next adhan, Isha before midnight.
            val dhuhr=event(Prayer.DHUHR).time
            listOf(Prayer.FAJR to sunrise,Prayer.DHUHR to asr,Prayer.ASR to maghrib,Prayer.MAGHRIB to isha).forEach {(prayer,end)->
                val at=end.minusSeconds(20*60)
                if(at>event(prayer).time.plusSeconds(10*60)) add(prayer,Reminder.LOG_CHECK,at)
            }
            if(dhuhr>fajr && tomorrow!=null) extraTimes(day,tomorrow)?.let {add(Prayer.ISHA,Reminder.LOG_CHECK,it.midnight.minusSeconds(30*60))}
        }
    }
}.sortedBy {it.at}

// ───────────────────────────── Calculation method by country ─────────────────────────────

/** The usual authority for a country, where one is clear; null leaves the choice to the person. */
fun suggestedMethod(countryCode: String?): Method? = when(countryCode?.uppercase()) {
    "SA","YE","BH" -> Method.UMM_AL_QURA
    "AE" -> Method.DUBAI
    "KW" -> Method.KUWAIT
    "QA" -> Method.QATAR
    "EG","SD","LY","SY","LB","JO","PS","IQ" -> Method.EGYPTIAN
    "TR" -> Method.TURKEY
    "MY","SG","ID","BN" -> Method.SINGAPORE
    "PK","IN","BD","AF" -> Method.KARACHI
    "US","CA" -> Method.ISNA
    "GB","IE" -> Method.MOONSIGHTING
    "FR" -> Method.FRANCE
    "IR" -> Method.TEHRAN
    "DZ","MA","TN","OM","MR","DE","NL","BE","AT","CH","SE","NO","DK","IT","ES","RU","AU","NZ" -> Method.MWL
    else -> null
}

// ───────────────────────────── Mosque profiles ─────────────────────────────

/** A mosque's iqama times (minutes after adhan), switchable and shareable as a link or QR code. */
data class Mosque(val id: String, val name: String, val iqamaMinutes: Map<Prayer,Int>, val jumuahSilence: Int? = null) {
    init { require(name.isNotBlank()); require(iqamaMinutes.keys==dailyPrayers && iqamaMinutes.values.all {it in 1..120}) }
}

object MosqueCode {
    /** Opens a small page that shows the times and hands them to Najwa. The data stays in the fragment,
     * which browsers never send to the server. */
    const val WEB_PREFIX="https://taroq4-2.github.io/Najwa/m.html#"
    const val APP_PREFIX="najwa://mosque#"
    private val order=listOf(Prayer.FAJR,Prayer.DHUHR,Prayer.ASR,Prayer.MAGHRIB,Prayer.ISHA)
    fun encode(m: Mosque): String {
        val raw="N1|${m.name.replace('|',' ').trim().take(60)}|${order.joinToString(",") {m.iqamaMinutes.getValue(it).toString()}}|${m.jumuahSilence ?: ""}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }
    fun link(m: Mosque)=WEB_PREFIX+encode(m)
    /** Accepts a shared link, an app link or the bare code. */
    fun decode(text: String): Mosque? = runCatching {
        val code=text.trim().substringAfterLast('#').substringAfterLast("d=").trim()
        val raw=String(Base64.getUrlDecoder().decode(code),StandardCharsets.UTF_8).split('|')
        require(raw.size>=3 && raw[0]=="N1")
        val minutes=raw[2].split(',').map {it.trim().toInt()}
        require(minutes.size==5)
        Mosque(UUID.nameUUIDFromBytes(code.toByteArray()).toString(),raw[1].trim().take(60),order.zip(minutes).toMap(),raw.getOrNull(3)?.toIntOrNull()?.takeIf {it in 5..120})
    }.getOrNull()
}

// ───────────────────────────── Prayer log ─────────────────────────────

/** How a prayer was prayed. [MISSED] is recorded explicitly (a blank day means "not marked yet"). */
enum class PrayerStatus { JAMAAH, ON_TIME, LATE, MISSED; val prayed get()=this!=MISSED }
/** Voluntary prayers that can be ticked per day. */
enum class SunnahPrayer { RAWATIB, DUHA, WITR, QIYAM }

/** A private, on-device record of prayers marked as prayed, how they were prayed, sunnah prayers,
 * plus a count of prayers to make up. [days] keeps the prayed set (older logs only have this). */
data class PrayerLog(val days: Map<LocalDate,Set<Prayer>> = emptyMap(), val qada: Int = 0,
                     val detail: Map<LocalDate,Map<Prayer,PrayerStatus>> = emptyMap(),
                     val sunnah: Map<LocalDate,Set<SunnahPrayer>> = emptyMap()) {
    fun prayed(date: LocalDate, prayer: Prayer) = prayer in days[date].orEmpty()
    /** Null when nothing is recorded; a plain "prayed" from older logs reads as on time. */
    fun status(date: LocalDate, prayer: Prayer): PrayerStatus? =
        detail[date]?.get(prayer) ?: if(prayed(date,prayer)) PrayerStatus.ON_TIME else null
    fun toggled(date: LocalDate, prayer: Prayer): PrayerLog =
        withStatus(date,prayer,if(prayed(date,prayer)) null else PrayerStatus.ON_TIME)
    fun withStatus(date: LocalDate, prayer: Prayer, status: PrayerStatus?): PrayerLog {
        require(prayer.isPrayer)
        val set=days[date].orEmpty().let {if(status?.prayed==true) it+prayer else it-prayer}
        val marks=detail[date].orEmpty().let {if(status==null) it-prayer else it+(prayer to status)}
        val keep={d: LocalDate->d>date.minusDays(400)}
        return copy(days=(if(set.isEmpty()) days-date else days+(date to set)).filterKeys(keep),
            detail=(if(marks.isEmpty()) detail-date else detail+(date to marks)).filterKeys(keep))
    }
    fun withSunnah(date: LocalDate, prayer: SunnahPrayer, done: Boolean): PrayerLog {
        val set=sunnah[date].orEmpty().let {if(done) it+prayer else it-prayer}
        return copy(sunnah=(if(set.isEmpty()) sunnah-date else sunnah+(date to set)).filterKeys {it>date.minusDays(400)})
    }
    fun withQada(value: Int) = copy(qada=value.coerceIn(0,100_000))
    /** Prayers marked in the [count] days ending on [today]. */
    fun total(today: LocalDate, count: Int) = (0 until count).sumOf {days[today.minusDays(it.toLong())]?.size ?: 0}
    /** Prayers prayed in congregation in the [count] days ending on [today]. */
    fun jamaah(today: LocalDate, count: Int) = (0 until count).sumOf {i->detail[today.minusDays(i.toLong())]?.values?.count {it==PrayerStatus.JAMAAH} ?: 0}
    /** Days in the last [count] on which [prayer] was prayed. */
    fun prayerDays(prayer: Prayer, today: LocalDate, count: Int) = (0 until count).count {prayed(today.minusDays(it.toLong()),prayer)}
    /** The prayer most often left unmarked lately, when one clearly lags (null if all are equal). */
    fun weakest(today: LocalDate, count: Int = 14): Prayer? {
        val rates=dailyPrayers.associateWith {prayerDays(it,today,count)}
        val low=rates.minByOrNull {it.value} ?: return null
        return low.key.takeIf {low.value<rates.values.max()}
    }
    /** Consecutive complete days (all five) ending today or yesterday. */
    fun streak(today: LocalDate): Int {
        var day=if(days[today]?.size==5) today else today.minusDays(1)
        var n=0
        while(days[day]?.size==5) {n++;day=day.minusDays(1)}
        return n
    }
    /** Consecutive days on which [prayer] was prayed, ending today or yesterday. */
    fun prayerStreak(prayer: Prayer, today: LocalDate): Int {
        var day=if(prayed(today,prayer)) today else today.minusDays(1)
        var n=0
        while(prayed(day,prayer)) {n++;day=day.minusDays(1)}
        return n
    }
    /** The longest run of complete days ever recorded. */
    fun bestStreak(): Int {
        var best=0;var run=0;var previous: LocalDate?=null
        for(day in days.filterValues {it.size==5}.keys.sorted()) {
            run=if(previous!=null && day==previous.plusDays(1)) run+1 else 1
            best=maxOf(best,run);previous=day
        }
        return best
    }
}
