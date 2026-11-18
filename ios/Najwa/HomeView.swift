import SwiftUI
import NajwaCore

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showCity = false

    var body: some View {
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let now = context.date
                let prefs = model.prefs
                let days = model.calculator.around(now, prefs)
                let today = days.first { $0.date == CivilDate(now, in: prefs.city.timeZone) }
                let countdown = prayerCountdown(days.flatMap(\.events), now: now, prefs: prefs, includePrayer: true)
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        header(now)
                        if let countdown { hero(countdown, now: now) } else { unavailable(today?.warning) }
                        if let today, today.complete { todayList(today, countdown: countdown, now: now) }
                    }
                    .padding(.horizontal, 18).padding(.bottom, 24)
                }
            }
            .background(Color(.systemGroupedBackground))
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showCity) { CityPickerView() }
        }
    }

    private func header(_ now: Date) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "leaf.fill").font(.title2).foregroundStyle(Madar.green)
                    .frame(width: 44, height: 44).background(Madar.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 0) {
                    Text("app.name").font(.title.bold())
                    Text("app.tagline").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button { showCity = true } label: {
                    Label(model.prefs.city.name, systemImage: "mappin.and.ellipse").font(.subheadline).lineLimit(1)
                        .padding(.horizontal, 12).padding(.vertical, 8).background(.thinMaterial, in: Capsule())
                }.buttonStyle(.plain)
            }
            HStack {
                Text(NajwaFormat.hijri(now, zone: model.prefs.city.timeZone)).font(.caption.bold()).foregroundStyle(Madar.green)
                Spacer()
                Text(NajwaFormat.weekdayDate(now, zone: model.prefs.city.timeZone)).font(.caption).foregroundStyle(.secondary)
            }
        }.padding(.top, 12)
    }

    private func hero(_ c: Countdown, now: Date) -> some View {
        let stage = c.stage(at: now)
        let accent = Madar.accent(stage)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Circle().fill(accent).frame(width: 7, height: 7)
                Text(stage.heading).font(.footnote.bold()).foregroundStyle(stage == .adhan ? Madar.sage : accent)
            }
            Text(c.prayer.title).font(.system(size: 40, weight: .bold)).foregroundStyle(Madar.ivory)
            Text(c.detail(model.prefs)).font(.subheadline).foregroundStyle(Madar.sage)
            Text(timerInterval: now...c.target, countsDown: true)
                .font(.system(size: 52, weight: .bold, design: .rounded).monospacedDigit())
                .foregroundStyle(accent).environment(\.layoutDirection, .leftToRight)
                .frame(maxWidth: .infinity)
            Text(stage.countdownLabel).font(.footnote).foregroundStyle(Madar.sage)
            ProgressView(value: c.progress(at: now)).tint(accent)
        }
        .padding(22)
        .background(Madar.heroGradient, in: RoundedRectangle(cornerRadius: 28))
        .shadow(color: .black.opacity(0.18), radius: 18, y: 10)
    }

    private func unavailable(_ warning: String?) -> some View {
        Text("home.unavailable").font(.subheadline).foregroundStyle(.secondary)
            .frame(maxWidth: .infinity).padding(24).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 22))
    }

    private func todayList(_ day: PrayerDay, countdown: Countdown?, now: Date) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("home.today").font(.title3.bold())
            VStack(spacing: 0) {
                ForEach(day.events, id: \.prayer) { event in
                    let active = event.prayer == countdown?.prayer
                    HStack {
                        Image(systemName: event.prayer == .sunrise ? "sunrise" : (event.time <= now && !active ? "checkmark.circle" : "circle"))
                            .foregroundStyle(active ? Madar.green : .secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(event.prayer.title).font(.body.weight(active ? .bold : .regular))
                            if event.prayer.isPrayer && model.prefs.showIqamaCountdown {
                                Text(String(localized: "label.iqama") + " " + NajwaFormat.time(event.time.addingTimeInterval(TimeInterval(model.prefs.iqama(event.prayer) * 60)), model.prefs))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Text(NajwaFormat.time(event.time, model.prefs)).font(.title3.weight(.semibold).monospacedDigit())
                    }
                    .padding(.vertical, 12).padding(.horizontal, 16)
                    .background(active ? Madar.green.opacity(0.12) : .clear)
                    if event.prayer != .isha { Divider().padding(.leading, 16) }
                }
            }
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
        }
    }
}
