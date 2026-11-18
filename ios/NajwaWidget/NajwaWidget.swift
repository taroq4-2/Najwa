import WidgetKit
import SwiftUI
import NajwaCore

struct PrayerEntry: TimelineEntry {
    let date: Date
    let prefs: Preferences
    let countdown: Countdown?
    let today: [PrayerEvent]
    var stage: Stage { countdown?.stage(at: date) ?? .adhan }
    var accent: Color { Madar.accent(stage) }
}

/// One entry per stage boundary (10 min before adhan, adhan, iqama, end of prayer, midnight).
/// Countdown text and the progress bar are rendered live by the system between entries.
struct PrayerProvider: TimelineProvider {
    func entry(at date: Date, prefs: Preferences) -> PrayerEntry {
        let countdown = widgetCountdown(date, prefs)
        let day = CivilDate((countdown?.phase == .adhan ? countdown?.target : nil) ?? date, in: prefs.city.timeZone)
        let today = PrayerCalculator().calculate(day, prefs).prayers
        return PrayerEntry(date: date, prefs: prefs, countdown: countdown, today: today)
    }
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

// MARK: - Views

struct StageLabel: View {
    let entry: PrayerEntry
    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(entry.accent).frame(width: 6, height: 6)
            Text(entry.stage.heading).font(.caption2.weight(.bold)).foregroundStyle(entry.stage == .adhan ? Madar.sage : entry.accent)
        }
    }
}

struct LiveCountdown: View {
    let entry: PrayerEntry
    let size: CGFloat
    var body: some View {
        if let c = entry.countdown {
            Text(timerInterval: entry.date...c.target, countsDown: true)
                .font(.system(size: size, weight: .bold, design: .rounded).monospacedDigit())
                .foregroundStyle(entry.accent)
                .environment(\.layoutDirection, .leftToRight)
                .lineLimit(1).minimumScaleFactor(0.6)
        }
    }
}

struct FillingBar: View {
    let entry: PrayerEntry
    var body: some View {
        if let c = entry.countdown, let start = c.start, start < c.target {
            ProgressView(timerInterval: start...c.target, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                .progressViewStyle(.linear).tint(entry.accent)
        }
    }
}

struct DayStrip: View {
    let entry: PrayerEntry
    var vertical = false
    var body: some View {
        let layout = vertical ? AnyLayout(VStackLayout(spacing: 2)) : AnyLayout(HStackLayout(spacing: 4))
        layout {
            ForEach(entry.today, id: \.prayer) { event in
                let active = event.prayer == entry.countdown?.prayer
                let passed = !active && event.time <= entry.date
                Group {
                    if vertical {
                        HStack {
                            Text(event.prayer.title).font(.caption)
                            Spacer()
                            Text(NajwaFormat.time(event.time, entry.prefs)).font(.caption.weight(.bold))
                        }
                    } else {
                        VStack(spacing: 1) {
                            Text(event.prayer.title).font(.system(size: 10))
                            Text(NajwaFormat.time(event.time, entry.prefs)).font(.system(size: 11, weight: .bold)).lineLimit(1).minimumScaleFactor(0.7)
                        }.frame(maxWidth: .infinity)
                    }
                }
                .foregroundStyle(active ? entry.accent : (passed ? Madar.sage.opacity(0.7) : Madar.ivory))
                .padding(.vertical, vertical ? 3 : 5).padding(.horizontal, 6)
                .background(active ? entry.accent.opacity(0.16) : .clear, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(active ? entry.accent.opacity(0.45) : .clear, lineWidth: 1))
            }
        }
    }
}

struct Hero: View {
    let entry: PrayerEntry
    let nameSize: CGFloat
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            StageLabel(entry: entry)
            if let c = entry.countdown {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(c.prayer.title).font(.system(size: nameSize, weight: .bold)).foregroundStyle(Madar.ivory)
                    Text(c.detail(entry.prefs)).font(.caption).foregroundStyle(Madar.sage).lineLimit(1).minimumScaleFactor(0.7)
                }
            } else {
                Text("widget.unavailable").font(.headline).foregroundStyle(Madar.ivory)
            }
        }
    }
}

struct NajwaWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PrayerEntry
    var body: some View {
        content.widgetURL(URL(string: "najwa://home"))
    }
    @ViewBuilder private var content: some View {
        switch family {
        case .accessoryInline:
            if let c = entry.countdown { Text("\(c.prayer.name) \(NajwaFormat.time(c.target, entry.prefs))") }
        case .accessoryCircular:
            if let c = entry.countdown, let start = c.start {
                ProgressView(timerInterval: start...c.target, countsDown: false) { Text(c.prayer.title) } currentValueLabel: {
                    Text(c.prayer.title).font(.system(size: 10, weight: .bold))
                }.progressViewStyle(.circular)
            }
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Text(entry.stage.heading).font(.caption2.weight(.bold))
                if let c = entry.countdown {
                    Text(c.prayer.title).font(.headline)
                    Text(timerInterval: entry.date...c.target, countsDown: true).font(.caption.monospacedDigit())
                }
            }
        case .systemSmall:
            VStack(alignment: .leading, spacing: 6) {
                Hero(entry: entry, nameSize: 22)
                Spacer(minLength: 0)
                Text(entry.stage.countdownLabel).font(.caption2).foregroundStyle(Madar.sage)
                LiveCountdown(entry: entry, size: 24)
                FillingBar(entry: entry)
            }
        case .systemLarge:
            VStack(alignment: .leading, spacing: 10) {
                header
                Hero(entry: entry, nameSize: 30)
                HStack { Text(entry.stage.countdownLabel).font(.caption).foregroundStyle(Madar.sage); Spacer(); LiveCountdown(entry: entry, size: 30) }
                FillingBar(entry: entry)
                Spacer(minLength: 0)
                DayStrip(entry: entry, vertical: true)
            }
        default:
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .bottom) {
                    Hero(entry: entry, nameSize: 24)
                    Spacer(minLength: 4)
                    VStack(alignment: .trailing, spacing: 0) {
                        Text(entry.stage.countdownLabel).font(.caption2).foregroundStyle(entry.stage == .adhan ? Madar.sage : entry.accent)
                        LiveCountdown(entry: entry, size: 24)
                    }
                }
                FillingBar(entry: entry)
                DayStrip(entry: entry)
            }
        }
    }
    private var header: some View {
        HStack {
            Image(systemName: "leaf.fill").foregroundStyle(Madar.mint)
            Text("app.name").font(.subheadline.weight(.bold)).foregroundStyle(Madar.ivory)
            Spacer()
            Text(entry.prefs.city.name).font(.caption).foregroundStyle(Madar.sage)
                .padding(.horizontal, 8).padding(.vertical, 3).background(Color.white.opacity(0.08), in: Capsule())
        }
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
