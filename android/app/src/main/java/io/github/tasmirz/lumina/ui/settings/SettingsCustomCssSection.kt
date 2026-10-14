package io.github.tasmirz.lumina.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.CustomTextureData
import io.github.tasmirz.lumina.model.CustomThemeData
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.ui.components.TexturePreviewCard

enum class ColorPickerTarget { BACKGROUND, TEXT, ACCENT, ORB }

data class ThemeStarterTemplate(
    val name: String,
    val bg: Color,
    val text: Color,
    val accent: Color
)

fun parseCssToTheme(css: String): ParsedThemeResult {
    var name = ""
    var bg: Color? = null
    var text: Color? = null
    var accent: Color? = null

    val nameRegex = Regex("""(?:--name|--theme-name|--lumina-theme-name):\s*["']?([^"';\n]+)["']?""", RegexOption.IGNORE_CASE)
    nameRegex.find(css)?.let { name = it.groupValues[1].trim() }

    fun extractHexColor(pattern: Regex): Color? {
        pattern.find(css)?.let { match ->
            val hex = match.groupValues[1]
            return parseHex(hex)
        }
        return null
    }

    bg = extractHexColor(Regex("""(?:--bg|--reader-bg|background-color|background):\s*(#[0-9a-fA-F]{3,8})""", RegexOption.IGNORE_CASE))
    text = extractHexColor(Regex("""(?:--text|--reader-text|--foreground|color):\s*(#[0-9a-fA-F]{3,8})""", RegexOption.IGNORE_CASE))
    accent = extractHexColor(Regex("""(?:--accent|--reader-accent|--primary|accent-color):\s*(#[0-9a-fA-F]{3,8})""", RegexOption.IGNORE_CASE))

    return ParsedThemeResult(name, bg, text, accent)
}

data class ParsedThemeResult(
    val name: String,
    val bg: Color?,
    val text: Color?,
    val accent: Color?
)

fun parseHex(hex: String): Color? {
    return try {
        val clean = hex.removePrefix("#")
        when (clean.length) {
            3 -> {
                val r = clean.substring(0, 1).repeat(2).toInt(16)
                val g = clean.substring(1, 2).repeat(2).toInt(16)
                val b = clean.substring(2, 3).repeat(2).toInt(16)
                Color(r, g, b)
            }
            6 -> {
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                val a = clean.substring(0, 2).toInt(16)
                val r = clean.substring(2, 4).toInt(16)
                val g = clean.substring(4, 6).toInt(16)
                val b = clean.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun generateCssSnippet(
    name: String,
    bg: Color,
    text: Color,
    accent: Color,
    wallpaper: String
): String {
    fun toHex(c: Color): String = String.format("#%02X%02X%02X", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    return """
/* Lumina EPUB Reader Theme: $name */
:root {
  --lumina-theme-name: "$name";
  --reader-bg: ${toHex(bg)};
  --reader-text: ${toHex(text)};
  --reader-accent: ${toHex(accent)};
  --reader-wallpaper: "$wallpaper";
}
""".trimIndent()
}

/**
 * Section 1: Custom CSS Themes, Palette Builder, CSS Import/Export, and Wallpaper.
 */
@Composable
fun SettingsCustomCssSection(
    repository: BookRepository?,
    customThemes: List<CustomThemeData>,
    onThemeFamilyChange: (ThemeFamily) -> Unit,
    onImportCss: () -> Unit,
    onExportCss: (String) -> Unit,
    onPickWallpaper: () -> Unit,
    customBgUri: String,
    onCustomBgUriChange: (String) -> Unit,
    onPickTexture: () -> Unit,
    backgroundTexture: BackgroundTexture,
    onBackgroundTextureChange: (BackgroundTexture) -> Unit,
    customTextures: List<CustomTextureData>,
    selectedCustomTextureId: String?,
    onRenameTexture: (CustomTextureData) -> Unit,
    onDeleteTexture: (CustomTextureData) -> Unit,
    onRenameTheme: (CustomThemeData) -> Unit,
    dynamicRollingTexture: Boolean,
    onDynamicRollingTextureChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    var customThemeName by remember { mutableStateOf("") }
    var customBgColor by remember { mutableStateOf(Color(0xFFFAF6EE)) }
    var customTextColor by remember { mutableStateOf(Color(0xFF24201D)) }
    var customAccentColor by remember { mutableStateOf(Color(0xFFC86446)) }
    var colorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }

    if (colorPickerTarget != null) {
        val target = colorPickerTarget!!
        val (pickerTitle, initialColor) = when (target) {
            ColorPickerTarget.BACKGROUND -> "Background Color" to customBgColor
            ColorPickerTarget.TEXT -> "Text Color" to customTextColor
            ColorPickerTarget.ACCENT -> "Accent Color" to customAccentColor
            ColorPickerTarget.ORB -> "Color" to customAccentColor
        }
        LuminaColorPickerDialog(
            title = pickerTitle,
            initialColor = initialColor,
            onDismiss = { colorPickerTarget = null },
            onColorConfirmed = { confirmed ->
                when (target) {
                    ColorPickerTarget.BACKGROUND -> customBgColor = confirmed
                    ColorPickerTarget.TEXT -> customTextColor = confirmed
                    ColorPickerTarget.ACCENT -> customAccentColor = confirmed
                    ColorPickerTarget.ORB -> {}
                }
                colorPickerTarget = null
            }
        )
    }

    CollapsibleCard(
        icon = Icons.Outlined.ColorLens,
        title = "Custom CSS Themes & Wallpaper",
        subtitle = "${customThemes.size} custom themes • CSS Import/Export"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Custom Palette Builder",
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Design color schemes with background, text, and accent styling, or load/export CSS files.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportCss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import CSS", fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val themeName = customThemeName.ifBlank { "Custom Theme" }
                            val cssSnippet = generateCssSnippet(
                                name = themeName,
                                bg = customBgColor,
                                text = customTextColor,
                                accent = customAccentColor,
                                wallpaper = customBgUri.ifBlank { "none" }
                            )
                            onExportCss(cssSnippet)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSS", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Quick Reading Starter Templates
            Text(
                text = "Reading Palette Presets",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val templates = listOf(
                    ThemeStarterTemplate("Warm Sepia", Color(0xFFF4ECD8), Color(0xFF3B2E25), Color(0xFFC86446)),
                    ThemeStarterTemplate("Matcha Calm", Color(0xFFEFF4ED), Color(0xFF1C281F), Color(0xFF5B8A68)),
                    ThemeStarterTemplate("Nord Slate", Color(0xFF181D24), Color(0xFFD6DBE0), Color(0xFF5E81AC)),
                    ThemeStarterTemplate("Velvet Dark", Color(0xFF211B17), Color(0xFFEDE6D6), Color(0xFFD4A017)),
                    ThemeStarterTemplate("High Contrast", Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFD600)),
                    ThemeStarterTemplate("Colorblind Safe", Color(0xFFF6F6F2), Color(0xFF101828), Color(0xFFD55E00))
                )
                templates.forEach { tmpl ->
                    AssistChip(
                        onClick = {
                            customThemeName = tmpl.name
                            customBgColor = tmpl.bg
                            customTextColor = tmpl.text
                            customAccentColor = tmpl.accent
                        },
                        label = { Text(tmpl.name, fontSize = 10.5.sp) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(tmpl.bg)
                                    .border(0.5.dp, Color.Gray, CircleShape)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = customThemeName,
                onValueChange = { customThemeName = it },
                placeholder = { Text("Theme Name (e.g. Warm Sepia / Nord)", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Background Color Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Background Color", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
                TextButton(
                    onClick = { colorPickerTarget = ColorPickerTarget.BACKGROUND },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Custom Hex", fontSize = 10.5.sp)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            ColorSwatchScrollRow(
                selectedColor = customBgColor,
                onColorSelected = { customBgColor = it },
                presets = listOf(
                    Color(0xFFFAF6EE), Color(0xFFF4ECD8), Color(0xFFEFE8D8), Color(0xFFEFF4ED),
                    Color(0xFFFBF2EE), Color(0xFF1E1C1A), Color(0xFF181D24), Color(0xFF211B17),
                    Color(0xFF141D17), Color(0xFF131722), Color(0xFF000000), Color(0xFFFFFFFF)
                ),
                onOpenPicker = { colorPickerTarget = ColorPickerTarget.BACKGROUND }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Text Color Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Text Color", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
                TextButton(
                    onClick = { colorPickerTarget = ColorPickerTarget.TEXT },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Custom Hex", fontSize = 10.5.sp)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            ColorSwatchScrollRow(
                selectedColor = customTextColor,
                onColorSelected = { customTextColor = it },
                presets = listOf(
                    Color(0xFF24201D), Color(0xFF33302C), Color(0xFF3B2E25), Color(0xFF1C281F),
                    Color(0xFFEDE6D6), Color(0xFFE2DAD0), Color(0xFFD6DBE0), Color(0xFFD8CDBA),
                    Color(0xFFFFE8B2), Color(0xFF000000), Color(0xFFFFFFFF)
                ),
                onOpenPicker = { colorPickerTarget = ColorPickerTarget.TEXT }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Accent Color Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accent Color", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
                TextButton(
                    onClick = { colorPickerTarget = ColorPickerTarget.ACCENT },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Custom Hex", fontSize = 10.5.sp)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            ColorSwatchScrollRow(
                selectedColor = customAccentColor,
                onColorSelected = { customAccentColor = it },
                presets = listOf(
                    Color(0xFFC86446), Color(0xFFD4A017), Color(0xFF5B8A68), Color(0xFFBD6B78),
                    Color(0xFF5E81AC), Color(0xFFCC7A24), Color(0xFFD55E00), Color(0xFF0072B2),
                    Color(0xFFFFD600)
                ),
                onOpenPicker = { colorPickerTarget = ColorPickerTarget.ACCENT }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Preview Box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = customBgColor,
                border = BorderStroke(1.dp, customAccentColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = customThemeName.ifBlank { "Chapter I. The Celestial Journey" },
                        color = customAccentColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The quiet hum of the night gave way to golden dawn across the endless pages.",
                        color = customTextColor,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val name = customThemeName.trim().ifBlank { "Custom Theme ${customThemes.size + 1}" }
                    repository?.saveCustomTheme(
                        name = name,
                        bg = customBgColor.toArgb().toLong(),
                        text = customTextColor.toArgb().toLong(),
                        accent = customAccentColor.toArgb().toLong()
                    )
                    Toast.makeText(context, "Theme '$name' saved to SQLite", Toast.LENGTH_SHORT).show()
                    customThemeName = ""
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Theme to Library", fontSize = 12.sp)
            }

            // Saved Themes List
            if (customThemes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Saved Themes (${customThemes.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    customThemes.forEach { th ->
                        val bg = Color(th.bgColor.toInt())
                        val txt = Color(th.textColor.toInt())
                        val acc = Color(th.accentColor.toInt())
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(bg))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(txt))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(acc))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = th.name,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = { onRenameTheme(th) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename Theme", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            val css = generateCssSnippet(
                                                name = th.name,
                                                bg = bg,
                                                text = txt,
                                                accent = acc,
                                                wallpaper = customBgUri.ifBlank { "none" }
                                            )
                                            onExportCss(css)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Export CSS", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            repository?.applyCustomTheme(th)
                                            onThemeFamilyChange(ThemeFamily.CUSTOM)
                                            Toast.makeText(context, "Applied '${th.name}'", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Apply", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            repository?.deleteCustomTheme(th.id)
                                            Toast.makeText(context, "Deleted '${th.name}'", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Background Textures
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Background Surface Textures",
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                )
                OutlinedButton(
                    onClick = onPickTexture,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load Texture", fontSize = 11.sp, fontWeight = FontWeight.Normal)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            val isDarkSettingsTab = isSystemInDarkTheme()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BackgroundTexture.NONE to "Clean",
                    BackgroundTexture.GRAIN to "Paper Grain",
                    BackgroundTexture.PARCHMENT to "Parchment",
                    BackgroundTexture.LINEN to "Linen",
                    BackgroundTexture.CANVAS to "Canvas",
                    BackgroundTexture.KRAFT to "Kraft",
                    BackgroundTexture.RULED_FINE to "Fine Lined",
                    BackgroundTexture.RULED_WIDE to "Wide Lined",
                    BackgroundTexture.RULED_GRID to "Grid Lined"
                ).forEach { (tex, label) ->
                    val isSel = backgroundTexture == tex
                    TexturePreviewCard(
                        label = label,
                        isSelected = isSel,
                        texture = tex,
                        isDark = isDarkSettingsTab,
                        onClick = { onBackgroundTextureChange(tex) }
                    )
                }
                customTextures.forEach { customTex ->
                    val isSel = backgroundTexture == BackgroundTexture.CUSTOM && selectedCustomTextureId == customTex.id
                    TexturePreviewCard(
                        label = customTex.name,
                        isSelected = isSel,
                        texture = BackgroundTexture.CUSTOM,
                        isDark = isDarkSettingsTab,
                        onClick = { repository?.selectCustomTexture(customTex.id) }
                    )
                }
            }

            if (customTextures.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Custom Surface Textures",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    customTextures.forEach { tex ->
                        val isSelected = backgroundTexture == BackgroundTexture.CUSTOM && selectedCustomTextureId == tex.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { repository?.selectCustomTexture(tex.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.Texture,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = tex.name,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 12.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        "Active",
                                                        fontSize = 8.5.sp,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${if (tex.isTiled) "Seamless Tiled" else "Cover Fit"} • ${(tex.opacity * 100).toInt()}% opacity",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onRenameTexture(tex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "Rename",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteTexture(tex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dynamic Rolling Texture", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                    Text("Roll background textures when scrolling or swiping pages", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = dynamicRollingTexture,
                    onCheckedChange = onDynamicRollingTextureChange
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Custom Wallpaper", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
                    if (customBgUri.isNotBlank()) {
                        Text("Image active", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row {
                    if (customBgUri.isNotBlank()) {
                        TextButton(onClick = { onCustomBgUriChange("") }) {
                            Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    OutlinedButton(
                        onClick = onPickWallpaper,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (customBgUri.isNotBlank()) "Change" else "Choose Image", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
