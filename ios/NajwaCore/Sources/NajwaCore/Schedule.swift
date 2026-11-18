import Foundation

public enum AlertKind: String, Codable, Sendable { case adhan, iqama, friday }

public struct ScheduledAlert: Equatable, Sendable {
    public let kind: AlertKind
    public let event: PrayerEvent
    public let at: Date
    /// Stable identifier, so rescheduling replaces rather than duplicates.
    public var id: String { "\(kind.rawValue):\(event.prayer.rawValue):\(Int(event.time.timeIntervalSince1970))" }
}

/// iOS keeps at most 64 pending local notifications per app.
public let maxPendingNotifications = 60

/// Adhan, iqama and Friday alerts for the coming days, soonest first, capped for iOS.
public func upcomingAlerts(from now: Date, prefs: Preferences, days: Int = 6,
                           calculator: PrayerCalculator = PrayerCalculator()) -> [ScheduledAlert] {
    let zone = prefs.city.timeZone
    let today = CivilDate(now, in: zone)
    var alerts: [ScheduledAlert] = []
    for offset in 0..<days {
        let day = calculator.calculate(today.adding(days: offset), prefs)
        guard day.complete else { continue }
        for event in day.prayers {
            if prefs.adhanNotifications && prefs.adhanPrayers.contains(event.prayer) {
                alerts.append(ScheduledAlert(kind: .adhan, event: event, at: event.time))
            }
            if prefs.iqamaNotifications && prefs.iqamaPrayers.contains(event.prayer) {
                alerts.append(ScheduledAlert(kind: .iqama, event: event, at: event.time.addingTimeInterval(TimeInterval(prefs.iqama(event.prayer) * 60))))
            }
            if prefs.fridayReminder && event.prayer == .dhuhr && day.date.isoWeekday == 5 {
                let at = event.time.addingTimeInterval(-TimeInterval(prefs.fridayLeadMinutes * 60))
                if CivilDate(at, in: zone) == day.date { alerts.append(ScheduledAlert(kind: .friday, event: event, at: at)) }
            }
        }
    }
    return Array(alerts.filter { $0.at > now }.sorted { $0.at < $1.at }.prefix(maxPendingNotifications))
}
