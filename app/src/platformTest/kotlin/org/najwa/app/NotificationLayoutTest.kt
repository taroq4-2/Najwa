package org.najwa.app

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.najwa.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class NotificationLayoutTest {
    private val time=Instant.parse("2026-11-07T09:00:00Z")
    private val prefs=Preferences()
    private val event=PrayerEvent(Prayer.DHUHR,time)
    private fun context(language: String,scale: Float): Context {
        val app=RuntimeEnvironment.getApplication() as NajwaApplication
        app.getSharedPreferences("interface-language",0).edit().putString("language",language).commit()
        val config=Configuration(app.resources.configuration).apply {fontScale=scale;densityDpi=320}
        return app.createConfigurationContext(config)
    }
    @Test fun compactAndExpandedKeepTitlesAndTimersInsideSystemHeightBudget() {
        for(language in listOf("ar","en","id","ur","bn","hi","tr"))
        for(scale in listOf(1f,1.4f,2f))
        for(width in listOf(260,320,420))
        for(expanded in listOf(false,true))
        for(action in listOf(PrayerAction.ADHAN,PrayerAction.IQAMA,PrayerAction.FRIDAY)) {
            val context=context(language,scale)
            val strings=context.prayerLanguage()
            val prayer=strings.prayerName(Prayer.DHUHR)
            val title=when {
                action==PrayerAction.FRIDAY -> strings.getString(R.string.system_friday_title)
                expanded -> strings.getString(if(action==PrayerAction.ADHAN)R.string.system_adhan_title else R.string.system_iqama_title,prayer)
                else -> strings.getString(if(action==PrayerAction.ADHAN)R.string.system_adhan else R.string.system_iqama)+" · "+prayer
            }
            val task=ScheduledPrayer(event,action,time)
            val view=notificationViews(context,task,prefs,title,"",expanded,time).apply(context,FrameLayout(context))
            val density=context.resources.displayMetrics.density
            view.measure(View.MeasureSpec.makeMeasureSpec((width*density).roundToInt(),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(((if(expanded)252 else 48)*density).roundToInt(),View.MeasureSpec.AT_MOST))
            view.layout(0,0,view.measuredWidth,view.measuredHeight)
            val label="$language scale=$scale width=$width expanded=$expanded action=$action"
            assertTrue(label,view.measuredHeight<=((if(expanded)252 else 48)*density).roundToInt())
            val heading=view.findViewById<TextView>(R.id.notification_title)
            assertEquals(label,title,heading.text.toString())
            assertTrue(label,heading.width>0 && heading.height>0)
            val timerBox=view.findViewById<View>(R.id.notification_timer_box)
            assertEquals(label,if(action==PrayerAction.ADHAN)View.VISIBLE else View.GONE,timerBox.visibility)
            if(action==PrayerAction.ADHAN) {
                val timer=view.findViewById<Chronometer>(R.id.notification_timer)
                assertTrue(label,timer.isCountDown)
                assertEquals(label,prefs.iqamaMinutes.getValue(Prayer.DHUHR)*60_000L,timer.base-SystemClock.elapsedRealtime())
                assertTrue(label,timer.width>0 && timer.height>0)
                timer.stop()
            }
        }
    }
    @Test fun reapplyingExpiredOrIqamaNotificationHidesAndStopsCompactCountdown() {
        val context=context("ar",1f)
        val parent=FrameLayout(context)
        val task=ScheduledPrayer(event,PrayerAction.ADHAN,time)
        for(expanded in listOf(false,true)) {
            val view=notificationViews(context,task,prefs,"الأذان","",expanded,time).apply(context,parent)
            val deadline=time.plusSeconds(prefs.iqamaMinutes.getValue(Prayer.DHUHR)*60L)
            notificationViews(context,task,prefs,"الأذان","",expanded,deadline).reapply(context,view)
            assertEquals(View.GONE,view.findViewById<View>(R.id.notification_timer_box).visibility)
            assertTrue(view.findViewById<Chronometer>(R.id.notification_timer).base>=SystemClock.elapsedRealtime())
            notificationViews(context,task.copy(action=PrayerAction.IQAMA),prefs,"الإقامة","",expanded,deadline).reapply(context,view)
            assertEquals(View.GONE,view.findViewById<View>(R.id.notification_timer_box).visibility)
        }
    }
}
