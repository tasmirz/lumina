package org.protidhoni.lumina.model

enum class HighlightColor(val colorValue: Long, val displayName: String) {
    GOLD(0xFFD4AF37, "Gold"),
    ROSE(0xFFE5B7B7, "Rose"),
    SAGE(0xFFB2C2B2, "Sage")
}

enum class ReadingMode {
    SCROLL,
    PAGED
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
    TOC("Table of Contents", "Jump directly to any book chapter"),
    SETTINGS("Quick Appearance", "Open reader font and theme quick controls"),
    VOICE("Voice Assistant", "Consult AI assistant with speech"),
    FULLSCREEN("Distraction-Free Mode", "Hide all chrome and status bars")
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
    val timestamp: String = "Just now"
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
    val chapters: List<Chapter> = emptyList()
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
