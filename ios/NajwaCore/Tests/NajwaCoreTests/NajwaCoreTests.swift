import XCTest
@testable import NajwaCore

final class GoldenTests: XCTestCase {
    struct Row: Decodable {
        let city: String, lat: Double, lon: Double, zone: String, method: String, hanafi: Bool, high: String
        let fajr: Double, isha: Double, date: String, times: [Int], ok: Bool
    }

    /// Every case exported from the Kotlin core must match to the second.
    func testMatchesAndroidCoreExactly() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "golden", withExtension: "json"))
        let rows = try JSONDecoder().decode([Row].self, from: Data(contentsOf: url))
        XCTAssertGreaterThan(rows.count, 700)
        let calculator = PrayerCalculator()
        var mismatches: [String] = []
        for row in rows {
            var p = Preferences()
            p.city = City(name: row.city, latitude: row.lat, longitude: row.lon, zone: row.zone)
            p.method = try XCTUnwrap(Method(rawValue: row.method))
            p.hanafi = row.hanafi
            p.highLatitude = try XCTUnwrap(HighLatitude(rawValue: row.high))
            p.customFajr = row.fajr; p.customIsha = row.isha
            let day = calculator.calculate(try XCTUnwrap(CivilDate(iso: row.date)), p)
            let times = day.events.map { Int($0.time.timeIntervalSince1970) }
            if day.complete != row.ok || times != row.times {
                mismatches.append("\(row.city) \(row.method) hanafi=\(row.hanafi) \(row.high) \(row.date): \(times) != \(row.times)")
            }
        }
        XCTAssertTrue(mismatches.isEmpty, "\(mismatches.count) mismatches, first: \(mismatches.prefix(3).joined(separator: "\n"))")
    }

    func testCivilDateRoundTripAndWeekday() {
        for day in stride(from: -800_000, through: 800_000, by: 997) { XCTAssertEqual(CivilDate(epochDay: day).epochDay, day) }
        XCTAssertEqual(CivilDate(year: 2026, month: 11, day: 20).isoWeekday, 5) // Friday
        XCTAssertEqual(CivilDate(year: 1970, month: 1, day: 1).epochDay, 0)
    }
}

final class CountdownTests: XCTestCase {
    private let prefs = Preferences()
    private func dhuhr() -> PrayerEvent {
        PrayerCalculator().calculate(CivilDate(year: 2026, month: 11, day: 18), prefs).events.first { $0.prayer == .dhuhr }!
    }

    func testFourStagesAroundDhuhr() {
        let adhan = dhuhr().time
        func stage(_ offset: TimeInterval) -> (Prayer?, Stage?) {
            let now = adhan.addingTimeInterval(offset)
            let c = widgetCountdown(now, prefs)
            return (c?.prayer, c?.stage(at: now))
        }
        XCTAssertEqual(stage(-30 * 60).1, .adhan)
        XCTAssertEqual(stage(-5 * 60).1, .soon)
        XCTAssertEqual(stage(5 * 60).1, .iqama)          // iqama after 15 min
        XCTAssertEqual(stage(20 * 60).1, .prayer)        // prayer window 30 min
        XCTAssertEqual(stage(20 * 60).0, .dhuhr)
        XCTAssertEqual(stage(31 * 60).0, .asr)            // then the next adhan
        XCTAssertEqual(stage(31 * 60).1, .adhan)
    }

    func testProgressFillsUp() {
        let adhan = dhuhr().time
        let early = widgetCountdown(adhan.addingTimeInterval(-3 * 3600), prefs)!
        let late = widgetCountdown(adhan.addingTimeInterval(-10 * 60), prefs)!
        XCTAssertLessThan(early.progress(at: adhan.addingTimeInterval(-3 * 3600)), late.progress(at: adhan.addingTimeInterval(-10 * 60)))
    }

    func testBoundariesIncludeEveryStageChange() {
        let adhan = dhuhr().time
        let points = stageBoundaries(from: adhan.addingTimeInterval(-3600), prefs: prefs)
        for offset in [-600.0, 0, 15 * 60, 30 * 60] { XCTAssertTrue(points.contains(adhan.addingTimeInterval(offset)), "\(offset)") }
    }

    func testAlertsAreCappedSortedAndIncludeFriday() {
        var p = prefs
        p.adhanNotifications = true; p.iqamaNotifications = true; p.fridayReminder = true
        let alerts = upcomingAlerts(from: dhuhr().time.addingTimeInterval(-3600), prefs: p, days: 8)
        XCTAssertLessThanOrEqual(alerts.count, maxPendingNotifications)
        XCTAssertEqual(alerts.map(\.at), alerts.map(\.at).sorted())
        XCTAssertTrue(alerts.contains { $0.kind == .friday })
        XCTAssertEqual(Set(alerts.map(\.id)).count, alerts.count)
    }
}
