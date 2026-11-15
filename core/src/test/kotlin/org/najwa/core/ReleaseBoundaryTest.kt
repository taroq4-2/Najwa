package org.najwa.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** Structural safety checks, not an independent astronomical accuracy reference. */
class ReleaseBoundaryTest {
    @Test fun seasonalWorldMatrixProducesOrderedDatesOrExplicitUnavailability() {
        val calculator=PrayerCalculator()
        val cities=Cities.all+listOf(
            City("Tromso",69.6492,18.9553,"Europe/Oslo"),
            City("Ushuaia",-54.8019,-68.303,"America/Argentina/Ushuaia"),
            City("Kiritimati",1.8721,-157.4278,"Pacific/Kiritimati"),
            City("Apia",-13.8333,-171.75,"Pacific/Apia"))
        var checked=0
        for(city in cities)for(month in 1..12)for(method in Method.entries)for(rule in HighLatitude.entries) {
            val date=LocalDate.of(2027,month,15)
            val p=Preferences(city=city,method=method,highLatitude=rule,
                autoSilent=true,adhanNotifications=true,iqamaNotifications=true)
            val day=calculator.calculate(date,p)
            val label="${city.name}/$date/$method/$rule"
            if(day.complete) {
                assertNull(label,day.warning)
                assertEquals(label,Prayer.entries,day.events.map {it.prayer})
                assertTrue(label,day.events.zipWithNext().all {it.first.time<it.second.time})
                assertEquals(label,date,day.events.single {it.prayer==Prayer.DHUHR}.time.atZone(city.zoneId).toLocalDate())
                val tasks=prayerSchedule(day.events,p)
                assertEquals(label,15,tasks.size)
                assertEquals(label,tasks.size,tasks.map {it.id}.distinct().size)
                assertTrue(label,tasks.none {it.event.prayer==Prayer.SUNRISE})
                assertTrue(label,tasks.zipWithNext().all {it.first.at<=it.second.at})
            } else {
                assertTrue(label,day.events.isEmpty())
                assertFalse(label,day.warning.isNullOrBlank())
                assertTrue(label,prayerSchedule(day.events,p).isEmpty())
            }
            checked++
        }
        assertEquals(6048,checked)
    }

    @Test fun lateAndFutureDeliveryNeverEscapesNotificationOrSilenceWindows() {
        val start=Instant.parse("2027-01-01T23:59:50Z")
        for(prayer in dailyPrayers)for(duration in listOf(5,20,120))for(iqama in listOf(1,5,120)) {
            val p=Preferences(autoSilent=true,adhanNotifications=true,iqamaNotifications=true,
                silenceMinutes=defaultSilenceMinutes().mapValues {duration},
                iqamaMinutes=defaultIqamaMinutes().mapValues {iqama})
            val tasks=prayerSchedule(listOf(PrayerEvent(prayer,start)),p)
            for(offset in listOf(-1L,0,19,20,120,121,299,300,7200,7321)) {
                val now=start.plusSeconds(offset)
                val due=dispatchablePrayers(tasks,tasks,now,p)
                assertTrue(due.all {it.at<=now})
                assertTrue(due.filter {it.action!=PrayerAction.SILENCE}.all {Duration.between(it.at,now).seconds<=120})
                assertTrue(due.filter {it.action==PrayerAction.SILENCE}.all {now<start.plusSeconds(duration*60L)})
                assertTrue(dispatchablePrayers(tasks,emptyList(),now,p).isEmpty())
                assertTrue(dispatchablePrayers(emptyList(),tasks,now,p).isEmpty())
            }
        }
    }
}
