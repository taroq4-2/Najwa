package org.najwa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.ArabCityCatalog
import org.najwa.app.platform.PreferencesStore
import org.najwa.core.Preferences
import org.najwa.core.PrayerCalculator
import org.najwa.core.citySearchKey
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],shadows=[WindowsAtomicFile::class])
class ArabCityCatalogTest {
    @Test fun offlineCatalogFindsArabicAndEnglishAliasesAndCalculatesLocalTimes() {
        val app=RuntimeEnvironment.getApplication()
        assertEquals(22,ArabCityCatalog.countries.size)
        val samples=mapOf("SA" to ("جدة" to "Jeddah"),"EG" to ("الإسكندرية" to "Alexandria"),"OM" to ("مسقط" to "Muscat"))
        for((code,names) in samples) {
            val country=ArabCityCatalog.countries.first {it.code==code}
            val (arabic,english)=names
            val places=ArabCityCatalog.load(app,country)
            val place=places.first {it.name==arabic && citySearchKey(english) in it.terms}
            val day=PrayerCalculator().calculate(LocalDate.of(2026,11,8),Preferences(city=place.city(country)))
            assertTrue(day.complete)
            assertEquals(6,day.events.size)
            assertEquals(code,ArabCityCatalog.forZone(place.zone).code)
        }
    }

    @Test fun verifiedBorderZonesAndCountryIdentitySurviveSelection() {
        val app=RuntimeEnvironment.getApplication()
        val samples=mapOf("DZ" to ("Africa/Bamako" to "Africa/Algiers"),"SD" to ("Africa/Ndjamena" to "Africa/Khartoum"),
            "AE" to ("Asia/Muscat" to "Asia/Dubai"),"PS" to ("Asia/Jerusalem" to "Asia/Jerusalem"))
        for((code,zones) in samples) {
            val country=ArabCityCatalog.countries.first {it.code==code}
            val place=ArabCityCatalog.load(app,country).first {it.zone==zones.first}
            val city=place.city(country)
            assertEquals(zones.second,city.zone)
            val store=PreferencesStore(app)
            store.write(Preferences(city=city))
            assertEquals(city,store.read().city)
            assertEquals(code,ArabCityCatalog.forCity(store.read().city).code)
        }
    }
}
