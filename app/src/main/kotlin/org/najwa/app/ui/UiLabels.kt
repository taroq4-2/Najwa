package org.najwa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.najwa.app.R
import org.najwa.app.platform.hijriLabel
import org.najwa.core.Method
import org.najwa.core.Prayer
import org.najwa.core.Preferences
import org.najwa.app.platform.prayerNameResource
import org.najwa.app.platform.prayerTime
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun localizedPrayerName(prayer: Prayer): String = stringResource(prayerNameResource(prayer))

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
internal fun localizedUiTime(time: Instant, preferences: Preferences): String =
    prayerTime(time,preferences,LocalConfiguration.current.locales[0])

@Composable
internal fun localizedUiDate(date: LocalDate): String {
    val locale=LocalConfiguration.current.locales[0]
    return date.format(DateTimeFormatter.ofPattern(if(locale.language=="ar")"EEEE، d MMMM" else "EEEE, d MMMM",locale))
}

@Composable
internal fun localizedUiHijriDate(date: LocalDate): String = LocalContext.current.hijriLabel(date)
