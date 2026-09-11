package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.data.OnlineBookItem
import io.github.tasmirz.lumina.data.OnlineCatalogSource
import io.github.tasmirz.lumina.data.OnlineEpubService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineEpubServiceTest {

    @Test
    fun testExactTitleMatchRanksFirst() {
        val books = listOf(
            OnlineBookItem(
                id = "ia-unrelated",
                title = "Stories About Transylvania",
                author = "Various",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.INTERNET_ARCHIVE
            ),
            OnlineBookItem(
                id = "gb-345",
                title = "Dracula",
                author = "Bram Stoker",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.GUTENBERG,
                downloadCount = 65000
            ),
            OnlineBookItem(
                id = "se-dracula-guest",
                title = "Dracula's Guest",
                author = "Bram Stoker",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.STANDARD_EBOOKS
            )
        )

        val ranked = OnlineEpubService.rankAndFilterResults(books, "Dracula")
        assertEquals(2, ranked.size)
        assertEquals("Dracula", ranked[0].title)
        assertEquals("gb-345", ranked[0].id)
        assertEquals("Dracula's Guest", ranked[1].title)
    }

    @Test
    fun testNonMatchingBooksAreFilteredOut() {
        val books = listOf(
            OnlineBookItem(
                id = "se-1",
                title = "Pride and Prejudice",
                author = "Jane Austen",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.STANDARD_EBOOKS
            ),
            OnlineBookItem(
                id = "ia-garbage-1",
                title = "Annual Report of the Board of Agriculture 1904",
                author = "State of Ohio",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.INTERNET_ARCHIVE
            ),
            OnlineBookItem(
                id = "ol-garbage-2",
                title = "Handbook of Mechanical Engineering",
                author = "John Smith",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.OPEN_LIBRARY
            )
        )

        val ranked = OnlineEpubService.rankAndFilterResults(books, "Pride and Prejudice")
        assertEquals("Only matching books should survive filtering", 1, ranked.size)
        assertEquals("Pride and Prejudice", ranked[0].title)
    }

    @Test
    fun testAuthorMatchRanksHigh() {
        val books = listOf(
            OnlineBookItem(
                id = "gb-1",
                title = "Sense and Sensibility",
                author = "Jane Austen",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.GUTENBERG
            ),
            OnlineBookItem(
                id = "gb-2",
                title = "Emma",
                author = "Jane Austen",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.GUTENBERG
            ),
            OnlineBookItem(
                id = "ia-3",
                title = "The Life and Letters of Jane Austen",
                author = "William Austen-Leigh",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.INTERNET_ARCHIVE
            ),
            OnlineBookItem(
                id = "ia-unrelated",
                title = "Calculus and Analytic Geometry",
                author = "George Thomas",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.INTERNET_ARCHIVE
            )
        )

        val ranked = OnlineEpubService.rankAndFilterResults(books, "Jane Austen")
        assertEquals(3, ranked.size)
        assertFalse("Unrelated book should not be present", ranked.any { it.title.contains("Calculus") })
        assertTrue("Jane Austen books must be included", ranked.any { it.title == "Sense and Sensibility" })
    }

    @Test
    fun testMultiTokenRelevance() {
        val books = listOf(
            OnlineBookItem(
                id = "se-1",
                title = "The Picture of Dorian Gray",
                author = "Oscar Wilde",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.STANDARD_EBOOKS
            ),
            OnlineBookItem(
                id = "gb-2",
                title = "Gray Days and Gold in England and Scotland",
                author = "William Winter",
                coverUrl = "",
                epubDownloadUrl = "",
                source = OnlineCatalogSource.GUTENBERG
            )
        )

        val ranked = OnlineEpubService.rankAndFilterResults(books, "Dorian Gray")
        assertEquals("The Picture of Dorian Gray", ranked[0].title)
    }

    @Test
    fun testFormatArchiveAuthorizationHeader() {
        assertEquals(null, OnlineEpubService.formatArchiveAuthorizationHeader(""))
        assertEquals(null, OnlineEpubService.formatArchiveAuthorizationHeader("   "))
        assertEquals("LOW access_key:secret_key", OnlineEpubService.formatArchiveAuthorizationHeader("access_key:secret_key"))
        assertEquals("LOW access_key:secret_key", OnlineEpubService.formatArchiveAuthorizationHeader("  access_key:secret_key  "))
        assertEquals("LOW already_has_low:key", OnlineEpubService.formatArchiveAuthorizationHeader("LOW already_has_low:key"))
        assertEquals("Bearer custom_token", OnlineEpubService.formatArchiveAuthorizationHeader("Bearer custom_token"))
    }
}
