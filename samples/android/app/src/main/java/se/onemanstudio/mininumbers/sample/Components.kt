package se.onemanstudio.mininumbers.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// The shared pieces of the UI, named as in the spec. They are not prefixed: material3's own Card is never imported.

/** Drawn as text from the icon font. */
@Composable
fun RemixIcon(code: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = code,
        modifier = modifier,
        style = TextStyle(
            fontFamily = RemixIcons,
            fontSize = with(LocalDensity.current) { size.toSp() },
            color = color,
            lineHeight = TextUnit.Unspecified,
        ),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    val c = Theme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.bgSecondary)
            .border(BorderStroke(Sizes.border, c.border), RoundedCornerShape(Radius.lg))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (title != null) Text(title, style = Theme.type.cardTitle, color = c.textPrimary)
        content()
    }
}

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = true,
) {
    val c = Theme.colors
    val background = if (primary) c.primary else c.bgTertiary
    val foreground = if (primary) c.onPrimary else c.textPrimary
    Row(
        modifier
            .fillMaxWidth()
            .height(Sizes.button)
            .clip(RoundedCornerShape(Radius.full))
            .background(background)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RemixIcon(icon, Sizes.buttonIcon, foreground)
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(label, style = Theme.type.button, color = foreground)
    }
}

@Composable
fun IconBadge(icon: String, color: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    val size = if (large) Sizes.badgeLarge else Sizes.badge
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.md))
            .background(color.copy(alpha = Theme.colors.badgeTint)),
        contentAlignment = Alignment.Center,
    ) {
        RemixIcon(icon, if (large) Sizes.badgeIconLarge else Sizes.badgeIcon, color)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Theme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.bgSecondary)
            .border(BorderStroke(Sizes.border, c.border), RoundedCornerShape(Radius.lg))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = Theme.type.label, color = c.textSecondary)
        Text(value, style = Theme.type.figure, color = c.textPrimary, maxLines = 1)
    }
}

@Composable
fun LogRow(entry: MiniNumbers.LogEntry, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val statusColor = when (entry.status) {
        "202" -> c.success
        "not sent", "no key" -> c.textMuted
        else -> c.danger
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            entry.type,
            style = Theme.type.smallMedium,
            color = c.textPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.full))
                .background(c.bgTertiary)
                .padding(horizontal = Spacing.smPlus, vertical = Spacing.xs),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            entry.label,
            style = Theme.type.body,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(entry.time, style = Theme.type.small, color = c.textMuted)
        Spacer(Modifier.width(Spacing.sm))
        Text(entry.status, style = Theme.type.label, color = statusColor)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
) {
    val c = Theme.colors
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = Theme.type.label, color = c.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = (if (mono) Theme.type.mono else Theme.type.body).copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.field)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Sizes.field)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(c.bgTertiary)
                        .border(
                            BorderStroke(Sizes.border, if (focused) c.primary else c.border),
                            RoundedCornerShape(Radius.md),
                        )
                        .padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

@Composable
fun ToggleRow(title: String, text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = Theme.type.bodyMedium, color = c.textPrimary)
            Text(text, style = Theme.type.small, color = c.textMuted)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.primary,
                checkedThumbColor = c.onPrimary,
                checkedBorderColor = c.primary,
                uncheckedTrackColor = c.bgTertiary,
                uncheckedThumbColor = c.textMuted,
                uncheckedBorderColor = c.border,
            ),
        )
    }
}

/** A scrolling column on the screen background: the layout every tab shares. */
@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.bgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        content()
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** [ScreenColumn] with the title block (lg above, md below) in front. */
@Composable
fun ScreenLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    val c = Theme.colors
    ScreenColumn {
        Column(Modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = Theme.type.title, color = c.textPrimary)
            Text(subtitle, style = Theme.type.body, color = c.textSecondary)
        }
        content()
    }
}
