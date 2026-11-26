package org.najwa.core

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Phase one of the connected day: the next step, the wird reminder and the after-prayer card. */
class Release17ConnectedTest {
    private val day=LocalDate.of(2026,11,24)
    private val riyadh=City("الرياض",24.7136,46.6753,"Asia/Riyadh","SA")
    private val fajr=Instant.parse("2026-11-24T02:00:00Z")
    private val asr=Instant.parse("2026-11-24T12:00:00Z")
    private val isha=Instant.parse("2026-11-24T16:00:00Z")
    private fun at(h: Int)=Instant.parse("2026-11-24T00:00:00Z").plusSeconds(h*3600L)

    @Test fun nextStepFollowsTheTimeOfDay() {
        val open=DayProgress(morningLeft=13,eveningLeft=12,sleepLeft=9,wirdLeft=4)
        assertEquals(NextStep(StepKind.MORNING_ADHKAR,13),DayEngine.next(at(5),fajr,asr,isha,open))
        assertEquals(NextStep(StepKind.EVENING_ADHKAR,12),DayEngine.next(at(13),fajr,asr,isha,open))
        assertEquals(NextStep(StepKind.WIRD,4),DayEngine.next(at(18),fajr,asr,isha,open))
        // Morning done: the portion comes next; everything done after Isha: sleep adhkar, then nothing.
        assertEquals(StepKind.WIRD,DayEngine.next(at(5),fajr,asr,isha,open.copy(morningLeft=0)).kind)
        assertEquals(StepKind.SLEEP_ADHKAR,DayEngine.next(at(18),fajr,asr,isha,open.copy(wirdLeft=0)).kind)
        assertEquals(StepKind.DONE,DayEngine.next(at(18),fajr,asr,isha,DayProgress(0,0,0,0)).kind)
        // Before Fajr the night continues.
        assertEquals(StepKind.SLEEP_ADHKAR,DayEngine.next(at(1),fajr,asr,isha,open.copy(wirdLeft=0)).kind)
        assertEquals(StepKind.EVENING_ADHKAR,DayEngine.adhkarOfTheMoment(at(1),fajr,asr))
        assertEquals(StepKind.MORNING_ADHKAR,DayEngine.adhkarOfTheMoment(at(5),fajr,asr))
    }

    @Test fun wirdReminderComesHalfAnHourAfterTheChosenPrayer() {
        val p=Preferences(city=riyadh,wirdReminder=true,wirdReminderAfter=Prayer.ASR)
        val calculator=PrayerCalculator()
        val days=(0L..1L).map {calculator.calculate(day.plusDays(it),p)}
        val wird=reminderSchedule(days,p).filter {it.reminder==Reminder.WIRD}
        assertEquals(1,wird.count {it.event.time.atZone(p.city.zoneId).toLocalDate()==day})
        val asrTime=days.first().events.first {it.prayer==Prayer.ASR}.time
        assertEquals(asrTime.plusSeconds(WIRD_DELAY_MINUTES*60L),wird.first().at)
        assertTrue(reminderSchedule(days,p.copy(wirdReminder=false)).none {it.reminder==Reminder.WIRD})
    }

    @Test fun afterPrayerCardFollowsEachSilenceWindow() {
        val p=Preferences(city=riyadh,prayerLog=true,afterPrayerCard=true,autoSilent=true)
        val days=listOf(PrayerCalculator().calculate(day,p))
        val cards=reminderSchedule(days,p).filter {it.reminder==Reminder.AFTER_PRAYER}
        assertEquals(dailyPrayers,cards.map {it.event.prayer}.toSet())
        cards.filter {it.event.prayer in p.enabled}.forEach {assertEquals(it.event.time.plusSeconds(p.silenceFor(it.event)*60L),it.at)}
        // With silence off: ten minutes after the iqama.
        val quiet=p.copy(autoSilent=false)
        reminderSchedule(days,quiet).filter {it.reminder==Reminder.AFTER_PRAYER}.forEach {
            assertEquals(it.event.time.plusSeconds((quiet.iqamaMinutes.getValue(it.event.prayer)+10)*60L),it.at)}
        // Needs the prayer log, and is off by default.
        assertTrue(reminderSchedule(days,p.copy(prayerLog=false)).none {it.reminder==Reminder.AFTER_PRAYER})
        assertTrue(reminderSchedule(days,Preferences(city=riyadh)).none {it.reminder==Reminder.AFTER_PRAYER})
    }
}
