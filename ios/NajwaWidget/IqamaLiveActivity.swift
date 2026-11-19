import ActivityKit
import WidgetKit
import SwiftUI

/// Iqama countdown in the Dynamic Island and on the lock screen.
struct IqamaLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: IqamaAttributes.self) { context in
            IqamaLockScreenView(model: IqamaActivityState(attributes: context.attributes, state: context.state, stale: context.isStale))
                .activityBackgroundTint(Madar.deep)
                .activitySystemActionForegroundColor(Madar.mint)
        } dynamicIsland: { context in
            let model = IqamaActivityState(attributes: context.attributes, state: context.state, stale: context.isStale)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label { Text(model.attributes.prayerName).font(.headline) } icon: { Image(systemName: "leaf.fill").foregroundStyle(Madar.mint) }
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    IqamaTimer(model: model, size: 28).padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(model.label).font(.caption.bold()).foregroundStyle(model.accent)
                        IqamaProgress(model: model)
                    }.padding(.horizontal, 4)
                }
            } compactLeading: {
                Image(systemName: "leaf.fill").foregroundStyle(model.accent)
            } compactTrailing: {
                IqamaCompactTimer(model: model)
            } minimal: {
                Image(systemName: "leaf.fill").foregroundStyle(model.accent)
            }
            .widgetURL(URL(string: "najwa://home"))
            .keylineTint(model.accent)
        }
    }
}
