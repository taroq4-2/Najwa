import SwiftUI
import WidgetKit
import NajwaCore

/// Renders the real widget views at their home-screen sizes for each stage.
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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(entries, id: \.0) { stage, entry in
                    tile(entry, family: .systemMedium, size: CGSize(width: 364, height: 170))
                }
                if let first = entries.first, let last = entries.last {
                    HStack(spacing: 14) {
                        tile(entries[1].1, family: .systemSmall, size: CGSize(width: 170, height: 170))
                        tile(last.1, family: .systemSmall, size: CGSize(width: 170, height: 170))
                    }
                    tile(first.1, family: .systemLarge, size: CGSize(width: 364, height: 382))
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
            .background(Madar.heroGradient)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .environment(\.colorScheme, .dark)
    }
}
