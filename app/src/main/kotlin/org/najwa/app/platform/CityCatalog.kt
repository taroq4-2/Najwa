package org.najwa.app.platform

import android.content.Context
import org.najwa.core.City
import org.najwa.core.citySearchKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class CatalogCountry(val code: String, val arabicName: String? = null)

internal object CityCatalog {
    private val arabCountries = listOf(
        CatalogCountry("SA", "السعودية"), CatalogCountry("AE", "الإمارات"), CatalogCountry("BH", "البحرين"),
        CatalogCountry("KW", "الكويت"), CatalogCountry("OM", "عُمان"), CatalogCountry("QA", "قطر"),
        CatalogCountry("JO", "الأردن"), CatalogCountry("PS", "فلسطين"), CatalogCountry("LB", "لبنان"),
        CatalogCountry("SY", "سوريا"), CatalogCountry("IQ", "العراق"), CatalogCountry("YE", "اليمن"),
        CatalogCountry("EG", "مصر"), CatalogCountry("SD", "السودان"), CatalogCountry("LY", "ليبيا"),
        CatalogCountry("TN", "تونس"), CatalogCountry("DZ", "الجزائر"), CatalogCountry("MA", "المغرب"),
        CatalogCountry("MR", "موريتانيا"), CatalogCountry("SO", "الصومال"), CatalogCountry("DJ", "جيبوتي"),
        CatalogCountry("KM", "جزر القمر")
    )

    fun countries(context: Context): List<CatalogCountry> = arabCountries +
        context.assets.list("cities-world").orEmpty().mapNotNull { file ->
            file.removeSuffix(".tsv").takeIf { it.length == 2 }?.let { CatalogCountry(it) }
        }

    data class Place(val id: String, val name: String, val terms: String, val lat: Double, val lon: Double, val zone: String, val region: String) {
        fun city(country: CatalogCountry) = City(name, lat, lon, catalogZone(country.code,zone),country.code,id)
    }

    suspend fun search(context: Context, country: CatalogCountry, term: String): List<Place> {
        val job = currentCoroutineContext()
        return read(context,country,term,100) { job.ensureActive() }
    }

    fun load(context: Context,country: CatalogCountry): List<Place> =
        read(context,country,"",Int.MAX_VALUE) {}

    private fun read(context: Context,country: CatalogCountry,term: String,limit: Int,checkActive: ()->Unit): List<Place> {
        val directory=if(country.arabicName!=null)"cities" else "cities-world"
        return context.assets.open("$directory/${country.code}.tsv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                checkActive()
                val fields = line.split('\t', limit = 7)
                if(fields.size != 7)return@mapNotNull null
                val (id, name, terms, lat, lon) = fields
                val region = fields[6]
                val searchable=citySearchKey("$name|$terms|$region")
                if(term.isNotEmpty() && term !in searchable)return@mapNotNull null
                Place(id, name, searchable, lat.toDouble(), lon.toDouble(), fields[5], region)
            }.take(limit).toList()
        }
    }

    fun forCity(context: Context, city: City, countries: List<CatalogCountry>): CatalogCountry {
        countries.firstOrNull {it.code==city.countryCode}?.let {return it}
        val code=forZone(city.zone) ?: context.assets.open("cities-world/zones.tsv").bufferedReader().useLines { lines ->
            lines.firstOrNull { it.startsWith(city.zone+"\t") }?.substringAfter('\t')
        }
        return countries.firstOrNull {it.code==code} ?: arabCountries.first()
    }

    // IANA zone.tab lists one national civil zone for each of these countries.
    private fun catalogZone(countryCode: String, sourceZone: String) = when(countryCode) {
        "DZ" -> "Africa/Algiers"
        "SD" -> "Africa/Khartoum"
        "AE" -> "Asia/Dubai"
        else -> sourceZone
    }

    fun forZone(zone: String): String? = when (zone) {
        "Asia/Riyadh" -> "SA"
        "Asia/Dubai" -> "AE"; "Asia/Bahrain" -> "BH"; "Asia/Kuwait" -> "KW"; "Asia/Muscat" -> "OM"
        "Asia/Qatar" -> "QA"; "Asia/Amman" -> "JO"; "Asia/Gaza", "Asia/Hebron" -> "PS"; "Asia/Beirut" -> "LB"
        "Asia/Damascus" -> "SY"; "Asia/Baghdad" -> "IQ"; "Asia/Aden" -> "YE"; "Africa/Cairo" -> "EG"
        "Africa/Khartoum" -> "SD"; "Africa/Tripoli" -> "LY"; "Africa/Tunis" -> "TN"; "Africa/Algiers" -> "DZ"
        "Africa/Casablanca" -> "MA"; "Africa/Nouakchott" -> "MR"; "Africa/Mogadishu" -> "SO"
        "Africa/Djibouti" -> "DJ"; "Indian/Comoro" -> "KM"; else -> null
    }
}
