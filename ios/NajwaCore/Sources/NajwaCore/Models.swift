import Foundation

/// The six daily events; sunrise is shown but is not a prayer.
public enum Prayer: String, CaseIterable, Codable, Sendable {
    case fajr = "FAJR", sunrise = "SUNRISE", dhuhr = "DHUHR", asr = "ASR", maghrib = "MAGHRIB", isha = "ISHA"
    public var isPrayer: Bool { self != .sunrise }
    public static let daily: [Prayer] = [.fajr, .dhuhr, .asr, .maghrib, .isha]
}

public enum Method: String, CaseIterable, Codable, Sendable {
    case ummAlQura = "UMM_AL_QURA", mwl = "MWL", egyptian = "EGYPTIAN", isna = "ISNA", karachi = "KARACHI", custom = "CUSTOM"
    public var fajrAngle: Double {
        switch self { case .ummAlQura: 18.5; case .mwl: 18; case .egyptian: 19.5; case .isna: 15; case .karachi: 18; case .custom: 18 }
    }
    public var ishaAngle: Double {
        switch self { case .ummAlQura: 0; case .mwl: 17; case .egyptian: 17.5; case .isna: 15; case .karachi: 18; case .custom: 17 }
    }
}

public enum HighLatitude: String, CaseIterable, Codable, Sendable { case middle = "MIDDLE", seventh = "SEVENTH", angle = "ANGLE" }
public enum ThemeChoice: String, CaseIterable, Codable, Sendable { case system = "SYSTEM", dark = "DARK", light = "LIGHT" }

public struct City: Codable, Equatable, Hashable, Sendable {
    public var name: String
    public var latitude: Double
    public var longitude: Double
    public var zone: String
    public var countryCode: String?
    public var catalogId: String?
    public init(name: String, latitude: Double, longitude: Double, zone: String, countryCode: String? = nil, catalogId: String? = nil) {
        self.name = name; self.latitude = latitude; self.longitude = longitude; self.zone = zone
        self.countryCode = countryCode; self.catalogId = catalogId
    }
    public var timeZone: TimeZone { TimeZone(identifier: zone) ?? .current }
    public var isValid: Bool {
        latitude.isFinite && (-90...90).contains(latitude) && longitude.isFinite && (-180...180).contains(longitude) && TimeZone(identifier: zone) != nil
    }
    public static let jeddah = City(name: "جدة", latitude: 21.5433, longitude: 39.1728, zone: "Asia/Riyadh", countryCode: "SA")
}

public let defaultIqamaMinutes: [Prayer: Int] = [.fajr: 20, .dhuhr: 15, .asr: 15, .maghrib: 5, .isha: 15]
public let defaultSilenceMinutes: [Prayer: Int] = [.fajr: 35, .dhuhr: 30, .asr: 30, .maghrib: 20, .isha: 30]
public let defaultFridayLeadMinutes = 30

/// Same settings model as Android, minus the ringer controls iOS does not allow.
public struct Preferences: Codable, Equatable, Sendable {
    public var city: City = .jeddah
    public var method: Method = .ummAlQura
    public var hanafi = false
    public var highLatitude: HighLatitude = .middle
    public var customFajr = 18.0
    public var customIsha = 17.0
    public var theme: ThemeChoice = .system
    public var hour24 = false
    public var adhanNotifications = false
    public var iqamaNotifications = false
    public var fridayReminder = false
    public var fridayLeadMinutes = defaultFridayLeadMinutes
    public var adhanPrayers: Set<Prayer> = Set(Prayer.daily)
    public var iqamaPrayers: Set<Prayer> = Set(Prayer.daily)
    public var iqamaMinutes: [Prayer: Int] = defaultIqamaMinutes
    /// Length of the "prayer has begun" window, counted from adhan (mirrors Android's silence window).
    public var prayerMinutes: [Prayer: Int] = defaultSilenceMinutes
    public var showIqamaCountdown = true
    /// Iqama countdown in the Dynamic Island / lock screen (Live Activity).
    public var liveIqama = true
    /// Minutes before iqama to show the Live Activity; 0 = from adhan.
    public var liveIqamaLead = 10
    /// Adds a "silence for prayer" button to adhan notifications (runs the user's Shortcut).
    public var silenceButton = false
    public var silenceShortcutName = "صمت نجوى"
    public var widgetTheme: WidgetTheme = .auto
    public init() {}

    public func iqama(_ prayer: Prayer) -> Int { iqamaMinutes[prayer] ?? defaultIqamaMinutes[prayer] ?? 15 }
    public func prayerWindow(_ prayer: Prayer) -> Int { prayerMinutes[prayer] ?? defaultSilenceMinutes[prayer] ?? 30 }

    /// Tolerant decoding: settings saved by an older version keep their values and
    /// new fields take defaults, instead of the whole object failing to decode.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = Preferences()
        func v<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T { (try? c.decodeIfPresent(T.self, forKey: key)) ?? fallback }
        city = v(.city, d.city); method = v(.method, d.method); hanafi = v(.hanafi, d.hanafi)
        highLatitude = v(.highLatitude, d.highLatitude); customFajr = v(.customFajr, d.customFajr); customIsha = v(.customIsha, d.customIsha)
        theme = v(.theme, d.theme); hour24 = v(.hour24, d.hour24)
        adhanNotifications = v(.adhanNotifications, d.adhanNotifications); iqamaNotifications = v(.iqamaNotifications, d.iqamaNotifications)
        fridayReminder = v(.fridayReminder, d.fridayReminder); fridayLeadMinutes = v(.fridayLeadMinutes, d.fridayLeadMinutes)
        adhanPrayers = v(.adhanPrayers, d.adhanPrayers); iqamaPrayers = v(.iqamaPrayers, d.iqamaPrayers)
        iqamaMinutes = v(.iqamaMinutes, d.iqamaMinutes); prayerMinutes = v(.prayerMinutes, d.prayerMinutes)
        showIqamaCountdown = v(.showIqamaCountdown, d.showIqamaCountdown)
        liveIqama = v(.liveIqama, d.liveIqama); liveIqamaLead = v(.liveIqamaLead, d.liveIqamaLead)
        silenceButton = v(.silenceButton, d.silenceButton); silenceShortcutName = v(.silenceShortcutName, d.silenceShortcutName)
        widgetTheme = v(.widgetTheme, d.widgetTheme)
    }
}

public enum WidgetTheme: String, CaseIterable, Codable, Sendable { case auto = "AUTO", dark = "DARK", light = "LIGHT" }

public struct PrayerEvent: Equatable, Sendable {
    public let prayer: Prayer
    public let time: Date
    public init(prayer: Prayer, time: Date) { self.prayer = prayer; self.time = time }
}

public struct PrayerDay: Equatable, Sendable {
    public let date: CivilDate
    public let events: [PrayerEvent]
    public let warning: String?
    public var complete: Bool { events.count == 6 }
    public var prayers: [PrayerEvent] { events.filter { $0.prayer.isPrayer } }
}
