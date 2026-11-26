package org.najwa.core

import java.time.Instant

/**
 * The day engine's single answer to "what now?": the one act of worship that fits this moment and is not done yet.
 * The home card, the day widget and the reminders all ask here, so they never disagree.
 */
enum class StepKind { PRAYER, AFTER_PRAYER_ADHKAR, MORNING_ADHKAR, EVENING_ADHKAR, WIRD, QADA, SLEEP_ADHKAR, LOG_PRAYERS, DONE }

data class NextStep(val kind: StepKind, val left: Int, val prayer: Prayer? = null)

/**
 * What is still open in the worship day, from the confirmed records.
 * [duePrayer]: the prayer whose time is running now and is not recorded (only with the prayer log on).
 * [afterPrayer]: the prayer just recorded whose after-prayer adhkar are still open, within a short window.
 * [unrecordedEarlier]: earlier prayers today whose time has passed without a record.
 */
data class DayProgress(
    val morningLeft: Int,
    val eveningLeft: Int,
    val sleepLeft: Int,
    val wirdLeft: Int,
    val duePrayer: Prayer? = null,
    val afterPrayer: Prayer? = null,
    val afterLeft: Int = 0,
    val qadaLeft: Int = 0,
    val unrecordedEarlier: Int = 0,
)

object DayEngine {
    /**
     * In order: the prayer of the moment, its after-prayer adhkar, morning adhkar (Fajr to Asr) or evening adhkar
     * (Asr to Isha), the Quran portion, the day's make-up prayers, sleep adhkar at night, and finally any earlier
     * prayer left unrecorded, so the day is never called complete while prayers are missing.
     */
    fun next(now: Instant, fajr: Instant, asr: Instant, isha: Instant, d: DayProgress): NextStep = when {
        d.duePrayer != null -> NextStep(StepKind.PRAYER, 0, d.duePrayer)
        d.afterPrayer != null && d.afterLeft > 0 -> NextStep(StepKind.AFTER_PRAYER_ADHKAR, d.afterLeft, d.afterPrayer)
        now >= fajr && now < asr && d.morningLeft > 0 -> NextStep(StepKind.MORNING_ADHKAR, d.morningLeft)
        now >= asr && now < isha && d.eveningLeft > 0 -> NextStep(StepKind.EVENING_ADHKAR, d.eveningLeft)
        d.wirdLeft > 0 -> NextStep(StepKind.WIRD, d.wirdLeft)
        d.qadaLeft > 0 -> NextStep(StepKind.QADA, d.qadaLeft)
        (now >= isha || now < fajr) && d.sleepLeft > 0 -> NextStep(StepKind.SLEEP_ADHKAR, d.sleepLeft)
        d.unrecordedEarlier > 0 -> NextStep(StepKind.LOG_PRAYERS, d.unrecordedEarlier)
        else -> NextStep(StepKind.DONE, 0)
    }

    /** Which adhkar routine belongs to this moment: morning from Fajr until Asr, evening from Asr until the next Fajr. */
    fun adhkarOfTheMoment(now: Instant, fajr: Instant, asr: Instant): StepKind =
        if(now >= fajr && now < asr) StepKind.MORNING_ADHKAR else StepKind.EVENING_ADHKAR

    /** Each prayer's time ends at the next one (Fajr at sunrise); Isha's at the next Fajr. */
    fun prayerWindowEnd(prayer: Prayer, day: PrayerDay, nextFajr: Instant?): Instant? {
        fun at(p: Prayer) = day.events.firstOrNull {it.prayer == p}?.time
        return when(prayer) {
            Prayer.FAJR -> at(Prayer.SUNRISE)
            Prayer.DHUHR -> at(Prayer.ASR)
            Prayer.ASR -> at(Prayer.MAGHRIB)
            Prayer.MAGHRIB -> at(Prayer.ISHA)
            Prayer.ISHA -> nextFajr
            else -> null
        }
    }

    /** The prayer whose time is running at [now], if any. */
    fun currentPrayer(now: Instant, day: PrayerDay, nextFajr: Instant?): Prayer? = dailyPrayers.lastOrNull {p->
        val start = day.events.firstOrNull {it.prayer == p}?.time ?: return@lastOrNull false
        val end = prayerWindowEnd(p, day, nextFajr)
        now >= start && (end == null || now < end)
    }
}
