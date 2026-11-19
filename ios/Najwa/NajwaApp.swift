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
        if args.contains("-NajwaLiveStart") { startDemoLiveActivity(prefs) }
    }

    private func persist() {
        SettingsStore.save(prefs)
        WidgetCenter.shared.reloadAllTimelines()
        Task { await reschedule() }
    }

    /// Re-plans pending notifications; iOS caps them at 64, so this also runs on every launch/foreground.
    func reschedule() async {
        await NotificationScheduler.reschedule(prefs)
        await LiveIqama.sync(prefs)
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
        var requests: [UNNotificationRequest] = []
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
                if prefs.silenceButton {
                    content.categoryIdentifier = NotificationIDs.adhanCategory
                    content.userInfo = ["minutes": prefs.prayerWindow(alert.event.prayer)]
                }
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
            requests.append(UNNotificationRequest(identifier: alert.id, content: content,
                                                  trigger: UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false)))
        }
        // Before iOS 26 a Live Activity cannot start by itself: remind at the window's start;
        // opening the notification starts the iqama countdown in the Dynamic Island.
        if !LiveIqama.canSchedule && prefs.liveIqamaLead > 0 {
            for window in iqamaWindows(from: now, prefs: prefs, horizon: 6 * 86_400) where window.showFrom > now {
                let content = UNMutableNotificationContent()
                let minutes = Int((window.iqama.timeIntervalSince(window.showFrom) / 60).rounded())
                content.title = String(format: String(localized: "live.soon_title %lld %@"), minutes, window.prayer.name)
                content.body = String(localized: "live.soon_body")
                content.interruptionLevel = .active
                content.threadIdentifier = "najwa.live"
                requests.append(UNNotificationRequest(identifier: "live:" + window.id, content: content,
                    trigger: UNTimeIntervalNotificationTrigger(timeInterval: window.showFrom.timeIntervalSince(now), repeats: false)))
            }
        }
        // iOS keeps at most 64 pending requests: keep the soonest.
        let sorted = requests.sorted {
            ($0.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval ?? 0 < ($1.trigger as? UNTimeIntervalNotificationTrigger)?.timeInterval ?? 0
        }
        for request in sorted.prefix(maxPendingNotifications) { try? await center.add(request) }
    }
}

enum NotificationIDs {
    static let adhanCategory = "NAJWA_ADHAN"
    static let silenceAction = "NAJWA_SILENCE"

    static func registerCategories() {
        let silence = UNNotificationAction(identifier: silenceAction, title: String(localized: "notify.silence_action"),
                                           options: [.foreground], icon: UNNotificationActionIcon(systemImageName: "moon.fill"))
        let category = UNNotificationCategory(identifier: adhanCategory, actions: [silence], intentIdentifiers: [])
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }
}

/// Registers background work and handles taps on Najwa notifications.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        NotificationIDs.registerCategories()
        LiveIqama.registerBackgroundTask()
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let minutes = response.notification.request.content.userInfo["minutes"] as? Int
        let action = response.actionIdentifier
        await MainActor.run {
            let prefs = SettingsStore.load()
            if action == NotificationIDs.silenceAction { PrayerSilence.run(prefs, minutes: minutes ?? 30) }
        }
        // Any Najwa notification opened inside an iqama window starts the countdown (pre-iOS 26 path).
        await LiveIqama.sync(SettingsStore.load())
    }
}
}

@main
struct NajwaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
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
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-NajwaWidgets") {
            WidgetGallery(prefs: model.prefs)
        } else if args.contains("-NajwaLive") {
            LiveGallery(prefs: model.prefs)
        } else if model.setupDone {
            TabView(selection: $model.tab) {
                HomeView().tabItem { Label("tab.home", systemImage: "house") }.tag(0)
                SettingsView().tabItem { Label("tab.settings", systemImage: "gearshape") }.tag(1)
            }
        } else {
            SetupView()
        }
    }
}
