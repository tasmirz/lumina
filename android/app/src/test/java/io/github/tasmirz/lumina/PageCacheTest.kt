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
}
