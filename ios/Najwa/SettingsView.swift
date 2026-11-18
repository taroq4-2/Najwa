import SwiftUI
import NajwaCore

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showCity = false

    var body: some View {
        NavigationStack {
            Form {
                Section("settings.quick") {
                    Toggle("alerts.adhan", isOn: $model.prefs.adhanNotifications)
                    Toggle("alerts.iqama", isOn: $model.prefs.iqamaNotifications)
                    Toggle("alerts.friday", isOn: $model.prefs.fridayReminder)
                    if !model.notificationsAllowed && (model.prefs.adhanNotifications || model.prefs.iqamaNotifications || model.prefs.fridayReminder) {
                        Button { Task { _ = await model.requestNotifications() } } label: {
                            Label("settings.allow_notifications", systemImage: "exclamationmark.triangle").foregroundStyle(.orange)
                        }
                    }
                }
                Section("settings.all") {
                    NavigationLink { CalculationView() } label: {
                        row("settings.times", "\(model.prefs.city.name)", "mappin.and.ellipse")
                    }
                    NavigationLink { AlertsView() } label: { row("settings.alerts", nil, "bell") }
                    NavigationLink { SilenceInfoView() } label: { row("settings.silence", nil, "speaker.slash") }
                    NavigationLink { AppearanceView() } label: { row("settings.appearance", nil, "paintpalette") }
                    Button { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } } label: {
                        row("settings.language", nil, "globe")
                    }.foregroundStyle(.primary)
                }
                Section {
                    Link(destination: URL(string: "https://taroq4-2.github.io/Najwa/")!) { row("settings.website", nil, "safari") }
                } footer: {
                    Text("Najwa 1.3.0 · اللهم ارحم ابي").frame(maxWidth: .infinity).padding(.top, 12)
                }
            }
            .navigationTitle("tab.settings")
        }
    }

    private func row(_ title: LocalizedStringKey, _ detail: String?, _ icon: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundStyle(Madar.green).frame(width: 30, height: 30)
                .background(Madar.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 9))
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                if let detail { Text(detail).font(.caption).foregroundStyle(.secondary) }
            }
        }
    }
}

struct CalculationView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showCity = false
    var body: some View {
        Form {
            Section("settings.city") {
                Button { showCity = true } label: {
                    LabeledContent(model.prefs.city.name, value: model.prefs.city.zone)
                }.foregroundStyle(.primary)
            }
            Section("settings.method") {
                Picker("settings.method", selection: $model.prefs.method) {
                    ForEach(NajwaCore.Method.allCases, id: \.self) { Text(LocalizedStringKey.key("method." + $0.rawValue)).tag($0) }
                }
                if model.prefs.method == .custom {
                    Stepper(value: $model.prefs.customFajr, in: 1...30, step: 0.5) { LabeledContent("settings.fajr_angle", value: "\(model.prefs.customFajr)°") }
                    Stepper(value: $model.prefs.customIsha, in: 1...30, step: 0.5) { LabeledContent("settings.isha_angle", value: "\(model.prefs.customIsha)°") }
                }
                Picker("settings.asr", selection: $model.prefs.hanafi) {
                    Text("asr.standard").tag(false)
                    Text("asr.hanafi").tag(true)
                }
                Picker("settings.high_latitude", selection: $model.prefs.highLatitude) {
                    ForEach(HighLatitude.allCases, id: \.self) { Text(LocalizedStringKey.key("high." + $0.rawValue)).tag($0) }
                }
            }
        }
        .navigationTitle("settings.times")
        .sheet(isPresented: $showCity) { CityPickerView() }
    }
}

struct AlertsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        Form {
            Section {
                Toggle("alerts.adhan", isOn: $model.prefs.adhanNotifications)
                Toggle("alerts.iqama", isOn: $model.prefs.iqamaNotifications)
                Toggle("alerts.friday", isOn: $model.prefs.fridayReminder)
                if model.prefs.fridayReminder {
                    Picker("alerts.friday_lead", selection: $model.prefs.fridayLeadMinutes) {
                        ForEach([5, 15, 30, 45, 60], id: \.self) { Text("\($0) min").tag($0) }
                    }
                }
            } footer: { Text("alerts.footer") }
            Section("alerts.prayers") {
                ForEach(Prayer.daily, id: \.self) { prayer in
                    HStack {
                        Text(prayer.title)
                        Spacer()
                        toggleChip("label.adhan", on: model.prefs.adhanPrayers.contains(prayer)) { model.prefs.adhanPrayers.formSymmetricDifference([prayer]) }
                        toggleChip("label.iqama", on: model.prefs.iqamaPrayers.contains(prayer)) { model.prefs.iqamaPrayers.formSymmetricDifference([prayer]) }
                    }
                }
            }
            Section {
                ForEach(Prayer.daily, id: \.self) { prayer in
                    Stepper(value: Binding(get: { model.prefs.iqama(prayer) }, set: { model.prefs.iqamaMinutes[prayer] = $0 }), in: 1...120) {
                        LabeledContent { Text("\(model.prefs.iqama(prayer)) min") } label: { Text(prayer.title) }
                    }
                }
            } header: { Text("alerts.iqama_times") } footer: { Text("alerts.iqama_footer") }
            Section {
                ForEach(Prayer.daily, id: \.self) { prayer in
                    Stepper(value: Binding(get: { model.prefs.prayerWindow(prayer) }, set: { model.prefs.prayerMinutes[prayer] = max($0, model.prefs.iqama(prayer) + 1) }), in: 5...120, step: 5) {
                        LabeledContent { Text("\(model.prefs.prayerWindow(prayer)) min") } label: { Text(prayer.title) }
                    }
                }
            } header: { Text("alerts.prayer_window") } footer: { Text("alerts.prayer_window_footer") }
        }
        .navigationTitle("settings.alerts")
    }

    private func toggleChip(_ title: LocalizedStringKey, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.caption.bold()).padding(.horizontal, 10).padding(.vertical, 5)
                .foregroundStyle(on ? .white : .secondary)
                .background(on ? Madar.green : Color(.tertiarySystemFill), in: Capsule())
        }.buttonStyle(.plain)
    }
}

struct AppearanceView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        Form {
            Picker("appearance.theme", selection: $model.prefs.theme) {
                Text("theme.system").tag(ThemeChoice.system)
                Text("theme.light").tag(ThemeChoice.light)
                Text("theme.dark").tag(ThemeChoice.dark)
            }.pickerStyle(.inline)
            Toggle("appearance.hour24", isOn: $model.prefs.hour24)
            Toggle("appearance.iqama_countdown", isOn: $model.prefs.showIqamaCountdown)
        }
        .navigationTitle("settings.appearance")
    }
}

/// iOS does not let apps change the ringer; offer the system's own tools instead.
struct SilenceInfoView: View {
    var body: some View {
        Form {
            Section { Text("silence.explain") }
            Section("silence.focus_title") { Text("silence.focus_steps") }
            Section("silence.shortcut_title") { Text("silence.shortcut_steps") }
        }
        .navigationTitle("settings.silence")
    }
}

struct CityPickerView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var countries: [CatalogCountry] = []
    @State private var country: CatalogCountry?
    @State private var query = ""
    @State private var results: [CatalogPlace] = []

    var body: some View {
        NavigationStack {
            List {
                if let country {
                    Section {
                        Picker("city.country", selection: Binding(get: { country }, set: { self.country = $0; search() })) {
                            ForEach(countries) { Text($0.displayName()).tag($0) }
                        }
                    }
                    Section {
                        ForEach(results) { place in
                            Button {
                                model.prefs.city = place.city(in: country)
                                dismiss()
                            } label: {
                                HStack {
                                    Text(place.label)
                                    Spacer()
                                    if model.prefs.city.catalogId == place.id { Image(systemName: "checkmark").foregroundStyle(Madar.green) }
                                }
                            }.foregroundStyle(.primary)
                        }
                    } footer: { if results.isEmpty { Text("city.none") } }
                }
            }
            .searchable(text: $query, prompt: Text("city.search"))
            .onChange(of: query) { _, _ in search() }
            .navigationTitle("city.title")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("action.close") { dismiss() } } }
            .task {
                guard let catalog = model.catalog else { return }
                countries = catalog.countries()
                country = catalog.country(for: model.prefs.city, in: countries)
                search()
            }
        }
    }

    private func search() {
        guard let catalog = model.catalog, let country else { return }
        let term = query
        Task.detached {
            let found = catalog.search(country, term: term)
            await MainActor.run { if term == query { results = found } }
        }
    }
}
