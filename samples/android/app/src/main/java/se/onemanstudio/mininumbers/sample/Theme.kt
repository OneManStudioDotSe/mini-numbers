package se.onemanstudio.mininumbers.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Every colour, size, font and icon of the app lives in this file (see SAMPLE SPEC "Design system").
// Screens and components read them from here and never write a literal of their own.

class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgTertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val success: Color,
    val danger: Color,
    val badge1: Color,
    val badge2: Color,
    val badge3: Color,
    val badge4: Color,
    val badgeTint: Float,
)

private val LightColors = AppColors(
    primary = Color(0xFFC9340F), onPrimary = Color(0xFFFFFFFF),
    bgPrimary = Color(0xFFF4F4F5), bgSecondary = Color(0xFFFFFFFF), bgTertiary = Color(0xFFEEEEF0),
    textPrimary = Color(0xFF151517), textSecondary = Color(0xFF4A4A52), textMuted = Color(0xFF6A6A73),
    border = Color(0xFFE3E3E7), success = Color(0xFF0B7A53), danger = Color(0xFFC81E4A),
    badge1 = Color(0xFFC9340F), badge2 = Color(0xFF2F7DF6), badge3 = Color(0xFF12A36D), badge4 = Color(0xFFC87A00),
    badgeTint = 0.12f,
)

private val DarkColors = AppColors(
    primary = Color(0xFFFF6E52), onPrimary = Color(0xFF190A06),
    bgPrimary = Color(0xFF0E0E0F), bgSecondary = Color(0xFF19191B), bgTertiary = Color(0xFF232326),
    textPrimary = Color(0xFFF5F5F6), textSecondary = Color(0xFFB6B6BD), textMuted = Color(0xFF9797A0),
    border = Color(0xFF29292D), success = Color(0xFF3ECF8E), danger = Color(0xFFFF6B8A),
    badge1 = Color(0xFFFF6E52), badge2 = Color(0xFF5AA7FF), badge3 = Color(0xFF3ECF8E), badge4 = Color(0xFFF5A524),
    badgeTint = 0.16f,
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val smPlus = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val full = 999.dp
}

object Sizes {
    val button = 44.dp
    val buttonIcon = 18.dp
    val badge = 40.dp
    val badgeLarge = 56.dp
    val badgeIcon = 20.dp
    val badgeIconLarge = 28.dp
    val field = 44.dp
    val border = 1.dp
    val tabIcon = 22.dp
    val chevron = 20.dp
}

private val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/** The icon font. Icons are drawn as text, one codepoint each. */
val RemixIcons = FontFamily(Font(R.font.remixicon))

class AppType(
    val title: TextStyle,
    val cardTitle: TextStyle,
    val figure: TextStyle,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val label: TextStyle,
    val small: TextStyle,
    val smallMedium: TextStyle,
    val button: TextStyle,
    val mono: TextStyle,
)

private fun style(size: Int, weight: FontWeight, family: FontFamily = Jakarta) =
    TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight, letterSpacing = 0.sp, lineHeight = (size * 1.35f).sp)

private val Type = AppType(
    title = style(28, FontWeight.Medium),
    cardTitle = style(17, FontWeight.Medium),
    figure = style(28, FontWeight.Medium),
    body = style(15, FontWeight.Normal),
    bodyMedium = style(15, FontWeight.Medium),
    label = style(13, FontWeight.Medium),
    small = style(12, FontWeight.Normal),
    smallMedium = style(12, FontWeight.Medium),
    button = style(15, FontWeight.SemiBold),
    mono = style(13, FontWeight.Normal, FontFamily.Monospace),
)

/** Remix Icon codepoints used by the app. */
object Icons {
    const val Store = ""
    const val Pulse = ""
    const val Settings = ""
    const val Cup = ""
    const val BookOpen = ""
    const val TShirt = ""
    const val ArrowLeft = ""
    const val ArrowRight = ""
    const val ExternalLink = ""
    const val UserAdd = ""
    const val ShoppingBag = ""
    const val Refund = ""
    const val Refresh = ""
}

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalType = staticCompositionLocalOf { Type }

object Theme {
    val colors: AppColors @Composable get() = LocalColors.current
    val type: AppType @Composable get() = LocalType.current
}

/** Light or dark follows the system setting. */
@Composable
fun CornerShopTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.bgPrimary, surface = c.bgSecondary)
    } else {
        lightColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.bgPrimary, surface = c.bgSecondary)
    }
    CompositionLocalProvider(LocalColors provides c, LocalType provides Type) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
