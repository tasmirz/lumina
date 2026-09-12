package io.github.tasmirz.lumina.model

import androidx.compose.runtime.Immutable
import io.github.tasmirz.lumina.data.AiProvider

enum class HighlightColor(val colorValue: Long, val displayName: String) {
    GOLD(0xFFD4AF37, "Gold"),
    ROSE(0xFFE5B7B7, "Rose"),
    SAGE(0xFFB2C2B2, "Sage")
}

enum class ReadingMode(val displayName: String) {
    SCROLL("Continuous Scroll"),
    PAGED("Strict Paged"),
    PAGED_SCROLL("Paged + Scroll")
}

enum class ThemeMode(val displayName: String) {
    WARM_PAPER("Warm Paper"),
    PURE_WHITE("Pure White"),
    NIGHT("Night")
}

enum class ThemeFamily(val displayName: String) {
    PAPER("Warm Paper"),
    MODERN("Clean Modern"),
    FOREST("Serene Forest"),
    PARCHMENT("Parchment"),
    LINEN("Linen Canvas"),
    HIGH_CONTRAST("High Contrast"),
    COLORBLIND("Colorblind Safe"),
    CUSTOM("Custom")
}

enum class ThemeVariant(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("Auto")
}

enum class BackgroundTexture(val displayName: String) {
    NONE("Clean"),
    GRAIN("Paper Grain"),
    PARCHMENT("Parchment"),
    LINEN("Linen Weave"),
    CANVAS("Artist Canvas"),
    KRAFT("Kraft Fiber"),
    RULED_FINE("Fine Lined"),
    RULED_WIDE("Wide Lined"),
    RULED_GRID("Grid Lined"),
    CUSTOM("Custom")
}

data class CustomTextureData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val imagePath: String,
    val isTiled: Boolean = true,
    val opacity: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis()
)

enum class OrbActionItem(val displayName: String, val description: String) {
    READING_MODE("Scroll / Paged Mode", "Toggle between continuous scroll and page flip"),
    TTS("Audiobook / TTS", "Play or pause text-to-speech reading"),
    NOTE("Highlights & Notes", "View or manage bookmarks and highlighted excerpts"),
    THEME_MODE("Light / Dark Theme", "Quickly toggle between light and dark mode"),
    SETTINGS("Quick Appearance", "Open reader font and theme quick controls"),
    VOICE("Voice Assistant", "Consult AI assistant with speech"),
    SEARCH("In-Book Search", "Search text and scenes in current book"),
    CHARACTERS("Character Guide", "View characters, roles, and spoiler-shielded lore"),
    FULLSCREEN("Distraction-Free Mode", "Hide all chrome and status bars")
}

enum class OrbSize(val displayName: String, val scale: Float, val dockedWidth: Int, val dockedHeight: Int) {
    NANO("Nano", 0.55f, 6, 16),
    MINI("Mini", 0.70f, 9, 22),
    COMPACT("Small", 0.85f, 13, 26),
    DEFAULT("Medium", 1.0f, 16, 32),
    LARGE("Large", 1.2f, 20, 38)
}

enum class OrbMenuSize(val displayName: String, val scale: Float, val innerRadiusDp: Int, val outerRadiusDp: Int, val itemSizeDp: Int, val iconSizeDp: Int) {
    COMPACT("Compact", 0.75f, 44, 80, 28, 14),
    MEDIUM("Medium", 0.90f, 52, 94, 32, 16),
    LARGE("Large", 1.05f, 62, 110, 36, 18)
}

enum class OrbColor(val displayName: String, val colorValue: Long) {
    THEME("Theme", 0L),
    SLATE("Slate", 0xFF64748BL),
    AMBER("Amber", 0xFFD97706L),
    ROSE("Rose", 0xFFE11D48L),
    EMERALD("Emerald", 0xFF059669L),
    INDIGO("Indigo", 0xFF4F46E5L),
    CUSTOM("Custom", -1L)
}

enum class TextAlignmentMode(val displayName: String) {
    JUSTIFY("Justified"),
    START("Left-aligned")
}

enum class TypefaceMode(val displayName: String) {
    SERIF("Serif"),
    SANS("Sans-Serif"),
    MONO("Monospace"),
    LITERARY("Literary Elegant"),
    DYSLEXIC("Dyslexic-Friendly"),
    GEORGIA("Georgia"),
    GARAMOND("Garamond"),
    PALATINO("Palatino"),
    MERRIWEATHER("Merriweather"),
    ROUNDED("Soft Rounded")
}

fun TypefaceMode.toFontFamily(): androidx.compose.ui.text.font.FontFamily = when (this) {
    TypefaceMode.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
    TypefaceMode.SANS -> androidx.compose.ui.text.font.FontFamily.SansSerif
    TypefaceMode.MONO -> androidx.compose.ui.text.font.FontFamily.Monospace
    TypefaceMode.LITERARY -> androidx.compose.ui.text.font.FontFamily.Cursive
    TypefaceMode.DYSLEXIC -> androidx.compose.ui.text.font.FontFamily.SansSerif
    TypefaceMode.GEORGIA -> androidx.compose.ui.text.font.FontFamily.Serif
    TypefaceMode.GARAMOND -> androidx.compose.ui.text.font.FontFamily.Serif
    TypefaceMode.PALATINO -> androidx.compose.ui.text.font.FontFamily.Serif
    TypefaceMode.MERRIWEATHER -> androidx.compose.ui.text.font.FontFamily.Serif
    TypefaceMode.ROUNDED -> androidx.compose.ui.text.font.FontFamily.SansSerif
}

enum class GestureAction(val displayName: String) {
    TOGGLE_AUTOSCROLL("Toggle Auto-scroll"),
    SUMMON_ORB("Summon Assistant Orb"),
    TOGGLE_BARS("Toggle Navigation Bars"),
    TTS_READ_ALOUD("Read Aloud from Paragraph"),
    IN_BOOK_SEARCH("In-Book Search"),
    PREVIOUS_CHAPTER("Previous Chapter"),
    NEXT_CHAPTER("Next Chapter"),
    ADD_BOOKMARK("Add Bookmark"),
    NONE("None (Disabled)")
}

@Immutable
data class Chapter(
    val title: String,
    val subtitle: String = "",
    val readTime: String = "15 mins",
    val paragraphs: List<String> = emptyList()
)

@Immutable
data class Bookmark(
    val id: Long = System.currentTimeMillis(),
    val bookTitle: String,
    val chapter: String,
    val quote: String,
    val color: HighlightColor = HighlightColor.GOLD,
    val note: String = "",
    val timestamp: String = "Just now",
    val pageNumber: Int = 0,
    val isHighlight: Boolean = false,
    val isLastRead: Boolean = false
)

@Immutable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String = "",
    val lastRead: String = "Just now",
    val progress: Int = 0,
    val readTimeLeft: String = "15m left",
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val scrollPos: Int = 0,
    val chapters: List<Chapter> = emptyList(),
    val filePath: String = "",
    val isDownloaded: Boolean = false,
    val downloadUrl: String = "",
    val fileSize: Long = 0L,
    val characterCheckpointChapter: Int = 0,
    val characterCheckpointPage: Int = 0,
    val language: String = "en"
)

data class WordDefinition(
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val definition: String,
    val example: String
)

data class WishlistBook(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val author: String = "",
    val note: String = "",
    val addedAt: String = "Recently"
)

data class SceneMatch(
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val snippet: String
)

data class CustomThemeData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val bgColor: Long,
    val textColor: Long,
    val accentColor: Long
)

data class BookCharacter(
    val id: Long = 0,
    val bookId: String,
    val name: String,
    val role: String,
    val firstAppearanceChapter: String = "",
    val summary: String,
    val keyEvents: String = "",
    val aliases: List<String> = emptyList(),
    val isSpoiler: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class BookLore(
    val id: Long = 0,
    val bookId: String,
    val title: String,
    val category: String = "World",
    val firstAppearanceChapter: String = "",
    val description: String,
    val keyFacts: String = "",
    val isSpoiler: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class ReadingPosition(
    val bookId: String = "",
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val scrollPos: Int = 0,
    val progressPct: Int = 0
)

@Immutable
data class ReaderSettings(
    val fontSize: Int = 18,
    val readingMode: ReadingMode = ReadingMode.SCROLL,
    val themeMode: ThemeMode = ThemeMode.WARM_PAPER,
    val themeFamily: ThemeFamily = ThemeFamily.PAPER,
    val themeVariant: ThemeVariant = ThemeVariant.LIGHT,
    val backgroundTexture: BackgroundTexture = BackgroundTexture.NONE,
    val selectedCustomTextureId: String = "",
    val dynamicRollingTexture: Boolean = true,
    val customBgUri: String = "",
    val orbActionItems: Set<OrbActionItem> = OrbActionItem.entries.toSet(),
    val orbActionOrder: List<OrbActionItem> = OrbActionItem.entries.toList(),
    val textAlignmentMode: TextAlignmentMode = TextAlignmentMode.JUSTIFY,
    val letterSpacing: Float = 0.2f,
    val typefaceMode: TypefaceMode = TypefaceMode.SERIF,
    val lineHeightMultiplier: Float = 1.68f,
    val paragraphSpacingMultiplier: Float = 1.2f,
    val showFloatingAssistant: Boolean = true,
    val horizontalPadding: Int = 20,
    val verticalPadding: Int = 16,
    val pagedSafeLinesToRemove: Int = 0,
    val showStartupLoadingScreen: Boolean = false,
    val assistantOrbStyle: String = "EDGE_DOT",
    val spoilerShield: Boolean = true,
    val autoScrollSpeed: Float = 1.0f,
    val localOnlyMode: Boolean = false,
    val disableAi: Boolean = false,
    val disableTts: Boolean = false,
    val disableStt: Boolean = false,
    val ttsEngine: String = "EDGE_NEURAL",
    val ttsEdgeVoice: String = "en-US-JennyNeural",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val autoStartMic: Boolean = true,
    val enableFtsIndexing: Boolean = false,
    val geminiApiKey: String = "",
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "gemini-3.1-flash-lite",
    val quickThemes: Set<ThemeFamily> = ThemeFamily.entries.toSet(),
    val quickFonts: Set<TypefaceMode> = setOf(TypefaceMode.SERIF, TypefaceMode.SANS, TypefaceMode.GEORGIA),
    val gestureDoubleTap: GestureAction = GestureAction.TOGGLE_AUTOSCROLL,
    val gestureTripleTap: GestureAction = GestureAction.SUMMON_ORB,
    val gestureSingleTap: GestureAction = GestureAction.TOGGLE_BARS,
    val gestureTtsTap: GestureAction = GestureAction.TTS_READ_ALOUD,
    val customBgColor: Long = 0xFF1C1917L,
    val customTextColor: Long = 0xFFE7E5E4L,
    val customAccentColor: Long = 0xFFD4AF37L,
    val orbSize: OrbSize = OrbSize.NANO,
    val orbMenuSize: OrbMenuSize = OrbMenuSize.MEDIUM,
    val orbEdgeSnap: Boolean = true,
    val orbPortraitX: Float = -1f,
    val orbPortraitY: Float = -1f,
    val orbLandscapeX: Float = -1f,
    val orbLandscapeY: Float = -1f,
    val orbColor: OrbColor = OrbColor.THEME,
    val customOrbColor: Long = 0xFF4F46E5L,
    val orbOpacity: Float = 0.85f,
    val preferredLanguage: String = "auto",
    val openLibraryApiKey: String = "",
    val customEndpoints: List<CustomCatalogEndpoint> = emptyList()
)

data class CustomCatalogEndpoint(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val galleryUrl: String = "",
    val searchUrl: String = "",
    val apiKey: String = "",
    val authHeader: String = "Authorization",
    val isEnabled: Boolean = true
)

