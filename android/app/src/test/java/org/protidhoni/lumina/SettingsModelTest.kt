package org.protidhoni.lumina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.protidhoni.lumina.model.BackgroundTexture
import org.protidhoni.lumina.model.OrbActionItem
import org.protidhoni.lumina.model.TextAlignmentMode
import org.protidhoni.lumina.model.ThemeFamily
import org.protidhoni.lumina.model.ThemeVariant
import org.protidhoni.lumina.model.TypefaceMode

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
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.TOC))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.SETTINGS))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.VOICE))
        assertTrue(OrbActionItem.entries.contains(OrbActionItem.FULLSCREEN))
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
        assertEquals(2, org.protidhoni.lumina.data.AiProvider.entries.size)
        assertTrue(org.protidhoni.lumina.data.AiProvider.entries.contains(org.protidhoni.lumina.data.AiProvider.GEMINI))
        assertTrue(org.protidhoni.lumina.data.AiProvider.entries.contains(org.protidhoni.lumina.data.AiProvider.OPENAI_COMPATIBLE))
    }

    @Test
    fun testHeadingDeduplication() {
        assertEquals("Part Two", org.protidhoni.lumina.data.EpubParser.deduplicateRepeatedHeading("Part TwoPart Two"))
        assertEquals("Part Two", org.protidhoni.lumina.data.EpubParser.deduplicateRepeatedHeading("Part Two Part Two"))
        assertEquals("Chapter 1", org.protidhoni.lumina.data.EpubParser.deduplicateRepeatedHeading("Chapter 1Chapter 1"))
        assertEquals("Chapter 1", org.protidhoni.lumina.data.EpubParser.deduplicateRepeatedHeading("Chapter 1 Chapter 1"))
        assertEquals("Nineteen Eighty-Four", org.protidhoni.lumina.data.EpubParser.deduplicateRepeatedHeading("Nineteen Eighty-FourNineteen Eighty-Four"))
        assertEquals("Normal Single Sentence", org.protidhoni.lumina.data.EpubParser.deduplicateRepeatedHeading("Normal Single Sentence"))
    }

    @Test
    fun testWishlistBookAndBookmark() {
        val item = org.protidhoni.lumina.model.WishlistBook(
            title = "Brave New World",
            author = "Aldous Huxley",
            note = "Recommended by friend"
        )
        assertEquals("Brave New World", item.title)
        assertEquals("Aldous Huxley", item.author)
        assertEquals("Recommended by friend", item.note)

        val sampleBookmark = org.protidhoni.lumina.model.Bookmark(
            id = 1L,
            bookTitle = "1984",
            chapter = "Chapter 1",
            quote = "It was a bright cold day in April.",
            color = org.protidhoni.lumina.model.HighlightColor.GOLD
        )
        assertEquals("1984", sampleBookmark.bookTitle)
        assertEquals("Chapter 1", sampleBookmark.chapter)
        assertEquals("It was a bright cold day in April.", sampleBookmark.quote)
        assertEquals(org.protidhoni.lumina.model.HighlightColor.GOLD, sampleBookmark.color)
    }
}
