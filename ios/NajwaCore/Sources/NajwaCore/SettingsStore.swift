import Foundation

/// Preferences shared by the app and its widget through the App Group container.
public enum SettingsStore {
    public static let appGroup = "group.org.najwa.app"
    private static let key = "najwa.preferences.v1"
    private static let setupKey = "najwa.setup.done.v1"

    public static var defaults: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    public static func load() -> Preferences {
        guard let data = defaults.data(forKey: key), let prefs = try? JSONDecoder().decode(Preferences.self, from: data), prefs.city.isValid
        else { return Preferences() }
        return prefs
    }

    public static func save(_ prefs: Preferences) {
        if let data = try? JSONEncoder().encode(prefs) { defaults.set(data, forKey: key) }
    }

    public static var setupDone: Bool {
        get { defaults.bool(forKey: setupKey) }
        set { defaults.set(newValue, forKey: setupKey) }
    }
}
