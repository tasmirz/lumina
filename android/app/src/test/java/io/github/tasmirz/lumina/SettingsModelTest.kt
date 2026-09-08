package io.github.tasmirz.lumina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.OrbActionItem
import io.github.tasmirz.lumina.model.TextAlignmentMode
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.model.ThemeVariant
import io.github.tasmirz.lumina.model.TypefaceMode

class SettingsModelTest {

    @Test
    fun testThemeFamiliesAndVariantsExist() {
        assertEquals(6, ThemeFamily.entries.size)
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.PAPER))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.MODERN))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.FOREST))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.PARCHMENT))
        assertTrue(ThemeFamily.entries.contains(ThemeFamily.LINEN))
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
        assertEquals(4, BackgroundTexture.entries.size)
        assertTrue(BackgroundTexture.entries.contains(BackgroundTexture.NONE))
        assertTrue(BackgroundTexture.entries.contains(BackgroundTexture.GRAIN))
        assertTrue(BackgroundTexture.entries.contains(BackgroundTexture.PARCHMENT))
        assertTrue(BackgroundTexture.entries.contains(BackgroundTexture.LINEN))

        assertTrue(TypefaceMode.entries.contains(TypefaceMode.LITERARY))
        assertTrue(TypefaceMode.entries.contains(TypefaceMode.DYSLEXIC))

        assertTrue(TextAlignmentMode.entries.contains(TextAlignmentMode.JUSTIFY))
        assertTrue(TextAlignmentMode.entries.contains(TextAlignmentMode.START))
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
}

