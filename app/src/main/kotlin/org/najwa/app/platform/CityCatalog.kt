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

    /** The last opened country stays parsed, so each keystroke filters memory instead of re-reading the file. */
    @Volatile private var cached: Pair<String,List<Place>>? = null

    suspend fun search(context: Context, country: CatalogCountry, term: String): List<Place> {
        val job = currentCoroutineContext()
        val places = cached?.takeIf { it.first == country.code }?.second
            ?: return read(context,country,"",Int.MAX_VALUE) { job.ensureActive() }
                .also { cached = country.code to it }
                .let { all -> all.filter { term.isEmpty() || term in it.terms }.take(100) }
        return places.asSequence().filter { job.ensureActive(); term.isEmpty() || term in it.terms }.take(100).toList()
    }

    fun load(context: Context,country: CatalogCountry): List<Place> =
        cached?.takeIf { it.first == country.code }?.second
            ?: read(context,country,"",Int.MAX_VALUE) {}.also { cached = country.code to it }

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

    /** Nearest catalogue place to a point across every country (used when the phone's time zone
     * does not identify the country). Reads only coordinates, so a full scan stays fast. */
    fun nearestAnywhere(context: Context, lat: Double, lon: Double, countries: List<CatalogCountry>): Pair<CatalogCountry,Place>? {
        var best: Triple<CatalogCountry,String,Double>? = null
        val cos = Math.cos(Math.toRadians(lat))
        for (country in countries) {
            val directory = if (country.arabicName != null) "cities" else "cities-world"
            runCatching {
                context.assets.open("$directory/${country.code}.tsv").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val a = line.indexOf('\t', line.indexOf('\t', line.indexOf('\t') + 1) + 1)
                        val b = line.indexOf('\t', a + 1); val c = line.indexOf('\t', b + 1)
                        if (a < 0 || b < 0 || c < 0) continue
                        val pLat = line.substring(a + 1, b).toDoubleOrNull() ?: continue
                        val pLon = line.substring(b + 1, c).toDoubleOrNull() ?: continue
                        val dx = (pLon - lon) * cos; val dy = pLat - lat
                        val d = dx * dx + dy * dy
                        if (best == null || d < best!!.third) best = Triple(country, line, d)
                    }
                }
            }
        }
        val (country, line) = best?.let { it.first to it.second } ?: return null
        val f = line.split('\t', limit = 7)
        return country to Place(f[0], f[1], citySearchKey("${f[1]}|${f[2]}|${f[6]}"), f[3].toDouble(), f[4].toDouble(), f[5], f[6])
    }

    /** The catalogue country whose civil time zone is [zone], or null. */
    fun countryForZone(context: Context, zone: String, countries: List<CatalogCountry>): CatalogCountry? {
        val code=forZone(zone) ?: context.assets.open("cities-world/zones.tsv").bufferedReader().useLines { lines ->
            lines.firstOrNull { it.startsWith(zone+"\t") }?.substringAfter('\t')
        }
        return countries.firstOrNull {it.code==code}
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
