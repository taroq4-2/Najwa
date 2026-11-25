package org.najwa.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.media.AudioManager
import android.os.Looper
import org.junit.Test
import org.junit.Before
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.najwa.app.platform.*
import org.najwa.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class ScheduledPrayerIntegrationTest {
    private lateinit var app: NajwaApplication
    private lateinit var alarm: AlarmManager
    private lateinit var audio: AudioManager
    private lateinit var notifications: NotificationManager
    private var now=Instant.parse("2026-11-07T00:00:00Z")
    private val prefs=Preferences(autoSilent=true,adhanNotifications=true,iqamaNotifications=true)
    private fun coldStart(): NajwaRuntime {
        val runtime=NajwaRuntime(app) {now}
        // Exercise the real manifest receiver, replacing only its clock for deterministic dates.
        NajwaApplication::class.java.getDeclaredField("runtime").apply {isAccessible=true}.set(app,runtime)
        return runtime
    }
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        alarm=app.getSystemService(AlarmManager::class.java)
        audio=app.getSystemService(AudioManager::class.java)
        notifications=app.getSystemService(NotificationManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        shadowOf(notifications).setNotificationsEnabled(true)
        audio.ringerMode=AudioManager.RINGER_MODE_NORMAL
    }
    private fun fire(at: Instant,action: String) {
        now=at
        val job=shadowOf(alarm).scheduledAlarms.firstOrNull {
            it.triggerAtTime==at.toEpochMilli() && shadowOf(it.operation).savedIntent.action==action
        } ?: error("No $action alarm at $at; scheduled=${shadowOf(alarm).scheduledAlarms.map {it.triggerAtTime}}")
        shadowOf(alarm).fireAlarm(job)
        shadowOf(Looper.getMainLooper()).idle()
        prayerExecutor.submit {}.get(10,java.util.concurrent.TimeUnit.SECONDS)
        assertNull(app.runtime.error)
    }
    /** Regression: the saved plan used to drop the reminder kind, so no reminder ever matched when its alarm rang. */
    @Test fun remindersArriveFromTheirOwnAlarm() {
        val p=prefs.copy(prayerLog=true,logCheckIn=true,adhkarReminders=true)
        coldStart().update(p)
        val upcoming=upcomingPrayerSchedule(PrayerCalculator(),now,p).filter {it.at>now && it.at<now.plusSeconds(86_400)}
        val adhkar=upcoming.first {it.reminder==Reminder.MORNING_ADHKAR}
        val check=upcoming.first {it.reminder==Reminder.LOG_CHECK && it.event.prayer==Prayer.DHUHR}
        for(task in listOf(adhkar,check).sortedBy {it.at}) fire(task.at,"org.najwa.prayer")
        val posted=notifications.activeNotifications.map {it.id}
        assertTrue("adhkar reminder in $posted",540+Reminder.MORNING_ADHKAR.ordinal in posted)
        assertTrue("check-in in $posted",560+Prayer.DHUHR.ordinal in posted)
    }
    @Test fun checkInStaysQuietOnceThePrayerIsMarked() {
        val p=prefs.copy(prayerLog=true,logCheckIn=true)
        val runtime=coldStart()
        runtime.update(p)
        val check=upcomingPrayerSchedule(PrayerCalculator(),now,p).first {it.at>now && it.reminder==Reminder.LOG_CHECK}
        runtime.setStatus(check.event.time.atZone(p.city.zoneId).toLocalDate(),check.event.prayer,PrayerStatus.JAMAAH)
        fire(check.at,"org.najwa.prayer")
        assertFalse(notifications.activeNotifications.any {it.id==560+check.event.prayer.ordinal})
    }
    @Test fun corruptPlanIsRebuiltAndIncomingAlarmStillDelivers() {
        val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.first {it.prayer.isPrayer}
        now=event.time.minusSeconds(60)
        coldStart().update(prefs)
        app.filesDir.resolve("alarm-plan.json").writeText("{broken")
        coldStart()
        fire(event.time,"org.najwa.prayer")
        assertNotNull(shadowOf(notifications).getNotification(300+event.prayer.ordinal*2))
        assertTrue(app.runtime.alarms.saved().any {it.at==event.time.plusSeconds(20)})
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
    }
    @Test fun receiverFallbackDeliversAlertsWithoutTouchingRinger() {
        val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.first {it.prayer.isPrayer}
        now=event.time.minusSeconds(60)
        val runtime=coldStart()
        runtime.update(prefs)
        now=event.time.plusSeconds(20)
        runtime.receive("org.najwa.prayer","batch",now.toEpochMilli(),allowRinger=false)
        assertNotNull(shadowOf(notifications).getNotification(300+event.prayer.ordinal*2))
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
        assertNull(runtime.session())
        assertTrue(runtime.alarms.saved().any {it.at>now})
    }
    @Test fun realAlarmReceiverPostsAdhanSilencesThenIqamaAndRestoresForFivePrayers() {
        val events=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.filter {it.prayer.isPrayer}
        now=events.first().time.minusSeconds(60)
        coldStart().update(prefs)
        assertTrue(app.runtime.preferences.autoSilent)
        assertTrue("All upcoming prayers are independently registered",shadowOf(alarm).scheduledAlarms.size>=10)
        events.forEach {event->
            coldStart()
            fire(event.time,"org.najwa.prayer")
            assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
            fire(event.time.plusSeconds(20),"org.najwa.prayer")
            assertEquals(AudioManager.RINGER_MODE_SILENT,audio.ringerMode)
            assertEquals(SessionState.ACTIVE,app.runtime.session()!!.state)
            assertNotNull(shadowOf(notifications).getNotification(300+event.prayer.ordinal*2))
            val deadline=event.time.plusSeconds(prefs.silenceMinutes.getValue(event.prayer)*60L)
            assertEquals(deadline.toEpochMilli(),app.runtime.session()!!.deadline)
            coldStart()
            fire(event.time.plusSeconds(prefs.iqamaMinutes.getValue(event.prayer)*60L),"org.najwa.prayer")
            assertNotNull(shadowOf(notifications).getNotification(301+event.prayer.ordinal*2))
            assertEquals(AudioManager.RINGER_MODE_SILENT,audio.ringerMode)
            fire(deadline,"org.najwa.restore")
            assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
            assertEquals(SessionState.RESTORED,app.runtime.session()!!.state)
        }
    }
    @Test fun deniedNotificationsNeverDisableAutomaticSilence() {
        val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.first()
        now=event.time.minusSeconds(30)
        shadowOf(notifications).setNotificationsEnabled(false)
        coldStart().update(prefs)
        coldStart()
        fire(event.time,"org.najwa.prayer")
            assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
            fire(event.time.plusSeconds(20),"org.najwa.prayer")
        assertEquals(AudioManager.RINGER_MODE_SILENT,audio.ringerMode)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test fun prayerAlarmsStayWithinTheRollingDay() {
        val runtime=coldStart()
        runtime.update(prefs)
        val planned=runtime.alarms.saved()
        assertTrue(planned.isNotEmpty())
        assertTrue(planned.all { it.at>now && it.at<=now.plusSeconds(86_400) })
        assertTrue(planned.any {it.action==PrayerAction.ADHAN})
        assertTrue(planned.any {it.action==PrayerAction.IQAMA})
        assertTrue(planned.any {it.action==PrayerAction.SILENCE})
    }

    @Test fun backupAlarmRestoresRingerIfExactAlarmNeverFires() {
        val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.first()
        now=event.time.minusSeconds(60)
        coldStart().update(prefs)
        fire(event.time.plusSeconds(20),"org.najwa.prayer")
        val session=app.runtime.session()!!
        assertEquals(SessionState.ACTIVE,session.state)
        assertEquals(AudioManager.RINGER_MODE_SILENT,audio.ringerMode)
        coldStart()
        fire(Instant.ofEpochMilli(session.deadline).plusSeconds(120),"org.najwa.restore")
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
        assertEquals(SessionState.RESTORED,app.runtime.session()!!.state)
        assertTrue(shadowOf(alarm).scheduledAlarms.none {
            shadowOf(it.operation).savedIntent.action=="org.najwa.restore"
        })
    }
    @Test fun repeatedForegroundDenialKeepsRestoreRetryBoundToOriginalSession() {
        val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.first()
        now=event.time.minusSeconds(60)
        coldStart().update(prefs)
        fire(event.time.plusSeconds(20),"org.najwa.prayer")
        val session=app.runtime.session()!!
        now=Instant.ofEpochMilli(session.deadline)
        app.runtime.receive("org.najwa.restore",session.id,session.deadline,allowRinger=false)
        now=now.plusSeconds(30)
        val retry=shadowOf(alarm).scheduledAlarms.single {
            it.triggerAtTime==now.toEpochMilli() && shadowOf(it.operation).savedIntent.action=="org.najwa.restore"
        }
        val intent=shadowOf(retry.operation).savedIntent
        assertEquals(session.deadline,intent.getLongExtra("at",0))
        coldStart().receive(intent.action,intent.getStringExtra("id")!!,intent.getLongExtra("at",0),allowRinger=false)
        assertEquals(AudioManager.RINGER_MODE_SILENT,audio.ringerMode)
        assertEquals(session.deadline,app.runtime.session()!!.deadline)
        fire(now.plusSeconds(300),"org.najwa.restore")
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
        assertEquals(SessionState.RESTORED,app.runtime.session()!!.state)
    }
    @Test fun fridayOnlyReminderRunsWeeklyThroughTheRealAlarmReceiver() {
        val friday=Preferences(fridayReminder=true,fridayLeadMinutes=45)
        val runtime=coldStart()
        runtime.update(friday)
        val reminder=runtime.nextScheduled!!
        assertEquals(PrayerAction.FRIDAY,reminder.action)
        assertEquals(DayOfWeek.FRIDAY,reminder.at.atZone(friday.city.zoneId).dayOfWeek)
        assertEquals(reminder.event.time.minusSeconds(45*60L),reminder.at)
        coldStart()
        fire(reminder.at,"org.najwa.prayer")
        val delivered=shadowOf(notifications).allNotifications.single()
        assertEquals("تذكير بصلاة الجمعة",delivered.extras.getCharSequence("android.title"))
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
        assertNull(app.runtime.session())
        val next=app.runtime.nextScheduled!!
        assertEquals(PrayerAction.FRIDAY,next.action)
        assertEquals(reminder.at.atZone(friday.city.zoneId).toLocalDate().plusWeeks(1),next.at.atZone(friday.city.zoneId).toLocalDate())
        assertEquals(friday,PreferencesStore(app).read())
    }
    @Test fun openingAtAdhanDoesNotLoseTheIncomingAlarmOrDuplicateSound() {
        val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.first()
        now=event.time.minusSeconds(30);coldStart().update(prefs)
        val operation=shadowOf(alarm).scheduledAlarms.first {it.triggerAtTime==event.time.toEpochMilli()}.operation
        val deliveredIntent=shadowOf(operation).savedIntent
        now=event.time
        app.runtime.reconcile()
        val session=app.runtime.session()
        AlarmReceiver().onReceive(app,deliveredIntent)
        prayerExecutor.submit {}.get(10,java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(session,app.runtime.session())
        assertEquals(1,shadowOf(notifications).allNotifications.size)
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
        fire(event.time.plusSeconds(20),"org.najwa.prayer")
        assertEquals(AudioManager.RINGER_MODE_SILENT,audio.ringerMode)
    }
    @Test fun scheduledTrialUsesRealAlarmsForAdhanIqamaAndRestorationWithoutChangingSettings() {
        val runtime=coldStart()
        runtime.update(prefs.copy(autoSilent=false,adhanNotifications=false,iqamaNotifications=false))
        val original=runtime.preferences
        runtime.scheduledTrial()
        val start=now.plusSeconds(30)
        now=now.plusSeconds(1)
        runtime.scheduledTrial()
        assertNotNull(runtime.error)
        assertEquals(start,runtime.alarms.trialEvent()?.time)
        coldStart()
        fire(start,"org.najwa.trial")
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
        fire(start.plusSeconds(20),"org.najwa.trial")
        assertEquals(AudioManager.RINGER_MODE_SILENT,audio.ringerMode)
        assertNotNull(shadowOf(notifications).getNotification(499))
        assertEquals(start.plusSeconds(300).toEpochMilli(),app.runtime.session()!!.deadline)
        coldStart();fire(start.plusSeconds(60),"org.najwa.trial")
        assertNotNull(shadowOf(notifications).getNotification(499))
        fire(start.plusSeconds(300),"org.najwa.restore")
        assertEquals(AudioManager.RINGER_MODE_NORMAL,audio.ringerMode)
        assertEquals(original,PreferencesStore(app).read())
    }
}
