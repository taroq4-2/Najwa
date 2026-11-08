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
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5.copy(screenWidth=780,screenHeight=1688,density=Density.XHIGH,softButtons=false),theme="android:Theme.Material.Light.NoActionBar")
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
