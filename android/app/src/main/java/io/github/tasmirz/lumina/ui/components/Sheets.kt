package io.github.tasmirz.lumina.ui.components

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UnfoldMore
import io.github.tasmirz.lumina.model.ReadingMode
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.CustomTextureData
import io.github.tasmirz.lumina.model.HighlightColor
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.model.ThemeMode
import io.github.tasmirz.lumina.model.ThemeVariant
import io.github.tasmirz.lumina.model.TypefaceMode
import io.github.tasmirz.lumina.model.toFontFamily
import io.github.tasmirz.lumina.model.OrbSize
import io.github.tasmirz.lumina.model.OrbMenuSize
import io.github.tasmirz.lumina.model.OrbColor
import io.github.tasmirz.lumina.model.WordDefinition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
    definition: WordDefinition?,
    onDismiss: () -> Unit
) {
    if (definition == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
    currentBookTitle: String? = null,
    onNavigate: (Bookmark) -> Unit,
    onDelete: (Long) -> Unit,
    onShare: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredBookmarks = remember(bookmarks, searchQuery, selectedFilter) {
        val typeFiltered = when (selectedFilter) {
            "BOOKMARKS" -> bookmarks.filter { !it.isHighlight }
            "HIGHLIGHTS" -> bookmarks.filter { it.isHighlight }
            else -> bookmarks
        }
        if (searchQuery.isBlank()) typeFiltered
        else {
            val q = searchQuery.trim().lowercase()
            typeFiltered.filter {
                it.quote.lowercase().contains(q) ||
                it.note.lowercase().contains(q) ||
                it.chapter.lowercase().contains(q) ||
                it.bookTitle.lowercase().contains(q)
            }
        }
    }

    val grouped = remember(filteredBookmarks, currentBookTitle) {
        val map = filteredBookmarks.groupBy { it.bookTitle }
        if (currentBookTitle.isNullOrBlank()) map
        else {
            map.entries.sortedByDescending { it.key.equals(currentBookTitle, ignoreCase = true) }
                .associate { it.key to it.value }
        }
    }

    var expandedBooks by remember(filteredBookmarks, currentBookTitle, searchQuery) {
        mutableStateOf(
            if (searchQuery.isNotBlank()) filteredBookmarks.map { it.bookTitle }.toSet()
            else if (currentBookTitle != null) setOf(currentBookTitle) + filteredBookmarks.map { it.bookTitle }
            else filteredBookmarks.map { it.bookTitle }.toSet()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
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
                    text = "Highlights & Bookmarks (${filteredBookmarks.size}${if (searchQuery.isNotBlank() || selectedFilter != "ALL") " of ${bookmarks.size}" else ""})",
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

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar for Highlights & Bookmarks
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search highlights, notes, chapters...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Category Filter Chips: All, Bookmarks, Highlights
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "BOOKMARKS",
                    onClick = { selectedFilter = "BOOKMARKS" },
                    label = { Text("Bookmarks", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "HIGHLIGHTS",
                    onClick = { selectedFilter = "HIGHLIGHTS" },
                    label = { Text("Highlights", fontSize = 12.sp) }
                )
            }

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
            } else if (filteredBookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No highlights or notes match \"$searchQuery\"." else "No entries in this category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (bookTitle, bookMarks) ->
                        val isExpanded = bookTitle in expandedBooks

                        item(key = "header_$bookTitle") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedBooks = if (isExpanded) expandedBooks - bookTitle else expandedBooks + bookTitle
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Book,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = bookTitle,
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${bookMarks.size}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isExpanded) {
                            items(bookMarks, key = { it.id }) { mark ->
                                val markColor = when (mark.color) {
                                    HighlightColor.GOLD -> Color(0xFFD4AF37)
                                    HighlightColor.ROSE -> Color(0xFFE5B7B7)
                                    HighlightColor.SAGE -> Color(0xFFB2C2B2)
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 6.dp)
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (mark.isLastRead) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Bookmark,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.tertiary,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = "Last Read",
                                                                fontFamily = FontFamily.SansSerif,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                                            )
                                                        }
                                                    }
                                                }
                                                val locationText = if (mark.pageNumber > 0) "Page ${mark.pageNumber} • ${mark.chapter}" else mark.chapter
                                                Text(
                                                    text = locationText,
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
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
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Bookmark,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = mark.note,
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
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
    readingMode: ReadingMode = ReadingMode.SCROLL,
    onReadingModeChange: (ReadingMode) -> Unit = {},
    themeFamily: ThemeFamily = ThemeFamily.PAPER,
    onThemeFamilyChange: (ThemeFamily) -> Unit = {},
    themeVariant: ThemeVariant = ThemeVariant.LIGHT,
    onThemeVariantChange: (ThemeVariant) -> Unit = {},
    quickThemes: Set<ThemeFamily> = setOf(ThemeFamily.PAPER, ThemeFamily.MODERN),
    backgroundTexture: BackgroundTexture = BackgroundTexture.NONE,
    customTextures: List<CustomTextureData> = emptyList(),
    selectedCustomTextureId: String = "",
    onBackgroundTextureChange: (BackgroundTexture) -> Unit = {},
    onSelectCustomTexture: (CustomTextureData) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.WARM_PAPER,
    onThemeChange: (ThemeMode) -> Unit = {},
    showAssistant: Boolean = true,
    onToggleAssistant: (Boolean) -> Unit = {},
    orbSize: OrbSize = OrbSize.NANO,
    onOrbSizeChange: (OrbSize) -> Unit = {},
    orbMenuSize: OrbMenuSize = OrbMenuSize.MEDIUM,
    onOrbMenuSizeChange: (OrbMenuSize) -> Unit = {},
    orbColor: OrbColor = OrbColor.THEME,
    onOrbColorChange: (OrbColor) -> Unit = {},
    horizontalPadding: Int = 20,
    onHorizontalPaddingChange: (Int) -> Unit = {},
    verticalPadding: Int = 16,
    onVerticalPaddingChange: (Int) -> Unit = {},
    paragraphSpacing: Float = 1.2f,
    onParagraphSpacingChange: (Float) -> Unit = {},
    currentLanguage: String = "auto",
    onLanguageChange: ((String) -> Unit)? = null,
    onOpenAdvancedSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading Settings",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val sectionLabelWidth = 104.dp

            // 1. Reading Mode Selector — Right-aligned segmented switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading Mode",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple(ReadingMode.SCROLL, "Scroll", Icons.Filled.SwapVert),
                        Triple(ReadingMode.PAGED, "Paged", Icons.AutoMirrored.Filled.MenuBook),
                        Triple(ReadingMode.PAGED_SCROLL, "Paged+", Icons.Filled.UnfoldMore)
                    ).forEach { (mode, label, icon) ->
                        val isSelected = readingMode == mode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onReadingModeChange(mode) }
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

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Typeface - Segmented switcher with actual font preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Typeface",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(sectionLabelWidth)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .horizontalScroll(rememberScrollState())
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TypefaceMode.entries.forEach { mode ->
                        val isSelected = mode == typeface
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onTypefaceChange(mode) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = mode.displayName,
                                    fontFamily = mode.toFontFamily(),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Color Themes & Variant - Right-aligned mode switcher + full-width theme family cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Color Theme",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
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
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
            val familiesToShow = ThemeFamily.entries
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                        ThemeFamily.HIGH_CONTRAST -> if (!isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
                        ThemeFamily.COLORBLIND -> if (!isDark) Color(0xFFF6F6F2) else Color(0xFF12161F)
                        ThemeFamily.CUSTOM -> Color(0xFF1C1917)
                    }
                    val textPreview = when (family) {
                        ThemeFamily.PAPER -> if (!isDark) Color(0xFF2C221E) else Color(0xFFE8DCC4)
                        ThemeFamily.MODERN -> if (!isDark) Color(0xFF1A1A1A) else Color(0xFFE0E0E0)
                        ThemeFamily.FOREST -> if (!isDark) Color(0xFF1D2B20) else Color(0xFFD3E4D6)
                        ThemeFamily.PARCHMENT -> if (!isDark) Color(0xFF2A2118) else Color(0xFFE8DCBE)
                        ThemeFamily.LINEN -> if (!isDark) Color(0xFF242321) else Color(0xFFDDD8CF)
                        ThemeFamily.HIGH_CONTRAST -> if (!isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
                        ThemeFamily.COLORBLIND -> if (!isDark) Color(0xFF101828) else Color(0xFFF0F4F8)
                        ThemeFamily.CUSTOM -> Color(0xFFE7E5E4)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = bgPreview,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 0.8.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .widthIn(min = 72.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onThemeFamilyChange(family) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = family.displayName,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = textPreview
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Surface Texture Row — Immediately below Color Theme
            val isDarkTheme = themeVariant == ThemeVariant.DARK
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
                ).forEach { (texture, label) ->
                    val isSelected = backgroundTexture == texture
                    TexturePreviewCard(
                        label = label,
                        isSelected = isSelected,
                        texture = texture,
                        isDark = isDarkTheme,
                        onClick = { onBackgroundTextureChange(texture) }
                    )
                }

                customTextures.forEach { customTex ->
                    val isSelected = backgroundTexture == BackgroundTexture.CUSTOM && selectedCustomTextureId == customTex.id
                    TexturePreviewCard(
                        label = customTex.name,
                        isSelected = isSelected,
                        texture = BackgroundTexture.CUSTOM,
                        isDark = isDarkTheme,
                        onClick = { onSelectCustomTexture(customTex) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Floating AI Assistant Section (Compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Floating AI Assistant Orb",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = showAssistant,
                    onCheckedChange = onToggleAssistant,
                    modifier = Modifier.size(width = 48.dp, height = 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Open Advanced Settings",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open Advanced Settings",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(
    chapters: List<io.github.tasmirz.lumina.model.Chapter>,
    bookTitle: String,
    currentChapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
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

            var searchQuery by rememberSaveable { mutableStateOf("") }
            val indexedChapters = remember(chapters) {
                chapters.mapIndexed { idx, chapter -> idx to chapter }
            }
            val filteredChapters = remember(indexedChapters, searchQuery) {
                if (searchQuery.isBlank()) {
                    indexedChapters
                } else {
                    val query = searchQuery.trim()
                    indexedChapters.filter { (_, ch) ->
                        ch.title.contains(query, ignoreCase = true) ||
                        ch.subtitle.contains(query, ignoreCase = true)
                    }
                }
            }

            val initialIndex = remember(chapters.size, currentChapterIndex) {
                (currentChapterIndex - 1).coerceIn(0, maxOf(chapters.size - 1, 0))
            }
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

            if (chapters.size > 5) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Filter ${chapters.size} chapters...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredChapters,
                    key = { (origIdx, chapter) -> "toc_${origIdx}_${chapter.title.hashCode()}" },
                    contentType = { "toc_item" }
                ) { (origIdx, chapter) ->
                    val isCurrent = origIdx == currentChapterIndex
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectChapter(origIdx)
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
    book: io.github.tasmirz.lumina.model.Book,
    isCompleted: Boolean = false,
    onToggleCompleted: () -> Unit = {},
    onShare: () -> Unit,
    onViewDetails: () -> Unit,
    onResetProgress: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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

            // Action: Toggle Mark as Completed
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleCompleted(); onDismiss() },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCompleted) "Mark as Currently Reading" else "Mark as Completed (Read)",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
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

@Composable
fun TexturePreviewCard(
    label: String,
    isSelected: Boolean,
    texture: BackgroundTexture,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseBg = when (texture) {
        BackgroundTexture.NONE -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)
        BackgroundTexture.GRAIN -> if (isDark) Color(0xFF221F1C) else Color(0xFFFAF5ED)
        BackgroundTexture.PARCHMENT -> if (isDark) Color(0xFF262017) else Color(0xFFF7EED9)
        BackgroundTexture.LINEN -> if (isDark) Color(0xFF202022) else Color(0xFFEDE9E3)
        BackgroundTexture.CANVAS -> if (isDark) Color(0xFF1F2220) else Color(0xFFECEAE2)
        BackgroundTexture.KRAFT -> if (isDark) Color(0xFF281E15) else Color(0xFFE8D7BE)
        BackgroundTexture.RULED_FINE,
        BackgroundTexture.RULED_WIDE,
        BackgroundTexture.RULED_GRID -> if (isDark) Color(0xFF1B2026) else Color(0xFFF7F9FC)
        BackgroundTexture.CUSTOM -> if (isDark) Color(0xFF252528) else Color(0xFFEBEBEB)
    }
    val textColor = when (texture) {
        BackgroundTexture.NONE -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF222222)
        BackgroundTexture.GRAIN -> if (isDark) Color(0xFFEADBC8) else Color(0xFF3B2E24)
        BackgroundTexture.PARCHMENT -> if (isDark) Color(0xFFE8DCBE) else Color(0xFF38291B)
        BackgroundTexture.LINEN -> if (isDark) Color(0xFFDDD8CF) else Color(0xFF2C2B29)
        BackgroundTexture.CANVAS -> if (isDark) Color(0xFFD6DCD7) else Color(0xFF2B332C)
        BackgroundTexture.KRAFT -> if (isDark) Color(0xFFDEC8AA) else Color(0xFF3D2C1B)
        BackgroundTexture.RULED_FINE,
        BackgroundTexture.RULED_WIDE,
        BackgroundTexture.RULED_GRID -> if (isDark) Color(0xFFD0DFEE) else Color(0xFF203854)
        BackgroundTexture.CUSTOM -> if (isDark) Color(0xFFE0E0E0) else Color(0xFF222222)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.8.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .widthIn(min = 72.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(baseBg)
                .drawBehind {
                    val strokeColor = if (isDark) Color.White else Color.Black
                    when (texture) {
                        BackgroundTexture.GRAIN -> {
                            val dotAlpha = if (isDark) 0.32f else 0.22f
                            for (i in 0 until 55) {
                                val x = ((i * 37 + 13) % size.width.toInt().coerceAtLeast(1)).toFloat()
                                val y = ((i * 59 + 29) % size.height.toInt().coerceAtLeast(1)).toFloat()
                                drawCircle(
                                    color = strokeColor.copy(alpha = dotAlpha),
                                    radius = if (i % 3 == 0) 1.8f else 1.1f,
                                    center = Offset(x, y)
                                )
                            }
                        }
                        BackgroundTexture.PARCHMENT -> {
                            val fiberAlpha = if (isDark) 0.38f else 0.30f
                            val fiberColor = if (isDark) Color(0xFFE0B888) else Color(0xFF6A4015)
                            for (i in 0 until 24) {
                                val sx = ((i * 47 + 7) % size.width.toInt().coerceAtLeast(1)).toFloat()
                                val sy = ((i * 31 + 11) % size.height.toInt().coerceAtLeast(1)).toFloat()
                                val len = 8f + (i % 5) * 4f
                                drawLine(
                                    color = fiberColor.copy(alpha = fiberAlpha),
                                    start = Offset(sx, sy),
                                    end = Offset(sx + len, sy + len * 0.45f),
                                    strokeWidth = 1.4f
                                )
                            }
                        }
                        BackgroundTexture.LINEN -> {
                            val lineAlpha = if (isDark) 0.26f else 0.18f
                            val step = 5.dp.toPx()
                            var x = 0f
                            while (x < size.width) {
                                drawLine(
                                    color = strokeColor.copy(alpha = lineAlpha),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1.0f
                                )
                                x += step
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(
                                    color = strokeColor.copy(alpha = lineAlpha),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.0f
                                )
                                y += step
                            }
                        }
                        BackgroundTexture.CANVAS -> {
                            val lineAlpha = if (isDark) 0.28f else 0.20f
                            val step = 7.dp.toPx()
                            var x = -size.height
                            while (x < size.width + size.height) {
                                drawLine(
                                    color = strokeColor.copy(alpha = lineAlpha),
                                    start = Offset(x, 0f),
                                    end = Offset(x + size.height, size.height),
                                    strokeWidth = 1.2f
                                )
                                drawLine(
                                    color = strokeColor.copy(alpha = lineAlpha),
                                    start = Offset(x + size.height, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1.2f
                                )
                                x += step
                            }
                        }
                        BackgroundTexture.KRAFT -> {
                            val speckColor = if (isDark) Color(0xFFD4A56E) else Color(0xFF5A3612)
                            for (i in 0 until 35) {
                                val x = ((i * 53 + 17) % size.width.toInt().coerceAtLeast(1)).toFloat()
                                val y = ((i * 41 + 19) % size.height.toInt().coerceAtLeast(1)).toFloat()
                                drawCircle(
                                    color = speckColor.copy(alpha = if (i % 2 == 0) 0.40f else 0.25f),
                                    radius = if (i % 4 == 0) 2.4f else 1.4f,
                                    center = Offset(x, y)
                                )
                            }
                        }
                        BackgroundTexture.RULED_FINE -> {
                            val lineAlpha = if (isDark) 0.55f else 0.45f
                            val lineColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF2A60A0)
                            val step = 7.dp.toPx()
                            var y = step
                            while (y < size.height) {
                                drawLine(
                                    color = lineColor.copy(alpha = lineAlpha),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.3f
                                )
                                y += step
                            }
                        }
                        BackgroundTexture.RULED_WIDE -> {
                            val lineAlpha = if (isDark) 0.58f else 0.48f
                            val lineColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF2A60A0)
                            val step = 12.dp.toPx()
                            var y = step
                            while (y < size.height) {
                                drawLine(
                                    color = lineColor.copy(alpha = lineAlpha),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.5f
                                )
                                y += step
                            }
                        }
                        BackgroundTexture.RULED_GRID -> {
                            val lineAlpha = if (isDark) 0.42f else 0.35f
                            val lineColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF2A60A0)
                            val step = 7.dp.toPx()
                            var x = 0f
                            while (x < size.width) {
                                drawLine(
                                    color = lineColor.copy(alpha = lineAlpha),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1.1f
                                )
                                x += step
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(
                                    color = lineColor.copy(alpha = lineAlpha),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.1f
                                )
                                y += step
                            }
                        }
                        else -> {}
                    }
                }
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                maxLines = 1,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

