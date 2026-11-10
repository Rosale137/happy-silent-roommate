package io.github.rosale137.happysilentroommate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// One look only: it is an evening app, so no light theme and no dynamic colour for now.
private val AppColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    secondary = Teal,
    error = Coral,
    background = Navy,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    outline = Track,
)

@Composable
fun HappysilentroommateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
