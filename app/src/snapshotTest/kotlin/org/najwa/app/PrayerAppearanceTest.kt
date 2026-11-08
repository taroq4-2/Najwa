package org.najwa.app

import android.view.ViewGroup
import android.widget.*
import app.cash.paparazzi.*
import com.android.resources.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.najwa.app.platform.*
import org.najwa.core.*
import org.najwa.app.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate

class PrayerAppearanceTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5,theme="android:Theme.Material.Light.NoActionBar",supportsRtl=true)
    private val prefs=Preferences(adhanNotifications=true,iqamaNotifications=true)
    private val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),prefs).events.first {it.prayer==Prayer.DHUHR}
    @Test fun responsiveWidgetsShowBothPhasesAtThreeSizesAndLargeFonts() {
        val sizes=listOf(110 to 100,110 to 280,180 to 130,180 to 210,180 to 320,
            260 to 110,280 to 180,390 to 240,600 to 300)
        for(scale in listOf(1f,1.4f))for((w,h) in sizes)for(iqama in listOf(false,true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig=DeviceConfig.PIXEL_5.copy(screenWidth=w*2,screenHeight=h*2,density=Density.XHIGH,fontScale=scale,orientation=if(w<h)ScreenOrientation.PORTRAIT else ScreenOrientation.LANDSCAPE))
            val now=event.time.plusSeconds(if(iqama)60 else -60)
            val view=PrayerWidget.views(paparazzi.context,prefs,now,w,h).apply(paparazzi.context,FrameLayout(paparazzi.context))
            view.layoutParams=ViewGroup.LayoutParams(w*2,h*2)
            val timer=view.findViewById<Chronometer>(R.id.widget_countdown)
            timer.stop();timer.text=if(iqama)"14:00" else "01:00"
            assertEquals(if(iqama)"المتبقي على الإقامة" else "الصلاة القادمة",view.findViewById<TextView>(R.id.widget_phase).text.toString())
            assertEquals("الظهر",view.findViewById<TextView>(R.id.widget_prayer).text.toString())
            assertTrue(view.findViewById<TextView>(R.id.widget_hijri).text.endsWith("هـ"))
            paparazzi.snapshot(view,"widget_${w}x${h}_${if(iqama)"iqama" else "adhan"}_${scale}")
            assertEquals(w*2,view.width)
            assertEquals(h*2,view.height)
            assertTrue("Counter keeps readable height at ${w}x${h}, scale=$scale, iqama=$iqama (height=${timer.height})",timer.height>12)
        }
    }
    @Test fun bothNotificationDesignsInflateAtNarrowWidth() {
        for(scale in listOf(1f,1.4f))for(w in listOf(260,320,420))for(expanded in listOf(false,true))for(adhan in listOf(false,true)) {
            val h=if(expanded)224 else 48
            paparazzi.unsafeUpdateConfig(deviceConfig=DeviceConfig.PIXEL_5.copy(screenWidth=w*2,screenHeight=h*2,density=Density.XHIGH,fontScale=scale,orientation=ScreenOrientation.LANDSCAPE))
            val task=ScheduledPrayer(event,if(adhan)PrayerAction.ADHAN else PrayerAction.IQAMA,event.time)
            val title=if(adhan)"حان وقت صلاة الظهر" else "حان وقت إقامة الظهر"
            val view=notificationViews(paparazzi.context,task,prefs,title,"جدة · الإقامة بعد 15 دقيقة · حسب إعدادك",expanded).apply(paparazzi.context,FrameLayout(paparazzi.context))
            view.layoutParams=ViewGroup.LayoutParams(w*2,h*2)
            if(expanded && adhan)view.findViewById<Chronometer>(R.id.notification_timer).apply {stop();text="15:00"}
            assertEquals(title,view.findViewById<TextView>(R.id.notification_title).text.toString())
            if(expanded)assertTrue(view.findViewById<TextView>(R.id.notification_message).text.isNotBlank())
            paparazzi.snapshot(view,"notification_${w}_${scale}_${if(adhan)"adhan" else "iqama"}_${if(expanded)"expanded" else "compact"}")
        }
    }
    @Test fun homeShowsIqamaAsTheMainCountdown() {
        val now=event.time.plusSeconds(60)
        val days=PrayerCalculator().around(now,prefs)
        for(dark in listOf(true,false))paparazzi.snapshot("home_iqama_${if(dark)"dark" else "light"}") {
            NajwaTheme(dark) {Column(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                PageScroll(Screen.HOME) {HomeScreen(prefs,now,days[1],timeline(days.flatMap {it.events},now),true,{},{})}
                BottomNavigation(0) {}
            }}
        }
    }
}
