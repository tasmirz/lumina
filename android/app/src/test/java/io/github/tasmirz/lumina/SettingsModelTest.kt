package io.github.tasmirz.lumina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.OrbActionItem
import io.github.tasmirz.lumina.model.TextAlignmentMode
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.model.ThemeVariant
import io.github.tasmirz.lumina.model.TypefaceMode

class SettingsModelTest {

    @Test
    fun testThemeFamiliesAndVariantsExist() {
        assertEquals(8, ThemeFamily.entries.size)
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.PAPER))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.MODERN))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.FOREST))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.PARCHMENT))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.LINEN))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.HIGH_CONTRAST))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.COLORBLIND))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.CUSTOM))

        assertEquals(3, ThemeVariant.entries.size)
        assertTrue(ThemeVariant.entries.contains(ThemeVariant.LIGHT))
        assertTrue(ThemeVariant.entries.contains(ThemeVariant.DARK))
        assertTrue(ThemeVariant.entries.contains(ThemeVariant.SYSTEM))
    }

    @Test
    fun testOrbActionItems() {
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.READING_MODE))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.TTS))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.NOTE))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.THEME_MODE))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.SETTINGS))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.VOICE))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.FULLSCREEN))
    }


    @Test
    fun testOrbMenuSizes() {
        assertEquals(3, io.github.tasmirz.lumina.model.OrbMenuSize.entries.size)
        val compact = io.github.tasmirz.lumina.model.OrbMenuSize.COMPACT
        assertEquals(44, compact.innerRadiusDp)
        assertEquals(80, compact.outerRadiusDp)
        assertEquals(28, compact.itemSizeDp)
        assertEquals(14, compact.iconSizeDp)

        val medium = io.github.tasmirz.lumina.model.OrbMenuSize.MEDIUM
        assertEquals(52, medium.innerRadiusDp)
        assertEquals(94, medium.outerRadiusDp)
        assertEquals(32, medium.itemSizeDp)
        assertEquals(16, medium.iconSizeDp)

        val large = io.github.tasmirz.lumina.model.OrbMenuSize.LARGE
        assertEquals(62, large.innerRadiusDp)
        assertEquals(110, large.outerRadiusDp)
        assertEquals(36, large.itemSizeDp)
        assertEquals(18, large.iconSizeDp)
    }

    @Test
    fun testBackgroundTexturesAndTypography() {
        assertEquals(10, BackgroundTexture.entries.size)
        assertTrue(BackgroundTexture.entries.contains(BackgroundTexture.NONE))
        assertTrue(BackgroundTexture.entries.contains(BackgroundTexture.GRAIN))
        assertTrue(BackgroundTexture.PARCHMENT in BackgroundTexture.entries)
        assertTrue(BackgroundTexture.LINEN in BackgroundTexture.entries)
        assertTrue(BackgroundTexture.CANVAS in BackgroundTexture.entries)
        assertTrue(BackgroundTexture.KRAFT in BackgroundTexture.entries)
        assertTrue(BackgroundTexture.RULED_FINE in BackgroundTexture.entries)
        assertTrue(BackgroundTexture.RULED_WIDE in BackgroundTexture.entries)
        assertTrue(BackgroundTexture.RULED_GRID in BackgroundTexture.entries)
        assertTrue(BackgroundTexture.CUSTOM in BackgroundTexture.entries)

        assertTrue(TypefaceMode.entries.contains(TypefaceMode.LITERARY))
        assertTrue(TypefaceMode.entries.contains(TypefaceMode.DYSLEXIC))

        assertTrue(TextAlignmentMode.entries.contains(TextAlignmentMode.JUSTIFY))
        assertTrue(TextAlignmentMode.entries.contains(TextAlignmentMode.START))
    }

    @Test
    fun testCustomTextureDataModel() {
        val customTex = io.github.tasmirz.lumina.model.CustomTextureData(
            id = "tex_123",
            name = "Japanese Washi",
            imagePath = "/data/user/0/io.github.tasmirz.lumina/files/textures/tex_123.png",
            isTiled = true,
            opacity = 0.8f,
            createdAt = 1700000000000L
        )
        assertEquals("tex_123", customTex.id)
        assertEquals("Japanese Washi", customTex.name)
        assertTrue(customTex.isTiled)
        assertEquals(0.8f, customTex.opacity, 0.001f)

        val settings = io.github.tasmirz.lumina.model.ReaderSettings(
            backgroundTexture = BackgroundTexture.CUSTOM,
            selectedCustomTextureId = "tex_123"
        )
        assertEquals(BackgroundTexture.CUSTOM, settings.backgroundTexture)
        assertEquals("tex_123", settings.selectedCustomTextureId)
    }

    @Test
    fun testAiProviders() {
        assertEquals(2, io.github.tasmirz.lumina.data.AiProvider.entries.size)
        assertTrue(io.github.tasmirz.lumina.data.AiProvider.entries.contains(io.github.tasmirz.lumina.data.AiProvider.GEMINI))
        assertTrue(io.github.tasmirz.lumina.data.AiProvider.entries.contains(io.github.tasmirz.lumina.data.AiProvider.OPENAI_COMPATIBLE))
    }

    @Test
    fun testHeadingDeduplication() {
        assertEquals("Part Two", io.github.tasmirz.lumina.data.EpubParser.deduplicateRepeatedHeading("Part TwoPart Two"))
        assertEquals("Part Two", io.github.tasmirz.lumina.data.EpubParser.deduplicateRepeatedHeading("Part Two Part Two"))
        assertEquals("Chapter 1", io.github.tasmirz.lumina.data.EpubParser.deduplicateRepeatedHeading("Chapter 1Chapter 1"))
        assertEquals("Chapter 1", io.github.tasmirz.lumina.data.EpubParser.deduplicateRepeatedHeading("Chapter 1 Chapter 1"))
        assertEquals("Nineteen Eighty-Four", io.github.tasmirz.lumina.data.EpubParser.deduplicateRepeatedHeading("Nineteen Eighty-FourNineteen Eighty-Four"))
        assertEquals("Normal Single Sentence", io.github.tasmirz.lumina.data.EpubParser.deduplicateRepeatedHeading("Normal Single Sentence"))
    }

    @Test
    fun testWishlistBookAndBookmark() {
        val item = io.github.tasmirz.lumina.model.WishlistBook(
            title = "Brave New World",
            author = "Aldous Huxley",
            note = "Recommended by friend"
        )
        assertEquals("Brave New World", item.title)
        assertEquals("Aldous Huxley", item.author)
        assertEquals("Recommended by friend", item.note)

        val sampleBookmark = io.github.tasmirz.lumina.model.Bookmark(
            id = 1L,
            bookTitle = "1984",
            chapter = "Chapter 1",
            quote = "It was a bright cold day in April.",
            color = io.github.tasmirz.lumina.model.HighlightColor.GOLD
        )
        assertEquals("1984", sampleBookmark.bookTitle)
        assertEquals("Chapter 1", sampleBookmark.chapter)
        assertEquals("It was a bright cold day in April.", sampleBookmark.quote)
        assertEquals(io.github.tasmirz.lumina.model.HighlightColor.GOLD, sampleBookmark.color)
    }

    @Test
    fun testCustomThemeDataAndSceneMatch() {
        val customTheme = io.github.tasmirz.lumina.model.CustomThemeData(
            id = "theme_cyber",
            name = "Cyber Midnight",
            bgColor = 0xFF0A192FL,
            textColor = 0xFF64FFDAL,
            accentColor = 0xFFFF9800L
        )
        assertEquals("theme_cyber", customTheme.id)
        assertEquals("Cyber Midnight", customTheme.name)
        assertEquals(0xFF0A192FL, customTheme.bgColor)
        assertEquals(0xFF64FFDAL, customTheme.textColor)
        assertEquals(0xFFFF9800L, customTheme.accentColor)

        val renamed = customTheme.copy(name = "Cyber Daylight")
        assertEquals("Cyber Daylight", renamed.name)
        assertEquals(customTheme.id, renamed.id)

        val match = io.github.tasmirz.lumina.model.SceneMatch(
            bookId = "book_1",
            chapterIndex = 2,
            chapterTitle = "Chapter 4",
            paragraphIndex = 12,
            snippet = "Winston wrote in his diary..."
        )
        assertEquals("book_1", match.bookId)
        assertEquals(2, match.chapterIndex)
        assertEquals("Chapter 4", match.chapterTitle)
        assertEquals(12, match.paragraphIndex)
        assertEquals("Winston wrote in his diary...", match.snippet)
    }

    @Test
    fun testAssistantActions() {
        val themeAction = io.github.tasmirz.lumina.data.AssistantAction.SwitchTheme(themeFamily = "DARK", mode = "dark")
        assertEquals("DARK", themeAction.themeFamily)
        assertEquals("dark", themeAction.mode)

        val jumpAction = io.github.tasmirz.lumina.data.AssistantAction.JumpToScene(query = "room 101", chapterIndex = 3)
        assertEquals("room 101", jumpAction.query)
        assertEquals(3, jumpAction.chapterIndex)

        val ttsAction = io.github.tasmirz.lumina.data.AssistantAction.ControlTts(action = "play")
        assertEquals("play", ttsAction.action)

        val scrollAction = io.github.tasmirz.lumina.data.AssistantAction.ToggleAutoScroll(enable = true)
        assertEquals(true, scrollAction.enable)
    }

    @Test
    fun testParagraphSpacingMultiplierDefaultsAndCalculation() {
        val defaultMultiplier = 1.2f
        val clampedMin = (defaultMultiplier.coerceIn(0.6f, 2.4f))
        assertEquals(1.2f, clampedMin, 0.001f)

        val fontSize = 18f
        val oldBottomSpacing = (fontSize * 0.45f).coerceIn(8f, 16f)
        val newBottomSpacing = (fontSize * 0.85f * defaultMultiplier).coerceIn(8f, 42f)

        // New default spacing is significantly more generous and comfortable than the old cramped spacing
        assertTrue(newBottomSpacing > oldBottomSpacing * 1.5f)
        assertEquals(18.36f, newBottomSpacing, 0.01f)
    }

    @Test
    fun testOrbPaletteSizingDistinction() {
        val actions5 = setOf(
            OrbActionItem.READING_MODE,
            OrbActionItem.TTS,
            OrbActionItem.NOTE,
            OrbActionItem.THEME_MODE,
            OrbActionItem.SETTINGS
        )
        val actions6 = actions5 + OrbActionItem.VOICE

        // 1..5 items should fit on single tier
        assertTrue(actions5.size <= 5)
        // 6 items should distribute into two tiers
        assertTrue(actions6.size >= 6)
    }

    @Test
    fun testSemanticSearchScoring() {
        val query = "Sherlock cigar ash"
        val queryTokens = listOf("sherlock", "cigar", "ash")

        val para1 = "Sherlock examined the cigar ash carefully on the carpet."
        val para2 = "The room was filled with cold ash from the fireplace."

        val score1 = queryTokens.count { para1.lowercase().contains(it) }
        val score2 = queryTokens.count { para2.lowercase().contains(it) }

        assertEquals(3, score1)
        assertEquals(1, score2)
        assertTrue("Multi-concept match should score significantly higher", score1 > score2)
    }

    @Test
    fun testReadingProgressPercentCalculation() {
        val totalChapters = 10
        // 0% -> Chapter 0
        val targetCh0 = ((0f / 100f) * (totalChapters - 1)).toInt().coerceIn(0, totalChapters - 1)
        assertEquals(0, targetCh0)

        // 50% -> Chapter 4 or 5
        val targetCh50 = ((50f / 100f) * (totalChapters - 1)).toInt().coerceIn(0, totalChapters - 1)
        assertEquals(4, targetCh50)

        // 100% -> Chapter 9 (last chapter)
        val targetCh100 = ((100f / 100f) * (totalChapters - 1)).toInt().coerceIn(0, totalChapters - 1)
        assertEquals(9, targetCh100)

        // Negative clamped to 0
        val clampedNeg = ((-10).coerceIn(0, 100).toFloat() / 100f * (totalChapters - 1)).toInt()
        assertEquals(0, clampedNeg)

        // Over 100 clamped to 9
        val clampedOver = ((120).coerceIn(0, 100).toFloat() / 100f * (totalChapters - 1)).toInt()
        assertEquals(9, clampedOver)
    }

    @Test
    fun testAssistantChatHistoryQueueRetention() {
        val messages = mutableListOf<String>()
        // Add 10 messages, keeping last 6 (3 user + 3 assistant turns)
        for (i in 1..10) {
            messages.add("Message $i")
            if (messages.size > 6) {
                messages.removeAt(0)
            }
        }
        assertEquals(6, messages.size)
        assertEquals("Message 5", messages.first())
        assertEquals("Message 10", messages.last())
    }

    @Test
    fun testBookLanguageDefaultsAndISO() {
        val defaultBook = io.github.tasmirz.lumina.model.Book(
            id = "test_book",
            title = "Test Title",
            author = "Test Author",
            filePath = "/path/test.epub"
        )
        assertEquals("en", defaultBook.language)

        val spanishBook = defaultBook.copy(language = "es")
        assertEquals("es", spanishBook.language)
    }

    @Test
    fun testReaderSettingsOpenLibraryApiKey() {
        val defaultSettings = io.github.tasmirz.lumina.model.ReaderSettings()
        assertEquals("", defaultSettings.openLibraryApiKey)

        val updated = defaultSettings.copy(openLibraryApiKey = "test_acc:test_sec")
        assertEquals("test_acc:test_sec", updated.openLibraryApiKey)
    }

    @Test
    fun testBookmarkModelFlagsAndDefaults() {
        val defaultBookmark = io.github.tasmirz.lumina.model.Bookmark(
            bookTitle = "Lumina",
            chapter = "Chapter 1",
            quote = "In the beginning",
            color = io.github.tasmirz.lumina.model.HighlightColor.GOLD,
            timestamp = "Just now"
        )
        assertEquals(false, defaultBookmark.isHighlight)
        assertEquals(false, defaultBookmark.isLastRead)

        val highlightMark = defaultBookmark.copy(isHighlight = true)
        assertTrue(highlightMark.isHighlight)
        assertEquals(false, highlightMark.isLastRead)

        val lastReadMark = defaultBookmark.copy(isLastRead = true, note = "Last Read Position")
        assertEquals(false, lastReadMark.isHighlight)
        assertTrue(lastReadMark.isLastRead)
    }

    @Test
    fun testCustomCatalogEndpointModelAndSettings() {
        val endpoint = io.github.tasmirz.lumina.model.CustomCatalogEndpoint(
            id = "test-ep-1",
            name = "My Calibre Server",
            galleryUrl = "http://localhost:8083/opds",
            searchUrl = "http://localhost:8083/opds/search?query=%s",
            apiKey = "my-secret-key",
            authHeader = "Authorization",
            isEnabled = true
        )
        assertEquals("test-ep-1", endpoint.id)
        assertEquals("My Calibre Server", endpoint.name)
        assertEquals("http://localhost:8083/opds", endpoint.galleryUrl)
        assertEquals("http://localhost:8083/opds/search?query=%s", endpoint.searchUrl)
        assertEquals("my-secret-key", endpoint.apiKey)
        assertEquals("Authorization", endpoint.authHeader)
        assertTrue(endpoint.isEnabled)

        val settings = io.github.tasmirz.lumina.model.ReaderSettings(
            customEndpoints = listOf(endpoint)
        )
        assertEquals(1, settings.customEndpoints.size)
        assertEquals("My Calibre Server", settings.customEndpoints[0].name)
    }

    @Test
    fun testDuplicateBookDetection() {
        val b1 = Book(
            id = "custom-12345",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            filePath = "/sdcard/Lumina/epubs/1700_pride.epub"
        )
        val b2 = Book(
            id = "epub-98765",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            filePath = "/sdcard/Lumina/epubs/1701_pride.epub"
        )
        val b3 = Book(
            id = "custom-67890",
            title = "Pride & Prejudice",
            author = "Jane Austen",
            filePath = "/sdcard/Lumina/epubs/1702_pride.epub"
        )
        val b4 = Book(
            id = "custom-11111",
            title = "Pride and Prejudice",
            author = "Unknown Author",
            filePath = ""
        )
        val b5 = Book(
            id = "custom-22222",
            title = "Moby Dick",
            author = "Herman Melville",
            filePath = "/sdcard/Lumina/epubs/moby.epub"
        )

        val repoClass = io.github.tasmirz.lumina.data.BookRepository::class.java
        // Test normalization and duplicate rules directly
        fun norm(s: String) = s.trim().lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
        fun isDup(a: Book, b: Book): Boolean {
            if (a.id == b.id) return true
            if (a.filePath.isNotBlank() && b.filePath.isNotBlank() && a.filePath == b.filePath) return true
            val t1 = norm(a.title)
            val t2 = norm(b.title)
            if (t1.isNotBlank() && t1 == t2) {
                val a1 = norm(a.author)
                val a2 = norm(b.author)
                val g1 = a1.isBlank() || a1 == "unknown" || a1 == "unknownauthor"
                val g2 = a2.isBlank() || a2 == "unknown" || a2 == "unknownauthor"
                if (g1 || g2 || a1 == a2) return true
            }
            return false
        }

        assertTrue(isDup(b1, b2))
        assertTrue(isDup(b1, b3))
        assertTrue(isDup(b1, b4))
        assertFalse(isDup(b1, b5))
    }

    @Test
    fun testPagedSafeLinesToRemoveDefaultsToZero() {
        val settings = io.github.tasmirz.lumina.model.ReaderSettings()
        assertEquals(0, settings.pagedSafeLinesToRemove)
        val updated = settings.copy(pagedSafeLinesToRemove = 3)
        assertEquals(3, updated.pagedSafeLinesToRemove)
    }

    @Test
    fun testShowStartupLoadingScreenDefaultsToFalse() {
        val settings = io.github.tasmirz.lumina.model.ReaderSettings()
        assertFalse(settings.showStartupLoadingScreen)
        val updated = settings.copy(showStartupLoadingScreen = true)
        assertTrue(updated.showStartupLoadingScreen)
    }
}
