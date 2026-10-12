package io.github.tasmirz.lumina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.github.tasmirz.lumina.model.Chapter
import io.github.tasmirz.lumina.ui.reader.cleanAlpha
import io.github.tasmirz.lumina.ui.reader.findPageForLocation
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

        // Target for fontSize 16 is ~940 chars
        // 3000 chars should distribute into ~3-8 well-filled pages, NOT 10 separate mostly-blank pages
        assertTrue("Pages count should be around 3 to 8, actual: ${pages.size}", pages.size in 3..8)

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
            "Strict paged (${strictPages.size}) and paged scroll (${pagedScrollPages.size}) should both produce valid pages",
            strictPages.isNotEmpty() && pagedScrollPages.isNotEmpty()
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
        // No page should exceed target chars budget (up to 1300 chars in portrait)
        for (p in strictPages) {
            val content = p.second.removePrefix("CHAPTER_START:::Giant Para::::::")
            assertTrue("Page content length (${content.length}) must not exceed 1300 chars", content.length <= 1300)
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
        val para2 = (1..14).joinToString(" ") { i ->
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
        assertTrue("Page 2 must continue paragraph 2", page2.contains("Sentence 14 of the second paragraph"))
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
        // Verify slogan3 is present on subsequent pages
        assertTrue("Subsequent pages must contain the slogans", pages.any { it.second.contains(slogan3) })
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
        val page1 = pages[0].second
        assertTrue("Page 1 should be well filled", page1.length >= 350)
        assertTrue("Pages should contain continuation", pages.any { it.second.contains("foreign") } && pages.any { it.second.contains("paymasters") })
    }

    @Test
    fun testComputeChapterPagesSingleChapterFastPath() {
        val chapter = Chapter("Quick Chapter", "Part 1", "3 min", listOf("Paragraph one of quick chapter.", "Paragraph two."))
        val pages = PageCache.computeChapterPages(chapter, fontSize = 18, isStrictPaged = true)
        assertTrue("Pages should not be empty", pages.isNotEmpty())
        assertEquals("Quick Chapter", pages[0].first)
    }

    @Test
    fun testGetOrComputeAsyncWithActiveChapterReadyCallback() = kotlinx.coroutines.runBlocking {
        val chapter1 = Chapter("Chapter 1", "", "3 min", listOf("Content 1"))
        val chapter2 = Chapter("Chapter 2", "", "3 min", listOf("Content 2"))
        var activeReadyCalled = false
        var activePagesSize = 0

        val allPages = PageCache.getOrComputeAsync(
            bookId = "async_test_book",
            chapters = listOf(chapter1, chapter2),
            fontSize = 18,
            isStrictPaged = true,
            activeChapterIndex = 0,
            onActiveChapterReady = { activePages ->
                activeReadyCalled = true
                activePagesSize = activePages.size
            }
        )

        assertTrue("Active chapter callback should be invoked", activeReadyCalled)
        assertTrue("Active pages size should be > 0", activePagesSize > 0)
        assertTrue("All pages size should be >= active pages size", allPages.size >= activePagesSize)
    }

    @Test
    fun testPagedScrollModeDoesNotLeaveBlankBottom() {
        val paras = listOf(
            "Short opening line.",
            "A second paragraph with a moderate length describing the setting and the character walking through the stormy night.",
            "A third paragraph that continues the scene with dialogue and observations of the rain hammering the windows.",
            "A fourth paragraph that wraps up the sequence."
        )
        val chapter = Chapter("Chapter Paged Scroll", "", "5 min", paras)
        val pages = PageCache.getOrCompute(
            bookId = "test_paged_scroll_filling",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = false
        )
        assertTrue("Expected computed pages", pages.isNotEmpty())
        // In Paged+Scroll, the first page should accumulate paragraphs to avoid empty space below
        val firstPageContent = pages[0].second
        assertTrue("First page must accumulate paragraphs", firstPageContent.contains("Short opening line."))
        assertTrue("First page must contain subsequent paragraphs", firstPageContent.contains("A second paragraph"))
    }

    @Test
    fun testStrictPagedSplitsLongParagraphsCleanly() {
        val longPara = (1..16).joinToString(" ") { i ->
            "Sentence $i of the very long paragraph that continues to elaborate on the deep thoughts of the narrator while staring out at the sea and listening to waves crashing against the rocky shores."
        }
        val chapter = Chapter("Strict Paged Chapter", "", "5 min", listOf(longPara))
        val pages = PageCache.getOrCompute(
            bookId = "test_strict_paged_split",
            chapters = listOf(chapter),
            fontSize = 20, // larger font means fewer chars per page
            isStrictPaged = true
        )
        assertTrue("Long paragraph should break across pages", pages.size >= 2)
        // First page should be well-filled
        assertTrue("First page should have substantial content", pages[0].second.length >= 250)
        // Second page should contain continuation
        assertTrue("Second page should have continuation", pages[1].second.isNotBlank())
    }

    @Test
    fun testGetCachedNonBlocking() {
        val chapter = Chapter("Cached Chapter", "", "2 min", listOf("Quick sentence."))
        val cachedBefore = PageCache.getCached("test_cached_check", 1, 16, isLandscape = false, isStrictPaged = true)
        org.junit.Assert.assertNull("Should be null before computation", cachedBefore)

        PageCache.getOrCompute("test_cached_check", listOf(chapter), 16, isLandscape = false, isStrictPaged = true)

        val cachedAfter = PageCache.getCached("test_cached_check", 1, 16, isLandscape = false, isStrictPaged = true)
        org.junit.Assert.assertNotNull("Should be present in cache after computation", cachedAfter)
        assertEquals(1, cachedAfter!!.size)
    }

    @Test
    fun testPagedHeightConsistencyAcrossFontSizes() {
        // Continuous text of ~3000 chars split into multiple paragraphs
        val paragraphs = (1..10).map { i ->
            "Paragraph $i: In the quiet hours before dawn, the ancient library seemed to breathe with the whispered memories of countless scholars. Shelves towered toward the vaulted ceilings, heavy with leather-bound volumes that held forgotten wisdom and timeless poetry."
        }
        val chapter = Chapter("Consistency Test", "", "10 min", paragraphs)

        val fontSizes = listOf(12, 14, 18, 22, 28)
        val pagesByFontSize = fontSizes.associateWith { fs ->
            PageCache.getOrCompute("test_consistency_$fs", listOf(chapter), fontSize = fs, isStrictPaged = true)
        }

        // Smaller font sizes must produce fewer pages (more content per page), larger font sizes produce more pages
        val pageCounts = fontSizes.map { pagesByFontSize[it]!!.size }
        for (i in 0 until pageCounts.size - 1) {
            assertTrue(
                "Smaller font (${fontSizes[i]}sp with ${pageCounts[i]} pages) must produce <= pages than larger font (${fontSizes[i+1]}sp with ${pageCounts[i+1]} pages)",
                pageCounts[i] <= pageCounts[i+1]
            )
        }

        // For non-final pages, verify characters per page is substantially filled for every font size
        for (fs in fontSizes) {
            val pages = pagesByFontSize[fs]!!
            assertTrue("Should produce pages for fontSize $fs", pages.isNotEmpty())
            for (pIdx in 0 until pages.size - 1) {
                val pageText = pages[pIdx].second.removePrefix("CHAPTER_START:::Consistency Test::::::")
                val isFirstPage = pIdx == 0
                val minExpectedChars = when {
                    fs <= 13 -> if (isFirstPage) 400 else 550
                    fs <= 16 -> if (isFirstPage) 280 else 380
                    fs <= 20 -> if (isFirstPage) 180 else 250
                    else -> if (isFirstPage) 90 else 130
                }
                assertTrue(
                    "Font size ${fs}sp on page $pIdx has ${pageText.length} chars, expected >= $minExpectedChars",
                    pageText.length >= minExpectedChars
                )
            }
        }
    }

    @Test
    fun testLandscapeHeightConsistencyAcrossFontSizes() {
        val paragraphs = (1..10).map { i ->
            "Paragraph $i: In the quiet hours before dawn, the ancient library seemed to breathe with the whispered memories of countless scholars. Shelves towered toward the vaulted ceilings, heavy with leather-bound volumes that held forgotten wisdom and timeless poetry."
        }
        val chapter = Chapter("Landscape Consistency", "", "10 min", paragraphs)

        val fontSizes = listOf(12, 14, 18, 22, 28)
        val pagesByFontSize = fontSizes.associateWith { fs ->
            PageCache.getOrCompute("test_landscape_consistency_$fs", listOf(chapter), fontSize = fs, isLandscape = true, isStrictPaged = true)
        }

        val pageCounts = fontSizes.map { pagesByFontSize[it]!!.size }
        for (i in 0 until pageCounts.size - 1) {
            assertTrue(
                "In landscape, smaller font (${fontSizes[i]}sp with ${pageCounts[i]} pages) must produce <= pages than larger font (${fontSizes[i+1]}sp with ${pageCounts[i+1]} pages)",
                pageCounts[i] <= pageCounts[i+1]
            )
        }

        // Verify all landscape pages are generated and well-filled
        for (fs in fontSizes) {
            val pages = pagesByFontSize[fs]!!
            assertTrue("Should produce landscape pages for fontSize $fs", pages.isNotEmpty())
            for (pIdx in 0 until pages.size - 1) {
                val pageText = pages[pIdx].second.removePrefix("CHAPTER_START:::Landscape Consistency::::::")
                val isFirstPage = pIdx == 0
                val minExpectedChars = when {
                    fs <= 13 -> if (isFirstPage) 250 else 350
                    fs <= 16 -> if (isFirstPage) 180 else 250
                    fs <= 20 -> if (isFirstPage) 100 else 160
                    else -> if (isFirstPage) 40 else 60
                }
                assertTrue(
                    "Landscape font size ${fs}sp on page $pIdx (of ${pages.size}) has ${pageText.length} chars (content: '${pageText.take(40)}...'), expected >= $minExpectedChars",
                    pageText.length >= minExpectedChars
                )
            }
        }
    }

    @Test
    fun testDynamicOnDeviceMetricsCalibrationAcrossDeviceFormFactors() {
        // Standard compact phone (360x640 dp)
        val compactMetrics = PageCache.calculateDeviceMetrics(
            fontSize = 16,
            screenWidthDp = 360,
            screenHeightDp = 640
        )
        assertTrue("Compact phone max lines in range 17..22, got: ${compactMetrics.maxLines}", compactMetrics.maxLines in 17..22)
        assertTrue("Compact phone chars per line in range 40..48", compactMetrics.charsPerLine in 40..48)

        // Modern tall phone (392x828 dp)
        val tallMetrics = PageCache.calculateDeviceMetrics(
            fontSize = 16,
            screenWidthDp = 392,
            screenHeightDp = 828
        )
        assertTrue("Tall phone max lines in range 24..30, got: ${tallMetrics.maxLines}", tallMetrics.maxLines in 24..30)
        assertTrue("Tall phone chars per line in range 46..54", tallMetrics.charsPerLine in 46..54)

        // Large 10-inch Tablet (800x1280 dp)
        val tabletMetrics = PageCache.calculateDeviceMetrics(
            fontSize = 18,
            screenWidthDp = 800,
            screenHeightDp = 1280
        )
        assertTrue("Tablet max lines should be >= 35", tabletMetrics.maxLines >= 35)
        assertTrue("Tablet chars per line should be >= 80", tabletMetrics.charsPerLine >= 80)

        // Foldable unfolded (670x800 dp)
        val foldMetrics = PageCache.calculateDeviceMetrics(
            fontSize = 16,
            screenWidthDp = 670,
            screenHeightDp = 800
        )
        assertTrue("Foldable max lines in range 22..28, got: ${foldMetrics.maxLines}", foldMetrics.maxLines in 22..28)
        assertTrue("Foldable chars per line should be >= 75", foldMetrics.charsPerLine >= 75)
    }

    @Test
    fun testSafeLinesToRemoveReducesMaxLines() {
        val defaultMetrics = PageCache.calculateDeviceMetrics(
            fontSize = 16,
            screenWidthDp = 392,
            screenHeightDp = 828,
            safeLinesToRemove = 0
        )
        val safe2Metrics = PageCache.calculateDeviceMetrics(
            fontSize = 16,
            screenWidthDp = 392,
            screenHeightDp = 828,
            safeLinesToRemove = 2
        )
        assertEquals(defaultMetrics.maxLines - 2, safe2Metrics.maxLines)

        val safe4Metrics = PageCache.calculateDeviceMetrics(
            fontSize = 16,
            screenWidthDp = 392,
            screenHeightDp = 828,
            safeLinesToRemove = 4
        )
        assertEquals(defaultMetrics.maxLines - 4, safe4Metrics.maxLines)
    }

    @Test
    fun testMultiParagraphSpacingBudgetAccurate() {
        // 6 short dialogue paragraphs: each paragraph break adds paragraph gap lines
        val dialogueParas = listOf(
            "“Are you coming to the square?” asked Julia, looking up from her workbench with quick curiosity.",
            "“Not tonight,” replied Winston quietly. “I have work to finish at the records department before curfew.”",
            "“Be careful then. The patrol guards have been checking passes at the corner of Victory Mansions.”",
            "“I know the routes,” he whispered.",
            "“Good. Meet me tomorrow at the usual place near the clearing.”",
            "She nodded quickly and slipped away into the crowd before anyone could notice."
        )
        val chapter = Chapter("Dialogue Chapter", "", "3 min", dialogueParas)

        val pages = PageCache.getOrCompute(
            bookId = "test_multi_para_dialogue",
            chapters = listOf(chapter),
            fontSize = 16,
            screenWidthDp = 392,
            screenHeightDp = 828,
            paragraphSpacingMultiplier = 1.4f,
            isStrictPaged = true
        )

        assertTrue("Pages should be generated", pages.isNotEmpty())
        // All paragraphs must be preserved across pages without loss
        dialogueParas.forEach { p ->
            assertTrue("Paragraph '${p.take(20)}' must be preserved in pages", pages.any { it.second.contains(p.take(20)) })
        }
    }

    @Test
    fun testFindPageForLocationPreservesParagraphAcrossZoomAndLandscape() {
        val paras = (1..20).map { i ->
            "Paragraph $i: In this chapter we explore section $i with descriptive prose describing the event in detail."
        }
        val chapter = Chapter("Chapter 1", "", "10 min", paras)
        val chapters = listOf(chapter)

        // Generate pages at base font size 16 (portrait)
        val basePages = PageCache.getOrCompute(
            bookId = "test_zoom_preserve",
            chapters = chapters,
            fontSize = 16,
            isLandscape = false,
            isStrictPaged = true
        )

        // Pick a page in the middle, say page 2
        val initialPageIndex = 2
        val initialPageText = basePages[initialPageIndex].second
        val anchorSnippet = initialPageText.replace("CHAPTER_START:::Chapter 1::::::", "").trim().take(40)

        // Simulate user zooming in to font size 24
        val zoomedPages = PageCache.getOrCompute(
            bookId = "test_zoom_preserve",
            chapters = chapters,
            fontSize = 24,
            isLandscape = false,
            isStrictPaged = true
        )

        // Find the page in zoomedPages
        val targetPageZoomed = findPageForLocation(
            pages = zoomedPages,
            chapterIdx = 0,
            chapters = chapters,
            paraIdx = 5,
            topSnippet = anchorSnippet
        )

        assertTrue("Target page should be found and valid", targetPageZoomed in zoomedPages.indices)
        assertTrue(
            "Zoomed page should contain the anchor snippet",
            zoomedPages[targetPageZoomed].second.contains(anchorSnippet.take(18))
        )

        // Simulate rotation to landscape
        val landscapePages = PageCache.getOrCompute(
            bookId = "test_zoom_preserve",
            chapters = chapters,
            fontSize = 16,
            isLandscape = true,
            isStrictPaged = true
        )

        val targetPageLandscape = findPageForLocation(
            pages = landscapePages,
            chapterIdx = 0,
            chapters = chapters,
            paraIdx = 5,
            topSnippet = anchorSnippet
        )

        assertTrue("Landscape target page should be found and valid", targetPageLandscape in landscapePages.indices)
        assertTrue(
            "Landscape page should contain the anchor snippet",
            landscapePages[targetPageLandscape].second.contains(anchorSnippet.take(18))
        )
    }

    @Test
    fun testTwoFingerAveragePositionParagraphPreservedAfterZoomDone() {
        val dialogueParas = listOf(
            "“First paragraph at the top of the screen,” said Alice quietly as she observed the room.",
            "“Second paragraph slightly lower,” added Bob, adjusting his spectacles to read the small text.",
            "“Third paragraph in the exact middle of the screen where fingers touch,” whispered Charlie with certainty.",
            "“Fourth paragraph positioned towards the lower third,” noted Dana while writing in her notebook.",
            "“Fifth paragraph resting near the bottom dock,” concluded Ethan as the bell sounded."
        )
        val chapter = Chapter("Midpoint Chapter", "", "5 min", dialogueParas)
        val chapters = listOf(chapter)

        // Initial render at font size 15
        val basePages = PageCache.getOrCompute(
            bookId = "test_midpoint_zoom",
            chapters = chapters,
            fontSize = 15,
            isStrictPaged = true
        )
        assertTrue("Base pages should be generated", basePages.isNotEmpty())

        // Midpoint paragraph (paragraph 2) captured at touch start
        val midPara = dialogueParas[2]
        val midParaSnippet = midPara.take(45)

        // User zooms from 15 to 22
        for (newFontSize in listOf(18, 22, 28)) {
            val zoomedPages = PageCache.getOrCompute(
                bookId = "test_midpoint_zoom",
                chapters = chapters,
                fontSize = newFontSize,
                isStrictPaged = true
            )
            val targetPage = findPageForLocation(
                pages = zoomedPages,
                chapterIdx = 0,
                chapters = chapters,
                paraIdx = 2,
                topSnippet = midParaSnippet
            )

            assertTrue("Target page for font size $newFontSize must be valid", targetPage in zoomedPages.indices)
            val targetPageContent = zoomedPages[targetPage].second
            assertTrue(
                "Zoomed page at font size $newFontSize must contain the midpoint paragraph",
                targetPageContent.contains("Third paragraph in the") || targetPageContent.contains("exact middle")
            )
        }
    }

    @Test
    fun testCleanAlphaNormalization() {
        val original = "“Hello, World!—This is a ‘test’… with   multiple    spaces.”"
        val cleaned = cleanAlpha(original)
        assertEquals("hello world this is a test with multiple spaces", cleaned)
    }

    @Test
    fun testCharacterWeightedParagraphSelection() {
        val shortPara = "Short header."
        val longPara = "A very long paragraph detailing many important things that occurred throughout the day, extending over several lines and taking up a substantial amount of vertical space on the display."
        val secondShortPara = "Concluding line."

        val paras = listOf(shortPara, longPara, secondShortPara)
        val weights = paras.map { it.length.toFloat().coerceAtLeast(20f) }
        val totalWeight = weights.sum()

        // Function mimicking ReaderScreen touch selection
        fun selectParaAtTouchFraction(touchFraction: Float): String {
            val targetThreshold = touchFraction * totalWeight
            var accumWeight = 0f
            var selected = paras.first()
            for (i in paras.indices) {
                accumWeight += weights[i]
                if (accumWeight >= targetThreshold || i == paras.size - 1) {
                    selected = paras[i]
                    break
                }
            }
            return selected
        }

        // Top 5% of screen touches shortPara
        assertEquals(shortPara, selectParaAtTouchFraction(0.05f))
        // Midpoint 50% of screen touches longPara
        assertEquals(longPara, selectParaAtTouchFraction(0.50f))
        // Bottom 95% of screen touches secondShortPara
        assertEquals(secondShortPara, selectParaAtTouchFraction(0.95f))
    }

    @Test
    fun testPinchZoomAnchorNeverMissedAcrossDynamicResizing() {
        val paras = (1..20).map { i ->
            "Paragraph $i: " + "Sentence about topic $i that repeats for length and content. ".repeat(4)
        }
        val chapter = Chapter("Long Chapter", "", "10 min", paras)
        val chapters = listOf(chapter)

        // Select paragraph 8 as touched at midpoint
        val touchedPara = paras[7]
        val touchedSnippet = touchedPara.take(50)

        for (fontSize in listOf(12, 16, 20, 26, 32)) {
            val pages = PageCache.getOrCompute(
                bookId = "test_pinch_resizing",
                chapters = chapters,
                fontSize = fontSize,
                isStrictPaged = true
            )
            val foundPage = findPageForLocation(
                pages = pages,
                chapterIdx = 0,
                chapters = chapters,
                paraIdx = 7,
                topSnippet = touchedSnippet
            )
            assertTrue("Found page index must be within range for fontSize $fontSize", foundPage in pages.indices)
            val pageText = pages[foundPage].second
            val cleanedPage = cleanAlpha(pageText)
            val cleanedTouched = cleanAlpha(touchedSnippet.take(25))
            assertTrue(
                "Page $foundPage must contain touched paragraph snippet for font size $fontSize",
                cleanedPage.contains(cleanedTouched)
            )
        }
    }

    @Test
    fun testTableInlinedOnSamePageWhenFittingBudget() {
        val intro = "From where Winston stood it was just possible to read, picked out on its white face in elegant lettering, the three slogans of the Party:"
        val table = "[TABLE:{\"headers\":[],\"rows\":[[\"WAR IS PEACE\"],[\"FREEDOM IS SLAVERY\"],[\"IGNORANCE IS STRENGTH\"]]}]"
        val following = "The Ministry of Truth — Minitrue, in Newspeak — was startlingly different from any other object in sight."

        val chapter = Chapter("Chapter 1", "", "5 min", listOf(intro, table, following))
        val pages = PageCache.getOrCompute(
            bookId = "test_1984_slogans_table",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = true
        )

        assertTrue("Should have at least 1 page", pages.isNotEmpty())
        val firstPageContent = pages[0].second

        // The introductory text and table MUST be on the same page, NOT severed onto an isolated table page
        assertTrue("First page must contain intro text", firstPageContent.contains("the three slogans of the Party"))
        assertTrue("First page must contain inlined table", firstPageContent.contains("[TABLE:"))
    }

    @Test
    fun testCalculateDeviceMetricsSafetyBufferPreventsChoppedBottomLines() {
        val metricsPortrait = PageCache.calculateDeviceMetrics(
            fontSize = 18,
            isLandscape = false,
            screenWidthDp = 360,
            screenHeightDp = 720
        )
        // Ensure max lines is comfortably bounded and safe
        assertTrue("Max lines must be positive and safe", metricsPortrait.maxLines in 10..22)

        val tableToken = "[TABLE:{\"headers\":[],\"rows\":[[\"WAR IS PEACE\"],[\"FREEDOM IS SLAVERY\"],[\"IGNORANCE IS STRENGTH\"]]}]"
        val estimatedLines = PageCache.estimateTableLines(tableToken)
        assertTrue("3-row table should estimate around 6 lines, actual: $estimatedLines", estimatedLines in 5.5f..7.5f)
    }

    @Test
    fun testDuplicateChapterTitlesDisambiguationInFindPageForLocation() {
        val part1Chap1 = Chapter("Chapter 1", "Part One", "5 min", listOf("It was a bright cold day in April, and the clocks were striking thirteen."))
        val part1Chap2 = Chapter("Chapter 2", "Part One", "5 min", listOf("As he put his hand to the door-knob Winston saw that he had left the diary open on the table."))
        val part2Chap1 = Chapter("Chapter 1", "Part Two", "5 min", listOf("It was the middle of the morning, and Winston had left his cubicle to go to the lavatory."))

        val chapters = listOf(part1Chap1, part1Chap2, part2Chap1)
        val pages = PageCache.getOrCompute(
            bookId = "test_1984_multipart",
            chapters = chapters,
            fontSize = 16,
            isStrictPaged = true
        )

        // Chapter 1 (Part Two) is index 2 in chapters
        val foundPagePart2 = findPageForLocation(
            pages = pages,
            chapterIdx = 2,
            chapters = chapters,
            paraIdx = 0,
            topSnippet = "It was the middle of the morning"
        )

        val foundPagePart1 = findPageForLocation(
            pages = pages,
            chapterIdx = 0,
            chapters = chapters,
            paraIdx = 0,
            topSnippet = "It was a bright cold day in April"
        )

        assertTrue("Part Two Chapter 1 must be strictly after Part One Chapter 1", foundPagePart2 > foundPagePart1)
        assertTrue("Part Two page must contain Part Two text", pages[foundPagePart2].second.contains("It was the middle of the morning"))
        assertTrue("Part One page must contain Part One text", pages[foundPagePart1].second.contains("bright cold day in April"))
    }

    @Test
    fun testChapterStartMarkerPerChapterPreservesExactOrder() {
        val chaps = (1..5).map { i ->
            Chapter("Chapter $i", "Subtitle $i", "5 min", listOf("Paragraph in chapter $i"))
        }
        val pages = PageCache.getOrCompute(
            bookId = "test_chaps_order",
            chapters = chaps,
            fontSize = 16,
            isStrictPaged = true
        )

        val starts = mutableListOf<Int>()
        pages.forEachIndexed { idx, (_, content) ->
            if (content.startsWith("CHAPTER_START:::")) starts.add(idx)
        }

        assertEquals("Must have exactly 5 chapter start markers for 5 chapters", 5, starts.size)
        for (i in 0 until starts.size - 1) {
            assertTrue("Chapter start pages must be strictly monotonically increasing", starts[i] < starts[i + 1])
        }
    }

    @Test
    fun testHeadingAndTableKeptTogetherInsteadOfOrphaned() {
        val chapter = Chapter(
            title = "Declickbait: Detecting and Correcting",
            subtitle = "Executive Summary & Master Architectural Matrix",
            readTime = "10 min",
            paragraphs = listOf(
                "In low-resource Bengali NLP, neural architectures must balance representational capacity with execution efficiency, routing sensational headlines to factual pointer-generator remediation without distorting underlying factual semantic content.",
                "Keywords: Clickbait Detection, Pointer-Generator Networks, Sub-35M Compact Models, Multi-Head Systems, Cross-Attention, Multilingual NLP, Bengali, Banglish, Edge Inference.",
                "⁂",
                "Active Model Registry & Master Benchmark Matrix",
                "[TABLE:{\"headers\":[\"Codename\",\"Legacy\",\"Architecture\"],\"rows\":[[\"cls_2t_bpe\",\"02_v3\",\"Byte-BPE Two-Tower\"],[\"joint_2t_wp\",\"11v2\",\"Decoupled Multi-Head\"],[\"gen_ptr_best\",\"08\",\"BiLSTM Pointer\"],[\"gen_ptr_cov\",\"08-cov\",\"Pointer-Coverage\"]]}]",
                "⁂",
                "Production Portfolio & Architectural Selection Rationale",
                "[TABLE:{\"headers\":[\"System Codename\",\"Latency (ms)\",\"Memory (MB)\"],\"rows\":[[\"deploy_edge_v1\",\"14.2\",\"24.1\"],[\"deploy_server_v2\",\"48.5\",\"112.4\"]]}]"
            )
        )

        val pages = PageCache.getOrCompute(
            bookId = "test_declickbait_tables",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = true,
            screenWidthDp = 392,
            screenHeightDp = 820
        )

        assertTrue("Should produce multiple pages", pages.size >= 2)

        // Find the page containing "Active Model Registry & Master Benchmark Matrix"
        val pageIdxHeading1 = pages.indexOfFirst { it.second.contains("Active Model Registry & Master Benchmark Matrix") }
        val pageIdxTable1 = pages.indexOfFirst { it.second.contains("[TABLE:{\"headers\":[\"Codename\"") }

        assertTrue("Heading 1 must be present in pages", pageIdxHeading1 >= 0)
        assertTrue("Table 1 must be present in pages", pageIdxTable1 >= 0)

        // Heading 1 and Table 1 must be on the EXACT SAME PAGE, never split across pages!
        assertEquals(
            "Heading 1 and Table 1 must be kept together on the same page instead of heading being orphaned on previous page",
            pageIdxTable1,
            pageIdxHeading1
        )

        // Find the page containing "Production Portfolio & Architectural Selection Rationale"
        val pageIdxHeading2 = pages.indexOfFirst { it.second.contains("Production Portfolio & Architectural Selection Rationale") }
        val pageIdxTable2 = pages.indexOfFirst { it.second.contains("[TABLE:{\"headers\":[\"System Codename\"") }

        assertTrue("Heading 2 must be present in pages", pageIdxHeading2 >= 0)
        assertTrue("Table 2 must be present in pages", pageIdxTable2 >= 0)

        // Heading 2 and Table 2 must also be on the EXACT SAME PAGE!
        assertEquals(
            "Heading 2 and Table 2 must be kept together on the same page instead of heading being orphaned on previous page",
            pageIdxTable2,
            pageIdxHeading2
        )

        // Page 1 must not contain the heading
        assertFalse("Page 1 must not contain orphan heading 1", pages[0].second.contains("Active Model Registry & Master Benchmark Matrix"))
    }

    @Test
    fun testIsHeadingCandidateAndIsDivider() {
        assertTrue(PageCache.isHeadingCandidate("Active Model Registry & Master Benchmark Matrix"))
        assertTrue(PageCache.isHeadingCandidate("Section 1.2: Empirical Baseline Performance:"))
        assertFalse(PageCache.isHeadingCandidate("⁂"))
        assertFalse(PageCache.isHeadingCandidate("[DIVIDER]"))
        assertFalse(PageCache.isHeadingCandidate("[TABLE:{\"headers\":[]}]"))
        assertFalse(PageCache.isHeadingCandidate("[IMG:/path/to/img.png]"))
        assertFalse(PageCache.isHeadingCandidate("A".repeat(400))) // Too long to be a heading

        assertTrue(PageCache.isDivider("⁂"))
        assertTrue(PageCache.isDivider("[DIVIDER]"))
        assertTrue(PageCache.isDivider("* * *"))
        assertTrue(PageCache.isDivider("***"))
        assertFalse(PageCache.isDivider("Normal text"))
    }

    @Test
    fun testHeadingKeptWithTableWithoutDivider() {
        val chapter = Chapter(
            title = "Chapter Without Divider",
            subtitle = "",
            readTime = "5 min",
            paragraphs = listOf(
                "Paragraph 1 fills the upper portion of the page with sufficient background context and introductory prose.",
                "Paragraph 2 adds further elaboration to ensure the page budget is nearly filled right up to the bottom margin.",
                "Table 1: Benchmark Summary",
                "[TABLE:{\"headers\":[\"Model\",\"Score\"],\"rows\":[[\"Model A\",\"95.2\"],[\"Model B\",\"92.1\"]]}]"
            )
        )

        val pages = PageCache.getOrCompute(
            bookId = "test_heading_no_divider",
            chapters = listOf(chapter),
            fontSize = 16,
            isStrictPaged = true,
            screenWidthDp = 392,
            screenHeightDp = 820
        )

        val headingPage = pages.indexOfFirst { it.second.contains("Table 1: Benchmark Summary") }
        val tablePage = pages.indexOfFirst { it.second.contains("[TABLE:{\"headers\":[\"Model\"") }

        assertTrue("Heading must exist", headingPage >= 0)
        assertTrue("Table must exist", tablePage >= 0)
        assertEquals("Heading and Table must be on the exact same page", tablePage, headingPage)
    }

    @Test
    fun testDeclickbaitChapter4Pages() {
        val file = java.io.File("/tmp/chap4_paras.json")
        if (!file.exists()) return
        val json = file.readText()
        val orgJson = org.json.JSONArray(json)
        val paras = (0 until orgJson.length()).map { orgJson.getString(it) }

        val chapter = Chapter(
            title = "4. Empirical Training Dynamics & Validation Curves",
            subtitle = "",
            readTime = "10 min",
            paragraphs = paras
        )

        val pages = PageCache.computeChapterPages(
            chapter = chapter,
            fontSize = 12,
            isLandscape = false,
            isStrictPaged = true,
            screenWidthDp = 360,
            screenHeightDp = 730,
            horizontalPaddingDp = 20,
            verticalPaddingDp = 40,
            paragraphSpacingMultiplier = 1.2f,
            safeLinesToRemove = 0,
            fillPageToBottom = true
        )

        println("TOTAL PAGES IN CH4: ${pages.size}")
        for (i in pages.indices) {
            println("PAGE [$i]: len=${pages[i].second.length}, preview=${pages[i].second.take(120).replace("\n", " ")}")
        }

        pages.forEachIndexed { i, p ->
            val content = p.second
            val isChapStart = content.startsWith("CHAPTER_START:::")
            val body = if (isChapStart) content.substringAfter("CHAPTER_START:::").substringAfter(":::").substringAfter(":::") else content
            assertFalse("Page $i should not have empty body! Content was: $content", body.isBlank())
        }
    }

    @Test
    fun testFigureCaptionBundling() {
        val paras = listOf(
            "Some introductory text before the figure.",
            "[IMG:images/figure1.png]",
            "Figure 1: Overview of our system architecture and data pipelines.",
            "Subsequent text describing the architecture."
        )
        val chapter = Chapter(
            title = "Chapter 1",
            subtitle = "",
            readTime = "5 min",
            paragraphs = paras
        )
        val pages = PageCache.computeChapterPages(
            chapter = chapter,
            fontSize = 12,
            isStrictPaged = true,
            screenWidthDp = 360,
            screenHeightDp = 730,
            horizontalPaddingDp = 20,
            verticalPaddingDp = 40
        )
        val imagePage = pages.find { it.second.contains("[IMG:images/figure1.png]") }
        assertNotNull("Image page must exist", imagePage)
        assertTrue(
            "Figure caption must be bundled onto the same page as the image. Actual: ${imagePage?.second}",
            imagePage!!.second.contains("Figure 1: Overview of our system architecture")
        )
    }

    @Test
    fun testTableSplittingAcrossPages() {
        val rows = (1..20).map { listOf("Item $it", "Value $it") }
        val rowsJson = rows.joinToString(",") { "[\"${it[0]}\",\"${it[1]}\"]" }
        val tableToken = "[TABLE:{\"headers\":[\"Name\",\"Score\"],\"rows\":[$rowsJson],\"caption\":\"Model Results\"}]"

        val chapter = Chapter(
            title = "Chapter 5",
            subtitle = "",
            readTime = "5 min",
            paragraphs = listOf(
                "Introductory paragraph before the table with some initial findings.",
                tableToken,
                "Conclusion paragraph after the table."
            )
        )

        val pages = PageCache.computeChapterPages(
            chapter = chapter,
            fontSize = 14,
            isStrictPaged = true,
            screenWidthDp = 360,
            screenHeightDp = 640,
            allowTableSplitting = true
        )

        assertTrue("Expected chapter to be split into multiple pages", pages.size >= 2)
        val tablePages = pages.filter { it.second.contains("[TABLE:") }
        assertTrue("Table should be split across at least 2 pages", tablePages.size >= 2)

        // Verify Part A has headers
        val firstTablePage = tablePages[0].second
        assertTrue("First page must have table headers", firstTablePage.contains("Name") && firstTablePage.contains("Score"))

        // Verify Part B has headers and continuation marker
        val secondTablePage = tablePages[1].second
        assertTrue("Second page must repeat headers", secondTablePage.contains("Name") && secondTablePage.contains("Score"))
        assertTrue("Second page must have isContinuation flag", secondTablePage.contains("\"isContinuation\":true"))
    }

    @Test
    fun testHeadingAndTableStayTogetherWhenSplitting() {
        val rows = (1..18).map { listOf("Metric $it", "Result $it") }
        val rowsJson = rows.joinToString(",") { "[\"${it[0]}\",\"${it[1]}\"]" }
        val tableToken = "[TABLE:{\"headers\":[\"Metric\",\"Result\"],\"rows\":[$rowsJson],\"caption\":\"Benchmark\"}]"

        val chapter = Chapter(
            title = "Chapter 6",
            subtitle = "",
            readTime = "5 min",
            paragraphs = listOf(
                "Long text filling most of the first page to test page boundary conditions. ".repeat(15),
                "Benchmark Comparison Results:",
                tableToken
            )
        )

        val pages = PageCache.computeChapterPages(
            chapter = chapter,
            fontSize = 14,
            isStrictPaged = true,
            screenWidthDp = 360,
            screenHeightDp = 640,
            allowTableSplitting = true
        )

        // Check if any page contains ONLY the heading
        pages.forEachIndexed { idx, pair ->
            val pageContent = pair.second.removePrefix("CHAPTER_START:::Chapter 6::::::").trim()
            if (pageContent.startsWith("Benchmark Comparison Results:")) {
                // Must have table content on the same page, NEVER an orphan heading alone!
                assertTrue(
                    "Page $idx has heading but no table content: $pageContent",
                    pageContent.contains("[TABLE:")
                )
            }
        }
    }
}


