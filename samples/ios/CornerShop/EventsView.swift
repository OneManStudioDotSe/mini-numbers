import SwiftUI

/// Tab 2 (/events): try a custom event and an outbound link, and watch the tracker's log.
struct EventsView: View {
    @ObservedObject private var tracker = MiniNumbers.shared
    @Environment(\.openURL) private var openURL

    private let docsUrl = "https://github.com/OneManStudioDotSe/mini-numbers"

    var body: some View {
        Screen(title: "Events", subtitle: "Tap a button, watch it go.") {
            HStack(spacing: Theme.Space.md) {
                StatTile(label: "Sent", value: String(tracker.sentCount))
                StatTile(label: "Session", value: String(tracker.sessionId.prefix(8)))
            }

            Card(title: "Try it") {
                VStack(spacing: Theme.Space.sm) {
                    PillButton(title: "Sign up", icon: .userAdd) {
                        tracker.event("signup", properties: ["plan": "free", "source": "sample-app"])
                    }
                    PillButton(title: "Open the docs", icon: .externalLink, style: .ghost) {
                        tracker.outbound(docsUrl)
                        if let url = URL(string: docsUrl) { openURL(url) }
                    }
                }
            }

            Card(title: "Log") {
                if tracker.log.isEmpty {
                    Text("Nothing sent yet.").font(Theme.Typography.body).foregroundStyle(Theme.textMuted)
                } else {
                    VStack(spacing: 0) {
                        ForEach(tracker.log) { LogRow(entry: $0) }
                    }
                }
            }
        }
        .onAppear { tracker.screen("/events") }
    }
}
