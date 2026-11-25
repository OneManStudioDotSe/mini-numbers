import SwiftUI

@main
struct CornerShopApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let defaults = UserDefaults.standard
        MiniNumbers.shared.configure(
            baseUrl: defaults.string(forKey: "serverUrl") ?? SettingsView.defaultServerUrl,
            projectKey: defaults.string(forKey: "projectKey") ?? "")
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .onChange(of: scenePhase) { _, phase in
            // The heartbeat runs only while the app is in the foreground.
            if phase == .active { MiniNumbers.shared.startHeartbeat() } else { MiniNumbers.shared.stopHeartbeat() }
        }
    }
}

enum Tab: CaseIterable {
    case shop, events, settings

    var title: String {
        switch self { case .shop: "Shop"; case .events: "Events"; case .settings: "Settings" }
    }
    var icon: Theme.Icon {
        switch self { case .shop: .store; case .events: .pulse; case .settings: .settings }
    }
}

struct RootView: View {
    @State private var tab = Tab.shop
    private var initialProduct: Product?

    init() {
        #if DEBUG
        // For screenshots only: -startScreen shop|detail|events|settings
        switch UserDefaults.standard.string(forKey: "startScreen") {
        case "detail": _tab = State(initialValue: .shop); initialProduct = Product.all.first
        case "events": _tab = State(initialValue: .events)
        case "settings": _tab = State(initialValue: .settings)
        default: break
        }
        #endif
    }

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch tab {
                case .shop: ShopView(selected: initialProduct)
                case .events: EventsView()
                case .settings: SettingsView()
                }
            }
            tabBar
        }
        .background(Theme.bgPrimary)
        .ignoresSafeArea(.keyboard)
    }

    private var tabBar: some View {
        HStack {
            ForEach(Tab.allCases, id: \.self) { item in
                Button { tab = item } label: {
                    VStack(spacing: Theme.Space.xs) {
                        RemixIcon(icon: item.icon, size: 24)
                        Text(item.title).font(Theme.Typography.label)
                    }
                    .foregroundStyle(tab == item ? Theme.primary : Theme.textMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, Theme.Space.smPlus)
                    .padding(.bottom, Theme.Space.sm)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.title)
                .accessibilityAddTraits(tab == item ? .isSelected : [])
                .accessibilityIdentifier("tab-\(item.title.lowercased())")
            }
        }
        .background(Theme.bgSecondary.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Rectangle().fill(Theme.border).frame(height: 1) }
    }
}
