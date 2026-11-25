package se.onemanstudio.mininumbers.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Locale

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val currency: String,
    val icon: String,
    val badge: Int,
) {
    /** `€12.00`, `$24.00`, or `-€12.00` for a refund. */
    fun priceText(amount: Double = price): String {
        val symbol = if (currency == "EUR") "€" else "$"
        val sign = if (amount < 0) "-" else ""
        return sign + symbol + String.format(Locale.US, "%.2f", kotlin.math.abs(amount))
    }

    @Composable
    fun badgeColor(): Color = with(Theme.colors) { when (badge) { 1 -> badge1; 2 -> badge2; else -> badge3 } }
}

val Products = listOf(
    Product("mug", "Mug", 12.00, "EUR", Icons.Cup, 1),
    Product("notebook", "Notebook", 8.50, "EUR", Icons.BookOpen, 2),
    Product("t-shirt", "T-shirt", 24.00, "USD", Icons.TShirt, 3),
)

@Composable
fun ShopScreen(onOpen: (Product) -> Unit) {
    LaunchedEffect(Unit) { MiniNumbers.screen("/shop") }
    ScreenLayout("Corner Shop", "Three things, none of them real.") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Products.forEach { product ->
                Card(Modifier.clickable { onOpen(product) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(product.icon, product.badgeColor())
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(product.name, style = Theme.type.cardTitle, color = Theme.colors.textPrimary)
                            Text(product.priceText(), style = Theme.type.label, color = Theme.colors.textSecondary)
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        RemixIcon(Icons.ArrowRight, Sizes.chevron, Theme.colors.textMuted)
                    }
                }
            }
        }
    }
}
