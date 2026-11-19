import ActivityKit
import BackgroundTasks
import UIKit
import NajwaCore

/// Starts, schedules and ends the iqama countdown Live Activity.
/// iOS 26+: the next windows are scheduled to start on their own (Activity.request(...start:)).
/// Earlier iOS: an activity can only start while the app is open, so the window's start
/// also gets a notification; opening it (or the app) inside the window starts the countdown.
@MainActor
enum LiveIqama {
    /// Scheduled activities count toward the system's simultaneous limit; keep it small.
    static let scheduledAhead = 2
    static var canSchedule: Bool { if #available(iOS 26.0, *) { return true } else { return false } }

    static func sync(_ prefs: Preferences, now: Date = Date()) async {
        let windows = iqamaWindows(from: now, prefs: prefs)
        let wanted = Set(windows.prefix(scheduledAhead).map(\.id))
        for activity in Activity<IqamaAttributes>.activities {
            let finished = activity.content.state.end <= now
            let unwanted = !wanted.contains(activity.attributes.key) && activity.content.state.iqama > now
            if !prefs.liveIqama || finished || unwanted {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
        guard prefs.liveIqama, ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let live = Set(Activity<IqamaAttributes>.activities
            .filter { $0.activityState != .ended && $0.activityState != .dismissed }.map(\.attributes.key))
        for window in windows.prefix(scheduledAhead) where !live.contains(window.id) {
            let attributes = IqamaAttributes(prayer: window.prayer.rawValue, city: prefs.city.name, key: window.id)
            let content = ActivityContent(state: IqamaAttributes.ContentState(adhan: window.adhan, iqama: window.iqama, end: window.end),
                                          staleDate: window.iqama, relevanceScore: 100)
            if window.showFrom <= now {
                _ = try? Activity.request(attributes: attributes, content: content, pushType: nil)
            } else {
                schedule(attributes, content, at: window.showFrom, prefs: prefs)
            }
        }
        scheduleCleanup(after: windows.first?.end)
    }

    private static func schedule(_ attributes: IqamaAttributes, _ content: ActivityContent<IqamaAttributes.ContentState>,
                                 at start: Date, prefs: Preferences) {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            let title = String(format: NSLocalizedString("live.alert_title %@", comment: ""), attributes.prayerName)
            let alert = AlertConfiguration(title: LocalizedStringResource(stringLiteral: title),
                                           body: LocalizedStringResource(stringLiteral: attributes.city), sound: .default)
            _ = try? Activity.request(attributes: attributes, content: content, pushType: nil, style: .standard,
                                      alertConfiguration: alert, start: start)
        }
        #endif
    }

    // MARK: Background clean-up (best effort; the system also ends activities after 8 hours).

    static let refreshTask = "org.najwa.app.refresh"

    static func registerBackgroundTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTask, using: nil) { task in
            Task { @MainActor in
                let prefs = SettingsStore.load()
                await sync(prefs)
                await NotificationScheduler.reschedule(prefs)
                task.setTaskCompleted(success: true)
            }
        }
    }

    static func scheduleCleanup(after date: Date?) {
        let request = BGAppRefreshTaskRequest(identifier: refreshTask)
        request.earliestBeginDate = date ?? Date().addingTimeInterval(6 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }
}

/// Silence during prayer: iOS does not let apps change the ringer, so Najwa runs the
/// person's own Shortcut (Set Focus → Do Not Disturb until now + minutes).
enum PrayerSilence {
    static func url(shortcut: String, minutes: Int) -> URL? {
        var c = URLComponents(string: "shortcuts://run-shortcut")
        c?.queryItems = [URLQueryItem(name: "name", value: shortcut), URLQueryItem(name: "input", value: "text"),
                         URLQueryItem(name: "text", value: String(minutes))]
        return c?.url
    }

    @MainActor static func run(_ prefs: Preferences, minutes: Int) {
        guard let url = url(shortcut: prefs.silenceShortcutName, minutes: minutes) else { return }
        UIApplication.shared.open(url)
    }
}
