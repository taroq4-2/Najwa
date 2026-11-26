package org.najwa.app

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.najwa.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** The "My day" card: one layout with the prayer part, a mark per recorded prayer and the day's tiles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class DayCardPlatformTest {
    private lateinit var app: NajwaApplication
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        app.getSharedPreferences("interface-language",0).edit().putString("language","ar").commit()
    }
    private fun day(p: Preferences,now: Instant,height: Int=400): View=DayWidget.render(app,p,now,height).apply(app,FrameLayout(app))

    @Test fun prayerPartAndTilesRenderInOneCard() {
        WorshipSettingsStore.write(app,WorshipSettings(enabled=true))
        val p=Preferences(prayerLog=true)
        val now=Instant.now()
        val v=day(p,now)
        assertTrue(v.findViewById<TextView>(R.id.widget_prayer).text.isNotBlank())
        assertEquals(View.VISIBLE,v.findViewById<View>(R.id.day_stats).visibility)
        // The card speaks the chosen interface language (Arabic here).
        assertEquals("صلوات اليوم",v.findViewById<TextView>(R.id.day_p_title).text.toString())
        assertTrue(v.findViewById<TextView>(R.id.day_p_value).text.isNotBlank())
        assertTrue(v.findViewById<TextView>(R.id.day_c_title).text.isNotBlank())
    }

    @Test fun shortCardsDropTilesBeforeThePrayerPart() {
        WorshipSettingsStore.write(app,WorshipSettings(enabled=true))
        val p=Preferences()
        assertEquals(View.GONE,day(p,Instant.now(),300).findViewById<View>(R.id.day_chips).visibility)
        val small=day(p,Instant.now(),220)
        assertEquals(View.GONE,small.findViewById<View>(R.id.day_stats).visibility)
        assertTrue(small.findViewById<TextView>(R.id.widget_prayer).text.isNotBlank())
    }

    @Test fun withoutTheCompanionOnlyThePrayerPartShows() {
        WorshipSettingsStore.write(app,WorshipSettings(enabled=false))
        val v=day(Preferences(),Instant.now())
        assertEquals(View.GONE,v.findViewById<View>(R.id.day_stats).visibility)
    }

    @Test fun backgroundTransparencyCoversTheWholeRange() {
        assertEquals(0,Preferences(widgetOpacity=0).widgetOpacity)
        assertEquals(100,Preferences(widgetOpacity=100).widgetOpacity)
        assertNotNull(day(Preferences(widgetOpacity=0),Instant.now()))
    }
}
