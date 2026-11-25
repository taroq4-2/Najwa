package org.najwa.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ProgressBar
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class WidgetRedesignTest {
    private lateinit var app: NajwaApplication
    private val prefs=Preferences()
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        app.getSharedPreferences("interface-language",0).edit().putString("language","ar").commit()
    }
    private fun dhuhr(): Instant {
        val date=Instant.parse("2026-11-07T09:00:00Z").atZone(prefs.city.zoneId).toLocalDate()
        return PrayerCalculator().calculate(date,prefs).events.first {it.prayer==Prayer.DHUHR}.time
    }
    private fun render(now: Instant,width: Int,height: Int): View {
        val colors=WidgetColors(false,1,2,3,4,5,6)
        return PrayerWidget.views(app,prefs,now,width,height,widgetPrayer(now,prefs),colors).apply(app,FrameLayout(app))
    }

    @Test fun countdownTurnsToAlertColourInLastTenMinutesOnly() {
        val adhan=dhuhr()
        val early=render(adhan.minusSeconds(30*60),360,240)
        assertEquals(3,early.findViewById<Chronometer>(R.id.widget_countdown).currentTextColor)
        val soon=render(adhan.minusSeconds(5*60),360,240)
        assertEquals(5,soon.findViewById<Chronometer>(R.id.widget_countdown).currentTextColor)
        assertEquals(5,soon.findViewById<TextView>(R.id.widget_countdown_label).currentTextColor)
    }

    @Test fun afterIqamaShowsPrayerInProgressUntilSilenceWindowEnds() {
        val adhan=dhuhr()
        // Dhuhr defaults: iqama after 15 min, silence window 30 min.
        val view=render(adhan.plusSeconds(20*60),360,240)
        assertEquals("قد قامت الصلاة",view.findViewById<TextView>(R.id.widget_phase).text.toString())
        assertEquals("الظهر",view.findViewById<TextView>(R.id.widget_prayer).text.toString())
        assertEquals(6,view.findViewById<Chronometer>(R.id.widget_countdown).currentTextColor)
        val waiting=render(adhan.plusSeconds(5*60),360,240)
        assertEquals(4,waiting.findViewById<Chronometer>(R.id.widget_countdown).currentTextColor)
        val after=render(adhan.plusSeconds(31*60),360,240)
        assertEquals("العصر",after.findViewById<TextView>(R.id.widget_prayer).text.toString())
    }

    @Test fun everyWidgetSizeNamesTheStageNotJustANumber() {
        val adhan=dhuhr()
        val sizes=listOf(110 to 100,110 to 250,180 to 170,180 to 260,260 to 110,280 to 180,360 to 240)
        val stages=mapOf(
            adhan.minusSeconds(5*60) to null,                 // label depends on size (short in mini)
            adhan.plusSeconds(5*60) to null,
            adhan.plusSeconds(20*60) to "قد قامت الصلاة")
        for((w,h) in sizes) for((now,expected) in stages) {
            val phase=render(now,w,h).findViewById<TextView>(R.id.widget_phase)
            assertNotNull("$w×$h",phase)
            assertEquals("$w×$h",View.VISIBLE,phase.visibility)
            assertTrue("$w×$h",phase.text.isNotBlank())
            if(expected!=null)assertEquals("$w×$h",expected,phase.text.toString())
        }
    }

    @Test fun progressFillsUpAsPrayerApproaches() {
        val adhan=dhuhr()
        val far=render(adhan.minusSeconds(4*3600),360,240).findViewById<ProgressBar>(R.id.widget_progress).progress
        val near=render(adhan.minusSeconds(10*60),360,240).findViewById<ProgressBar>(R.id.widget_progress).progress
        assertTrue("far=$far near=$near",near>far)
    }

    @Test fun roomySizesListAllFivePrayersAndHighlightTheNextOne() {
        val adhan=dhuhr()
        for((w,h) in listOf(360 to 240,280 to 180,260 to 110,180 to 260,110 to 250)) {
            val view=render(adhan.minusSeconds(3600),w,h)
            val names=listOf(R.id.widget_p0_name,R.id.widget_p1_name,R.id.widget_p2_name,R.id.widget_p3_name,R.id.widget_p4_name)
                .map {view.findViewById<TextView>(it).text.toString()}
            assertEquals("$w×$h",listOf("الفجر","الظهر","العصر","المغرب","العشاء"),names)
            // Dhuhr is next → highlighted with the adhan accent.
            assertEquals("$w×$h",3,view.findViewById<TextView>(R.id.widget_p1_time).currentTextColor)
        }
        // The smallest size keeps only the essentials.
        assertNull(render(adhan.minusSeconds(3600),110,100).findViewById<View>(R.id.widget_day))
    }

    @Test fun adhanNotificationUsesNativeTemplateWithIqamaCountdownAndStopAction() {
        val manager=app.getSystemService(NotificationManager::class.java)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
        // The runtime uses the real clock, so the countdown must target the future.
        val now=Instant.ofEpochMilli(System.currentTimeMillis()/1000*1000)
        app.runtime.notifications.show(ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now),prefs,true)
        val all=shadowOf(manager).allNotifications
        val posted=all.single {it.channelId!=PrayerNotifications.LIVE}
        // The live iqama card accompanies the adhan: ongoing, silent, counting down to iqama.
        val live=all.single {it.channelId==PrayerNotifications.LIVE}
        assertTrue(live.flags and Notification.FLAG_ONGOING_EVENT!=0)
        assertEquals(now.toEpochMilli()+prefs.iqamaMinutes.getValue(Prayer.DHUHR)*60_000L,live.`when`)
        assertNull("no custom card",posted.contentView)
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(now.toEpochMilli()+prefs.iqamaMinutes.getValue(Prayer.DHUHR)*60_000L,posted.`when`)
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains(prefs.city.name))
        assertEquals(1,posted.actions.size)
    }
}
