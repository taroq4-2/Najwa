package org.najwa.core

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NavigationWidgetTest {
    @Test fun detailBackUsesActualEntryAndRepeatedNavigationDoesNotGrowTrail() {
        val settings=navigateScreens(listOf(Screen.HOME.name),Screen.SETTINGS,true)
        val silent=navigateScreens(settings,Screen.SILENCE)
        val permissions=navigateScreens(silent,Screen.PERMISSIONS)
        assertEquals(silent,backScreens(permissions))
        assertEquals(settings,backScreens(silent))
        assertEquals(permissions,navigateScreens(permissions,Screen.PERMISSIONS))
        assertEquals(settings,navigateScreens(permissions,Screen.SETTINGS))
        assertEquals(listOf(Screen.HOME.name),navigateScreens(permissions,Screen.HOME,true))
        assertEquals(listOf(Screen.HOME.name,Screen.SILENCE.name),navigateScreens(settings,Screen.SILENCE,true))
    }
    @Test fun everySubpageReturnsToHomeWithoutLoops() {
        Screen.entries.forEach {initial->
            var screen=initial
            val visited=mutableSetOf<Screen>()
            while(screen.parent!=null) {assertTrue(visited.add(screen));screen=screen.parent!!}
            assertEquals(Screen.HOME,screen)
        }
    }
    @Test fun settingsDetailsReturnToTheirListFirst() {
        listOf(Screen.ABOUT,Screen.CONTACT,Screen.PRIVACY).forEach {assertEquals(Screen.INFORMATION,it.parent)}
        assertEquals(Screen.SETTINGS,Screen.INFORMATION.parent)
        assertEquals(Screen.SETTINGS,Screen.WIDGET.parent)
        assertEquals(Screen.SILENCE,Screen.PERMISSIONS.parent)
    }
    @Test fun savedNavigationAndTabsStayConsistent() {
        Screen.entries.forEach {assertEquals(it,Screen.fromSaved(it.name))}
        assertEquals(Screen.HOME,Screen.fromSaved("deleted_screen"))
        assertEquals(Screen.SETTINGS,Screen.forTab(Screen.PRIVACY.tab))
    }
    @Test fun widgetSkipsSunriseAndAdvancesAtPrayerBoundary() {
        val p=Preferences()
        val day=PrayerCalculator().calculate(java.time.LocalDate.of(2026,11,7),p)
        val fajr=day.events.first {it.prayer==Prayer.FAJR}
        assertEquals(Prayer.FAJR,widgetPrayer(fajr.time.minusSeconds(1),p)!!.prayer)
        assertEquals(CountdownPhase.IQAMA,widgetPrayer(fajr.time,p)!!.phase)
        assertEquals(Prayer.FAJR,widgetPrayer(fajr.time,p)!!.prayer)
        assertEquals(Prayer.DHUHR,widgetPrayer(fajr.time.plusSeconds(20*60),p)!!.prayer)
    }
    @Test fun widgetAdvancesToTomorrowAfterIsha() {
        val p=Preferences()
        val isha=PrayerCalculator().calculate(java.time.LocalDate.of(2026,11,7),p).events.last()
        val next=widgetPrayer(isha.time.plusSeconds(15*60),p)!!
        assertEquals(Prayer.FAJR,next.prayer)
        assertTrue(next.time>isha.time)
    }
    @Test fun widgetUsesCurrentCityTimeFormatAndIqamaSettings() {
        val p=Preferences(hour24=true,iqamaNotifications=true)
        val data=widgetPrayer(Instant.parse("2026-11-07T09:00:00Z"),p)!!
        assertEquals(p.city.name,data.city)
        assertEquals(displayTime(data.time,p),data.timeLabel)
        assertEquals(displayTime(data.time.plusSeconds(900),p),data.iqamaLabel)
        assertNull(widgetPrayer(Instant.parse("2026-11-07T09:00:00Z"),p.copy(showIqamaCountdown=false))!!.iqamaLabel)
    }
    @Test fun polarUnavailableTimesDoNotProduceBrokenCountdown() {
        val p=Preferences(city=City("القطب",89.0,0.0,"UTC"))
        assertNull(widgetPrayer(Instant.parse("2026-06-21T12:00:00Z"),p))
    }
}
