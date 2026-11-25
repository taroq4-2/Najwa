package org.najwa.core

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class Release14Test {
    private val riyadh=City("الرياض",24.7136,46.6753,"Asia/Riyadh")
    private val date=LocalDate.of(2026,11,23)
    private fun times(p: Preferences)=PrayerCalculator().calculate(date,p).events.associate {it.prayer to it.time}

    @Test fun manualOffsetsMoveOnlyTheirPrayer() {
        val base=times(Preferences(city=riyadh))
        val moved=times(Preferences(city=riyadh,offsets=mapOf(Prayer.MAGHRIB to 3,Prayer.FAJR to -2)))
        assertEquals(3L,Duration.between(base.getValue(Prayer.MAGHRIB),moved.getValue(Prayer.MAGHRIB)).toMinutes())
        assertEquals(-2L,Duration.between(base.getValue(Prayer.FAJR),moved.getValue(Prayer.FAJR)).toMinutes())
        assertEquals(base.getValue(Prayer.DHUHR),moved.getValue(Prayer.DHUHR))
    }

    @Test fun offsetsOutsideTheRangeAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {Preferences(offsets=mapOf(Prayer.ASR to MAX_OFFSET_MINUTES+1))}
        assertThrows(IllegalArgumentException::class.java) {Preferences(hijriOffset=3)}
    }

    @Test fun qatarUsesNinetyMinutesAfterMaghrib() {
        val t=times(Preferences(city=riyadh,method=Method.QATAR))
        assertEquals(90L,Duration.between(t.getValue(Prayer.MAGHRIB),t.getValue(Prayer.ISHA)).toMinutes())
    }

    @Test fun methodAdjustmentsAreApplied() {
        val mwl=times(Preferences(city=riyadh,method=Method.MWL))
        val turkey=times(Preferences(city=riyadh,method=Method.TURKEY))
        // Same 18°/17° angles as MWL, plus Diyanet's published corrections.
        assertEquals(7L,Duration.between(turkey.getValue(Prayer.MAGHRIB),mwl.getValue(Prayer.MAGHRIB)).toMinutes()*-1)
        assertEquals(-7L,Duration.between(mwl.getValue(Prayer.SUNRISE),turkey.getValue(Prayer.SUNRISE)).toMinutes())
    }

    @Test fun everyMethodProducesACompleteOrderedDay() {
        for(method in Method.entries) {
            val day=PrayerCalculator().calculate(date,Preferences(city=riyadh,method=method))
            assertTrue(method.name,day.complete)
            assertNull(method.name,day.issue)
        }
    }

    @Test fun silenceIsExtendedToCoverIqama() {
        val p=Preferences(iqamaMinutes=defaultIqamaMinutes()+(Prayer.ASR to 40),silenceMinutes=defaultSilenceMinutes()+(Prayer.ASR to 20)).normalized()
        assertEquals(40+SILENCE_AFTER_IQAMA_MINUTES,p.silenceMinutes.getValue(Prayer.ASR))
        // Longer windows are never shortened.
        assertEquals(defaultSilenceMinutes().getValue(Prayer.FAJR),p.silenceMinutes.getValue(Prayer.FAJR))
        Preferences().let {assertSame(it,it.normalized())}
    }

    @Test fun jumuahHasItsOwnSilenceWindow() {
        val friday=date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        val p=Preferences(city=riyadh,fridaySilenceMinutes=50)
        val dhuhr=PrayerCalculator().calculate(friday,p).events.first {it.prayer==Prayer.DHUHR}
        assertTrue(p.isJumuah(dhuhr))
        assertEquals(50,p.silenceFor(dhuhr))
        assertEquals(p.silenceMinutes.getValue(Prayer.DHUHR),p.copy(jumuah=false).silenceFor(dhuhr))
        val thursday=PrayerCalculator().calculate(friday.minusDays(1),p).events.first {it.prayer==Prayer.DHUHR}
        assertFalse(p.isJumuah(thursday))
    }

    @Test fun preAdhanReminderIsScheduledBeforeAdhanOnly() {
        val p=Preferences(city=riyadh,preAdhanReminder=true,preAdhanMinutes=10,adhanPrayers=setOf(Prayer.ASR))
        val events=PrayerCalculator().calculate(date,p).events
        val reminders=prayerSchedule(events,p).filter {it.action==PrayerAction.PRE_ADHAN}
        assertEquals(1,reminders.size)
        val asr=events.first {it.prayer==Prayer.ASR}
        assertEquals(asr.time.minusSeconds(600),reminders.single().at)
        // Delivered late but still before adhan: kept. After adhan: dropped.
        assertEquals(reminders,duePrayers(reminders,reminders,asr.time.minusSeconds(30),INEXACT_GRACE_MS) {30})
        assertTrue(duePrayers(reminders,reminders,asr.time.plusSeconds(10),INEXACT_GRACE_MS) {30}.isEmpty())
    }

    @Test fun inexactDeliveryAllowsLateNotifications() {
        val p=Preferences(city=riyadh,adhanNotifications=true)
        val tasks=prayerSchedule(PrayerCalculator().calculate(date,p).events,p).filter {it.action==PrayerAction.ADHAN}.take(1)
        val late=tasks.first().at.plusSeconds(600)
        assertTrue(dispatchablePrayers(tasks,tasks,late,p).isEmpty())
        assertEquals(tasks,dispatchablePrayers(tasks,tasks,late,p,exact=false))
    }

    @Test fun qiblaBearingMatchesKnownCities() {
        assertEquals(244.0,qiblaBearing(riyadh),1.0)
        assertEquals(119.0,qiblaBearing(City("London",51.5074,-0.1278,"Europe/London")),1.0)
        assertEquals(136.0,qiblaBearing(City("Cairo",30.0444,31.2357,"Africa/Cairo")),1.0)
        assertEquals(790.0,qiblaDistanceKm(riyadh),15.0)
    }
}
