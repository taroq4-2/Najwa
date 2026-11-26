package org.najwa.core

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Prayer(val label: String) { FAJR("الفجر"), SUNRISE("الشروق"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء"); val isPrayer get() = this != SUNRISE }
/** [ishaInterval]: fixed minutes after maghrib instead of an angle. [adjustments]: the
 * authority's published minute corrections per time (adhan library conventions). */
/** [maghribAngle]: maghrib when the sun is this far below the horizon (Shia methods) instead of sunset.
 * [seasonal]: the Moonsighting Committee's seasonal twilight limits. */
enum class Method(val label: String, val fajr: Double, val isha: Double, val ishaInterval: Int = 0, val adjustments: Map<Prayer,Int> = emptyMap(),
                  val maghribAngle: Double = 0.0, val seasonal: Boolean = false) {
    UMM_AL_QURA("أم القرى",18.5,0.0,90), MWL("رابطة العالم الإسلامي",18.0,17.0), EGYPTIAN("الهيئة المصرية",19.5,17.5),
    ISNA("أمريكا الشمالية (ISNA)",15.0,15.0), KARACHI("كراتشي",18.0,18.0),
    DUBAI("دبي",18.2,18.2,0,mapOf(Prayer.SUNRISE to -3,Prayer.DHUHR to 3,Prayer.ASR to 3,Prayer.MAGHRIB to 3)),
    KUWAIT("الكويت",18.0,17.5), QATAR("قطر",18.0,0.0,90),
    SINGAPORE("سنغافورة وماليزيا",20.0,18.0,0,mapOf(Prayer.DHUHR to 1)),
    TURKEY("تركيا (ديانت)",18.0,17.0,0,mapOf(Prayer.SUNRISE to -7,Prayer.DHUHR to 5,Prayer.ASR to 4,Prayer.MAGHRIB to 7)),
    FRANCE("فرنسا (UOIF)",12.0,12.0),
    MOONSIGHTING("لجنة رؤية الهلال",18.0,18.0,0,mapOf(Prayer.DHUHR to 5,Prayer.MAGHRIB to 3),seasonal=true),
    TEHRAN("طهران",17.7,14.0,maghribAngle=4.5),
    JAFARI("الجعفري (قم)",16.0,14.0,maghribAngle=4.0),
    CUSTOM("زوايا مخصصة",18.0,17.0) }
enum class HighLatitude(val label: String) { MIDDLE("منتصف الليل"), SEVENTH("سُبع الليل"), ANGLE("نسبة زاوية الشفق") }
enum class ThemeChoice(val label: String) { SYSTEM("النظام"), DARK("داكن"), LIGHT("فاتح") }
enum class WidgetTheme(val label: String) { AUTO("تلقائي"), DARK("داكن"), LIGHT("فاتح") }
fun defaultIqamaMinutes() = mapOf(Prayer.FAJR to 20,Prayer.DHUHR to 15,Prayer.ASR to 15,Prayer.MAGHRIB to 5,Prayer.ISHA to 15)
fun defaultSilenceMinutes() = mapOf(Prayer.FAJR to 35,Prayer.DHUHR to 30,Prayer.ASR to 30,Prayer.MAGHRIB to 20,Prayer.ISHA to 30)
val dailyPrayers: Set<Prayer> = Prayer.entries.filter {it.isPrayer}.toSet()
data class City(val name: String, val latitude: Double, val longitude: Double, val zone: String,
                val countryCode: String? = null, val catalogId: String? = null) {
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
    val fridayLeadMinutes: Int = DEFAULT_FRIDAY_LEAD_MINUTES,
    val showLockScreenDetails: Boolean = false,
    val adhanPrayers: Set<Prayer> = dailyPrayers,
    val iqamaPrayers: Set<Prayer> = dailyPrayers,
    /** Manual minute corrections per time (including sunrise), to match a local mosque or calendar. */
    val offsets: Map<Prayer,Int> = emptyMap(),
    /** Days added to the Umm al-Qura Hijri date where the local month starts differently. */
    val hijriOffset: Int = 0,
    val preAdhanReminder: Boolean = false,
    val preAdhanMinutes: Int = DEFAULT_PRE_ADHAN_MINUTES,
    /** On Fridays Dhuhr is shown as Jumu'ah and uses its own (longer) silence window. */
    val jumuah: Boolean = true,
    val fridaySilenceMinutes: Int = DEFAULT_FRIDAY_SILENCE_MINUTES,
    val dynamicColor: Boolean = false,
    /** Height above sea level in metres; lowers the apparent horizon for sunrise and sunset. */
    val elevation: Int = 0,
    val showExtraTimes: Boolean = false,
    /** Ramadan: iftar countdown at home, optional suhoor and taraweeh reminders. */
    val ramadanMode: Boolean = true,
    val suhoorReminder: Boolean = false, val suhoorMinutes: Int = 45,
    val taraweehReminder: Boolean = false, val taraweehMinutes: Int = 20,
    val sunriseReminder: Boolean = false, val sunriseLeadMinutes: Int = 15,
    val duhaReminder: Boolean = false, val qiyamReminder: Boolean = false,
    val adhkarReminders: Boolean = false, val kahfReminder: Boolean = false, val fastingReminders: Boolean = false,
    /** Ongoing iqama countdown (Live Updates / Now Bar on Android 16+). */
    val liveIqama: Boolean = true,
    /** Najwa plays the adhan itself: fade-in, flip or volume key to stop, quiet during calls. */
    val enhancedAdhan: Boolean = false, val adhanFadeIn: Boolean = true, val flipToStop: Boolean = true,
    val headphonesOnly: Boolean = false, val quietDuringCalls: Boolean = true,
    val announceAdhan: Boolean = false, val flashAlert: Boolean = false,
    val prayerLog: Boolean = true, val largeDisplay: Boolean = false,
    /** A short Quranic or prophetic dua on the home screen, changing daily. */
    val dailyDua: Boolean = true,
    /** Prayer log: a gentle "Have you prayed?" before a prayer's time runs out, when it is not marked yet. */
    val logCheckIn: Boolean = false,
    val secondCity: City? = null,
    /** Broadcast prayer events for automation apps such as Tasker. */
    val broadcastEvents: Boolean = false,
    /** Id of the mosque profile whose iqama times are in use. */
    val activeMosque: String? = null,
    /** A reminder of the day's Quran portion, 30 minutes after [wirdReminderAfter] (Fajr, Asr or Isha), only while pages remain. */
    val wirdReminder: Boolean = false,
    val wirdReminderAfter: Prayer = Prayer.ISHA,
    /** When a prayer's silence ends: "May Allah accept it", with prayed / prayed in congregation / after-prayer adhkar. */
    val afterPrayerCard: Boolean = false,
    /** Digits for counts in the worship pages, widget and reminders. */
    val digits: Digits = Digits.AUTO,
    /** Sleep adhkar reminder, [sleepReminderMinutes] after Isha. */
    val sleepReminder: Boolean = false,
    val sleepReminderMinutes: Int = 90,
    /** The week in one gentle line after Jumu'ah. */
    val weeklySummary: Boolean = false,
) { init {
    require(durationMinutes in 5..120); require(customFajr in 1.0..30.0 && customIsha in 1.0..30.0)
    require(mode != Ringer.NORMAL); require(enabled.none { !it.isPrayer })
    require(iqamaMinutes.keys == Prayer.entries.filter { it.isPrayer }.toSet())
    require(iqamaMinutes.values.all { it in 1..120 })
    require(silenceMinutes.keys == iqamaMinutes.keys && silenceMinutes.values.all {it in 5..120})
    require(widgetOpacity in 80..100)
    require(fridayLeadMinutes in 5..60)
    require(adhanPrayers.all {it.isPrayer} && iqamaPrayers.all {it.isPrayer})
    require(offsets.values.all {it in -MAX_OFFSET_MINUTES..MAX_OFFSET_MINUTES})
    require(hijriOffset in -2..2)
    require(preAdhanMinutes in 1..60)
    require(fridaySilenceMinutes in 5..120)
    require(elevation in 0..6000)
    require(suhoorMinutes in 10..180 && taraweehMinutes in 0..120 && sunriseLeadMinutes in 5..60)
} }
const val MAX_OFFSET_MINUTES = 30
const val DEFAULT_PRE_ADHAN_MINUTES = 10
const val DEFAULT_FRIDAY_SILENCE_MINUTES = 60
/** Gap kept between iqama and the end of silence so the ringer never returns before the prayer. */
const val SILENCE_AFTER_IQAMA_MINUTES = 5
fun Preferences.offset(prayer: Prayer) = offsets[prayer] ?: 0
fun Preferences.isFriday(event: PrayerEvent) = event.time.atZone(city.zoneId).dayOfWeek==DayOfWeek.FRIDAY
/** Dhuhr on Friday, when Jumu'ah mode is on. */
fun Preferences.isJumuah(event: PrayerEvent) = jumuah && event.prayer==Prayer.DHUHR && isFriday(event)
/** Silence for [event], counted from adhan; Jumu'ah has its own window. */
fun Preferences.silenceFor(event: PrayerEvent) = if(isJumuah(event)) fridaySilenceMinutes else silenceMinutes.getValue(event.prayer)
/** Smallest silence that still covers the iqama of [prayer]. */
fun Preferences.minimumSilence(prayer: Prayer) = (iqamaMinutes.getValue(prayer)+SILENCE_AFTER_IQAMA_MINUTES).coerceIn(5,120)
/** Silence windows are extended (never shortened) so the ringer cannot return before iqama. */
fun Preferences.normalized(): Preferences {
    val silence=silenceMinutes.mapValues {(prayer,minutes)->maxOf(minutes,minimumSilence(prayer))}
    val friday=maxOf(fridaySilenceMinutes,minimumSilence(Prayer.DHUHR))
    return if(silence==silenceMinutes && friday==fridaySilenceMinutes) this else copy(silenceMinutes=silence,fridaySilenceMinutes=friday)
}
data class PrayerEvent(val prayer: Prayer, val time: Instant) { val id get() = "${prayer.name}:${time.toEpochMilli()}" }
/** Why a day could not be calculated; the app shows a localized explanation. */
enum class DayIssue { ZONE_DATE, NO_SUN, NIGHT_LENGTH, HIJRI_RANGE, ASR, ORDER }
data class PrayerDay(val date: LocalDate, val events: List<PrayerEvent>, val issue: DayIssue? = null) {
    val complete get() = events.size == 6
    val warning get() = issue?.name
}
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
    val text = if (prefs.hour24) local.format(DateTimeFormatter.ofPattern("HH:mm",Locale.US))
    else "${if(local.hour % 12 == 0) 12 else local.hour % 12}:${local.minute.toString().padStart(2,'0')} ${if(local.hour < 12) "ص" else "م"}"
    return prefs.digits.render(text)
}
fun countdown(seconds: Long): String = "%02d:%02d:%02d".format(Locale.US, seconds/3600,seconds%3600/60,seconds%60)

/** The Kaaba, for the qibla bearing. */
const val KAABA_LATITUDE = 21.422487
const val KAABA_LONGITUDE = 39.826206
/** Initial great-circle bearing from [city] to the Kaaba, degrees clockwise from true north. */
fun qiblaBearing(city: City): Double {
    val lat1=Math.toRadians(city.latitude); val lat2=Math.toRadians(KAABA_LATITUDE)
    val dLon=Math.toRadians(KAABA_LONGITUDE-city.longitude)
    val y=Math.sin(dLon)*Math.cos(lat2)
    val x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y,x))+360)%360
}
/** Great-circle distance to the Kaaba in kilometres. */
fun qiblaDistanceKm(city: City): Double = distanceKm(city.latitude,city.longitude,KAABA_LATITUDE,KAABA_LONGITUDE)
fun distanceKm(lat1: Double,lon1: Double,lat2: Double,lon2: Double): Double {
    val a=Math.sin(Math.toRadians(lat2-lat1)/2).let {it*it}+
        Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(Math.toRadians(lon2-lon1)/2).let {it*it}
    return 6371.0*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a))
}

/** How counts are written: Arabic-Indic digits (١٢٣), Western digits (123), or by the app language. */
enum class Digits { AUTO, ARABIC, LATIN;
    /** Rewrites digits in the chosen style; AUTO leaves the text as the language wrote it. */
    fun render(text: String): String = when(this) {
        AUTO -> text
        ARABIC -> text.map {if(it in '0'..'9') '٠' + (it - '0') else it}.joinToString("")
        LATIN -> text.map {when(it) {in '٠'..'٩' -> '0' + (it - '٠'); in '۰'..'۹' -> '0' + (it - '۰'); else -> it}}.joinToString("")
    }
}
