import Foundation

/// A calendar date without time or zone (Java's LocalDate), with exact epoch-day arithmetic.
public struct CivilDate: Hashable, Comparable, Codable, Sendable, CustomStringConvertible {
    public let year: Int, month: Int, day: Int
    public init(year: Int, month: Int, day: Int) { self.year = year; self.month = month; self.day = day }

    /// Days since 1970-01-01 (proleptic Gregorian), H. Hinnant's days_from_civil.
    public var epochDay: Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    public init(epochDay z0: Int) {
        let z = z0 + 719468
        let era = (z >= 0 ? z : z - 146096) / 146097
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        self.init(year: yoe + era * 400 + (m <= 2 ? 1 : 0), month: m, day: d)
    }

    public init(_ date: Date, in zone: TimeZone) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: c.year!, month: c.month!, day: c.day!)
    }

    public func adding(days: Int) -> CivilDate { CivilDate(epochDay: epochDay + days) }
    /// Start of this day in UTC, as Java's `atStartOfDay(ZoneOffset.UTC)`.
    public var utcStart: Date { Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400) }
    /// Monday = 1 … Sunday = 7 (ISO), so Friday = 5.
    public var isoWeekday: Int { ((epochDay % 7 + 7) % 7 + 3) % 7 + 1 }

    public static func < (a: CivilDate, b: CivilDate) -> Bool { a.epochDay < b.epochDay }
    public var description: String { String(format: "%04d-%02d-%02d", year, month, day) }
    public init?(iso: String) {
        let p = iso.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return nil }
        self.init(year: p[0], month: p[1], day: p[2])
    }
}
