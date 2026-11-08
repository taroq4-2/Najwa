package org.najwa.app

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import org.najwa.app.ui.*
import org.najwa.core.*
import java.time.*

/** Deterministic sample values exist exclusively in the debug source set. */
class GoldenActivity: ComponentActivity() {
    @Volatile var contentRect: Rect?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val p=Preferences(autoSilent=true)
        val now=ZonedDateTime.of(2026,11,6,16,43,28,0,p.city.zoneId).toInstant()
        val clocks=listOf(5 to 1,6 to 16,11 to 48,15 to 8,17 to 31,19 to 1)
        val events=Prayer.entries.mapIndexed { i,prayer->PrayerEvent(prayer,ZonedDateTime.of(2026,11,6,clocks[i].first,clocks[i].second,0,0,p.city.zoneId).toInstant()) }
        val day=PrayerDay(LocalDate.of(2026,11,6),events)
        setContent {
            val c=if(intent.getBooleanExtra("light",false))Light else Dark
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,LocalPalette provides c) {
                NajwaTheme(!intent.getBooleanExtra("light",false)) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Column(Modifier.requiredSize(390.dp,844.dp).background(c.bg).onGloballyPositioned { val r=it.boundsInWindow();contentRect=Rect(r.left.toInt(),r.top.toInt(),r.right.toInt(),r.bottom.toInt()) }) {
                            Column(Modifier.weight(1f).padding(horizontal=20.dp)) {HomeScreen(p,now,day,timeline(events,now),true,{},{})}
                            BottomNavigation(0) {}
                        }
                    }
                }
            }
        }
    }
}
