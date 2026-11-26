package org.najwa.core

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** The fix round: prayers in the day engine, prayer windows, digits, and the new reminders. */
class Release17FixRoundTest {
    private val day=LocalDate.of(2026,11,24)
    private val riyadh=City("الرياض",24.7136,46.6753,"Asia/Riyadh","SA")
    private val fajr=Instant.parse("2026-11-24T02:00:00Z")
    private val asr=Instant.parse("2026-11-24T12:00:00Z")
    private val isha=Instant.parse("2026-11-24T16:00:00Z")
    private fun at(h: Int)=Instant.parse("2026-11-24T00:00:00Z").plusSeconds(h*3600L)
    private val open=DayProgress(morningLeft=13,eveningLeft=12,sleepLeft=9,wirdLeft=4)

    @Test fun thePrayerOfTheMomentComesFirst() {
        assertEquals(NextStep(StepKind.PRAYER,0,Prayer.ASR),DayEngine.next(at(13),fajr,asr,isha,open.copy(duePrayer=Prayer.ASR)))
        // Recorded: its after-prayer adhkar, then the evening adhkar.
        assertEquals(StepKind.AFTER_PRAYER_ADHKAR,DayEngine.next(at(13),fajr,asr,isha,open.copy(afterPrayer=Prayer.ASR,afterLeft=8)).kind)
        assertEquals(StepKind.EVENING_ADHKAR,DayEngine.next(at(13),fajr,asr,isha,open.copy(afterPrayer=Prayer.ASR,afterLeft=0)).kind)
    }

    @Test fun theDayIsNeverDoneWhilePrayersAreUnrecorded() {
        val done=DayProgress(0,0,0,0)
        assertEquals(StepKind.DONE,DayEngine.next(at(18),fajr,asr,isha,done).kind)
        assertEquals(NextStep(StepKind.LOG_PRAYERS,2),DayEngine.next(at(18),fajr,asr,isha,done.copy(unrecordedEarlier=2)))
        // Make-up prayers come after the Quran portion and before sleep adhkar.
        assertEquals(StepKind.QADA,DayEngine.next(at(18),fajr,asr,isha,done.copy(qadaLeft=2,sleepLeft=5)).kind)
        assertEquals(StepKind.WIRD,DayEngine.next(at(18),fajr,asr,isha,done.copy(qadaLeft=2,wirdLeft=3)).kind)
    }

    @Test fun eachPrayerRunsUntilTheNextOne() {
        val p=Preferences(city=riyadh)
        val d=PrayerCalculator().calculate(day,p)
        val nextFajr=PrayerCalculator().calculate(day.plusDays(1),p).events.first {it.prayer==Prayer.FAJR}.time
        fun t(prayer: Prayer)=d.events.first {it.prayer==prayer}.time
        assertEquals(t(Prayer.SUNRISE),DayEngine.prayerWindowEnd(Prayer.FAJR,d,nextFajr))
        assertEquals(nextFajr,DayEngine.prayerWindowEnd(Prayer.ISHA,d,nextFajr))
        assertEquals(Prayer.ASR,DayEngine.currentPrayer(t(Prayer.ASR).plusSeconds(60),d,nextFajr))
        // Between sunrise and Dhuhr no prayer is running.
        assertNull(DayEngine.currentPrayer(t(Prayer.SUNRISE).plusSeconds(60),d,nextFajr))
        assertEquals(Prayer.ISHA,DayEngine.currentPrayer(nextFajr.minusSeconds(60),d,nextFajr))
    }

    @Test fun digitsFollowTheChoice() {
        assertEquals("٥:٣٧ م",Digits.ARABIC.render("5:37 م"))
        assertEquals("5:37",Digits.LATIN.render("٥:٣٧"))
        assertEquals("5:37",Digits.AUTO.render("5:37"))
        val t=Instant.parse("2026-11-24T14:37:00Z")
        assertEquals("٥:٣٧ م",displayTime(t,Preferences(city=riyadh,digits=Digits.ARABIC)))
    }

    @Test fun sleepAndWeeklyRemindersAreOptIn() {
        val friday=LocalDate.of(2026,11,27)
        val p=Preferences(city=riyadh,sleepReminder=true,sleepReminderMinutes=120,weeklySummary=true)
        val days=listOf(PrayerCalculator().calculate(friday,p))
        val r=reminderSchedule(days,p)
        val isha=days.first().events.first {it.prayer==Prayer.ISHA}.time
        assertEquals(isha.plusSeconds(7200),r.single {it.reminder==Reminder.SLEEP_ADHKAR}.at)
        val dhuhr=days.first().events.first {it.prayer==Prayer.DHUHR}.time
        assertEquals(dhuhr.plusSeconds(7200),r.single {it.reminder==Reminder.WEEKLY}.at)
        val off=reminderSchedule(days,Preferences(city=riyadh))
        assertTrue(off.none {it.reminder==Reminder.SLEEP_ADHKAR || it.reminder==Reminder.WEEKLY})
    }
}
