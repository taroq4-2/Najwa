package org.najwa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.najwa.app.R
import org.najwa.app.platform.hijriLabel
import org.najwa.core.Method
import org.najwa.core.Prayer
import org.najwa.core.Preferences
import org.najwa.core.displayTime
import java.time.Instant
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

@Composable
fun localizedPrayerName(prayer: Prayer): String = stringResource(when (prayer) {
    Prayer.FAJR -> R.string.en_prayer_fajr
    Prayer.SUNRISE -> R.string.en_prayer_sunrise
    Prayer.DHUHR -> R.string.en_prayer_dhuhr
    Prayer.ASR -> R.string.en_prayer_asr
    Prayer.MAGHRIB -> R.string.en_prayer_maghrib
    Prayer.ISHA -> R.string.en_prayer_isha
})

@Composable
fun localizedMethodName(method: Method): String = stringResource(when (method) {
    Method.UMM_AL_QURA -> R.string.en_method_umm_al_qura
    Method.MWL -> R.string.en_method_mwl
    Method.EGYPTIAN -> R.string.en_method_egyptian
    Method.ISNA -> R.string.en_method_isna
    Method.KARACHI -> R.string.en_method_karachi
    Method.CUSTOM -> R.string.en_method_custom
})

@Composable
internal fun localizedUiTime(time: Instant, preferences: Preferences): String {
    val english = LocalConfiguration.current.locales[0].language == "en"
    if (!english || preferences.hour24) return displayTime(time, preferences)
    return time.atZone(preferences.city.zoneId).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}

@Composable
internal fun localizedUiDate(date: LocalDate): String {
    val english = LocalConfiguration.current.locales[0].language == "en"
    val locale = if (english) Locale.ENGLISH else Locale.forLanguageTag("ar")
    return date.format(DateTimeFormatter.ofPattern(if (english) "EEEE, d MMMM" else "EEEE، d MMMM", locale))
}

@Composable
internal fun localizedUiHijriDate(date: LocalDate): String {
    if (LocalConfiguration.current.locales[0].language != "en") return hijriLabel(date)
    val unavailable = stringResource(R.string.en_home_hijri_unavailable)
    return try {
        val hijri = HijrahDate.from(date)
        val day = hijri.get(ChronoField.DAY_OF_MONTH)
        val month = hijri.get(ChronoField.MONTH_OF_YEAR)
        val year = hijri.get(ChronoField.YEAR)
        "$day/$month/$year AH"
    } catch (_: java.time.DateTimeException) {
        unavailable
    }
}
