import Foundation

/// Swift port of org.najwa.core.PrayerCalculator. Keep both in lock-step:
/// GoldenTests compares this output with times exported from the Kotlin core.
public struct PrayerCalculator: Sendable {
    public init() {}

    private func rad(_ v: Double) -> Double { v * .pi / 180 }
    private func deg(_ v: Double) -> Double { v * 180 / .pi }
    private func norm(_ v: Double) -> Double { (v.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) }
    /// Java's Math.round / roundToLong: floor(x + 0.5).
    private func javaRound(_ v: Double) -> Int { Int((v + 0.5).rounded(.down)) }

    private struct Sun { let equation: Double; let declination: Double }
    private func sun(_ jd: Double) -> Sun {
        let t = (jd - 2451545.0) / 36525.0
        let l = norm(280.46646 + t * (36000.76983 + 0.0003032 * t))
        let m = norm(357.52911 + t * (35999.05029 - 0.0001537 * t))
        let e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        let c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        let omega = 125.04 - 1934.136 * t
        let lambda = l + c - 0.00569 - 0.00478 * sin(rad(omega))
        let epsilon = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0 + 0.00256 * cos(rad(omega))
        let decl = asin(sin(rad(epsilon)) * sin(rad(lambda)))
        let y = pow(tan(rad(epsilon) / 2), 2)
        let eq = 4 * deg(y * sin(2 * rad(l)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l)) - 0.5 * y * y * sin(4 * rad(l)) - 1.25 * e * e * sin(2 * rad(m)))
        return Sun(equation: eq, declination: decl)
    }

    private func hourAngle(_ latitude: Double, _ decl: Double, _ elevation: Double) -> Double? {
        let x = (sin(rad(elevation)) - sin(rad(latitude)) * sin(decl)) / (cos(rad(latitude)) * cos(decl))
        return x.isFinite && (-1...1).contains(x) ? deg(acos(x)) : nil
    }
    private func julian(_ date: CivilDate) -> Double { Double(date.epochDay) + 2440587.5 }

    private func transit(_ date: CivilDate, _ city: City) -> Double {
        var n = 720.0 - 4 * city.longitude
        for _ in 0..<3 { n = 720.0 - 4 * city.longitude - sun(julian(date) + n / 1440).equation }
        return n
    }

    private func crossing(_ date: CivilDate, _ city: City, morning: Bool, _ elevation: (Double) -> Double) -> Double? {
        var n = transit(date, city) + (morning ? -360 : 360)
        for _ in 0..<4 {
            let s = sun(julian(date) + n / 1440)
            guard let h = hourAngle(city.latitude, s.declination, elevation(s.declination)) else { return nil }
            n = 720.0 - 4 * city.longitude - s.equation + (morning ? -4 : 4) * h
        }
        return n
    }

    private struct SolarDay { let noon: Double; let rise: Double?; let set: Double? }
    private func solar(_ date: CivilDate, _ city: City) -> SolarDay {
        SolarDay(noon: transit(date, city), rise: crossing(date, city, morning: true) { _ in -0.833 }, set: crossing(date, city, morning: false) { _ in -0.833 })
    }

    private func portion(_ rule: HighLatitude, _ angle: Double) -> Double {
        switch rule { case .middle: 0.5; case .seventh: 1.0 / 7; case .angle: angle / 60 }
    }

    /// Hijri month in the Umm al-Qura calendar (Java's default HijrahChronology).
    static func hijriMonth(_ date: CivilDate) -> Int? {
        var calendar = Calendar(identifier: .islamicUmmAlQura)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar.dateComponents([.month], from: date.utcStart.addingTimeInterval(12 * 3600)).month
    }

    public func calculate(_ date: CivilDate, _ prefs: Preferences) -> PrayerDay {
        let city = prefs.city
        let zone = city.timeZone
        // Longitude-based calculations use a UTC date. Across the date line the transit can
        // fall on the next local date; align the solar anchor while keeping `date` civil.
        var solarDate = date
        for _ in 0..<3 {
            let noon = solarDate.utcStart.addingTimeInterval(TimeInterval(javaRound(transit(solarDate, city) * 60)))
            let local = CivilDate(noon, in: zone)
            if local != date { solarDate = solarDate.adding(days: date.epochDay - local.epochDay) }
        }
        let base = solarDate.utcStart
        func instant(_ minutes: Double) -> Date { base.addingTimeInterval(TimeInterval(javaRound(minutes * 60 / 60) * 60)) }
        let s = solar(solarDate, city)
        if CivilDate(instant(s.noon), in: zone) != date {
            return PrayerDay(date: date, events: [], warning: "unavailable_in_zone")
        }
        guard let rise = s.rise, let set = s.set else { return PrayerDay(date: date, events: [], warning: "no_sunrise_or_sunset") }
        guard let prevSet = solar(solarDate.adding(days: -1), city).set, let nextRise = solar(solarDate.adding(days: 1), city).rise else {
            return PrayerDay(date: date, events: [], warning: "night_length_unknown")
        }
        let fajrAngle = prefs.method == .custom ? prefs.customFajr : prefs.method.fajrAngle
        let ishaAngle = prefs.method == .custom ? prefs.customIsha : prefs.method.ishaAngle
        let fajrLimit = rise - (rise + 1440 - prevSet) * portion(prefs.highLatitude, fajrAngle)
        let fajr = max(crossing(solarDate, city, morning: true) { _ in -fajrAngle } ?? fajrLimit, fajrLimit)
        let isha: Double
        if prefs.method == .ummAlQura {
            guard let month = Self.hijriMonth(date) else { return PrayerDay(date: date, events: [], warning: "hijri_out_of_range") }
            isha = set + (month == 9 ? 120.0 : 90.0)
        } else {
            let limit = set + (nextRise + 1440 - set) * portion(prefs.highLatitude, ishaAngle)
            isha = min(crossing(solarDate, city, morning: false) { _ in -ishaAngle } ?? limit, limit)
        }
        let latitude = rad(city.latitude)
        guard let asr = crossing(solarDate, city, morning: false, { decl in
            self.deg(atan(1.0 / ((prefs.hanafi ? 2 : 1) + tan(abs(latitude - decl)))))
        }) else { return PrayerDay(date: date, events: [], warning: "asr_unavailable") }
        let events = [(Prayer.fajr, fajr), (.sunrise, rise), (.dhuhr, s.noon), (.asr, asr), (.maghrib, set), (.isha, isha)]
            .map { PrayerEvent(prayer: $0.0, time: instant($0.1)) }
        for i in 1..<events.count where events[i - 1].time >= events[i].time {
            return PrayerDay(date: date, events: [], warning: "overlapping_times")
        }
        return PrayerDay(date: date, events: events, warning: nil)
    }

    /// Yesterday, today and the next two days in the city's zone (Kotlin `around`).
    public func around(_ now: Date, _ prefs: Preferences) -> [PrayerDay] {
        let today = CivilDate(now, in: prefs.city.timeZone)
        return (-1...2).map { calculate(today.adding(days: $0), prefs) }
    }
}
