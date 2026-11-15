package org.najwa.app.platform

import android.content.Context
import android.content.res.Configuration
import org.najwa.app.appLanguage
import org.najwa.app.R
import org.najwa.core.Prayer
import org.najwa.core.Preferences
import org.najwa.core.displayTime
import java.time.Instant
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.chrono.HijrahChronology
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

internal fun Context.prayerLanguage(): Context {
    val locale=Locale.forLanguageTag(appLanguage(this))
    val current=resources.configuration
    if(current.locales[0]==locale)return this
    // Android's layout preview bridge can return null for a configuration context.
    val localized: Context?=createConfigurationContext(Configuration(current).apply {setLocale(locale)})
    return localized ?: this
}

internal val Context.usesArabic get() = resources.configuration.locales[0].language=="ar"

internal val Context.prayerLayoutDirection get() =
    android.text.TextUtils.getLayoutDirectionFromLocale(resources.configuration.locales[0])

internal fun prayerNameResource(prayer: Prayer): Int = when(prayer) {
    Prayer.FAJR -> R.string.en_prayer_fajr
    Prayer.SUNRISE -> R.string.en_prayer_sunrise
    Prayer.DHUHR -> R.string.en_prayer_dhuhr
    Prayer.ASR -> R.string.en_prayer_asr
    Prayer.MAGHRIB -> R.string.en_prayer_maghrib
    Prayer.ISHA -> R.string.en_prayer_isha
}

internal fun Context.prayerName(prayer: Prayer): String = getString(prayerNameResource(prayer))

internal fun Context.prayerTime(date: Instant, preferences: Preferences): String {
    return prayerTime(date,preferences,resources.configuration.locales[0])
}

internal fun prayerTime(date: Instant, preferences: Preferences, locale: Locale): String =
    if(locale.language=="ar" || preferences.hour24)displayTime(date,preferences)
    else date.atZone(preferences.city.zoneId).format(DateTimeFormatter.ofPattern("h:mm a",locale))

private val hijriMonths=listOf("محرم","صفر","ربيع الأول","ربيع الآخر","جمادى الأولى","جمادى الآخرة",
    "رجب","شعبان","رمضان","شوال","ذو القعدة","ذو الحجة")

private fun arabicHijriLabel(date: LocalDate): String = try {
    val hijri=HijrahDate.from(date)
    val day=hijri.get(ChronoField.DAY_OF_MONTH)
    val month=hijri.get(ChronoField.MONTH_OF_YEAR)
    val year=hijri.get(ChronoField.YEAR)
    val text="$day ${hijriMonths[month-1]} $year هـ"
    text.map {if(it in '0'..'9') '٠'+(it-'0') else it}.joinToString("")
} catch(_: java.time.DateTimeException) { "التاريخ الهجري غير متاح" }

internal fun Context.hijriLabel(date: LocalDate): String {
    if(usesArabic)return arabicHijriLabel(date)
    return try {
        val dateText=DateTimeFormatter.ofPattern("d/M/yyyy",resources.configuration.locales[0])
            .withChronology(HijrahChronology.INSTANCE).format(date)
        getString(R.string.system_hijri_format,dateText)
    } catch(_: java.time.DateTimeException) { getString(R.string.en_home_hijri_unavailable) }
}

internal fun prayerMessage(prayer: Prayer, adhan: Boolean): String = if(adhan) when(prayer) {
    Prayer.FAJR -> "ابدأ يومك بصلاة الفجر."
    Prayer.DHUHR -> "توقّف قليلًا في منتصف يومك، فقد دخل وقت الظهر."
    Prayer.ASR -> "حان وقت العصر؛ اجعل للصلاة موعدًا في بقية يومك."
    Prayer.MAGHRIB -> "غربت الشمس ودخل وقت المغرب."
    Prayer.ISHA -> "حان وقت العشاء؛ اختم يومك بالصلاة."
    else -> ""
} else when(prayer) {
    Prayer.FAJR -> "«من صلى البردين دخل الجنة» · البخاري ٥٧٤. البردان: الفجر والعصر."
    Prayer.DHUHR -> "من معاني الحديث: فضل التبكير إلى صلاة الظهر · البخاري ٦١٥."
    Prayer.ASR -> "«من صلى البردين دخل الجنة» · البخاري ٥٧٤. البردان: الفجر والعصر."
    Prayer.MAGHRIB -> "من هدي النبي ﷺ صلاة ركعتين بعد المغرب في بيته · البخاري ١١٨٠."
    Prayer.ISHA -> "من صلى العشاء في جماعة فكأنما قام نصف الليل · مسلم ٦٥٦."
    else -> ""
}
