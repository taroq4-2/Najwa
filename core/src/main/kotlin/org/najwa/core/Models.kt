package org.najwa.core

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Prayer(val label: String) { FAJR("الفجر"), SUNRISE("الشروق"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء"); val isPrayer get() = this != SUNRISE }
enum class Method(val label: String, val fajr: Double, val isha: Double) { UMM_AL_QURA("أم القرى",18.5,0.0), MWL("رابطة العالم الإسلامي",18.0,17.0), EGYPTIAN("الهيئة المصرية",19.5,17.5), ISNA("أمريكا الشمالية (ISNA)",15.0,15.0), KARACHI("كراتشي",18.0,18.0), CUSTOM("زوايا مخصصة",18.0,17.0) }
enum class HighLatitude(val label: String) { MIDDLE("منتصف الليل"), SEVENTH("سُبع الليل"), ANGLE("نسبة زاوية الشفق") }
enum class ThemeChoice(val label: String) { SYSTEM("النظام"), DARK("داكن"), LIGHT("فاتح") }
enum class WidgetTheme(val label: String) { AUTO("تلقائي"), DARK("داكن"), LIGHT("فاتح") }
fun defaultIqamaMinutes() = mapOf(Prayer.FAJR to 20,Prayer.DHUHR to 15,Prayer.ASR to 15,Prayer.MAGHRIB to 5,Prayer.ISHA to 15)
fun defaultSilenceMinutes() = mapOf(Prayer.FAJR to 35,Prayer.DHUHR to 30,Prayer.ASR to 30,Prayer.MAGHRIB to 20,Prayer.ISHA to 30)
data class City(val name: String, val latitude: Double, val longitude: Double, val zone: String) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0); require(longitude.isFinite() && longitude in -180.0..180.0); ZoneId.of(zone) }
    val zoneId get() = ZoneId.of(zone)
}
data class Preferences(
    val city: City = Cities.all.first(), val method: Method = Method.UMM_AL_QURA,
    val hanafi: Boolean = false, val highLatitude: HighLatitude = HighLatitude.MIDDLE,
    val customFajr: Double = 18.0, val customIsha: Double = 17.0,
    val theme: ThemeChoice = ThemeChoice.SYSTEM, val hour24: Boolean = false,
    val autoSilent: Boolean = false, val enabled: Set<Prayer> = Prayer.entries.filter { it.isPrayer }.toSet(),
    val durationMinutes: Int = 15, val mode: Ringer = Ringer.SILENT,
    val adhanNotifications: Boolean = false, val iqamaNotifications: Boolean = false,
    val iqamaMinutes: Map<Prayer,Int> = defaultIqamaMinutes(),
    val silenceMinutes: Map<Prayer,Int> = defaultSilenceMinutes(),
    val showIqamaCountdown: Boolean = true,
    val widgetTheme: WidgetTheme = WidgetTheme.AUTO,
    val widgetOpacity: Int = 85,
    val fridayReminder: Boolean = false,
    val fridayLeadMinutes: Int = DEFAULT_FRIDAY_LEAD_MINUTES
) { init {
    require(durationMinutes in 5..120); require(customFajr in 1.0..30.0 && customIsha in 1.0..30.0)
    require(mode != Ringer.NORMAL); require(enabled.none { !it.isPrayer })
    require(iqamaMinutes.keys == Prayer.entries.filter { it.isPrayer }.toSet())
    require(iqamaMinutes.values.all { it in 1..120 })
    require(silenceMinutes.keys == iqamaMinutes.keys && silenceMinutes.values.all {it in 5..120})
    require(widgetOpacity in 80..100)
    require(fridayLeadMinutes in 5..60)
} }
data class PrayerEvent(val prayer: Prayer, val time: Instant) { val id get() = "${prayer.name}:${time.toEpochMilli()}" }
data class PrayerDay(val date: LocalDate, val events: List<PrayerEvent>, val warning: String? = null) { val complete get() = events.size == 6 }
data class Timeline(val previous: PrayerEvent, val next: PrayerEvent, val now: Instant) {
    val remainingSeconds get() = Duration.between(now,next.time).seconds.coerceAtLeast(0)
    val progress get() = (Duration.between(previous.time,now).toMillis().toDouble() / Duration.between(previous.time,next.time).toMillis().coerceAtLeast(1)).coerceIn(0.0,1.0)
}
fun timeline(events: List<PrayerEvent>, now: Instant): Timeline? {
    val prayers = events.filter { it.prayer.isPrayer }.sortedBy { it.time }
    val next = prayers.firstOrNull { it.time > now } ?: return null
    val previous = prayers.lastOrNull { it.time <= now } ?: return null
    return Timeline(previous,next,now)
}
fun displayTime(time: Instant, prefs: Preferences): String {
    val local = time.atZone(prefs.city.zoneId)
    return if (prefs.hour24) local.format(DateTimeFormatter.ofPattern("HH:mm",Locale.US))
    else "${if(local.hour % 12 == 0) 12 else local.hour % 12}:${local.minute.toString().padStart(2,'0')} ${if(local.hour < 12) "ص" else "م"}"
}
fun countdown(seconds: Long): String = "%02d:%02d:%02d".format(Locale.US, seconds/3600,seconds%3600/60,seconds%60)
