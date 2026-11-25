package org.najwa.app

import android.Manifest
import android.app.Notification
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class Release14PlatformTest {
    private lateinit var app: NajwaApplication
    private lateinit var manager: NotificationManager
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        app.getSharedPreferences("interface-language",0).edit().putString("language","ar").commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager=app.getSystemService(NotificationManager::class.java)
        app.runtime.notifications.updateChannels()
    }

    @Test fun settingsBackupRoundTripsEveryNewOption() {
        val store=PreferencesStore(app)
        val p=Preferences(offsets=mapOf(Prayer.MAGHRIB to 3,Prayer.SUNRISE to -2),hijriOffset=-1,preAdhanReminder=true,preAdhanMinutes=15,
            jumuah=false,fridaySilenceMinutes=75,dynamicColor=true,method=Method.TURKEY)
        val restored=store.import(store.export(p))
        assertEquals(p,restored)
        store.write(p)
        assertEquals(p,store.read())
        assertThrows(IllegalArgumentException::class.java) {store.import("""{"city":"x"}""")}
    }

    @Test fun storedSilenceShorterThanIqamaIsExtendedOnRead() {
        val store=PreferencesStore(app)
        store.write(Preferences(iqamaMinutes=defaultIqamaMinutes()+(Prayer.ISHA to 40)))
        assertEquals(40+SILENCE_AFTER_IQAMA_MINUTES,store.read().silenceMinutes.getValue(Prayer.ISHA))
    }

    @Test fun builtInAdhansAreSelectableAndRecognised() {
        val sounds=app.runtime.notifications.sounds
        for(index in AdhanSounds.BUILT_IN) {
            val choice=sounds.builtIn(index)
            val channel=manager.getNotificationChannel(choice.channelId)
            assertEquals(index,AdhanSounds.builtInIndex(channel.sound.toString()))
            assertEquals(choice.channelId,sounds.selected().channelId)
        }
        assertNotNull(app.resources.openRawResourceFd(R.raw.adhan_1))
        assertNotNull(app.resources.openRawResourceFd(R.raw.adhan_2))
        assertNull(AdhanSounds.builtInIndex("content://media/external/audio/1"))
    }

    @Test fun preAdhanReminderPostsWithItsOwnChannel() {
        val adhan=Instant.parse("2026-11-07T09:00:00Z")
        val task=ScheduledPrayer(PrayerEvent(Prayer.ASR,adhan),PrayerAction.PRE_ADHAN,adhan.minusSeconds(600))
        assertEquals(NotificationDelivery.POSTED,app.runtime.notifications.show(task,Preferences()))
        val posted=shadowOf(manager).allNotifications.single()
        assertEquals(PrayerNotifications.PRE_ADHAN,posted.channelId)
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("العصر"))
    }

    @Test fun jumuahNameAppearsOnFridaysOnly() {
        val p=Preferences()
        val friday=LocalDate.of(2026,11,23).with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
        val dhuhr=PrayerCalculator().calculate(friday,p).events.first {it.prayer==Prayer.DHUHR}
        assertEquals("الجمعة",app.prayerLanguage().prayerName(dhuhr,p))
        assertEquals("الظهر",app.prayerLanguage().prayerName(dhuhr,p.copy(jumuah=false)))
        assertEquals("الظهر",app.prayerLanguage().prayerName(dhuhr.copy(time=dhuhr.time.minusSeconds(86_400)),p))
    }

    @Test fun oneByOneWidgetShowsPrayerAndCountdown() {
        val p=Preferences()
        val now=Instant.parse("2026-11-07T07:00:00Z")
        val colors=WidgetColors(false,1,2,3,4,5,6)
        val view=PrayerWidget.views(app,p,now,57,50,widgetPrayer(now,p),colors).apply(app,FrameLayout(app))
        assertTrue(view.findViewById<TextView>(R.id.widget_prayer).text.isNotBlank())
        assertNotNull(view.findViewById<android.widget.Chronometer>(R.id.widget_countdown))
        assertNull(view.findViewById<TextView>(R.id.widget_city))
    }

    @Test fun manualSilenceNeedsRingerAccess() {
        // Robolectric grants no notification-policy access: the tile/shortcut must explain instead of muting.
        if(app.runtime.canSilenceNow())return
        assertNull(app.runtime.manualSilence())
        assertNotNull(app.runtime.error)
    }
}
