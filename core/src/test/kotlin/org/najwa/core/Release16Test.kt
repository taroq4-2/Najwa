package org.najwa.core

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Release16Test {
    private val day=LocalDate.of(2026,11,24)
    private val riyadh=City("الرياض",24.7136,46.6753,"Asia/Riyadh","SA")

    @Test fun statusesKeepPrayedSetInStep() {
        var log=PrayerLog().withStatus(day,Prayer.FAJR,PrayerStatus.JAMAAH)
        assertTrue(log.prayed(day,Prayer.FAJR))
        assertEquals(PrayerStatus.JAMAAH,log.status(day,Prayer.FAJR))
        log=log.withStatus(day,Prayer.FAJR,PrayerStatus.MISSED)
        assertFalse(log.prayed(day,Prayer.FAJR))
        assertEquals(PrayerStatus.MISSED,log.status(day,Prayer.FAJR))
        log=log.withStatus(day,Prayer.FAJR,null)
        assertNull(log.status(day,Prayer.FAJR))
        assertTrue(log.days.isEmpty() && log.detail.isEmpty())
    }

    @Test fun olderLogsReadAsOnTime() {
        val log=PrayerLog(days=mapOf(day to setOf(Prayer.ASR)))
        assertEquals(PrayerStatus.ON_TIME,log.status(day,Prayer.ASR))
        // Toggling still works and clears the mark.
        assertNull(log.toggled(day,Prayer.ASR).status(day,Prayer.ASR))
    }

    @Test fun statisticsAndInsights() {
        var log=PrayerLog()
        // Ten complete days, then three more without Asr.
        for(i in 0 until 10) dailyPrayers.forEach {log=log.withStatus(day.minusDays(12L-i),it,if(it==Prayer.FAJR) PrayerStatus.JAMAAH else PrayerStatus.ON_TIME)}
        for(i in 0 until 3) dailyPrayers.filter {it!=Prayer.ASR}.forEach {log=log.withStatus(day.minusDays(2L-i),it,PrayerStatus.ON_TIME)}
        assertEquals(10,log.bestStreak())
        assertEquals(0,log.streak(day))
        assertEquals(Prayer.ASR,log.weakest(day))
        assertEquals(13,log.prayerStreak(Prayer.FAJR,day))
        assertEquals(10,log.jamaah(day,30))
        assertEquals(10,log.prayerDays(Prayer.ASR,day,14))
    }

    @Test fun sunnahPrayersToggle() {
        val log=PrayerLog().withSunnah(day,SunnahPrayer.WITR,true).withSunnah(day,SunnahPrayer.DUHA,true).withSunnah(day,SunnahPrayer.DUHA,false)
        assertEquals(setOf(SunnahPrayer.WITR),log.sunnah[day])
    }

    @Test fun checkInRemindersComeBeforeEachPrayerEnds() {
        val p=Preferences(city=riyadh,prayerLog=true,logCheckIn=true)
        val calculator=PrayerCalculator()
        val days=(0L..2L).map {calculator.calculate(day.plusDays(it),p)}
        val checks=reminderSchedule(days,p).filter {it.reminder==Reminder.LOG_CHECK && it.event.time.atZone(p.city.zoneId).toLocalDate()==day}
        assertEquals(dailyPrayers,checks.map {it.event.prayer}.toSet())
        val today=days.first()
        fun at(prayer: Prayer): Instant=today.events.first {it.prayer==prayer}.time
        // Fajr: twenty minutes before sunrise; Asr: twenty minutes before Maghrib.
        assertEquals(at(Prayer.SUNRISE).minusSeconds(1200),checks.first {it.event.prayer==Prayer.FAJR}.at)
        assertEquals(at(Prayer.MAGHRIB).minusSeconds(1200),checks.first {it.event.prayer==Prayer.ASR}.at)
        checks.forEach {assertTrue(it.at>it.event.time)}
        // Off unless both the log and the check-in are on.
        assertTrue(reminderSchedule(days,p.copy(logCheckIn=false)).none {it.reminder==Reminder.LOG_CHECK})
        assertTrue(reminderSchedule(days,p.copy(prayerLog=false)).none {it.reminder==Reminder.LOG_CHECK})
    }

    @Test fun maghribCheckInIsUpcomingInTheEvening() {
        val p=Preferences(city=riyadh,prayerLog=true,logCheckIn=true)
        val now=Instant.parse("2026-11-24T15:11:20Z")
        val tasks=upcomingPrayerSchedule(PrayerCalculator(),now,p).filter {it.reminder==Reminder.LOG_CHECK && it.at>now}
        val first=tasks.first()
        assertEquals(Prayer.MAGHRIB,first.event.prayer)
        assertTrue(first.at<now.plusSeconds(3600))
    }

    @Test fun quranScreenHasAHomeParent() {
        assertEquals(Screen.HOME,Screen.QURAN.parent)
        assertEquals(0,Screen.QURAN.tab)
    }
}
