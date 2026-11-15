package org.najwa.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

class ExternalPrayerTimesTest {
    @Test fun localCalculationAgreesWithIndependentReference() {
        // AlAdhan timings API, same coordinates, method, zone and standard Asr.
        // Three minutes allow differences in rounding and horizon conventions.
        val cases = listOf(
            Reference("Jeddah",21.5433,39.1728,"Asia/Riyadh",Method.UMM_AL_QURA,"2026-11-11","05:13 06:32 12:07 15:20 17:42 19:12"),
            Reference("London",51.5074,-0.1278,"Europe/London",Method.MWL,"2026-10-25","04:49 06:42 11:45 14:18 16:47 18:32"),
            Reference("Cairo",30.0444,31.2357,"Africa/Cairo",Method.EGYPTIAN,"2026-11-11","04:48 06:17 11:39 14:40 17:01 18:21"),
            Reference("Karachi",24.8607,67.0011,"Asia/Karachi",Method.KARACHI,"2026-11-11","05:27 06:45 12:16 15:25 17:46 19:05")
        )
        for(reference in cases) {
            val city=City(reference.name,reference.latitude,reference.longitude,reference.zone)
            val times=PrayerCalculator().calculate(LocalDate.parse(reference.date),Preferences(city=city,method=reference.method))
            assertEquals(Prayer.entries,times.events.map {it.prayer})
            for((event,expected) in times.events.zip(reference.times.split(" ").map(LocalTime::parse))) {
                val observed=event.time.atZone(city.zoneId).toLocalTime()
                val difference=abs(Duration.between(expected,observed).toMinutes())
                assertTrue("${city.name} ${event.prayer}: $observed vs $expected",difference<=3)
            }
        }
    }

    private data class Reference(
        val name: String, val latitude: Double, val longitude: Double, val zone: String,
        val method: Method, val date: String, val times: String
    )
}
