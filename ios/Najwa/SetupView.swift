import SwiftUI
import NajwaCore

/// First run in three steps: place → features → notifications, then straight into the app.
struct SetupView: View {
    @EnvironmentObject private var model: AppModel
    @State private var step = 0
    @State private var showCity = false
    @State private var adhan = true
    @State private var iqama = true
    @State private var friday = true

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                ForEach(0..<3, id: \.self) { i in
                    Capsule().fill(i <= step ? Madar.green : Color(.tertiarySystemFill)).frame(height: 5)
                }
            }.padding(.top, 20)
            Text(String(format: String(localized: "setup.step %lld %lld"), step + 1, 3)).font(.caption).foregroundStyle(.secondary).padding(.vertical, 10)
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    switch step {
                    case 0: place
                    case 1: features
                    default: permissions
                    }
                }
            }
        }
        .padding(.horizontal, 22)
        .sheet(isPresented: $showCity) { CityPickerView() }
    }

    private var place: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                Image(systemName: "leaf.fill").font(.title).foregroundStyle(Madar.mint)
                    .frame(width: 56, height: 56).background(Madar.emerald, in: RoundedRectangle(cornerRadius: 17))
                VStack(alignment: .leading) {
                    Text("setup.welcome").font(.title.bold())
                    Text("setup.tagline").font(.subheadline).foregroundStyle(.secondary)
                }
            }
            card {
                Text("settings.city").font(.headline)
                HStack {
                    VStack(alignment: .leading) {
                        Text(model.prefs.city.name).font(.title3.bold())
                        Text(model.prefs.city.zone).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("setup.change") { showCity = true }
                }
                Divider()
                Picker("settings.method", selection: $model.prefs.method) {
                    ForEach(NajwaCore.Method.allCases, id: \.self) { Text(LocalizedStringKey.key("method." + $0.rawValue)).tag($0) }
                }
                Picker("settings.asr", selection: $model.prefs.hanafi) {
                    Text("asr.standard").tag(false)
                    Text("asr.hanafi").tag(true)
                }
            }
            primary("setup.continue") { step = 1 }
        }
    }

    private var features: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("setup.features_title").font(.title.bold())
            Text("setup.features_help").font(.subheadline).foregroundStyle(.secondary)
            card {
                feature("bell", "alerts.adhan", "alerts.adhan_help", $adhan)
                feature("bell.badge", "alerts.iqama", "alerts.iqama_help", $iqama)
                feature("calendar", "alerts.friday", "alerts.friday_help", $friday)
            }
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "speaker.slash").foregroundStyle(Madar.green)
                Text("setup.silence_note").font(.footnote).foregroundStyle(.secondary)
            }
            HStack {
                Button("setup.back") { step = 0 }.buttonStyle(.bordered)
                primary("setup.next") { step = 2 }
            }
        }
    }

    private var permissions: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("setup.permissions_title").font(.title.bold())
            Text("setup.permissions_help").font(.subheadline).foregroundStyle(.secondary)
            card {
                HStack {
                    Label("setup.notifications", systemImage: "bell.badge")
                    Spacer()
                    Text(model.notificationsAllowed ? "setup.granted" : "setup.required")
                        .font(.caption.bold()).foregroundStyle(model.notificationsAllowed ? Madar.green : .orange)
                }
                if !model.notificationsAllowed && (adhan || iqama || friday) {
                    Button("setup.allow") { Task { _ = await model.requestNotifications() } }.buttonStyle(.borderedProminent)
                }
            }
            HStack {
                Button("setup.back") { step = 1 }.buttonStyle(.bordered)
                primary("setup.start") {
                    model.prefs.adhanNotifications = adhan
                    model.prefs.iqamaNotifications = iqama
                    model.prefs.fridayReminder = friday
                    model.setupDone = true
                }
            }
        }
        .task { await model.refreshPermission() }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 14, content: content)
            .padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
    }

    private func feature(_ icon: String, _ title: LocalizedStringKey, _ help: LocalizedStringKey, _ isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundStyle(Madar.green).frame(width: 34, height: 34)
                    .background(Madar.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.body.bold())
                    Text(help).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func primary(_ title: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) { Text(title).bold().frame(maxWidth: .infinity).padding(.vertical, 6) }
            .buttonStyle(.borderedProminent).tint(Madar.green)
    }
}
