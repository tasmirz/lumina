package io.github.tasmirz.lumina.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.model.ThemeMode
import io.github.tasmirz.lumina.model.ThemeVariant

private val WarmPaperColorScheme = lightColorScheme(
    primary = WarmOnSurface,
    onPrimary = WarmBackground,
    primaryContainer = WarmSurface,
    onPrimaryContainer = WarmOnSurface,
    secondary = GoldAccent,
    onSecondary = WarmOnSurface,
    secondaryContainer = WarmSurface,
    onSecondaryContainer = WarmTextSecondary,
    background = WarmBackground,
    onBackground = WarmOnSurface,
    surface = WarmSurface,
    onSurface = WarmOnSurface,
    surfaceVariant = WarmSurface,
    onSurfaceVariant = WarmTextSecondary,
    outline = WarmBorder,
    outlineVariant = WarmBorder
)

private val PureWhiteColorScheme = lightColorScheme(
    primary = WhiteOnSurface,
    onPrimary = WhiteBackground,
    primaryContainer = WhiteSurface,
    onPrimaryContainer = WhiteOnSurface,
    secondary = GoldAccent,
    onSecondary = WhiteOnSurface,
    secondaryContainer = WhiteSurface,
    onSecondaryContainer = WhiteTextSecondary,
    background = WhiteBackground,
    onBackground = WhiteOnSurface,
    surface = WhiteSurface,
    onSurface = WhiteOnSurface,
    surfaceVariant = WhiteSurface,
    onSurfaceVariant = WhiteTextSecondary,
    outline = WhiteBorder,
    outlineVariant = WhiteBorder
)

private val NightColorScheme = darkColorScheme(
    primary = NightOnSurface,
    onPrimary = NightBackground,
    primaryContainer = NightSurface,
    onPrimaryContainer = NightOnSurface,
    secondary = GoldAccent,
    onSecondary = NightBackground,
    secondaryContainer = NightSurface,
    onSecondaryContainer = NightTextSecondary,
    background = NightBackground,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder,
    outlineVariant = NightBorder
)

private val PaperDarkColorScheme = darkColorScheme(
    primary = PaperDarkOnSurface,
    onPrimary = PaperDarkBackground,
    primaryContainer = PaperDarkSurface,
    onPrimaryContainer = PaperDarkOnSurface,
    secondary = GoldAccent,
    onSecondary = PaperDarkBackground,
    secondaryContainer = PaperDarkSurface,
    onSecondaryContainer = PaperDarkTextSecondary,
    background = PaperDarkBackground,
    onBackground = PaperDarkOnSurface,
    surface = PaperDarkSurface,
    onSurface = PaperDarkOnSurface,
    surfaceVariant = PaperDarkSurface,
    onSurfaceVariant = PaperDarkTextSecondary,
    outline = PaperDarkBorder,
    outlineVariant = PaperDarkBorder
)

private val ForestLightColorScheme = lightColorScheme(
    primary = ForestLightOnSurface,
    onPrimary = ForestLightBackground,
    primaryContainer = ForestLightSurface,
    onPrimaryContainer = ForestLightOnSurface,
    secondary = GoldAccent,
    onSecondary = ForestLightOnSurface,
    secondaryContainer = ForestLightSurface,
    onSecondaryContainer = ForestLightTextSecondary,
    background = ForestLightBackground,
    onBackground = ForestLightOnSurface,
    surface = ForestLightSurface,
    onSurface = ForestLightOnSurface,
    surfaceVariant = ForestLightSurface,
    onSurfaceVariant = ForestLightTextSecondary,
    outline = ForestLightBorder,
    outlineVariant = ForestLightBorder
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = ForestDarkOnSurface,
    onPrimary = ForestDarkBackground,
    primaryContainer = ForestDarkSurface,
    onPrimaryContainer = ForestDarkOnSurface,
    secondary = GoldAccent,
    onSecondary = ForestDarkBackground,
    secondaryContainer = ForestDarkSurface,
    onSecondaryContainer = ForestDarkTextSecondary,
    background = ForestDarkBackground,
    onBackground = ForestDarkOnSurface,
    surface = ForestDarkSurface,
    onSurface = ForestDarkOnSurface,
    surfaceVariant = ForestDarkSurface,
    onSurfaceVariant = ForestDarkTextSecondary,
    outline = ForestDarkBorder,
    outlineVariant = ForestDarkBorder
)

private val ParchmentLightColorScheme = lightColorScheme(
    primary = Color(0xFF2A2118),
    onPrimary = Color(0xFFF5EEDB),
    primaryContainer = Color(0xFFE8DECA),
    onPrimaryContainer = Color(0xFF2A2118),
    secondary = Color(0xFFC9882C),
    onSecondary = Color(0xFFF5EEDB),
    secondaryContainer = Color(0xFFE8DECA),
    onSecondaryContainer = Color(0xFF6B5B4D),
    background = Color(0xFFF5EEDB),
    onBackground = Color(0xFF2A2118),
    surface = Color(0xFFEFE6D1),
    onSurface = Color(0xFF2A2118),
    surfaceVariant = Color(0xFFE8DECA),
    onSurfaceVariant = Color(0xFF6B5B4D),
    outline = Color(0x332A2118),
    outlineVariant = Color(0x222A2118)
)

private val ParchmentDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8DCBE),
    onPrimary = Color(0xFF211B14),
    primaryContainer = Color(0xFF332B20),
    onPrimaryContainer = Color(0xFFE8DCBE),
    secondary = Color(0xFFD89E48),
    onSecondary = Color(0xFF211B14),
    secondaryContainer = Color(0xFF332B20),
    onSecondaryContainer = Color(0xFFB5A68E),
    background = Color(0xFF211B14),
    onBackground = Color(0xFFE8DCBE),
    surface = Color(0xFF2A231A),
    onSurface = Color(0xFFE8DCBE),
    surfaceVariant = Color(0xFF332B20),
    onSurfaceVariant = Color(0xFFB5A68E),
    outline = Color(0x33E8DCBE),
    outlineVariant = Color(0x22E8DCBE)
)

private val LinenLightColorScheme = lightColorScheme(
    primary = Color(0xFF242321),
    onPrimary = Color(0xFFECE7DF),
    primaryContainer = Color(0xFFDDD7CD),
    onPrimaryContainer = Color(0xFF242321),
    secondary = Color(0xFF5E6F5C),
    onSecondary = Color(0xFFECE7DF),
    secondaryContainer = Color(0xFFDDD7CD),
    onSecondaryContainer = Color(0xFF63615D),
    background = Color(0xFFECE7DF),
    onBackground = Color(0xFF242321),
    surface = Color(0xFFE5DFD4),
    onSurface = Color(0xFF242321),
    surfaceVariant = Color(0xFFDDD7CD),
    onSurfaceVariant = Color(0xFF63615D),
    outline = Color(0x33242321),
    outlineVariant = Color(0x22242321)
)

private val LinenDarkColorScheme = darkColorScheme(
    primary = Color(0xFFDDD8CF),
    onPrimary = Color(0xFF1B1B19),
    primaryContainer = Color(0xFF2C2C28),
    onPrimaryContainer = Color(0xFFDDD8CF),
    secondary = Color(0xFF8F9F8D),
    onSecondary = Color(0xFF1B1B19),
    secondaryContainer = Color(0xFF2C2C28),
    onSecondaryContainer = Color(0xFFA8A399),
    background = Color(0xFF1B1B19),
    onBackground = Color(0xFFDDD8CF),
    surface = Color(0xFF242421),
    onSurface = Color(0xFFDDD8CF),
    surfaceVariant = Color(0xFF2C2C28),
    onSurfaceVariant = Color(0xFFA8A399),
    outline = Color(0x33DDD8CF),
    outlineVariant = Color(0x22DDD8CF)
)

private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF003899),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E2E2),
    onSecondaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF0F0F0),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE2E2E2),
    onSurfaceVariant = Color(0xFF1A1A1A),
    outline = Color(0xFF000000),
    outlineVariant = Color(0x44000000)
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFD600),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = Color(0xFFF0F0F0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFF0F0F0),
    outline = Color(0xFFFFFFFF),
    outlineVariant = Color(0x44FFFFFF)
)

private val ColorblindLightColorScheme = lightColorScheme(
    primary = Color(0xFF101828),
    onPrimary = Color(0xFFF6F6F2),
    primaryContainer = Color(0xFFDFDFD6),
    onPrimaryContainer = Color(0xFF101828),
    secondary = Color(0xFFD55E00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFDFD6),
    onSecondaryContainer = Color(0xFF344054),
    background = Color(0xFFF6F6F2),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFEBEBE4),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFDFDFD6),
    onSurfaceVariant = Color(0xFF344054),
    outline = Color(0x44101828),
    outlineVariant = Color(0x22101828)
)

private val ColorblindDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0F4F8),
    onPrimary = Color(0xFF12161F),
    primaryContainer = Color(0xFF232D3F),
    onPrimaryContainer = Color(0xFFF0F4F8),
    secondary = Color(0xFFE69F00),
    onSecondary = Color(0xFF12161F),
    secondaryContainer = Color(0xFF232D3F),
    onSecondaryContainer = Color(0xFFCBD5E1),
    background = Color(0xFF12161F),
    onBackground = Color(0xFFF0F4F8),
    surface = Color(0xFF1A212E),
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = Color(0xFF232D3F),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0x44F0F4F8),
    outlineVariant = Color(0x22F0F4F8)
)

@Composable
fun LuminaReaderTheme(
    themeFamily: ThemeFamily = ThemeFamily.PAPER,
    themeVariant: ThemeVariant = ThemeVariant.LIGHT,
    customBgColor: Long = 0xFF1C1917L,
    customTextColor: Long = 0xFFE7E5E4L,
    customAccentColor: Long = 0xFFD4AF37L,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeVariant) {
        ThemeVariant.DARK -> true
        ThemeVariant.LIGHT -> false
        ThemeVariant.SYSTEM -> systemDark
    }

    val colorScheme = when (themeFamily) {
        ThemeFamily.PAPER -> {
            if (isDark) PaperDarkColorScheme else WarmPaperColorScheme
        }
        ThemeFamily.MODERN -> {
            if (isDark) NightColorScheme else PureWhiteColorScheme
        }
        ThemeFamily.FOREST -> {
            if (isDark) ForestDarkColorScheme else ForestLightColorScheme
        }
        ThemeFamily.PARCHMENT -> {
            if (isDark) ParchmentDarkColorScheme else ParchmentLightColorScheme
        }
        ThemeFamily.LINEN -> {
            if (isDark) LinenDarkColorScheme else LinenLightColorScheme
        }
        ThemeFamily.HIGH_CONTRAST -> {
            if (isDark) HighContrastDarkColorScheme else HighContrastLightColorScheme
        }
        ThemeFamily.COLORBLIND -> {
            if (isDark) ColorblindDarkColorScheme else ColorblindLightColorScheme
        }
        ThemeFamily.CUSTOM -> {
            val bg = Color(customBgColor)
            val fg = Color(customTextColor)
            val accent = Color(customAccentColor)
            val surface = bg
            if (isDark) {
                darkColorScheme(
                    primary = fg,
                    onPrimary = bg,
                    primaryContainer = surface,
                    onPrimaryContainer = fg,
                    secondary = accent,
                    onSecondary = bg,
                    secondaryContainer = surface,
                    onSecondaryContainer = fg.copy(alpha = 0.7f),
                    background = bg,
                    onBackground = fg,
                    surface = surface,
                    onSurface = fg,
                    surfaceVariant = surface,
                    onSurfaceVariant = fg.copy(alpha = 0.7f),
                    outline = fg.copy(alpha = 0.25f)
                )
            } else {
                lightColorScheme(
                    primary = fg,
                    onPrimary = bg,
                    primaryContainer = surface,
                    onPrimaryContainer = fg,
                    secondary = accent,
                    onSecondary = fg,
                    secondaryContainer = surface,
                    onSecondaryContainer = fg.copy(alpha = 0.7f),
                    background = bg,
                    onBackground = fg,
                    surface = surface,
                    onSurface = fg,
                    surfaceVariant = surface,
                    onSurfaceVariant = fg.copy(alpha = 0.7f),
                    outline = fg.copy(alpha = 0.25f)
                )
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun LuminaReaderTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val (family, variant) = when (themeMode) {
        ThemeMode.WARM_PAPER -> ThemeFamily.PAPER to ThemeVariant.LIGHT
        ThemeMode.PURE_WHITE -> ThemeFamily.MODERN to ThemeVariant.LIGHT
        ThemeMode.NIGHT -> ThemeFamily.MODERN to ThemeVariant.DARK
    }
    LuminaReaderTheme(themeFamily = family, themeVariant = variant, content = content)
}
