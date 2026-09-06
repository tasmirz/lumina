package org.protidhoni.lumina.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.protidhoni.lumina.model.Bookmark
import org.protidhoni.lumina.model.HighlightColor
import org.protidhoni.lumina.model.ThemeFamily
import org.protidhoni.lumina.model.ThemeMode
import org.protidhoni.lumina.model.ThemeVariant
import org.protidhoni.lumina.model.TypefaceMode
import org.protidhoni.lumina.model.WordDefinition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
    definition: WordDefinition?,
    onDismiss: () -> Unit
) {
    if (definition == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dictionary Lookup",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = definition.word,
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (definition.phonetic.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = definition.phonetic,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = definition.partOfSpeech.uppercase(),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = definition.definition,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (definition.example.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "“${definition.example}”",
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    onNavigate: (Bookmark) -> Unit,
    onDelete: (Long) -> Unit,
    onShare: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Highlights & Bookmarks (${bookmarks.size})",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onShare != null && bookmarks.isNotEmpty()) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share Highlights", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved highlights yet. Select any text to highlight or bookmark.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bookmarks) { mark ->
                        val markColor = when (mark.color) {
                            HighlightColor.GOLD -> Color(0xFFD4AF37)
                            HighlightColor.ROSE -> Color(0xFFE5B7B7)
                            HighlightColor.SAGE -> Color(0xFFB2C2B2)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(mark) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${mark.bookTitle} • ${mark.chapter}",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    IconButton(
                                        onClick = { onDelete(mark.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(36.dp)
                                            .background(markColor, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "“${mark.quote}”",
                                        fontFamily = FontFamily.Serif,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (mark.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Note: ${mark.note}",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = mark.timestamp,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    typeface: TypefaceMode,
    onTypefaceChange: (TypefaceMode) -> Unit,
    quickFonts: Set<TypefaceMode> = setOf(TypefaceMode.SERIF, TypefaceMode.SANS),
    themeFamily: ThemeFamily = ThemeFamily.PAPER,
    onThemeFamilyChange: (ThemeFamily) -> Unit = {},
    themeVariant: ThemeVariant = ThemeVariant.LIGHT,
    onThemeVariantChange: (ThemeVariant) -> Unit = {},
    quickThemes: Set<ThemeFamily> = setOf(ThemeFamily.PAPER, ThemeFamily.MODERN),
    themeMode: ThemeMode = ThemeMode.WARM_PAPER,
    onThemeChange: (ThemeMode) -> Unit = {},
    showAssistant: Boolean = true,
    onToggleAssistant: (Boolean) -> Unit = {},
    onOpenAdvancedSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading Settings",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Font Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Text Size", fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Normal)
                Text("${fontSize}sp", fontFamily = FontFamily.SansSerif, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { if (fontSize > 14) onFontSizeChange(fontSize - 1) },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("A-", fontFamily = FontFamily.SansSerif) }
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { onFontSizeChange(it.toInt()) },
                    valueRange = 14f..30f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                OutlinedButton(
                    onClick = { if (fontSize < 30) onFontSizeChange(fontSize + 1) },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("A+", fontFamily = FontFamily.SansSerif) }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Typeface - displays user-selected quickFonts (or fallback to Serif and Sans)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Typeface", fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Normal)
                Text(
                    text = "${quickFonts.size} available",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val fontsToShow = if (quickFonts.isNotEmpty()) quickFonts.toList() else listOf(TypefaceMode.SERIF, TypefaceMode.SANS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fontsToShow.forEach { mode ->
                    val isSelected = mode == typeface
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTypefaceChange(mode) },
                        label = {
                            Text(
                                text = mode.displayName,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Color Themes & Variant - Fully unified with ThemeFamily & ThemeVariant
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color Theme", fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Normal)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple(ThemeVariant.LIGHT, "Light", Icons.Default.LightMode),
                        Triple(ThemeVariant.DARK, "Dark", Icons.Default.DarkMode),
                        Triple(ThemeVariant.SYSTEM, "Auto", Icons.Default.BrightnessAuto)
                    ).forEach { (variant, label, icon) ->
                        val isSelected = themeVariant == variant
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onThemeVariantChange(variant) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(11.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val familiesToShow = if (quickThemes.isNotEmpty()) quickThemes.toList() else ThemeFamily.entries.take(4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                familiesToShow.forEach { family ->
                    val isSelected = family == themeFamily
                    val isDark = themeVariant == ThemeVariant.DARK
                    val bgPreview = when (family) {
                        ThemeFamily.PAPER -> if (!isDark) Color(0xFFFBF0D9) else Color(0xFF1E1A16)
                        ThemeFamily.MODERN -> if (!isDark) Color(0xFFFFFFFF) else Color(0xFF121212)
                        ThemeFamily.FOREST -> if (!isDark) Color(0xFFEFF5F0) else Color(0xFF131A15)
                        ThemeFamily.PARCHMENT -> if (!isDark) Color(0xFFF5EEDB) else Color(0xFF211B14)
                        ThemeFamily.LINEN -> if (!isDark) Color(0xFFECE7DF) else Color(0xFF1B1B19)
                        ThemeFamily.CUSTOM -> Color(0xFF1C1917)
                    }
                    val textPreview = when (family) {
                        ThemeFamily.PAPER -> if (!isDark) Color(0xFF2C221E) else Color(0xFFE8DCC4)
                        ThemeFamily.MODERN -> if (!isDark) Color(0xFF1A1A1A) else Color(0xFFE0E0E0)
                        ThemeFamily.FOREST -> if (!isDark) Color(0xFF1D2B20) else Color(0xFFD3E4D6)
                        ThemeFamily.PARCHMENT -> if (!isDark) Color(0xFF2A2118) else Color(0xFFE8DCBE)
                        ThemeFamily.LINEN -> if (!isDark) Color(0xFF242321) else Color(0xFFDDD8CF)
                        ThemeFamily.CUSTOM -> Color(0xFFE7E5E4)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bgPreview,
                        border = BorderStroke(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onThemeFamilyChange(family) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = family.displayName,
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = textPreview
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Floating AI Assistant Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating AI Assistant Orb",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Movable voice & action orb with inward arc",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showAssistant,
                    onCheckedChange = onToggleAssistant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Open Advanced Settings Button
            Surface(
                onClick = {
                    onDismiss()
                    onOpenAdvancedSettings()
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Open Advanced Settings",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open Advanced Settings",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(
    chapters: List<org.protidhoni.lumina.model.Chapter>,
    bookTitle: String,
    currentChapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Table of Contents",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = bookTitle,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${chapters.size} Chapters",
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(chapters) { idx, chapter ->
                    val isCurrent = idx == currentChapterIndex
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectChapter(idx)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = if (isCurrent) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapter.title,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 15.sp,
                                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                                if (chapter.subtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = chapter.subtitle,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCurrent) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = "Reading",
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = chapter.readTime,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookContextMenuSheet(
    book: org.protidhoni.lumina.model.Book,
    onShare: () -> Unit,
    onViewDetails: () -> Unit,
    onResetProgress: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Book Header Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    source = book.coverUrl,
                    titleFallback = book.title,
                    authorFallback = book.author,
                    modifier = Modifier
                        .size(width = 48.dp, height = 68.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${book.progress}% completed • ${book.readTimeLeft}",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action: Share Book
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShare(); onDismiss() },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share Book Details",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Action: View Details & Stats
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onViewDetails(); onDismiss() },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Book Details & Chapter Index",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Action: Reset Reading Progress
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onResetProgress(); onDismiss() },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reset Reading Progress to 0%",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            // Action: Delete Book
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDelete(); onDismiss() },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delete Book from Library",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
