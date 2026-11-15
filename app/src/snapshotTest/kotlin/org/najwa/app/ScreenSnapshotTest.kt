package org.najwa.app

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import org.junit.Rule
import org.junit.Test
import org.najwa.app.ui.*
import org.najwa.core.*
import java.time.Instant

class ScreenSnapshotTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5.copy(locale="ar",screenWidth=780,screenHeight=1688,density=Density.XHIGH,softButtons=false),theme="android:Theme.Material.Light.NoActionBar")
    @Test fun madarHomeAtPhoneAndTabletWidths() {
        val prefs=Preferences(autoSilent=true,adhanNotifications=true,iqamaNotifications=true)
        val now=Instant.parse("2026-11-11T14:24:18Z")
        val days=PrayerCalculator().around(now,prefs)
        val day=days.first {it.date==now.atZone(prefs.city.zoneId).toLocalDate()}
        for(width in listOf(320,360,600)) for(scale in listOf(1f,1.3f,2f))
            for(language in listOf("ar","en")) for(dark in listOf(true,false)) {
                paparazzi.unsafeUpdateConfig(deviceConfig=DeviceConfig.PIXEL_5.copy(
                    locale=language,screenWidth=width*2,screenHeight=1688,density=Density.XHIGH,
                    fontScale=scale,softButtons=false))
                paparazzi.snapshot("madar_home_${width}_${scale}_${language}_${if(dark)"dark" else "light"}") {
                    InterfaceLanguage {
                        NajwaTheme(dark) {
                            Column(Modifier.fillMaxSize().background(LocalPalette.current.bg),
                                horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                PageScroll(Screen.HOME) {
                                    HomeScreen(prefs,now,day,timeline(days.flatMap {it.events},now),true,{},{})
                                }
                                BottomNavigation(0) {}
                            }
                        }
                    }
                }
            }
    }
    @Test fun screensInBothThemes() {
        val prefs=Preferences(adhanNotifications=true,iqamaNotifications=true)
        val now=Instant.parse("2026-11-06T13:43:28Z")
        val days=PrayerCalculator().around(now,prefs)
        val day=days.first {it.date==now.atZone(prefs.city.zoneId).toLocalDate()}
        for(scale in listOf(1f,1.4f)) for(dark in listOf(true,false)) for(tab in 0..3) {
            paparazzi.snapshot(name="${listOf("home","silence","settings","notifications")[tab]}_${if(dark)"dark" else "light"}${if(scale>1f)"_large" else ""}") {
                CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(LocalDensity.current.density,scale)) {
                NajwaTheme(dark) {
                    Column(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
                            when(tab) {
                                0->HomeScreen(prefs,now,day,timeline(days.flatMap {it.events},now),false,{},{})
                                1->SilenceContent(prefs,null,now,true,false,{},{},{})
                                2->SettingsScreen(prefs,{},{})
                                3->{PageHeading("الأذان والإقامة","تنبيه في الموعد، وتذكير بالإقامة حسب اختيارك");NotificationPreferences(prefs,{})}
                            }
                        }
                        BottomNavigation(tab.coerceAtMost(2)) {}
                    }
                }
                }
            }
        }
    }
}
