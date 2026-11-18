import Foundation

/// ADHAN: waiting for the next adhan. IQAMA: adhan called, waiting for iqama.
/// PRAYER (widget): iqama reached, prayer in progress until the prayer window ends.
public enum CountdownPhase: String, Codable, Sendable { case adhan, iqama, prayer }

/// Visual stage, adding the "last 10 minutes before adhan" alert to the phases.
public enum Stage: String, Codable, Sendable { case adhan, soon, iqama, prayer }

public struct Countdown: Equatable, Sendable {
    public let prayer: Prayer
    /// The moment the countdown reaches zero (adhan, iqama or end of prayer).
    public let target: Date
    public let phase: CountdownPhase
    /// Start of the current waiting period, for progress.
    public let start: Date?
    /// The next prayer's iqama time, while waiting for its adhan.
    public let upcomingIqama: Date?

    public static let soonInterval: TimeInterval = 10 * 60

    public func stage(at now: Date) -> Stage {
        switch phase {
        case .iqama: .iqama
        case .prayer: .prayer
        case .adhan: target.timeIntervalSince(now) <= Self.soonInterval ? .soon : .adhan
        }
    }

    /// 0 → 1 as the target approaches (fills up, as on Android).
    public func progress(at now: Date) -> Double {
        guard let start, start < target else { return 0 }
        return min(max(now.timeIntervalSince(start) / target.timeIntervalSince(start), 0), 1)
    }
}

/// Kotlin `prayerCountdown`. `includePrayer` enables the "prayer has begun" phase (widgets).
public func prayerCountdown(_ events: [PrayerEvent], now: Date, prefs: Preferences, includePrayer: Bool = false) -> Countdown? {
    let prayers = events.filter { $0.prayer.isPrayer }.sorted { $0.time < $1.time }
    let previous = prayers.last { $0.time <= now }
    if prefs.showIqamaCountdown, let previous {
        let iqama = previous.time.addingTimeInterval(TimeInterval(prefs.iqama(previous.prayer) * 60))
        if now < iqama {
            return Countdown(prayer: previous.prayer, target: iqama, phase: .iqama, start: previous.time, upcomingIqama: nil)
        }
        let end = previous.time.addingTimeInterval(TimeInterval(prefs.prayerWindow(previous.prayer) * 60))
        if includePrayer && now < end {
            return Countdown(prayer: previous.prayer, target: end, phase: .prayer, start: iqama, upcomingIqama: nil)
        }
    }
    guard let next = prayers.first(where: { $0.time > now }) else { return nil }
    return Countdown(prayer: next.prayer, target: next.time, phase: .adhan, start: previous?.time,
                     upcomingIqama: prefs.showIqamaCountdown ? next.time.addingTimeInterval(TimeInterval(prefs.iqama(next.prayer) * 60)) : nil)
}

public func widgetCountdown(_ now: Date, _ prefs: Preferences, calculator: PrayerCalculator = PrayerCalculator()) -> Countdown? {
    prayerCountdown(calculator.around(now, prefs).flatMap(\.events), now: now, prefs: prefs, includePrayer: true)
}

/// Every instant at which the widget must re-render (stage boundaries), within `horizon`.
public func stageBoundaries(from now: Date, prefs: Preferences, horizon: TimeInterval = 24 * 3600,
                            calculator: PrayerCalculator = PrayerCalculator()) -> [Date] {
    let end = now.addingTimeInterval(horizon)
    var points: [Date] = []
    for event in calculator.around(now, prefs).flatMap(\.events) where event.prayer.isPrayer {
        let adhan = event.time
        points += [adhan.addingTimeInterval(-Countdown.soonInterval), adhan,
                   adhan.addingTimeInterval(TimeInterval(prefs.iqama(event.prayer) * 60)),
                   adhan.addingTimeInterval(TimeInterval(prefs.prayerWindow(event.prayer) * 60))]
    }
    let today = CivilDate(now, in: prefs.city.timeZone)
    points.append(today.adding(days: 1).utcStart.addingTimeInterval(-TimeInterval(prefs.city.timeZone.secondsFromGMT(for: now))))
    return Array(Set(points.filter { $0 > now && $0 <= end })).sorted()
}
