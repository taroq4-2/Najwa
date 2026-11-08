package org.najwa.core

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PrayerFlowTest {
    private val p=Preferences(autoSilent=true,adhanNotifications=true,iqamaNotifications=true)
    @Test fun requestedDefaultsAreExactForAllFivePrayers() {
        assertEquals(listOf(20,15,15,5,15),p.iqamaMinutes.values.toList())
        assertEquals(listOf(35,30,30,20,30),p.silenceMinutes.values.toList())
    }
    @Test fun realCalculatedPrayerTimesDispatchAdhanMuteIqamaAndRestoreForEveryPrayer() {
        val events=PrayerCalculator().calculate(LocalDate.of(2026,11,7),p).events.filter {it.prayer.isPrayer}
        events.forEach {event->
            val f=SessionTest.Fixture();f.now=event.time.toEpochMilli()
            val sent=mutableListOf<PrayerAction>()
            val dispatcher=PrayerDispatch(f.manager) {task,_->sent+=task.action}
            val plan=prayerSchedule(listOf(event),p)
            // Fresh process or a rolled-forward plan: incoming timestamp is sufficient.
            dispatcher.deliver(dispatchablePrayers(emptyList(),plan,event.time,p,event.time.toEpochMilli()),p)
            assertEquals(listOf(PrayerAction.ADHAN),sent)
            assertEquals(Ringer.NORMAL,f.mode)
            f.now+=20_000
            dispatcher.deliver(dispatchablePrayers(emptyList(),plan,Instant.ofEpochMilli(f.now),p,f.now),p)
            assertEquals(Ringer.SILENT,f.mode)
            assertEquals(SessionState.ACTIVE,f.state.session!!.state)
            assertEquals(event.time.toEpochMilli()+p.silenceMinutes.getValue(event.prayer)*60000L,f.scheduled)
            f.now=event.time.toEpochMilli()+p.iqamaMinutes.getValue(event.prayer)*60000L
            // Recreate the exact session manager used by the receiver after process death.
            val recreated=SilenceSessionManager(f.store,f.audio,f.alarm,{f.now})
            PrayerDispatch(recreated) {task,_->sent+=task.action}.deliver(
                dispatchablePrayers(emptyList(),plan,Instant.ofEpochMilli(f.now),p,f.now),p)
            assertEquals(listOf(PrayerAction.ADHAN,PrayerAction.IQAMA),sent)
            assertEquals(Ringer.SILENT,f.mode)
            f.now=f.state.session!!.deadline
            recreated.restore(f.state.session!!.id)
            assertEquals(Ringer.NORMAL,f.mode)
            assertEquals(SessionState.RESTORED,f.state.session!!.state)
        }
    }
    @Test fun iqamaTakesOverAtAdhanAndEndsAtConfiguredBoundaryWithoutNotifications() {
        val prefs=p.copy(adhanNotifications=false,iqamaNotifications=false)
        Prayer.entries.filter {it.isPrayer}.forEach {prayer->
            val at=Instant.parse("2026-11-07T09:00:00Z")
            val events=listOf(PrayerEvent(prayer,at),PrayerEvent(Prayer.ISHA,at.plusSeconds(4*3600)))
            assertEquals(CountdownPhase.ADHAN,prayerCountdown(events,at.minusSeconds(1),prefs)!!.phase)
            val start=prayerCountdown(events,at,prefs)!!
            assertEquals(CountdownPhase.IQAMA,start.phase)
            assertEquals(at.plusSeconds(prefs.iqamaMinutes.getValue(prayer)*60L),start.time)
            assertEquals(CountdownPhase.IQAMA,prayerCountdown(events,start.time.minusSeconds(1),prefs)!!.phase)
            assertEquals(CountdownPhase.ADHAN,prayerCountdown(events,start.time,prefs)!!.phase)
        }
    }
    @Test fun editedIqamaAndMuteDurationsAreUsedIndependently() {
        val prefs=p.copy(iqamaMinutes=p.iqamaMinutes+(Prayer.MAGHRIB to 8),silenceMinutes=p.silenceMinutes+(Prayer.MAGHRIB to 28))
        val f=SessionTest.Fixture();val event=PrayerEvent(Prayer.MAGHRIB,Instant.ofEpochMilli(f.now))
        val tasks=prayerSchedule(listOf(event),prefs)
        assertEquals(event.time.plusSeconds(8*60),tasks.single {it.action==PrayerAction.IQAMA}.at)
        f.now+=20_000
        PrayerDispatch(f.manager) {_,_->}.deliver(tasks.filter {it.action==PrayerAction.SILENCE},prefs)
        assertEquals(event.time.toEpochMilli()+28*60000,f.scheduled)
    }
    @Test fun notificationFailureCannotPreventSilentMode() {
        val f=SessionTest.Fixture();val event=PrayerEvent(Prayer.DHUHR,Instant.ofEpochMilli(f.now))
        f.now+=20_000
        var failures=0
        PrayerDispatch(f.manager) {_,_->throw IllegalStateException("channel unavailable")}.deliver(prayerSchedule(listOf(event),p).filter {it.at<=event.time.plusSeconds(20)},p) {_,_->failures++}
        assertEquals(1,failures);assertEquals(Ringer.SILENT,f.mode)
    }
    @Test fun duplicateDeliveryCannotExtendOrRemuteManuallyRestoredPrayer() {
        val f=SessionTest.Fixture();val event=PrayerEvent(Prayer.DHUHR,Instant.ofEpochMilli(f.now))
        val tasks=prayerSchedule(listOf(event),p).filter {it.at<=event.time.plusSeconds(20)}
        f.now+=20_000
        val dispatch=PrayerDispatch(f.manager) {_,_->}
        dispatch.deliver(tasks,p)
        val deadline=f.scheduled
        f.now+=60_000;dispatch.deliver(tasks,p);assertEquals(deadline,f.scheduled)
        f.manager.restore(f.state.session!!.id,true)
        dispatch.deliver(tasks,p);assertEquals(Ringer.NORMAL,f.mode)
    }
    @Test fun missedEarlierAlarmDoesNotPreventLaterScheduledPrayer() {
        val f=SessionTest.Fixture()
        val first=PrayerEvent(Prayer.DHUHR,Instant.ofEpochMilli(f.now))
        val second=PrayerEvent(Prayer.ASR,first.time.plusSeconds(3*3600))
        val tasks=prayerSchedule(listOf(first,second),p)
        f.now=second.time.plusSeconds(20).toEpochMilli()
        val due=dispatchablePrayers(tasks,tasks,Instant.ofEpochMilli(f.now),p,f.now)
        assertTrue(due.all {it.event==second})
        PrayerDispatch(f.manager) {_,_->}.deliver(due,p)
        assertEquals(Ringer.SILENT,f.mode)
    }
    @Test fun disablingAutoDoesNotBlockNotificationsOrCauseSilentWrites() {
        val f=SessionTest.Fixture();val event=PrayerEvent(Prayer.DHUHR,Instant.ofEpochMilli(f.now))
        val current=prayerSchedule(listOf(event),p.copy(autoSilent=false))
        val due=dispatchablePrayers(prayerSchedule(listOf(event),p),current,event.time,p,event.time.toEpochMilli())
        var notifications=0
        PrayerDispatch(f.manager) {_,_->notifications++}.deliver(due,p)
        assertEquals(1,notifications);assertEquals(Ringer.NORMAL,f.mode);assertEquals(0,f.writes)
    }
    @Test fun invalidPerPrayerSilenceDurationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {p.copy(silenceMinutes=p.silenceMinutes+(Prayer.MAGHRIB to 0))}
        assertThrows(IllegalArgumentException::class.java) {p.copy(silenceMinutes=emptyMap())}
    }
}
