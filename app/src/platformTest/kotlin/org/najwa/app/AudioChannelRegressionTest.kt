package org.najwa.app

import android.Manifest
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
        app.runtime.notifications.show(task,Preferences())
        assertNull(JsonFile(app,"notification-history.json").read())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertTrue(app.runtime.notifications.enabled(PrayerNotifications.IQAMA))
        assertNull(manager.getNotificationChannel(PrayerNotifications.IQAMA).sound)
    }
}
