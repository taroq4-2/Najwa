package org.najwa.app

import android.Manifest
import android.app.NotificationManager
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.najwa.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class Release15PlatformTest {
    private lateinit var app: NajwaApplication
    private lateinit var manager: NotificationManager
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        app.getSharedPreferences("interface-language",0).edit().putString("language","ar").commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager=app.getSystemService(NotificationManager::class.java)
        app.runtime.notifications.updateChannels()
    }

    @Test fun remindersPostOnTheirOwnChannel() {
        val now=Instant.now()
        val task=ScheduledPrayer(PrayerEvent(Prayer.FAJR,now),PrayerAction.REMINDER,now,Reminder.MORNING_ADHKAR)
        assertEquals(NotificationDelivery.POSTED,app.runtime.notifications.show(task,Preferences()))
        val posted=shadowOf(manager).allNotifications.single()
        assertEquals(ReminderChannels.ADHKAR,posted.channelId)
        assertTrue(posted.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().contains("أذكار"))
    }

    @Test fun mosqueProfilesPersistAndApplyTheirIqama() {
        val m=Mosque("m1","مسجد الحي",dailyPrayers.associateWith {25},45)
        app.runtime.saveMosque(m,true)
        assertEquals(listOf(m),app.runtime.mosqueList())
        assertEquals("m1",app.runtime.preferences.activeMosque)
        assertEquals(25,app.runtime.preferences.iqamaMinutes.getValue(Prayer.ASR))
        // Silence is extended so it never ends before the new iqama.
        assertTrue(app.runtime.preferences.silenceMinutes.getValue(Prayer.MAGHRIB)>=25+SILENCE_AFTER_IQAMA_MINUTES)
        app.runtime.deleteMosque("m1")
        assertTrue(app.runtime.mosqueList().isEmpty())
        assertNull(app.runtime.preferences.activeMosque)
    }

    @Test fun prayerLogIsStoredOnTheDevice() {
        val day=LocalDate.of(2026,11,24)
        app.runtime.setPrayed(day,Prayer.FAJR,true)
        app.runtime.setPrayed(day,Prayer.FAJR,true)
        app.runtime.setQada(4)
        val log=PrayerLogStore(app).read()
        assertTrue(log.prayed(day,Prayer.FAJR))
        assertEquals(4,log.qada)
        app.runtime.setPrayed(day,Prayer.FAJR,false)
        assertFalse(app.runtime.log().prayed(day,Prayer.FAJR))
    }

    @Test fun newSettingsSurviveStorage() {
        val store=PreferencesStore(app)
        val p=Preferences(method=Method.JAFARI,elevation=900,suhoorReminder=true,taraweehReminder=true,adhkarReminders=true,
            enhancedAdhan=true,flipToStop=false,announceAdhan=true,largeDisplay=true,broadcastEvents=true,activeMosque="x",
            secondCity=City("مكة المكرمة",21.4225,39.8262,"Asia/Riyadh","SA"))
        store.write(p)
        assertEquals(p,store.read())
    }

    @Test fun listAndClearWidgetsRender() {
        val p=Preferences()
        val now=Instant.parse("2026-11-07T07:00:00Z")
        val colors=WidgetColors(false,1,2,3,4,5,6)
        val list=PrayerWidget.listViews(app,p,now,widgetPrayer(now,p),colors).apply(app,FrameLayout(app))
        assertTrue(list.findViewById<TextView>(R.id.widget_p0_name).text.isNotBlank())
        assertTrue(list.findViewById<TextView>(R.id.widget_p4_time).text.isNotBlank())
        val minimal=PrayerWidget.minimalViews(app,p,now,widgetPrayer(now,p)).apply(app,FrameLayout(app))
        assertTrue(minimal.findViewById<TextView>(R.id.widget_prayer).text.isNotBlank())
    }

    @Test fun healthCheckFlagsMissingExactAlarms() {
        app.runtime.update(app.runtime.preferences.copy(adhanNotifications=true))
        val issues=healthIssues(app,app.runtime)
        if(!app.runtime.alarms.exactAccess)assertTrue(HealthIssue.EXACT_ALARMS in issues)
        assertEquals(100,healthScore(emptyList()))
        assertTrue(healthScore(issues)<=100)
    }
}
