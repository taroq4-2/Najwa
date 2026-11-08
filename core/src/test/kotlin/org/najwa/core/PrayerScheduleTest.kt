package org.najwa.core

import java.time.Instant
import java.time.DayOfWeek
import org.junit.Assert.*
import org.junit.Test

class PrayerScheduleTest {
    private val at=Instant.parse("2026-11-07T09:00:00Z")
    private val prayer=PrayerEvent(Prayer.DHUHR,at)
    private val prefs=Preferences(autoSilent=true,adhanNotifications=true,iqamaNotifications=true)
    @Test fun notificationsDoNotRequireAutoSilenceOrSelectedPrayers() {
        val tasks=prayerSchedule(listOf(prayer),prefs.copy(autoSilent=false,enabled=emptySet()))
        assertEquals(listOf(PrayerAction.ADHAN,PrayerAction.IQAMA),tasks.map {it.action})
    }
    @Test fun sunriseNeverCreatesAnyTask() {assertTrue(prayerSchedule(listOf(PrayerEvent(Prayer.SUNRISE,at)),prefs).isEmpty())}
    @Test fun iqamaUsesEachPrayerDelayAcrossMidnight() {
        val event=PrayerEvent(Prayer.ISHA,Instant.parse("2026-11-07T23:50:00Z"))
        val tasks=prayerSchedule(listOf(event),prefs.copy(iqamaMinutes=prefs.iqamaMinutes+(Prayer.ISHA to 30)))
        assertEquals(Instant.parse("2026-11-08T00:20:00Z"),tasks.single {it.action==PrayerAction.IQAMA}.at)
    }
    @Test fun silenceStartsTwentySecondsAfterAdhan() {
        val tasks=prayerSchedule(listOf(prayer),prefs)
        assertEquals(setOf(PrayerAction.ADHAN),nextPrayers(tasks,at.minusSeconds(1)).map {it.action}.toSet())
    }
    @Test fun openingAtDeadlineRecoversTheSavedBatch() {
        val tasks=prayerSchedule(listOf(prayer),prefs)
        val saved=nextPrayers(tasks,at.minusSeconds(1))
        assertEquals(saved,duePrayers(saved,tasks,at,15))
        assertEquals(PrayerAction.SILENCE,nextPrayers(tasks,at).single().action)
        assertEquals(at.plusSeconds(20),nextPrayers(tasks,at).single().at)
    }
    @Test fun delayedBroadcastMutesWithoutPlayingStaleAdhan() {
        val tasks=prayerSchedule(listOf(prayer),prefs)
        val saved=nextPrayers(tasks,at.minusSeconds(1))
        assertEquals(listOf(PrayerAction.SILENCE),duePrayers(tasks,tasks,at.plusSeconds(180),15).map {it.action})
    }
    @Test fun expiredSilenceNeverMutes() {
        val tasks=prayerSchedule(listOf(prayer),prefs)
        assertTrue(duePrayers(tasks.filter {it.action==PrayerAction.SILENCE},tasks,at.plusSeconds(900),15).isEmpty())
    }
    @Test fun disabledOrChangedCalculationInvalidatesSavedWork() {
        val saved=nextPrayers(prayerSchedule(listOf(prayer),prefs),at.minusSeconds(1))
        assertTrue(duePrayers(saved,prayerSchedule(listOf(prayer),Preferences()),at,15).isEmpty())
        val changed=prayerSchedule(listOf(prayer.copy(time=at.plusSeconds(60))),prefs)
        assertTrue(duePrayers(saved,changed,at,15).isEmpty())
    }
    @Test fun changedIqamaOffsetInvalidatesOldReminder() {
        val old=prayerSchedule(listOf(prayer),prefs)
        val updated=prayerSchedule(listOf(prayer),prefs.copy(iqamaMinutes=prefs.iqamaMinutes+(Prayer.DHUHR to 20)))
        assertTrue(duePrayers(old,updated,at.plusSeconds(900),15).isEmpty())
    }
    @Test fun enablingNotificationsDoesNotRingUnscheduledPastPrayers() {
        assertTrue(duePrayers(emptyList(),prayerSchedule(listOf(prayer),prefs),at.plusSeconds(30),15).isEmpty())
    }
    @Test fun invalidIqamaDelaysAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {prefs.copy(iqamaMinutes=prefs.iqamaMinutes+(Prayer.DHUHR to 0))}
        assertThrows(IllegalArgumentException::class.java) {prefs.copy(iqamaMinutes=emptyMap())}
    }
    @Test fun fridayUsesTheSelectedCityDayAndHasItsOwnSwitch() {
        val fridayInDubai=PrayerEvent(Prayer.DHUHR,Instant.parse("2026-11-05T21:00:00Z"))
        val dubai=City("دبي",25.20,55.27,"Asia/Dubai")
        val options=Preferences(city=dubai,fridayReminder=true,fridayLeadMinutes=45)
        val events=listOf(fridayInDubai,fridayInDubai.copy(prayer=Prayer.ASR),prayer)
        val reminder=prayerSchedule(events,options).single()
        assertEquals(PrayerAction.FRIDAY,reminder.action)
        assertEquals(fridayInDubai.time.minusSeconds(45*60L),reminder.at)
        assertTrue(prayerSchedule(events,options.copy(fridayReminder=false)).isEmpty())
        assertTrue(prayerSchedule(events,options.copy(city=dubai.copy(zone="UTC"))).isEmpty())
    }
    @Test fun fridayOnlySchedulesTheNextWeekWhenOpenedAfterFriday() {
        val now=Instant.parse("2026-11-07T09:00:00Z")
        val p=Preferences(fridayReminder=true)
        val reminder=upcomingPrayerSchedule(PrayerCalculator(),now,p).single {it.at>now}
        assertEquals(PrayerAction.FRIDAY,reminder.action)
        assertEquals(DayOfWeek.FRIDAY,reminder.at.atZone(p.city.zoneId).dayOfWeek)
        assertEquals(reminder.event.time.minusSeconds(30*60L),reminder.at)
        val following=upcomingPrayerSchedule(PrayerCalculator(),reminder.at,p)
        assertEquals(reminder,following.first())
        assertTrue(following.last().at>reminder.event.time.plusSeconds(6*86_400L))
    }
}
