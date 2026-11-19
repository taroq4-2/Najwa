import ActivityKit
import SwiftUI
import NajwaCore

/// Live Activity for the iqama countdown (Dynamic Island + lock screen).
struct IqamaAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var adhan: Date
        var iqama: Date
        /// End of the prayer window; after iqama the activity shows "the prayer has begun" until here.
        var end: Date
    }
    var prayer: String
    var city: String
    /// IqamaWindow.id, so the app never starts two activities for the same prayer.
    var key: String

    var prayerName: String { NSLocalizedString("prayer." + prayer, comment: "") }
}

/// Phase shown by the activity: counting down to iqama, or prayer in progress after it.
/// `stale` is set by the system at the content's stale date (iqama), re-rendering the views.
struct IqamaActivityState {
    let attributes: IqamaAttributes
    let state: IqamaAttributes.ContentState
    let stale: Bool
    var praying: Bool { stale || state.iqama <= Date() }
    var accent: Color { praying ? Madar.sky : Madar.gold }
    var label: LocalizedStringKey { praying ? "stage.prayer" : "stage.to_iqama" }
    var range: ClosedRange<Date> { praying ? state.iqama...max(state.end, state.iqama) : state.adhan...state.iqama }
}

struct IqamaTimer: View {
    let model: IqamaActivityState
    var size: CGFloat
    var body: some View {
        Text(timerInterval: model.range, countsDown: true)
            .font(.system(size: size, weight: .bold, design: .rounded).monospacedDigit())
            .foregroundStyle(model.accent)
            .multilineTextAlignment(.trailing)
            .environment(\.layoutDirection, .leftToRight)
    }
}

struct IqamaProgress: View {
    let model: IqamaActivityState
    var body: some View {
        ProgressView(timerInterval: model.range, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
            .progressViewStyle(.linear).tint(model.accent)
    }
}

/// Lock screen / banner presentation.
struct IqamaLockScreenView: View {
    let model: IqamaActivityState
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "leaf.fill").foregroundStyle(Madar.mint)
                Text("app.name").font(.footnote.bold()).foregroundStyle(Madar.ivory)
                Spacer()
                Text(model.attributes.city).font(.caption).foregroundStyle(Madar.sage)
            }
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(model.label).font(.caption.bold()).foregroundStyle(model.accent)
                    Text(model.attributes.prayerName).font(.title2.bold()).foregroundStyle(Madar.ivory)
                }
                Spacer()
                IqamaTimer(model: model, size: 34)
            }
            IqamaProgress(model: model)
        }
        .padding(16)
    }
}

/// Expanded Dynamic Island content, reused by the app's preview gallery.
struct IqamaExpandedView: View {
    let model: IqamaActivityState
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center) {
                Image(systemName: "leaf.fill").foregroundStyle(Madar.mint)
                VStack(alignment: .leading, spacing: 0) {
                    Text(model.label).font(.caption2.bold()).foregroundStyle(model.accent)
                    Text(model.attributes.prayerName).font(.headline).foregroundStyle(.white)
                }
                Spacer()
                IqamaTimer(model: model, size: 30)
            }
            IqamaProgress(model: model)
        }
    }
}

struct IqamaCompactTimer: View {
    let model: IqamaActivityState
    var body: some View {
        Text(timerInterval: model.range, countsDown: true)
            .font(.system(size: 14, weight: .semibold, design: .rounded).monospacedDigit())
            .foregroundStyle(model.accent)
            .frame(maxWidth: 46)
            .environment(\.layoutDirection, .leftToRight)
    }
}
