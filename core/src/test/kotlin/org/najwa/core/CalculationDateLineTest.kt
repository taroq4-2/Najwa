package org.najwa.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.time.chrono.HijrahDate

class CalculationDateLineTest {
    private val calculator=PrayerCalculator()
    private val locations=listOf(
        City("Kiritimati",1.8721,-157.4278,"Pacific/Kiritimati"),
        City("Apia",-13.8333,-171.75,"Pacific/Apia"))

    @Test fun westernLongitudesEastOfDateLineKeepRequestedLocalPrayerDate() {
        for(city in locations)for(month in listOf(1,4,7,11)) {
            val date=LocalDate.of(2026,month,15)
            val day=calculator.calculate(date,Preferences(city=city,method=Method.MWL))
            assertTrue("${city.name}/$date",day.complete)
            assertEquals(date,day.date)
            day.events.forEach {assertEquals("${city.name}/${it.prayer}",date,it.time.atZone(city.zoneId).toLocalDate())}
        }
    }

    @Test fun aroundMidnightRetainsUniqueChronologicalEventsForEachCivilDate() {
        for(city in locations) {
            val now=LocalDate.of(2026,12,31).atTime(23,59).atZone(city.zoneId).toInstant()
            val prefs=Preferences(city=city,method=Method.MWL)
            val days=calculator.around(now,prefs)
            assertEquals(listOf(LocalDate.of(2026,12,30),LocalDate.of(2026,12,31),LocalDate.of(2027,1,1),LocalDate.of(2027,1,2)),days.map {it.date})
            days.forEach {day->assertEquals(day.date,day.events.first {it.prayer==Prayer.DHUHR}.time.atZone(city.zoneId).toLocalDate())}
            val events=days.flatMap {it.events}
            assertEquals(events.size,events.map {it.id}.toSet().size)
            assertTrue(events.zipWithNext().all {it.first.time<it.second.time})
            assertEquals(LocalDate.of(2027,1,1),timeline(events,now)!!.next.time.atZone(city.zoneId).toLocalDate())
        }
    }

    @Test fun ramadanIntervalUsesRequestedCivilDateInsteadOfShiftedSolarAnchor() {
        val start=LocalDate.from(HijrahDate.of(1447,9,1))
        for(city in locations)for(date in listOf(start.minusDays(1),start)) {
            val day=calculator.calculate(date,Preferences(city=city))
            assertTrue(day.complete)
            val maghrib=day.events.first {it.prayer==Prayer.MAGHRIB}
            val isha=day.events.first {it.prayer==Prayer.ISHA}
            assertEquals(date,maghrib.time.atZone(city.zoneId).toLocalDate())
            assertEquals(if(date==start)120L else 90L,Duration.between(maghrib.time,isha.time).toMinutes())
        }
    }

    @Test fun civilDateSkippedByTimezoneReformProducesExplicitWarning() {
        val skipped=calculator.calculate(LocalDate.of(2011,12,30),Preferences(city=locations.last(),method=Method.MWL))
        assertFalse(skipped.complete)
        assertNotNull(skipped.warning)
    }
}
