package org.najwa.core

import java.time.*
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.*

/** Locally evaluated solar coordinates. External timetable validation remains a release gate. */
class PrayerCalculator {
    private fun rad(v: Double) = Math.toRadians(v)
    private fun deg(v: Double) = Math.toDegrees(v)
    private fun norm(v: Double) = ((v % 360) + 360) % 360
    private data class Sun(val equation: Double, val declination: Double)
    private fun sun(jd: Double): Sun {
        val t = (jd - 2451545.0) / 36525.0
        val l = norm(280.46646 + t * (36000.76983 + 0.0003032 * t))
        val m = norm(357.52911 + t * (35999.05029 - 0.0001537 * t))
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2*m)) * (0.019993 - 0.000101*t) + sin(rad(3*m))*0.000289
        val omega = 125.04 - 1934.136*t
        val lambda = l + c - 0.00569 - 0.00478*sin(rad(omega))
        val epsilon = 23.0 + (26.0 + (21.448 - t*(46.815 + t*(0.00059 - t*0.001813)))/60.0)/60.0 + 0.00256*cos(rad(omega))
        val decl = asin(sin(rad(epsilon))*sin(rad(lambda)))
        val y = tan(rad(epsilon)/2).pow(2)
        val eq = 4*deg(y*sin(2*rad(l)) - 2*e*sin(rad(m)) + 4*e*y*sin(rad(m))*cos(2*rad(l)) - 0.5*y*y*sin(4*rad(l)) - 1.25*e*e*sin(2*rad(m)))
        return Sun(eq,decl)
    }
    private data class SolarDay(val noon: Double, val rise: Double?, val set: Double?)
    private fun hourAngle(latitude: Double, decl: Double, elevation: Double): Double? {
        val x = (sin(rad(elevation))-sin(rad(latitude))*sin(decl))/(cos(rad(latitude))*cos(decl))
        return if (x.isFinite() && x in -1.0..1.0) deg(acos(x)) else null
    }
    private fun julian(date: LocalDate) = date.toEpochDay() + 2440587.5
    private fun transit(date: LocalDate, city: City): Double {
        var n = 720.0-4*city.longitude
        repeat(3) { n = 720.0-4*city.longitude-sun(julian(date)+n/1440).equation }
        return n
    }
    private fun crossing(date: LocalDate, city: City, morning: Boolean, elevation: (Double)->Double): Double? {
        var n = transit(date,city) + if(morning) -360 else 360
        repeat(4) {
            val s = sun(julian(date)+n/1440)
            val h = hourAngle(city.latitude,s.declination,elevation(s.declination)) ?: return null
            n = 720.0-4*city.longitude-s.equation + (if(morning) -4 else 4)*h
        }
        return n
    }
    private fun solar(date: LocalDate, city: City, horizon: Double = -0.833) = SolarDay(transit(date,city),crossing(date,city,true){horizon},crossing(date,city,false){horizon})
    /** Standard refraction plus the dip of the horizon seen from [elevation] metres. */
    private fun horizon(elevation: Int) = -0.833 - 0.0347*sqrt(elevation.coerceAtLeast(0).toDouble())
    /** Moonsighting Committee (Khalid Shaukat) seasonal twilight, minutes before sunrise / after sunset. */
    private fun seasonal(latitude: Double, date: LocalDate, morning: Boolean): Double {
        val l=abs(latitude)/55.0
        val (a,b,c,d)=if(morning) listOf(75+28.65*l,75+19.44*l,75+32.74*l,75+48.10*l) else listOf(75+25.60*l,75+2.050*l,75-9.21*l,75+6.14*l)
        val days=date.lengthOfYear()
        var dyy=if(latitude>=0) date.dayOfYear-1+10 else date.dayOfYear-1-(if(date.isLeapYear)173 else 172)
        if(dyy>=days)dyy-=days
        if(dyy<0)dyy+=days
        return when {
            dyy<91 -> a+(b-a)/91.0*dyy
            dyy<137 -> b+(c-b)/46.0*(dyy-91)
            dyy<183 -> c+(d-c)/46.0*(dyy-137)
            dyy<229 -> d+(c-d)/46.0*(dyy-183)
            dyy<275 -> c+(b-c)/46.0*(dyy-229)
            else -> b+(a-b)/91.0*(dyy-275)
        }
    }
    private fun portion(rule: HighLatitude, angle: Double) = when(rule) { HighLatitude.MIDDLE->0.5; HighLatitude.SEVENTH->1.0/7; HighLatitude.ANGLE->angle/60 }
    fun calculate(date: LocalDate, prefs: Preferences): PrayerDay {
        val city = prefs.city
        // Longitude-based solar calculations use a UTC date. Political time zones
        // across the date line (e.g. Kiritimati UTC+14 at western longitude) can
        // place that transit on the next local date. Align the solar anchor first,
        // while keeping `date` as the civil/Hijri date requested by the caller.
        var solarDate=date
        repeat(3) {
            val noon=solarDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                .plusSeconds((transit(solarDate,city)*60).roundToLong())
            val localDate=noon.atZone(city.zoneId).toLocalDate()
            if(localDate!=date)solarDate=solarDate.plusDays(ChronoUnit.DAYS.between(localDate,date))
        }
        val base = solarDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        fun instant(minutes: Double) = base.plusSeconds((minutes*60/60).roundToLong()*60)
        val horizon = horizon(prefs.elevation)
        val s = solar(solarDate,city,horizon)
        if(instant(s.noon).atZone(city.zoneId).toLocalDate()!=date)
            return PrayerDay(date,emptyList(),DayIssue.ZONE_DATE)
        if(s.rise == null || s.set == null) return PrayerDay(date,emptyList(),DayIssue.NO_SUN)
        val prevSet = solar(solarDate.minusDays(1),city,horizon).set
        val nextRise = solar(solarDate.plusDays(1),city,horizon).rise
        if(prevSet == null || nextRise == null) return PrayerDay(date,emptyList(),DayIssue.NIGHT_LENGTH)
        val fajrAngle = if(prefs.method == Method.CUSTOM) prefs.customFajr else prefs.method.fajr
        val ishaAngle = if(prefs.method == Method.CUSTOM) prefs.customIsha else prefs.method.isha
        val fajrLimit = if(prefs.method.seasonal && abs(city.latitude)<55) s.rise - seasonal(city.latitude,solarDate,true)
            else s.rise - (s.rise + 1440 - prevSet)*portion(prefs.highLatitude,fajrAngle)
        val fajr = (crossing(solarDate,city,true){-fajrAngle} ?: fajrLimit).coerceAtLeast(fajrLimit)
        val isha = if(prefs.method == Method.UMM_AL_QURA) {
            val ramadan = try { HijrahDate.from(date).plus(prefs.hijriOffset.toLong(),ChronoUnit.DAYS).get(ChronoField.MONTH_OF_YEAR)==9 } catch(_: java.time.DateTimeException) {
                return PrayerDay(date,emptyList(),DayIssue.HIJRI_RANGE)
            }
            s.set + if(ramadan) 120.0 else 90.0
        } else if(prefs.method.ishaInterval>0) {
            s.set + prefs.method.ishaInterval
        } else {
            val limit = if(prefs.method.seasonal && abs(city.latitude)<55) s.set + seasonal(city.latitude,solarDate,false)
                else s.set + (nextRise+1440-s.set)*portion(prefs.highLatitude,ishaAngle)
            (crossing(solarDate,city,false){-ishaAngle} ?: limit).coerceAtMost(limit)
        }
        val asr = crossing(solarDate,city,false) { decl -> deg(atan(1.0/((if(prefs.hanafi) 2 else 1)+tan(abs(rad(city.latitude)-decl))))) }
            ?: return PrayerDay(date,emptyList(),DayIssue.ASR)
        // Authority corrections (e.g. Diyanet) and the person's own minute offsets.
        fun adjusted(prayer: Prayer,minutes: Double)=minutes+(prefs.method.adjustments[prayer] ?: 0)+prefs.offset(prayer)
        // Shia methods: maghrib once the sun is a few degrees below the horizon (never before sunset).
        val maghrib = if(prefs.method.maghribAngle>0) (crossing(solarDate,city,false){-prefs.method.maghribAngle} ?: s.set).coerceAtLeast(s.set) else s.set
        val events = listOf(Prayer.FAJR to fajr,Prayer.SUNRISE to s.rise,Prayer.DHUHR to s.noon,Prayer.ASR to asr,Prayer.MAGHRIB to maghrib,Prayer.ISHA to isha).map { PrayerEvent(it.first,instant(adjusted(it.first,it.second))) }
        if(events.zipWithNext().any { it.first.time >= it.second.time }) return PrayerDay(date,emptyList(),DayIssue.ORDER)
        return PrayerDay(date,events)
    }
    fun around(now: Instant,prefs: Preferences): List<PrayerDay> {
        val day = now.atZone(prefs.city.zoneId).toLocalDate()
        return (-1L..2L).map { calculate(day.plusDays(it),prefs) }
    }
}
