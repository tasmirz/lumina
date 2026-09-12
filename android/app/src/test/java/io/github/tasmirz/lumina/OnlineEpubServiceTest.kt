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

    @Test
    fun testParseOpdsFeed() {
        val opdsXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Standard Ebooks Catalog</title>
              <entry>
                <id>urn:uuid:12345</id>
                <title>The Time Machine</title>
                <author>
                  <name>H. G. Wells</name>
                </author>
                <link rel="http://opds-spec.org/image/thumbnail" href="https://example.com/cover.jpg" type="image/jpeg" />
                <link rel="http://opds-spec.org/acquisition" href="https://example.com/timemachine.epub" type="application/epub+zip" />
              </entry>
              <entry>
                <id>urn:uuid:67890</id>
                <title>The Invisible Man</title>
                <author>
                  <name>H. G. Wells</name>
                </author>
                <link rel="http://opds-spec.org/acquisition" href="/ebooks/invisible-man.epub" type="application/epub+zip" />
              </entry>
            </feed>
        """.trimIndent()

        val endpoint = io.github.tasmirz.lumina.model.CustomCatalogEndpoint(
            id = "test-endpoint-1",
            name = "My OPDS",
            galleryUrl = "https://example.com/opds/feed",
            searchUrl = "https://example.com/opds/search?q=%s"
        )
        val books = OnlineEpubService.parseOpdsFeed(
            xml = opdsXml,
            baseUrl = "https://example.com/opds/feed",
            endpoint = endpoint
        )

        assertEquals(2, books.size)
        assertEquals("The Time Machine", books[0].title)
        assertEquals("H. G. Wells", books[0].author)
        assertEquals("https://example.com/cover.jpg", books[0].coverUrl)
        assertEquals("https://example.com/timemachine.epub", books[0].epubDownloadUrl)
        assertEquals(OnlineCatalogSource.CUSTOM, books[0].source)
        assertEquals("My OPDS", books[0].customSourceName)

        assertEquals("The Invisible Man", books[1].title)
        assertEquals("https://example.com/ebooks/invisible-man.epub", books[1].epubDownloadUrl)
    }

    @Test
    fun testParseJsonFeedArray() {
        val jsonArray = """
            [
              {
                "id": "book-1",
                "title": "Foundation",
                "author": "Isaac Asimov",
                "cover": "https://example.com/foundation.jpg",
                "download_url": "https://example.com/foundation.epub"
              },
              {
                "id": "book-2",
                "name": "Dune",
                "creator": "Frank Herbert",
                "epub": "https://example.com/dune.epub"
              }
            ]
        """.trimIndent()

        val endpoint = io.github.tasmirz.lumina.model.CustomCatalogEndpoint(
            id = "test-json-1",
            name = "Sci-Fi Feed",
            galleryUrl = "https://example.com/books.json",
            searchUrl = "https://example.com/search?q=%s"
        )
        val books = OnlineEpubService.parseJsonFeed(
            jsonStr = jsonArray,
            baseUrl = "https://example.com",
            endpoint = endpoint
        )

        assertEquals(2, books.size)
        assertEquals("Foundation", books[0].title)
        assertEquals("Isaac Asimov", books[0].author)
        assertEquals("https://example.com/foundation.epub", books[0].epubDownloadUrl)
        assertEquals("Sci-Fi Feed", books[0].customSourceName)

        assertEquals("Dune", books[1].title)
        assertEquals("Frank Herbert", books[1].author)
        assertEquals("https://example.com/dune.epub", books[1].epubDownloadUrl)
    }

    @Test
    fun testParseJsonFeedObjectWithBooksList() {
        val jsonObject = """
            {
              "total": 1,
              "books": [
                {
                  "title": "Neuromancer",
                  "author": "William Gibson",
                  "downloadUrl": "https://example.com/neuromancer.epub"
                }
              ]
            }
        """.trimIndent()

        val endpoint = io.github.tasmirz.lumina.model.CustomCatalogEndpoint(
            id = "test-json-2",
            name = "Cyberpunk Feed",
            galleryUrl = "https://example.com/feed.json",
            searchUrl = "https://example.com/search?q=%s"
        )
        val books = OnlineEpubService.parseJsonFeed(
            jsonStr = jsonObject,
            baseUrl = "https://example.com",
            endpoint = endpoint
        )

        assertEquals(1, books.size)
        assertEquals("Neuromancer", books[0].title)
        assertEquals("William Gibson", books[0].author)
        assertEquals("https://example.com/neuromancer.epub", books[0].epubDownloadUrl)
    }

    @Test
    fun testCustomEndpointUrlMatching() {
        val ep1 = io.github.tasmirz.lumina.model.CustomCatalogEndpoint(
            id = "calibre-1",
            name = "Home Calibre",
            galleryUrl = "http://192.168.1.50:8083/opds",
            searchUrl = "http://192.168.1.50:8083/opds/search?query=%s",
            apiKey = "admin:secret",
            authHeader = "Authorization"
        )
        OnlineEpubService.customEndpoints = listOf(ep1)

        val matched = OnlineEpubService.getCustomEndpointForUrl("http://192.168.1.50:8083/opds/download/123.epub")
        assertEquals(ep1.id, matched?.id)
        assertEquals("admin:secret", matched?.apiKey)

        val nonMatched = OnlineEpubService.getCustomEndpointForUrl("https://standardebooks.org/ebooks/123.epub")
        assertEquals(null, nonMatched)
    }
}
