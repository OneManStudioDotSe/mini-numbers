package se.onemanstudio.mininumbers.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ProductScreen(product: Product, onBack: () -> Unit) {
    val c = Theme.colors
    var sent by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(product.id) { MiniNumbers.screen("/shop/${product.id}") }

    ScreenColumn {
        Row(
            Modifier
                .padding(top = Spacing.lg)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemixIcon(Icons.ArrowLeft, Sizes.chevron, c.primary)
            Spacer(Modifier.width(Spacing.xs))
            Text("Shop", style = Theme.type.bodyMedium, color = c.primary)
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                IconBadge(product.icon, product.badgeColor(), large = true)
                Text(product.name, style = Theme.type.title, color = c.textPrimary)
                Text(product.priceText(), style = Theme.type.figure, color = c.textPrimary)
                Text(
                    "Buying sends a purchase event with the price as revenue. Refunding sends the same amount as a negative.",
                    style = Theme.type.body,
                    color = c.textSecondary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PillButton("Buy", icon = Icons.ShoppingBag, onClick = {
                MiniNumbers.purchase(product.price, product.currency)
                sent = "Sent purchase · ${product.priceText()}"
            })
            PillButton("Refund", icon = Icons.Refund, primary = false, onClick = {
                MiniNumbers.refund(product.price, product.currency)
                sent = "Sent refund · ${product.priceText(-product.price)}"
            })
            sent?.let { Text(it, style = Theme.type.small, color = c.textMuted) }
        }
    }
}
