package org.najwa.app.platform

import android.content.Context
import org.najwa.core.City
import org.najwa.core.citySearchKey

internal data class ArabCountry(val code: String, val name: String)

internal object ArabCityCatalog {
    val countries = listOf(
        ArabCountry("SA", "السعودية"), ArabCountry("AE", "الإمارات"), ArabCountry("BH", "البحرين"),
        ArabCountry("KW", "الكويت"), ArabCountry("OM", "عُمان"), ArabCountry("QA", "قطر"),
        ArabCountry("JO", "الأردن"), ArabCountry("PS", "فلسطين"), ArabCountry("LB", "لبنان"),
        ArabCountry("SY", "سوريا"), ArabCountry("IQ", "العراق"), ArabCountry("YE", "اليمن"),
        ArabCountry("EG", "مصر"), ArabCountry("SD", "السودان"), ArabCountry("LY", "ليبيا"),
        ArabCountry("TN", "تونس"), ArabCountry("DZ", "الجزائر"), ArabCountry("MA", "المغرب"),
        ArabCountry("MR", "موريتانيا"), ArabCountry("SO", "الصومال"), ArabCountry("DJ", "جيبوتي"),
        ArabCountry("KM", "جزر القمر")
    )

    data class Place(val id: String, val name: String, val terms: String, val lat: Double, val lon: Double, val zone: String, val region: String) {
        fun city() = City(name, lat, lon, zone)
    }

    fun load(context: Context, country: ArabCountry): List<Place> =
        context.assets.open("cities/${country.code}.tsv").bufferedReader().useLines { lines ->
            lines.map { line ->
                val fields = line.split('\t', limit = 7)
                val (id, name, terms, lat, lon) = fields
                val region = fields[6]
                Place(id, name, citySearchKey("$name|$terms|$region"), lat.toDouble(), lon.toDouble(), fields[5], region)
            }.toList()
        }

    fun forZone(zone: String): ArabCountry = when (zone) {
        "Asia/Dubai" -> "AE"; "Asia/Bahrain" -> "BH"; "Asia/Kuwait" -> "KW"; "Asia/Muscat" -> "OM"
        "Asia/Qatar" -> "QA"; "Asia/Amman" -> "JO"; "Asia/Gaza", "Asia/Hebron" -> "PS"; "Asia/Beirut" -> "LB"
        "Asia/Damascus" -> "SY"; "Asia/Baghdad" -> "IQ"; "Asia/Aden" -> "YE"; "Africa/Cairo" -> "EG"
        "Africa/Khartoum" -> "SD"; "Africa/Tripoli" -> "LY"; "Africa/Tunis" -> "TN"; "Africa/Algiers" -> "DZ"
        "Africa/Casablanca" -> "MA"; "Africa/Nouakchott" -> "MR"; "Africa/Mogadishu" -> "SO"
        "Africa/Djibouti" -> "DJ"; "Indian/Comoro" -> "KM"; else -> "SA"
    }.let { code -> countries.first { it.code == code } }
}
