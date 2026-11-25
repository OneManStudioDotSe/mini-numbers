package se.onemanstudio.mininumbers.sample

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Settings kept on the device. */
class Prefs(context: Context) {
    private val store = context.getSharedPreferences("corner_shop", Context.MODE_PRIVATE)
    var serverUrl: String
        get() = store.getString("serverUrl", DefaultServer) ?: DefaultServer
        set(v) = store.edit().putString("serverUrl", v).apply()
    var projectKey: String
        get() = store.getString("projectKey", "") ?: ""
        set(v) = store.edit().putString("projectKey", v).apply()
    var optOut: Boolean
        get() = store.getBoolean("optOut", false)
        set(v) = store.edit().putBoolean("optOut", v).apply()

    companion object {
        /** The emulator's name for the machine it runs on. */
        const val DefaultServer = "http://10.0.2.2:8092"
    }
}

private class Tab(val route: String, val label: String, val icon: String)

private val Tabs = listOf(
    Tab("shop", "Shop", Icons.Store),
    Tab("events", "Events", Icons.Pulse),
    Tab("settings", "Settings", Icons.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        // Developers and tests can pass the server and key on launch:
        //   adb shell am start -n <package>/.MainActivity --es serverUrl http://10.0.2.2:8092 --es projectKey KEY
        intent.getStringExtra("serverUrl")?.let { prefs.serverUrl = it }
        intent.getStringExtra("projectKey")?.let { prefs.projectKey = it }
        MiniNumbers.optOut = prefs.optOut
        MiniNumbers.configure(prefs.serverUrl, prefs.projectKey)
        setContent { CornerShopTheme { App(prefs) } }
    }
}

@Composable
private fun App(prefs: Prefs) {
    val c = Theme.colors
    val nav = rememberNavController()

    // Keep the status and navigation bar icons readable when the system theme changes (the activity is not recreated).
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    SideEffect {
        val controls = WindowCompat.getInsetsController((view.context as Activity).window, view)
        controls.isAppearanceLightStatusBars = !dark
        controls.isAppearanceLightNavigationBars = !dark
    }
    var sessionId by remember { mutableStateOf(MiniNumbers.sessionId) }

    // The heartbeat runs while the app is in the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) MiniNumbers.startHeartbeat()
            if (event == Lifecycle.Event.ON_STOP) MiniNumbers.stopHeartbeat()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(c.bgPrimary).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = "shop") {
                composable("shop") { ShopScreen(onOpen = { nav.navigate("shop/${it.id}") }) }
                composable("shop/{id}") { entry ->
                    val product = Products.first { it.id == entry.arguments?.getString("id") }
                    ProductScreen(product, onBack = { nav.popBackStack() })
                }
                composable("events") { EventsScreen(sessionId) }
                composable("settings") {
                    SettingsScreen(prefs, sessionId, onNewSession = {
                        MiniNumbers.newSession()
                        sessionId = MiniNumbers.sessionId
                    })
                }
            }
        }
        val route by nav.currentBackStackEntryAsState()
        val current = route?.destination?.route ?: "shop"
        TabBar(selected = if (current.startsWith("shop")) "shop" else current) { tab ->
            nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun TabBar(selected: String, onSelect: (Tab) -> Unit) {
    val c = Theme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bgSecondary)
            .drawBehind { drawLine(c.border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(vertical = Spacing.smPlus),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tabs.forEach { tab ->
            val color = if (tab.route == selected) c.primary else c.textMuted
            Column(
                Modifier.clickable { onSelect(tab) }.padding(horizontal = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                RemixIcon(tab.icon, Sizes.tabIcon, color)
                Text(tab.label, style = Theme.type.smallMedium, color = color)
            }
        }
    }
}
