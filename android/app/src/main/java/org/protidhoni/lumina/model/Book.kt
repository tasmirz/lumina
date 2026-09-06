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

enum class TypefaceMode(val displayName: String) {
    SERIF("Serif"),
    SANS("Sans"),
    MONO("Mono")
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
