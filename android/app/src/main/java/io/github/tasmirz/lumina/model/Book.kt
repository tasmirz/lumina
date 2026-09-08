package io.github.tasmirz.lumina.model

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
    LINEN("Linen Canvas")
}

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
    INDIGO("Indigo", 0xFF4F46E5L)
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

data class Chapter(
    val title: String,
    val subtitle: String = "",
    val readTime: String = "15 mins",
    val paragraphs: List<String> = emptyList()
)

data class Bookmark(
    val id: Long = System.currentTimeMillis(),
    val bookTitle: String,
    val chapter: String,
    val quote: String,
    val color: HighlightColor = HighlightColor.GOLD,
    val note: String = "",
    val timestamp: String = "Just now",
    val pageNumber: Int = 0
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    var lastRead: String = "Just now",
    var progress: Int = 0,
    var readTimeLeft: String = "15m left",
    var currentChapter: Int = 0,
    var currentPage: Int = 0,
    var scrollPos: Int = 0,
    val chapters: List<Chapter> = emptyList(),
    var filePath: String = "",
    var isDownloaded: Boolean = false,
    var downloadUrl: String = "",
    var fileSize: Long = 0L,
    var characterCheckpointChapter: Int = 0,
    var characterCheckpointPage: Int = 0
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
    val isSpoiler: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
