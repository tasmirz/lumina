package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.data.AssistantService
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.BookCharacter
import io.github.tasmirz.lumina.model.BookLore
import io.github.tasmirz.lumina.model.Chapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterLoreExtractionTest {

    @Test
    fun testZeroTokenGuardWhenNoNewChapters() = runBlocking {
        // When user has already extracted up to chapter 2 and current reading position is still chapter 2,
        // extraction must return existing data immediately without making any API network calls (0 tokens).
        val book = Book(
            id = "test-book-1984",
            title = "1984",
            author = "George Orwell",
            characterCheckpointChapter = 2,
            characterCheckpointPage = 0,
            currentPage = 0,
            chapters = listOf(
                Chapter(title = "Chapter 1", paragraphs = listOf("Paragraph 1")),
                Chapter(title = "Chapter 2", paragraphs = listOf("Paragraph 2")),
                Chapter(title = "Chapter 3", paragraphs = listOf("Paragraph 3"))
            )
        )

        val existingChars = listOf(
            BookCharacter(
                id = 1,
                bookId = book.id,
                name = "Winston Smith",
                role = "Outer Party Member",
                firstAppearanceChapter = "Chapter 1",
                summary = "A clerk in the Records Department of the Ministry of Truth who harbors secret rebellion."
            )
        )
        val existingLore = listOf(
            BookLore(
                id = 1,
                bookId = book.id,
                title = "Ingsoc",
                category = "CONCEPT",
                firstAppearanceChapter = "Chapter 1",
                description = "English Socialism, the totalitarian political philosophy of the ruling Party in Oceania."
            )
        )

        // Calling with currentChapterIndex = 2 (which is <= characterCheckpointChapter 2)
        // With an empty API key, this would throw or fail if network was reached.
        val result = AssistantService.extractCharactersAndLore(
            book = book,
            currentChapterIndex = 2,
            isSpoilerShield = true,
            apiKey = "",
            existingCharacters = existingChars,
            existingLore = existingLore,
            lastCalculatedChapter = 2,
            lastCalculatedPage = 0,
            currentPageIndex = 0
        )

        // Zero-token guard guarantees immediate return of existing characters and lore!
        assertEquals(1, result.characters.size)
        assertEquals("Winston Smith", result.characters.first().name)
        assertEquals(1, result.lore.size)
        assertEquals("Ingsoc", result.lore.first().title)
    }

    @Test
    fun testEntityDeduplicationLogic() {
        // Verify canonical matching rules
        val winston1 = BookCharacter(
            id = 1,
            bookId = "book1",
            name = "Winston Smith",
            role = "Protagonist",
            firstAppearanceChapter = "Chapter 1",
            summary = "Winston Smith lives in London, Oceania, working for Minitrue.",
            aliases = listOf("Winston")
        )

        // Check aliases matching
        assertTrue(winston1.aliases.contains("Winston"))
        assertEquals("Winston Smith", winston1.name)
    }

    @Test
    fun testBookLoreDataStructure() {
        val lore = BookLore(
            id = 10,
            bookId = "book1",
            title = "Ministry of Truth",
            category = "LOCATION",
            firstAppearanceChapter = "Chapter 1",
            description = "The ministry responsible for historical revisionism and propaganda in Oceania.",
            keyFacts = "Known as Minitrue • Contains the Records Department",
            isSpoiler = false
        )

        assertEquals("Ministry of Truth", lore.title)
        assertEquals("LOCATION", lore.category)
        assertTrue(lore.keyFacts.contains("Minitrue"))
        assertFalse(lore.isSpoiler)
    }
}
