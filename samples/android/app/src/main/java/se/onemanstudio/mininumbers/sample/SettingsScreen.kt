package se.onemanstudio.mininumbers.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun SettingsScreen(prefs: Prefs, sessionId: String, onNewSession: () -> Unit) {
    val c = Theme.colors
    var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
    var projectKey by remember { mutableStateOf(prefs.projectKey) }
    var optOut by remember { mutableStateOf(prefs.optOut) }
    LaunchedEffect(Unit) { MiniNumbers.screen("/settings") }

    ScreenLayout("Settings", "Where the events go.") {
        Card(title = "Server") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                Field("Server address", serverUrl, { serverUrl = it })
                Field("Project key", projectKey, { projectKey = it }, mono = true)
                PillButton("Save", onClick = {
                    prefs.serverUrl = serverUrl
                    prefs.projectKey = projectKey
                    MiniNumbers.configure(serverUrl, projectKey)
                })
                Text(
                    "Find the key in the dashboard under Settings → Project → Tracking ID.",
                    style = Theme.type.small,
                    color = c.textMuted,
                )
            }
        }
        Card(title = "Privacy") {
            ToggleRow("Don't track me", "While this is on, the app sends nothing.", optOut) {
                optOut = it
                prefs.optOut = it
                MiniNumbers.optOut = it
            }
        }
        Card(title = "Session") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                Text(sessionId, style = Theme.type.mono, color = c.textMuted)
                PillButton("Start a new session", icon = Icons.Refresh, primary = false, onClick = onNewSession)
            }
        }
    }
}
