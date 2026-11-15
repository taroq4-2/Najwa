package org.najwa.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.CityCatalog
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
    @Test fun worldwideCitiesAreSearchableOfflineAndKeepTheirTimeZone() = runBlocking {
        val app=RuntimeEnvironment.getApplication()
        val countries=CityCatalog.countries(app)
        assertTrue(countries.size>=240)
        val samples=listOf(
            Triple("ID","Jakarta","Asia/Jakarta"),
            Triple("PK","Karachi","Asia/Karachi"),
            Triple("BD","ঢাকা","Asia/Dhaka"),
            Triple("IN","Mumbai","Asia/Kolkata"),
            Triple("TR","Istanbul","Europe/Istanbul"),
            Triple("GB","London","Europe/London"),
            Triple("US","New York","America/New_York"),
            Triple("BR","São Paulo","America/Sao_Paulo"),
            Triple("AU","Sydney","Australia/Sydney")
        )
        for((code,query,zone) in samples) {
            val country=countries.first {it.code==code}
            val match=CityCatalog.search(app,country,citySearchKey(query)).firstOrNull {it.zone==zone}
                ?: error("No match for $query in $code with zone $zone")
            val city=match.city(country)
            assertEquals(code,CityCatalog.forCity(app,city,countries).code)
            assertEquals(zone,city.zone)
            assertTrue(PrayerCalculator().calculate(LocalDate.of(2026,11,11),Preferences(city=city)).complete)
        }
    }

    @Test fun interactiveSearchBoundsResultsAndFindsArabicAndLatinAliases() = runBlocking {
        val app=RuntimeEnvironment.getApplication()
        val yemen=CityCatalog.countries(app).first {it.code=="YE"}
        assertEquals(100,CityCatalog.search(app,yemen,"").size)
        assertTrue(CityCatalog.search(app,yemen,"nonexistent_catalog_entry_2026").isEmpty())
        val saudi=CityCatalog.countries(app).first {it.code=="SA"}
        val arabic=CityCatalog.search(app,saudi,citySearchKey("جِدَّة"))
        val english=CityCatalog.search(app,saudi,citySearchKey("Jeddah"))
        assertTrue(arabic.any {it.name=="جدة"})
        assertEquals(arabic.first {it.name=="جدة"}.id,english.first {it.name=="جدة"}.id)
    }

    @Test fun offlineCatalogFindsArabicAndEnglishAliasesAndCalculatesLocalTimes() {
        val app=RuntimeEnvironment.getApplication()
        assertEquals(22,CityCatalog.countries(app).count {it.arabicName!=null})
        val samples=mapOf("SA" to ("جدة" to "Jeddah"),"EG" to ("الإسكندرية" to "Alexandria"),"OM" to ("مسقط" to "Muscat"))
        for((code,names) in samples) {
            val country=CityCatalog.countries(app).first {it.code==code}
            val (arabic,english)=names
            val places=CityCatalog.load(app,country)
            val place=places.first {it.name==arabic && citySearchKey(english) in it.terms}
            val day=PrayerCalculator().calculate(LocalDate.of(2026,11,8),Preferences(city=place.city(country)))
            assertTrue(day.complete)
            assertEquals(6,day.events.size)
            assertEquals(code,CityCatalog.forZone(place.zone))
        }
    }

    @Test fun verifiedBorderZonesAndCountryIdentitySurviveSelection() {
        val app=RuntimeEnvironment.getApplication()
        val samples=mapOf("DZ" to ("Africa/Bamako" to "Africa/Algiers"),"SD" to ("Africa/Ndjamena" to "Africa/Khartoum"),
            "AE" to ("Asia/Muscat" to "Asia/Dubai"),"PS" to ("Asia/Jerusalem" to "Asia/Jerusalem"))
        for((code,zones) in samples) {
            val country=CityCatalog.countries(app).first {it.code==code}
            val place=CityCatalog.load(app,country).first {it.zone==zones.first}
            val city=place.city(country)
            assertEquals(zones.second,city.zone)
            val store=PreferencesStore(app)
            store.write(Preferences(city=city))
            assertEquals(city,store.read().city)
            assertEquals(code,CityCatalog.forCity(app,store.read().city,CityCatalog.countries(app)).code)
        }
    }
}
