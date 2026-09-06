package org.protidhoni.lumina.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import org.protidhoni.lumina.model.ThemeMode

private val WarmPaperColorScheme = lightColorScheme(
    primary = WarmOnSurface,
    onPrimary = WarmBackground,
    secondary = GoldAccent,
    onSecondary = WarmOnSurface,
    background = WarmBackground,
    onBackground = WarmOnSurface,
    surface = WarmSurface,
    onSurface = WarmOnSurface,
    surfaceVariant = WarmSurface,
    onSurfaceVariant = WarmTextSecondary,
    outline = WarmBorder
)

private val PureWhiteColorScheme = lightColorScheme(
    primary = WhiteOnSurface,
    onPrimary = WhiteBackground,
    secondary = GoldAccent,
    onSecondary = WhiteOnSurface,
    background = WhiteBackground,
    onBackground = WhiteOnSurface,
    surface = WhiteSurface,
    onSurface = WhiteOnSurface,
    surfaceVariant = WhiteSurface,
    onSurfaceVariant = WhiteTextSecondary,
    outline = WhiteBorder
)

private val NightColorScheme = darkColorScheme(
    primary = NightOnSurface,
    onPrimary = NightBackground,
    secondary = GoldAccent,
    onSecondary = NightBackground,
    background = NightBackground,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder
)

@Composable
fun LuminaReaderTheme(
    themeMode: ThemeMode = ThemeMode.WARM_PAPER,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.WARM_PAPER -> WarmPaperColorScheme
        ThemeMode.PURE_WHITE -> PureWhiteColorScheme
        ThemeMode.NIGHT -> NightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
