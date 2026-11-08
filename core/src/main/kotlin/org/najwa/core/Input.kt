package org.najwa.core

import java.text.Normalizer

/** Accept Arabic and Persian digits without changing stored numeric formats. */
fun normalizedNumber(value: String): String = value.trim().map { c ->
    when (c) {
        in '٠'..'٩' -> '0' + (c - '٠')
        in '۰'..'۹' -> '0' + (c - '۰')
        '٫', ',' -> '.'
        '−' -> '-'
        else -> c
    }
}.joinToString("")

private val combiningMarks = Regex("\\p{M}+")

fun citySearchKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(combiningMarks, "").replace("ـ", "")
    .replace('ى', 'ي').replace('ة', 'ه').lowercase(java.util.Locale.ROOT)

fun searchCities(query: String): List<City> {
    val key = citySearchKey(query)
    return Cities.all.filter { citySearchKey(it.name).contains(key) || it.zone.lowercase(java.util.Locale.ROOT).contains(key) }
}
