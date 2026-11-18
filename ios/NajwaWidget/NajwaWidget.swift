import WidgetKit
import SwiftUI
import NajwaCore

/// One entry per stage boundary (10 min before adhan, adhan, iqama, end of prayer, midnight).
/// Countdown text and the progress bar are rendered live by the system between entries.
struct PrayerProvider: TimelineProvider {
    func entry(at date: Date, prefs: Preferences) -> PrayerEntry { PrayerEntry.make(at: date, prefs: prefs) }
    func placeholder(in context: Context) -> PrayerEntry { entry(at: Date(), prefs: Preferences()) }
    func getSnapshot(in context: Context, completion: @escaping (PrayerEntry) -> Void) {
        completion(entry(at: Date(), prefs: SettingsStore.load()))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<PrayerEntry>) -> Void) {
        let prefs = SettingsStore.load()
        let now = Date()
        let points = [now] + stageBoundaries(from: now, prefs: prefs)
        let entries = points.map { entry(at: $0, prefs: prefs) }
        completion(Timeline(entries: entries, policy: .after(points.last ?? now.addingTimeInterval(3600))))
    }
}

@main
struct NajwaWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NajwaPrayerWidget", provider: PrayerProvider()) { entry in
            NajwaWidgetView(entry: entry)
                .containerBackground(for: .widget) { Madar.heroGradient }
        }
        .configurationDisplayName("widget.name")
        .description("widget.description")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular, .accessoryCircular, .accessoryInline])
    }
}
