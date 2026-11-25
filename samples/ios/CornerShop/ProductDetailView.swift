import SwiftUI

/// Path /shop/{id}. Buy and Refund send a purchase or refund event with the price as revenue.
struct ProductDetailView: View {
    let product: Product
    let onBack: () -> Void
    @State private var sent: String?

    var body: some View {
        Screen {
            Button(action: onBack) {
                HStack(spacing: Theme.Space.xs) {
                    RemixIcon(icon: .back, size: 20)
                    Text("Shop").font(Theme.Typography.body)
                }
                .foregroundStyle(Theme.primary)
            }
            .padding(.top, Theme.Space.lg)
            .accessibilityLabel("Shop")
            .accessibilityIdentifier("back")

            Card {
                VStack(alignment: .leading, spacing: Theme.Space.smPlus) {
                    IconBadge(icon: product.icon, badge: product.badge, large: true)
                    Text(product.name).font(Theme.Typography.screenTitle).foregroundStyle(Theme.textPrimary)
                    Text(product.formatted()).font(Theme.Typography.figure).foregroundStyle(Theme.textPrimary)
                    Text("Buying sends a purchase event with the price as revenue. Refunding sends the same amount as a negative.")
                        .font(Theme.Typography.body).foregroundStyle(Theme.textSecondary)
                }
            }

            PillButton(title: "Buy", icon: .bag) {
                MiniNumbers.shared.purchase(amount: product.price, currency: product.currency)
                sent = "Sent purchase · \(product.formatted())"
            }
            PillButton(title: "Refund", icon: .refund, style: .ghost) {
                MiniNumbers.shared.refund(amount: product.price, currency: product.currency)
                sent = "Sent refund · \(product.formatted(-product.price))"
            }
            if let sent {
                Text(sent).font(Theme.Typography.body).foregroundStyle(Theme.textMuted)
            }
        }
        .onAppear { MiniNumbers.shared.screen("/shop/\(product.id)") }
    }
}
