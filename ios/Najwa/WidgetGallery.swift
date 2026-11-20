import SwiftUI
import WidgetKit
import ActivityKit
import NajwaCore

/// Renders the real widget views at their home-screen sizes for each stage, in both themes.
/// Opened only with the `-NajwaWidgets` launch argument (CI screenshots).
struct WidgetGallery: View {
    let prefs: Preferences

    private var entries: [(Stage, PrayerEntry)] {
        let now = Date()
        let events = PrayerCalculator().around(now, prefs).flatMap(\.events).filter { $0.prayer.isPrayer }
        guard let next = events.first(where: { $0.time > now.addingTimeInterval(15 * 60) }) else { return [] }
        let adhan = next.time
        let iqama = TimeInterval(prefs.iqama(next.prayer) * 60)
        let window = TimeInterval(prefs.prayerWindow(next.prayer) * 60)
        let moments: [(Stage, Date)] = [
            (.adhan, adhan.addingTimeInterval(-75 * 60)), (.soon, adhan.addingTimeInterval(-5 * 60)),
            (.iqama, adhan.addingTimeInterval(min(5 * 60, iqama / 2))), (.prayer, adhan.addingTimeInterval(iqama + (window - iqama) / 2)),
        ]
        return moments.map { ($0.0, PrayerEntry.make(at: $0.1, prefs: prefs)) }
    }

    private func themed(_ entry: PrayerEntry, _ theme: WidgetTheme) -> PrayerEntry {
        var p = entry.prefs
        p.widgetTheme = theme
        return PrayerEntry(date: entry.date, prefs: p, countdown: entry.countdown, today: entry.today)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(entries, id: \.0) { _, entry in
                    tile(themed(entry, .dark), family: .systemMedium, size: CGSize(width: 364, height: 170))
                }
                if entries.count == 4 {
                    HStack(spacing: 14) {
                        tile(themed(entries[0].1, .light), family: .systemSmall, size: CGSize(width: 170, height: 170))
                        tile(themed(entries[2].1, .light), family: .systemSmall, size: CGSize(width: 170, height: 170))
                    }
                    tile(themed(entries[3].1, .light), family: .systemMedium, size: CGSize(width: 364, height: 170))
                    tile(themed(entries[1].1, .light), family: .systemLarge, size: CGSize(width: 364, height: 382))
                }
            }
            .padding(14)
        }
        .background(LinearGradient(colors: [Color(hex: 0x1D3557), Color(hex: 0x264653)], startPoint: .top, endPoint: .bottom))
    }

    private func tile(_ entry: PrayerEntry, family: WidgetFamily, size: CGSize) -> some View {
        NajwaWidgetView(entry: entry, family: family)
            .padding(16)
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .background(WidgetBackground(theme: entry.prefs.widgetTheme))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .environment(\.colorScheme, entry.prefs.widgetTheme == .light ? .light : .dark)
    }
}

/// The iqama Live Activity presentations (Dynamic Island + lock screen) for both phases.
/// Opened only with `-NajwaLive` (CI screenshots).
struct LiveGallery: View {
    let prefs: Preferences

    private func model(praying: Bool) -> IqamaActivityState {
        let now = Date()
        let attributes = IqamaAttributes(prayer: Prayer.dhuhr.rawValue, city: prefs.city.name, key: "preview")
        let state = praying
            ? IqamaAttributes.ContentState(adhan: now.addingTimeInterval(-17 * 60), iqama: now.addingTimeInterval(-2 * 60), end: now.addingTimeInterval(13 * 60))
            : IqamaAttributes.ContentState(adhan: now.addingTimeInterval(-6 * 60), iqama: now.addingTimeInterval(9 * 60), end: now.addingTimeInterval(24 * 60))
        return IqamaActivityState(attributes: attributes, state: state, stale: praying)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                ForEach([false, true], id: \.self) { praying in
                    let m = model(praying: praying)
                    // Compact Dynamic Island
                    HStack {
                        Image(systemName: "leaf.fill").foregroundStyle(m.accent)
                        Spacer()
                        IqamaCompactTimer(model: m)
                    }
                    .padding(.horizontal, 18).frame(width: 250, height: 37)
                    .background(.black, in: Capsule())
                    // Expanded Dynamic Island
                    IqamaExpandedView(model: m)
                        .padding(.horizontal, 22).padding(.vertical, 18).frame(width: 370)
                        .background(.black, in: RoundedRectangle(cornerRadius: 44, style: .continuous))
                    // Lock screen
                    IqamaLockScreenView(model: m)
                        .frame(width: 364)
                        .background(Madar.deep, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                }
            }
            .padding(.vertical, 24).frame(maxWidth: .infinity)
        }
        .background(LinearGradient(colors: [Color(hex: 0x3A4A6B), Color(hex: 0x1B2638)], startPoint: .top, endPoint: .bottom))
        .environment(\.colorScheme, .dark)
    }
}

/// CI only: starts a real iqama Live Activity now, so the simulator shows it in the Dynamic Island.
@MainActor
func startDemoLiveActivity(_ prefs: Preferences) {
    let now = Date()
    let attributes = IqamaAttributes(prayer: Prayer.dhuhr.rawValue, city: prefs.city.name, key: "demo")
    let state = IqamaAttributes.ContentState(adhan: now.addingTimeInterval(-6 * 60), iqama: now.addingTimeInterval(9 * 60), end: now.addingTimeInterval(24 * 60))
    print("NajwaLive enabled=\(ActivityAuthorizationInfo().areActivitiesEnabled)")
    do {
        let activity = try Activity.request(attributes: attributes, content: ActivityContent(state: state, staleDate: state.iqama), pushType: nil)
        print("NajwaLive started id=\(activity.id) state=\(activity.activityState)")
    } catch {
        print("NajwaLive failed: \(error)")
    }
}
