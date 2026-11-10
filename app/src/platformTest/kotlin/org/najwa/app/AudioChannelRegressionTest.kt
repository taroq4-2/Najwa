package org.najwa.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class AudioChannelRegressionTest {
    private lateinit var app: NajwaApplication
    private lateinit var manager: NotificationManager
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        manager=app.getSystemService(NotificationManager::class.java)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
    }
    @Test fun adhanUsesAlarmCategoryWhileIqamaRemainsSilentReminder() {
        val now=Instant.parse("2026-11-07T09:00:00Z")
        for(action in listOf(PrayerAction.ADHAN,PrayerAction.IQAMA)) {
            app.runtime.notifications.show(ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),action,now),Preferences(),true)
            val notification=shadowOf(manager).allNotifications.single()
            assertEquals(if(action==PrayerAction.ADHAN)android.app.Notification.CATEGORY_ALARM else android.app.Notification.CATEGORY_REMINDER,notification.category)
            if(action==PrayerAction.IQAMA)assertNull(manager.getNotificationChannel(notification.channelId).sound)
        }
    }
    @Test fun unknownChannelIsNotReportedAsEnabled() {
        assertFalse(app.runtime.notifications.enabled("missing_selected_channel"))
        assertTrue(app.runtime.notifications.enabled(PrayerNotifications.ADHAN))
    }
    @Test fun blockedAdhanDoesNotClaimDeliveryOrAffectSilentIqamaChannel() {
        val channel=manager.getNotificationChannel(PrayerNotifications.ADHAN)
        channel.importance=NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)
        val now=Instant.parse("2026-11-07T09:00:00Z")
        val task=ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now)
        assertEquals(NotificationDelivery.BLOCKED,app.runtime.notifications.show(task,Preferences()))
        assertNull(JsonFile(app,"notification-history.json").read())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertTrue(app.runtime.notifications.enabled(PrayerNotifications.IQAMA))
        assertNull(manager.getNotificationChannel(PrayerNotifications.IQAMA).sound)
    }
    @Test fun postingRetriesOnceAndOnlyClaimsAfterSuccess() {
        val now=Instant.parse("2026-11-07T09:00:00Z")
        val task=ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now)
        val journal=JsonFile(app,"notification-history.json")
        var attempts=0
        val retrying=PrayerNotifications(app,{now}) { system,id,notification ->
            assertNull(journal.read())
            if(++attempts==1)throw IllegalStateException("Transient test failure")
            system.notify(id,notification)
        }
        assertEquals(NotificationDelivery.POSTED,retrying.show(task,Preferences()))
        assertEquals(2,attempts)
        assertEquals(task.id,journal.read()!!.getJSONArray("delivered").getString(0))
        assertEquals(NotificationDelivery.ALREADY_POSTED,retrying.show(task,Preferences()))
        assertEquals(2,attempts)
    }
    @Test fun failedPostingLeavesDeliveryUnclaimed() {
        val now=Instant.parse("2026-11-07T09:00:00Z")
        val task=ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now)
        var attempts=0
        val failing=PrayerNotifications(app,{now}) { _,_,_ ->
            attempts++
            throw IllegalStateException("Persistent test failure")
        }
        assertThrows(IllegalStateException::class.java) {failing.show(task,Preferences())}
        assertEquals(2,attempts)
        assertNull(JsonFile(app,"notification-history.json").read())
    }
    @Test fun lockScreenVersionDoesNotExposePrayerOrCity() {
        val now=Instant.parse("2026-11-07T09:00:00Z")
        val task=ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now)
        app.runtime.notifications.show(task,Preferences())
        val notification=shadowOf(manager).allNotifications.single()
        assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility)
        val publicVersion=notification.publicVersion!!
        assertEquals("نجوى",publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("لديك تنبيه للصلاة",publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertNull(publicVersion.contentView)
    }
    @Test fun invalidHistoryIsRebuiltAndRevokingPrivacyClearsActivePublicAlert() {
        val now=Instant.parse("2026-11-07T09:00:00Z")
        val task=ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now)
        app.filesDir.resolve("notification-history.json").writeText("{broken")
        val prefs=Preferences(showLockScreenDetails=true,adhanPrayers=emptySet(),iqamaPrayers=setOf(Prayer.ASR))
        app.runtime.update(prefs)
        assertEquals(prefs,PreferencesStore(app).read())
        assertEquals(NotificationDelivery.POSTED,app.runtime.notifications.show(task,prefs))
        assertEquals(NotificationDelivery.ALREADY_POSTED,app.runtime.notifications.show(task,prefs))
        app.runtime.update(prefs.copy(showLockScreenDetails=false))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
