package se.onemanstudio.mininumbers.sample

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private const val DocsUrl = "https://github.com/OneManStudioDotSe/mini-numbers"

@Composable
fun EventsScreen(sessionId: String) {
    val context = LocalContext.current
    val log by MiniNumbers.log.collectAsState()
    val sent by MiniNumbers.sent.collectAsState()
    LaunchedEffect(Unit) { MiniNumbers.screen("/events") }

    ScreenLayout("Events", "Tap a button, watch it go.") {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile("Sent", sent.toString(), Modifier.weight(1f))
            StatTile("Session", sessionId.take(8), Modifier.weight(1f))
        }
        Card(title = "Try it") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PillButton("Sign up", icon = Icons.UserAdd, onClick = {
                    MiniNumbers.event("signup", mapOf("plan" to "free", "source" to "sample-app"))
                })
                PillButton("Open the docs", icon = Icons.ExternalLink, primary = false, onClick = {
                    MiniNumbers.outbound(DocsUrl)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DocsUrl)))
                })
            }
        }
        Card(title = "Log") {
            if (log.isEmpty()) {
                Text("Nothing sent yet.", style = Theme.type.body, color = Theme.colors.textMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                    log.forEach { LogRow(it) }
                }
            }
        }
    }
}
