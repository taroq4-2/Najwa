import SwiftUI
import WidgetKit
import UserNotifications
import NajwaCore

@MainActor
final class AppModel: ObservableObject {
    @Published var prefs: Preferences { didSet { if prefs != oldValue { persist() } } }
    @Published var setupDone: Bool { didSet { SettingsStore.setupDone = setupDone } }
    @Published var notificationsAllowed = false
    let calculator = PrayerCalculator()
    let catalog = CityCatalog()

    /// Opened tab; `-NajwaTab settings` selects Settings (used for screenshots).
    @Published var tab = 0

    init() {
        prefs = SettingsStore.load()
        setupDone = SettingsStore.setupDone
        // Screenshot mode for CI: skip setup with alerts on. Never set by the app itself.
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-NajwaDemo") {
            setupDone = true
            prefs.adhanNotifications = true; prefs.iqamaNotifications = true; prefs.fridayReminder = true
        }
        if let i = args.firstIndex(of: "-NajwaTab"), args.indices.contains(i + 1), args[i + 1] == "settings" { tab = 1 }
    }

    private func persist() {
        SettingsStore.save(prefs)
        WidgetCenter.shared.reloadAllTimelines()
        Task { await reschedule() }
    }

    /// Re-plans pending notifications; iOS caps them at 64, so this also runs on every launch/foreground.
    func reschedule() async {
        await NotificationScheduler.reschedule(prefs)
        await refreshPermission()
    }

    func refreshPermission() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationsAllowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }

    func requestNotifications() async -> Bool {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        await reschedule()
        return granted
    }
}

enum NotificationScheduler {
    static func reschedule(_ prefs: Preferences, now: Date = Date()) async {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
        for alert in upcomingAlerts(from: now, prefs: prefs) {
            let content = UNMutableNotificationContent()
            let prayer = alert.event.prayer.name
            let time = NajwaFormat.time(alert.event.time, prefs)
            switch alert.kind {
            case .adhan:
                content.title = String(format: String(localized: "notify.adhan_title %@"), prayer)
                let iqama = NajwaFormat.time(alert.event.time.addingTimeInterval(TimeInterval(prefs.iqama(alert.event.prayer) * 60)), prefs)
                content.body = String(format: String(localized: "notify.adhan_body %@ %@ %@"), time, iqama, prefs.city.name)
                content.sound = .default
                content.interruptionLevel = .timeSensitive
            case .iqama:
                content.title = String(format: String(localized: "notify.iqama_title %@"), prayer)
                content.body = String(format: String(localized: "notify.iqama_body %@"), prefs.city.name)
                content.sound = nil
                content.interruptionLevel = .active
            case .friday:
                content.title = String(localized: "notify.friday_title")
                content.body = String(format: String(localized: "notify.friday_body %@ %@"), time, prefs.city.name)
                content.sound = .default
            }
            content.threadIdentifier = "najwa.\(alert.kind.rawValue)"
            // Absolute delay: stays correct if the phone's own time zone differs from the city's.
            let delay = alert.at.timeIntervalSince(now)
            guard delay > 1 else { continue }
            let request = UNNotificationRequest(identifier: alert.id, content: content,
                                                trigger: UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false))
            try? await center.add(request)
        }
    }
}

@main
struct NajwaApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(model.prefs.theme == .dark ? .dark : model.prefs.theme == .light ? .light : nil)
                .tint(Madar.green)
        }
        .onChange(of: phase) { _, newPhase in
            if newPhase == .active { Task { await model.reschedule(); WidgetCenter.shared.reloadAllTimelines() } }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        if model.setupDone {
            TabView(selection: $model.tab) {
                HomeView().tabItem { Label("tab.home", systemImage: "house") }.tag(0)
                SettingsView().tabItem { Label("tab.settings", systemImage: "gearshape") }.tag(1)
            }
        } else {
            SetupView()
        }
    }
}
