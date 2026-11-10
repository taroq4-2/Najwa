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
        assertNull(app.runtime.error)
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
