package org.najwa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.najwa.app.ui.*
import org.najwa.core.Screen

class AdhanSoundAppearanceTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5.copy(locale="ar"),theme="android:Theme.Material.Light.NoActionBar",supportsRtl=true)
    @Test fun soundPickerStatesStayReadable() {
        for(dark in listOf(true,false))for(large in listOf(false,true)) {
            paparazzi.snapshot("sound_${if(dark)"dark" else "light"}${if(large)"_large" else ""}") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,if(large)1.4f else 1f)) {
                    NajwaTheme(dark) {Column(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                        PageScroll(Screen.NOTIFICATIONS) {
                            BackLink("الرجوع إلى الإعدادات") {}
                            PageHeading("الأذان والإقامة","صوتك المفضل عند دخول وقت الصلاة")
                            AdhanSoundContent("أذان المسجد الحرام.mp3",true,false,"تم حفظ الصوت. جرّب تنبيه الأذان للتأكد.",{},{},{})
                        }
                        BottomNavigation(2) {}
                    }}
                }
            }
        }
    }
}
