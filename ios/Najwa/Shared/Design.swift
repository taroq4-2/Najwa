import SwiftUI
import NajwaCore

/// Madar identity, shared by the app and the widget.
enum Madar {
    static let ink = Color(hex: 0x193C30)
    static let paper = Color(hex: 0xF7F6EF)
    static let deep = Color(hex: 0x0F2E24)
    static let night = Color(hex: 0x081C19)
    static let emerald = Color(hex: 0x184B3C)
    static let mint = Color(hex: 0xA5E8C4)
    static let green = Color(hex: 0x3EB28C)
    static let ivory = Color(hex: 0xF6F4EC)
    static let sage = Color(hex: 0xB8CBC0)
    static let gold = Color(hex: 0xF0C787)
    static let coral = Color(hex: 0xFF9E6D)
    static let sky = Color(hex: 0x8FD0E6)

    static var heroGradient: LinearGradient {
        LinearGradient(colors: [emerald, deep, night], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static func accent(_ stage: Stage) -> Color {
        switch stage { case .adhan: mint; case .soon: coral; case .iqama: gold; case .prayer: sky }
    }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255, opacity: opacity)
    }
}

extension LocalizedStringKey {
    /// A key computed at runtime, looked up verbatim.
    static func key(_ value: String) -> LocalizedStringKey { LocalizedStringKey(stringLiteral: value) }
}

extension Prayer {
    /// Built keys must not go through interpolation (that would look up "prayer.%@").
    var title: LocalizedStringKey { .key("prayer." + rawValue) }
    var name: String { NSLocalizedString("prayer." + rawValue, comment: "") }
}

extension Stage {
    var heading: LocalizedStringKey {
        switch self { case .adhan, .soon: "stage.next"; case .iqama: "stage.iqama"; case .prayer: "stage.prayer" }
    }
    var countdownLabel: LocalizedStringKey {
        switch self { case .adhan, .soon: "stage.to_adhan"; case .iqama: "stage.to_iqama"; case .prayer: "stage.ends_in" }
    }
}

extension Countdown {
    /// Time shown next to the prayer name for this stage.
    func detail(_ prefs: Preferences) -> String {
        let target = NajwaFormat.time(self.target, prefs)
        switch phase {
        case .iqama: return String(localized: "label.iqama") + " " + target
        case .prayer: return String(format: String(localized: "label.until %@"), target)
        case .adhan:
            if let iqama = upcomingIqama { return target + " · " + String(localized: "label.iqama") + " " + NajwaFormat.time(iqama, prefs) }
            return target
        }
    }
}

/// Widget colours for the chosen widget theme (auto follows the phone's light/dark mode).
struct WidgetPalette {
    let light: Bool
    var text: Color { light ? Madar.ink : Madar.ivory }
    var secondary: Color { light ? Color(hex: 0x536B60) : Madar.sage }
    var mark: Color { light ? Color(hex: 0x126D53) : Madar.mint }
    var chip: Color { light ? Madar.ink.opacity(0.06) : Color.white.opacity(0.08) }
    func accent(_ stage: Stage) -> Color {
        guard light else { return Madar.accent(stage) }
        switch stage {
        case .adhan: return Color(hex: 0x126D53)
        case .soon: return Color(hex: 0xB4471B)
        case .iqama: return Color(hex: 0x81551E)
        case .prayer: return Color(hex: 0x1F6E8C)
        }
    }
    var background: LinearGradient {
        light ? LinearGradient(colors: [Color(hex: 0xFBFAF4), Color(hex: 0xE5ECDF)], startPoint: .topLeading, endPoint: .bottomTrailing)
              : Madar.heroGradient
    }
    static func resolve(_ theme: WidgetTheme, _ scheme: ColorScheme) -> WidgetPalette {
        switch theme {
        case .light: return WidgetPalette(light: true)
        case .dark: return WidgetPalette(light: false)
        case .auto: return WidgetPalette(light: scheme == .light)
        }
    }
}

private struct WidgetPaletteKey: EnvironmentKey { static let defaultValue = WidgetPalette(light: false) }
extension EnvironmentValues {
    var najwaPalette: WidgetPalette {
        get { self[WidgetPaletteKey.self] }
        set { self[WidgetPaletteKey.self] = newValue }
    }
}

/// Widget background following the widget theme.
struct WidgetBackground: View {
    @Environment(\.colorScheme) private var scheme
    let theme: WidgetTheme
    var body: some View { Rectangle().fill(WidgetPalette.resolve(theme, scheme).background) }
}
