import SwiftUI

struct Product: Identifiable, Equatable {
    let id: String
    let name: String
    let price: Double
    let currency: String
    let icon: Theme.Icon
    let badge: Theme.Badge

    /// "€12.00", "$24.00"; pass a negative amount for "-€12.00".
    func formatted(_ amount: Double? = nil) -> String {
        let value = amount ?? price
        let symbol = currency == "EUR" ? "€" : "$"
        return (value < 0 ? "-" : "") + symbol + String(format: "%.2f", abs(value))
    }

    static let all = [
        Product(id: "mug", name: "Mug", price: 12.00, currency: "EUR", icon: .cup, badge: .ember),
        Product(id: "notebook", name: "Notebook", price: 8.50, currency: "EUR", icon: .book, badge: .blue),
        Product(id: "t-shirt", name: "T-shirt", price: 24.00, currency: "USD", icon: .tShirt, badge: .green),
    ]
}

/// Tab 1: the product list (/shop), or the open product (/shop/{id}).
struct ShopView: View {
    @State var selected: Product?

    var body: some View {
        if let product = selected {
            ProductDetailView(product: product) { selected = nil }
        } else {
            list
        }
    }

    private var list: some View {
        Screen(title: "Corner Shop", subtitle: "Three things, none of them real.") {
            ForEach(Product.all) { product in
                Button { selected = product } label: {
                    Card {
                        HStack(spacing: Theme.Space.smPlus) {
                            IconBadge(icon: product.icon, badge: product.badge)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(product.name).font(Theme.Typography.cardTitle).foregroundStyle(Theme.textPrimary)
                                Text(product.formatted()).font(Theme.Typography.body).foregroundStyle(Theme.textSecondary)
                            }
                            Spacer()
                            RemixIcon(icon: .chevron, size: 20).foregroundStyle(Theme.textMuted)
                        }
                    }
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("product-\(product.id)")
            }
        }
        .onAppear { MiniNumbers.shared.screen("/shop") }
    }
}
