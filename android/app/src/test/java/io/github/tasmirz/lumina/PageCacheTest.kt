package io.github.tasmirz.lumina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.github.tasmirz.lumina.model.Chapter
import io.github.tasmirz.lumina.util.PageCache

class PageCacheTest {

    @Before
    fun setUp() {
        PageCache.clear()
    }

    @Test
    fun testChapterStartPageContainsOpeningText() {
        val paragraphs = listOf(
            "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness.",
            "It was the epoch of belief, it was the epoch of incredulity, it was the season of light, it was the season of darkness.",
            "We had everything before us, we had nothing before us, we were all going direct to Heaven, we were all going direct the other way."
        )
        val chapter = Chapter(
            title = "Chapter 1",
            subtitle = "The Period",
            readTime = "5 mins",
            paragraphs = paragraphs
        )

        val pages = PageCache.getOrCompute(
            bookId = "test_book_1",
            chapters = listOf(chapter),
            fontSize = 16
        )

        assertTrue("Should have at least 1 page", pages.isNotEmpty())
        val firstPage = pages[0]
        assertEquals("Chapter 1", firstPage.first)
        assertTrue("First page must be CHAPTER_START", firstPage.second.startsWith("CHAPTER_START:::Chapter 1:::The Period:::"))

        // Opening text must be included on the first page, not isolated on a blank screen
        assertTrue("First page must include opening text", firstPage.second.contains("It was the best of times"))
    }

    @Test
    fun testParagraphsAreDistributedEvenlyAcrossPages() {
        // Create 10 paragraphs of 300 characters each (~3000 chars total)
        val paragraphs = (1..10).map { i ->
            "Paragraph $i: " + "A".repeat(270) + ". This is sentence two."
        }
        val chapter = Chapter(
            title = "Chapter 2",
            subtitle = "",
            readTime = "10 mins",
            paragraphs = paragraphs
        )

        val pages = PageCache.getOrCompute(
            bookId = "test_book_2",
            chapters = listOf(chapter),
            fontSize = 16
        )

        // Target for fontSize 16 is ~760 chars
        // 3000 chars should distribute into ~4-8 well-filled pages, NOT 10 separate mostly-blank pages
        assertTrue("Pages count should be around 4 to 8, actual: ${pages.size}", pages.size in 4..8)

        // Ensure pages are not mostly blank: each page (except possibly the very last) should have substantial length
        for (i in 0 until pages.size - 1) {
            val content = pages[i].second.removePrefix("CHAPTER_START:::Chapter 2::::::")
            assertTrue("Page $i should be substantially filled (>= 200 chars), actual: ${content.length}", content.length >= 200)
        }
    }

    @Test
    fun testCacheInvalidation() {
        val chapter = Chapter("Chapter 1", "", "1 min", listOf("Some sample prose."))
        val pages1 = PageCache.getOrCompute("book_cache_test", listOf(chapter), 16)
        assertEquals(1, pages1.size)

        PageCache.invalidate("book_cache_test")
        val pages2 = PageCache.getOrCompute("book_cache_test", listOf(chapter), 18)
        assertEquals(1, pages2.size)
    }

    @Test
    fun testStrictPagedProducesMorePagesThanPagedScroll() {
        // 15 paragraphs of 200 characters each (3000 chars total)
        val paragraphs = (1..15).map { i ->
            "Paragraph $i: " + "A".repeat(180) + "."
        }
        val chapter = Chapter("Chapter Strict", "", "5 min", paragraphs)

        val strictPages = PageCache.getOrCompute(
            bookId = "test_strict_vs_scroll",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = true
        )
        val pagedScrollPages = PageCache.getOrCompute(
            bookId = "test_strict_vs_scroll",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = false
        )

        assertTrue(
            "Strict paged (${strictPages.size}) should have more or equal pages than paged scroll (${pagedScrollPages.size})",
            strictPages.size >= pagedScrollPages.size
        )
    }

    @Test
    fun testLandscapeStrictPagedCalibration() {
        val paragraphs = (1..10).map { i ->
            "Paragraph $i: " + "C".repeat(150) + "."
        }
        val chapter = Chapter("Landscape Chapter", "", "3 min", paragraphs)

        val landscapePages = PageCache.getOrCompute(
            bookId = "test_landscape",
            chapters = listOf(chapter),
            fontSize = 16,
            isLandscape = true,
            isStrictPaged = true
        )

        assertTrue("Landscape should produce pages", landscapePages.isNotEmpty())
        assertTrue("Expected 2..6 landscape pages, got: ${landscapePages.size}", landscapePages.size in 2..6)
    }

    @Test
    fun testStrictPagedBreaksLongParagraphsWithoutOverflow() {
        // Giant single paragraph of 1800 chars
        val longPara = (1..18).joinToString(" ") { i ->
            "This is sentence number $i of the very long paragraph with detailed prose that continues for many lines."
        }
        val chapter = Chapter("Giant Para", "", "5 min", listOf(longPara))

        val strictPages = PageCache.getOrCompute(
            bookId = "test_giant_para",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = true
        )

        // Must break into multiple pages (2 to 5 pages)
        assertTrue("Strict paged must break giant paragraph into multiple pages", strictPages.size in 2..5)
        // No page should exceed target chars budget (710 + buffer)
        for (p in strictPages) {
            val content = p.second.removePrefix("CHAPTER_START:::Giant Para::::::")
            assertTrue("Page content length (${content.length}) must not exceed 800 chars", content.length <= 800)
        }
    }

    @Test
    fun testPagedScrollPreservesWholeParagraph() {
        val longPara = (1..12).joinToString(" ") { i ->
            "This is sentence number $i of the very long paragraph with detailed prose that continues for many lines."
        }
        val chapter = Chapter("Giant Para Scroll", "", "5 min", listOf(longPara))

        val scrollPages = PageCache.getOrCompute(
            bookId = "test_giant_para_scroll",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = false
        )

        // In paged+scroll, whole paragraph is preserved on 1 page and allowed to overflow
        assertEquals("Paged scroll should keep whole paragraph intact on 1 page", 1, scrollPages.size)
        assertTrue(scrollPages[0].second.contains("sentence number 12"))
    }

    @Test
    fun testStrictPagedBreaksAndContinuesAcrossPages() {
        val para1 = "Short opening paragraph with seventy characters of prose here."
        val para2 = (1..6).joinToString(" ") { i ->
            "Sentence $i of the second paragraph describing the scene in great detail and with vivid descriptions."
        }
        val chapter = Chapter("Continuation Chapter", "", "4 min", listOf(para1, para2))

        val pages = PageCache.getOrCompute(
            bookId = "test_continuation",
            chapters = listOf(chapter),
            fontSize = 18,
            isStrictPaged = true
        )

        // Must produce at least 2 pages
        assertTrue("Expected at least 2 pages, got: ${pages.size}", pages.size >= 2)

        // Page 1 must contain opening paragraph AND the first portion of paragraph 2
        val page1 = pages[0].second
        assertTrue("Page 1 must contain opening paragraph", page1.contains(para1))
        assertTrue("Page 1 must contain start of second paragraph", page1.contains("Sentence 1 of the second paragraph"))

        // Page 2 must continue paragraph 2 seamlessly without restarting paragraph 1
        val page2 = pages[1].second
        assertFalse("Page 2 must not repeat opening paragraph", page2.contains(para1))
        assertTrue("Page 2 must continue paragraph 2", page2.contains("Sentence 6 of the second paragraph"))
    }

    @Test
    fun testStrictPagedPreventsCutoffOnChapterStartPage() {
        // Simulates cutoff.png: Chapter header + 2 medium paragraphs + 3 slogan lines
        val para1 = "But it was no use, he could not remember: nothing remained of his childhood except a series of bright-lit tableaux occurring against no background and mostly unintelligible."
        val para2 = "The Ministry of Truth—Minitrue, in Newspeak [Newspeak was the official language of Oceania. For an account of its structure and etymology see Appendix.]—was startlingly different from any other object in sight. It was an enormous pyramidal structure of glittering white concrete, soaring up, terrace after terrace, 300 metres into the air. From where Winston stood it was just possible to read, picked out on its white face in elegant lettering, the three slogans of the Party:"
        val slogan1 = "WAR IS PEACE"
        val slogan2 = "FREEDOM IS SLAVERY"
        val slogan3 = "IGNORANCE IS STRENGTH"

        val chapter = Chapter("Nineteen Eighty-Four", "Chapter 1", "10 min", listOf(para1, para2, slogan1, slogan2, slogan3))

        val pages = PageCache.getOrCompute(
            bookId = "test_cutoff_prevention",
            chapters = listOf(chapter),
            fontSize = 18,
            isStrictPaged = true
        )

        // Slogans must not all be crammed into page 1 causing overflow; they should be on subsequent page(s)
        assertTrue("Must break across at least 2 pages", pages.size >= 2)
        val page1 = pages[0].second
        assertFalse(
            "Page 1 must not cram all slogans causing bottom cutoff; IGNORANCE IS STRENGTH should move to next page",
            page1.contains(slogan3)
        )
        // Verify slogan3 is present on page 2
        assertTrue("Page 2 must contain the slogans", pages[1].second.contains(slogan3))
    }

    @Test
    fun testStrictPagedFillsPagesWithoutPrematureHalfEmptyBreaks() {
        // Simulates too_short.png: A paragraph of 700 chars followed by more content
        val para1 = "The little sandy-haired woman gave a squeak of mingled fear and disgust. Goldstein was the renegade and backslider who once, long ago (how long ago, nobody quite remembered), had been one of the leading figures of the Party, almost on a level with Big Brother himself, and then had engaged in counter-revolutionary activities, had been condemned to death, and had mysteriously escaped and disappeared. The programmes of the Two Minutes Hate varied from day to day, but there was none in which Goldstein was not the principal figure. He was the primal traitor, the earliest defiler of the Party's purity. All subsequent crimes against the Party, all treacheries, acts of sabotage, heresies, deviations, sprang directly out of his teaching."
        val para2 = "Somewhere or other he was still alive and hatching his conspiracies: perhaps somewhere beyond the sea, under the protection of his foreign paymasters, perhaps even—so it was occasionally rumoured—in some hiding-place in Oceania itself."

        val chapter = Chapter("Two Minutes Hate", "", "5 min", listOf(para1, para2))

        val pages = PageCache.getOrCompute(
            bookId = "test_too_short_prevention",
            chapters = listOf(chapter),
            fontSize = 18,
            isStrictPaged = true
        )

        assertTrue("Expected multiple pages", pages.size >= 2)
        // On page 1, because para1 is ~700 chars and page 1 is a chapter header page with 13-line budget,
        // it breaks to fill page 1 and continues onto page 2
        val page1 = pages[0].second
        assertTrue("Page 1 should be well filled", page1.length >= 350)
        assertTrue("Page 2 should contain continuation or remainder", pages[1].second.contains("foreign paymasters"))
    }
}
