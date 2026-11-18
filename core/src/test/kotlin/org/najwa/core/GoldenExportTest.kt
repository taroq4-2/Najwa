package org.najwa.core

import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Writes the cross-platform reference times used by the iOS NajwaCore tests.
 * Runs only when -Dnajwa.golden=<path> is set, so normal test runs are unaffected:
 *   ./gradlew :core:test --tests '*GoldenExportTest*' -Pnajwa.golden=ios/NajwaCore/Tests/NajwaCoreTests/Resources/golden.json
 */
class GoldenExportTest {
    @Test fun exportReferenceTimes() {
        val target = System.getProperty("najwa.golden") ?: return
        val cities = listOf(
            City("Jeddah", 21.5433, 39.1728, "Asia/Riyadh"), City("Makkah", 21.4225, 39.8262, "Asia/Riyadh"),
            City("Riyadh", 24.7136, 46.6753, "Asia/Riyadh"), City("Cairo", 30.0444, 31.2357, "Africa/Cairo"),
            City("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"), City("London", 51.5074, -0.1278, "Europe/London"),
            City("NewYork", 40.7128, -74.0060, "America/New_York"), City("Jakarta", -6.2088, 106.8456, "Asia/Jakarta"),
            City("Sydney", -33.8688, 151.2093, "Australia/Sydney"), City("Oslo", 59.9139, 10.7522, "Europe/Oslo"),
            City("Karachi", 24.8607, 67.0011, "Asia/Karachi"), City("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati"))
        val dates = listOf("2026-01-15", "2026-03-01", "2026-03-29", "2026-06-21", "2026-09-20", "2026-12-21").map(LocalDate::parse)
        val variants = Method.entries.map { Preferences(method = it) } +
            listOf(Preferences(hanafi = true), Preferences(method = Method.CUSTOM, customFajr = 16.5, customIsha = 14.0)) +
            HighLatitude.entries.map { Preferences(method = Method.MWL, highLatitude = it) }
        val calculator = PrayerCalculator()
        val rows = mutableListOf<String>()
        for (city in cities) for (variant in variants) for (date in dates) {
            val p = variant.copy(city = city)
            val day = calculator.calculate(date, p)
            val times = day.events.joinToString(",") { it.time.epochSecond.toString() }
            rows += """{"city":"${city.name}","lat":${city.latitude},"lon":${city.longitude},"zone":"${city.zone}",""" +
                """"method":"${p.method.name}","hanafi":${p.hanafi},"high":"${p.highLatitude.name}","fajr":${p.customFajr},"isha":${p.customIsha},""" +
                """"date":"$date","times":[$times],"ok":${day.complete}}"""
        }
        File(target).apply { parentFile.mkdirs() }.writeText("[\n" + rows.joinToString(",\n") + "\n]\n")
    }
}
