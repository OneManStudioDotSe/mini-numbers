import SwiftUI

/// Tab 3 (/settings). Server and key live in UserDefaults under `serverUrl` and `projectKey`, so launch
/// arguments (-serverUrl X -projectKey Y) override them without any code.
struct SettingsView: View {
    static let defaultServerUrl = "http://localhost:8092"

    @ObservedObject private var tracker = MiniNumbers.shared
    @State private var serverUrl = UserDefaults.standard.string(forKey: "serverUrl") ?? defaultServerUrl
    @State private var projectKey = UserDefaults.standard.string(forKey: "projectKey") ?? ""

    var body: some View {
        Screen(title: "Settings", subtitle: "Where the events go.") {
            Card(title: "Server") {
                VStack(alignment: .leading, spacing: Theme.Space.smPlus) {
                    Field(label: "Server address", text: $serverUrl, keyboard: .URL)
                    Field(label: "Project key", text: $projectKey, monospaced: true)
                    PillButton(title: "Save") {
                        UserDefaults.standard.set(serverUrl, forKey: "serverUrl")
                        UserDefaults.standard.set(projectKey, forKey: "projectKey")
                        tracker.configure(baseUrl: serverUrl, projectKey: projectKey)
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                    Text("Find the key in the dashboard under Settings → Project → Tracking ID.")
                        .font(Theme.Typography.small).foregroundStyle(Theme.textMuted)
                }
            }

            Card(title: "Privacy") {
                ToggleRow(title: "Don't track me", text: "While this is on, the app sends nothing.", isOn: $tracker.optOut)
            }

            Card(title: "Session") {
                VStack(alignment: .leading, spacing: Theme.Space.smPlus) {
                    Text(tracker.sessionId).font(Theme.Typography.mono).foregroundStyle(Theme.textMuted)
                    PillButton(title: "Start a new session", icon: .refresh, style: .ghost) { tracker.newSession() }
                }
            }
        }
        .onAppear { tracker.screen("/settings") }
    }
}
