package org.najwa.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.*
import app.cash.paparazzi.*
import com.android.resources.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.najwa.app.platform.PrayerWidget
import org.najwa.core.*
import java.time.LocalDate

class WidgetGlassTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5.copy(locale="ar"),theme="android:Theme.Material.Light.NoActionBar",supportsRtl=true)
    @Test fun transparentBackgroundKeepsTextOpaqueAcrossThemesAndWallpapers() {
        val defaults=Preferences()
        val event=PrayerCalculator().calculate(LocalDate.of(2026,11,7),defaults).events.first {it.prayer==Prayer.DHUHR}
        for(light in listOf(false,true))for(wallpaperLight in listOf(false,true))for(opacity in listOf(80,85,100))for(iqama in listOf(false,true)) {
            val w=360;val h=240
            paparazzi.unsafeUpdateConfig(deviceConfig=DeviceConfig.PIXEL_5.copy(locale="ar",screenWidth=w*2,screenHeight=h*2,density=Density.XHIGH,orientation=ScreenOrientation.LANDSCAPE))
            val parent=FrameLayout(paparazzi.context).apply {
                layoutParams=ViewGroup.LayoutParams(w*2,h*2)
                background=GradientDrawable(GradientDrawable.Orientation.TL_BR,if(wallpaperLight)intArrayOf(Color.parseColor("#F3D7A0"),Color.parseColor("#F8EEE3"),Color.parseColor("#C8DFCF")) else intArrayOf(Color.parseColor("#67508C"),Color.parseColor("#BB7979"),Color.parseColor("#243E5F")))
            }
            val p=defaults.copy(widgetTheme=if(light)WidgetTheme.LIGHT else WidgetTheme.DARK,widgetOpacity=opacity)
            val views=PrayerWidget.views(paparazzi.context,p,event.time.plusSeconds(if(iqama)60 else -60),w,h)
            val view=views.apply(paparazzi.context,parent)
            parent.addView(view,FrameLayout.LayoutParams(-1,-1))
            view.findViewById<Chronometer>(R.id.widget_countdown).apply {stop();text=if(iqama)"14:00" else "01:00"}
            assertEquals(opacity*255/100,view.findViewById<ImageView>(R.id.widget_backdrop).imageAlpha)
            assertEquals(1f,view.findViewById<TextView>(R.id.widget_prayer).alpha)
            paparazzi.snapshot(parent,"glass_${if(light)"light" else "dark"}_${opacity}_wall${wallpaperLight}_${if(iqama)"iqama" else "adhan"}")
        }
    }
}
