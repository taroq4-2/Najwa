import Foundation

public enum NajwaFormat {
    /// Prayer time in the city's zone; Latin digits as on Android, 12/24-hour per settings.
    public static func time(_ date: Date, _ prefs: Preferences, locale: Locale = .current) -> String {
        let f = DateFormatter()
        f.timeZone = prefs.city.timeZone
        let arabic = locale.language.languageCode?.identifier == "ar"
        f.locale = arabic ? Locale(identifier: "ar@numbers=latn") : locale
        f.dateFormat = prefs.hour24 ? "HH:mm" : "h:mm a"
        return f.string(from: date)
    }

    public static func weekdayDate(_ date: Date, zone: TimeZone, locale: Locale = .current) -> String {
        let f = DateFormatter()
        f.timeZone = zone
        f.locale = locale
        f.setLocalizedDateFormatFromTemplate("EEEEdMMMM")
        return f.string(from: date)
    }

    /// Umm al-Qura Hijri date, e.g. "٧ جمادى الآخرة ١٤٤٨ هـ".
    public static func hijri(_ date: Date, zone: TimeZone, locale: Locale = .current) -> String {
        var calendar = Calendar(identifier: .islamicUmmAlQura)
        calendar.timeZone = zone
        let f = DateFormatter()
        f.calendar = calendar
        f.timeZone = zone
        f.locale = locale
        f.setLocalizedDateFormatFromTemplate("dMMMMyG")
        return f.string(from: date)
    }

    /// Arabic and Persian digits → ASCII, Arabic decimal separators → ".".
    public static func normalizedNumber(_ value: String) -> String {
        String(value.trimmingCharacters(in: .whitespaces).map { c -> Character in
            if let v = c.unicodeScalars.first?.value {
                if (0x0660...0x0669).contains(v) { return Character(UnicodeScalar(v - 0x0660 + 48)!) }
                if (0x06F0...0x06F9).contains(v) { return Character(UnicodeScalar(v - 0x06F0 + 48)!) }
            }
            if c == "٫" || c == "," { return "." }
            if c == "−" { return "-" }
            return c
        })
    }

    /// Search key: strip diacritics/tatweel, unify ى/ي and ة/ه, lowercase (Kotlin citySearchKey).
    public static func searchKey(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespaces)
            .applyingTransform(.stripDiacritics, reverse: false)?
            .replacingOccurrences(of: "ـ", with: "")
            .replacingOccurrences(of: "ى", with: "ي")
            .replacingOccurrences(of: "ة", with: "ه")
            .lowercased() ?? value.lowercased()
    }
}
