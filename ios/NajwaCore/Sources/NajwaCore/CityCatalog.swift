import Foundation

public struct CatalogCountry: Hashable, Identifiable, Sendable {
    public let code: String
    public let arabicName: String?
    public var id: String { code }
    public func displayName(locale: Locale = .current) -> String {
        if locale.language.languageCode?.identifier == "ar", let arabicName { return arabicName }
        return locale.localizedString(forRegionCode: code) ?? code
    }
}

public struct CatalogPlace: Hashable, Identifiable, Sendable {
    public let id: String, name: String, latitude: Double, longitude: Double, zone: String, region: String
    public var label: String { region.isEmpty || region == name ? name : "\(name) · \(region)" }
    public func city(in country: CatalogCountry) -> City {
        City(name: name, latitude: latitude, longitude: longitude, zone: CityCatalog.catalogZone(country.code, zone),
             countryCode: country.code, catalogId: id)
    }
}

/// Reads the same TSV city files as the Android app (shared from app/src/main/assets):
/// `cities/` (Arab countries, Arabic names) and `cities-world/` (other countries).
/// Row: id \t name \t search terms \t lat \t lon \t zone \t region.
public struct CityCatalog: Sendable {
    public let root: URL
    public init(root: URL) { self.root = root }
    public init?(bundle: Bundle = .main) {
        guard let url = bundle.resourceURL else { return nil }
        self.root = url
    }

    static let arabCountries: [CatalogCountry] = [
        ("SA", "السعودية"), ("AE", "الإمارات"), ("BH", "البحرين"), ("KW", "الكويت"), ("OM", "عُمان"), ("QA", "قطر"),
        ("JO", "الأردن"), ("PS", "فلسطين"), ("LB", "لبنان"), ("SY", "سوريا"), ("IQ", "العراق"), ("YE", "اليمن"),
        ("EG", "مصر"), ("SD", "السودان"), ("LY", "ليبيا"), ("TN", "تونس"), ("DZ", "الجزائر"), ("MA", "المغرب"),
        ("MR", "موريتانيا"), ("SO", "الصومال"), ("DJ", "جيبوتي"), ("KM", "جزر القمر"),
    ].map { CatalogCountry(code: $0.0, arabicName: $0.1) }

    public func countries() -> [CatalogCountry] {
        let world = (try? FileManager.default.contentsOfDirectory(atPath: root.appendingPathComponent("cities-world").path)) ?? []
        let codes = world.filter { $0.hasSuffix(".tsv") }.map { String($0.dropLast(4)) }.filter { $0.count == 2 }.sorted()
        return Self.arabCountries + codes.map { CatalogCountry(code: $0, arabicName: nil) }
    }

    /// Up to `limit` places in `country` matching `term` (name, alternate names or region).
    public func search(_ country: CatalogCountry, term: String, limit: Int = 100) -> [CatalogPlace] {
        let folder = country.arabicName != nil ? "cities" : "cities-world"
        let url = root.appendingPathComponent(folder).appendingPathComponent("\(country.code).tsv")
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        let key = NajwaFormat.searchKey(term)
        var result: [CatalogPlace] = []
        text.enumerateLines { line, stop in
            let f = line.split(separator: "\t", maxSplits: 6, omittingEmptySubsequences: false)
            guard f.count == 7, let lat = Double(f[3]), let lon = Double(f[4]) else { return }
            if !key.isEmpty && !NajwaFormat.searchKey("\(f[1])|\(f[2])|\(f[6])").contains(key) { return }
            result.append(CatalogPlace(id: String(f[0]), name: String(f[1]), latitude: lat, longitude: lon, zone: String(f[5]), region: String(f[6])))
            if result.count >= limit { stop = true }
        }
        return result
    }

    /// The catalog country that most likely contains `city` (by stored code, then zone).
    public func country(for city: City, in countries: [CatalogCountry]) -> CatalogCountry {
        if let code = city.countryCode, let match = countries.first(where: { $0.code == code }) { return match }
        let zones = (try? String(contentsOf: root.appendingPathComponent("cities-world/zones.tsv"), encoding: .utf8)) ?? ""
        let code = zones.split(separator: "\n").first { $0.hasPrefix(city.zone + "\t") }.map { String($0.split(separator: "\t").last ?? "") }
        return countries.first { $0.code == code } ?? Self.arabCountries[0]
    }

    /// IANA lists one national civil zone for these countries.
    static func catalogZone(_ code: String, _ source: String) -> String {
        switch code { case "DZ": "Africa/Algiers"; case "SD": "Africa/Khartoum"; case "AE": "Asia/Dubai"; default: source }
    }
}
