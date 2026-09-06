package org.protidhoni.lumina.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class OnlineCatalogSource(val displayName: String) {
    ALL("All Sources"),
    GUTENBERG("Project Gutenberg"),
    STANDARD_EBOOKS("Standard Ebooks"),
    INTERNET_ARCHIVE("Internet Archive"),
    OPEN_LIBRARY("Open Library")
}

data class OnlineBookItem(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val epubDownloadUrl: String,
    val source: OnlineCatalogSource,
    val downloadCount: Int = 0,
    val tag: String = ""
)

object OnlineEpubService {

    val curatedClassics = listOf(
        OnlineBookItem(
            id = "se-pride-and-prejudice",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            coverUrl = "https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice/downloads/cover-thumbnail.jpg",
            epubDownloadUrl = "https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice/downloads/jane-austen_pride-and-prejudice.epub",
            source = OnlineCatalogSource.STANDARD_EBOOKS,
            downloadCount = 120000,
            tag = "NOVEL"
        ),
        OnlineBookItem(
            id = "se-frankenstein",
            title = "Frankenstein",
            author = "Mary Wollstonecraft Shelley",
            coverUrl = "https://standardebooks.org/ebooks/mary-shelley/frankenstein/downloads/cover-thumbnail.jpg",
            epubDownloadUrl = "https://standardebooks.org/ebooks/mary-shelley/frankenstein/downloads/mary-shelley_frankenstein.epub",
            source = OnlineCatalogSource.STANDARD_EBOOKS,
            downloadCount = 95000,
            tag = "GOTHIC"
        ),
        OnlineBookItem(
            id = "se-dorian-gray",
            title = "The Picture of Dorian Gray",
            author = "Oscar Wilde",
            coverUrl = "https://standardebooks.org/ebooks/oscar-wilde/the-picture-of-dorian-gray/downloads/cover-thumbnail.jpg",
            epubDownloadUrl = "https://standardebooks.org/ebooks/oscar-wilde/the-picture-of-dorian-gray/downloads/oscar-wilde_the-picture-of-dorian-gray.epub",
            source = OnlineCatalogSource.STANDARD_EBOOKS,
            downloadCount = 88000,
            tag = "CLASSIC"
        ),
        OnlineBookItem(
            id = "se-moby-dick",
            title = "Moby Dick; Or, The Whale",
            author = "Herman Melville",
            coverUrl = "https://standardebooks.org/ebooks/herman-melville/moby-dick/downloads/cover-thumbnail.jpg",
            epubDownloadUrl = "https://standardebooks.org/ebooks/herman-melville/moby-dick/downloads/herman-melville_moby-dick.epub",
            source = OnlineCatalogSource.STANDARD_EBOOKS,
            downloadCount = 74000,
            tag = "ADVENTURE"
        ),
        OnlineBookItem(
            id = "gb-345",
            title = "Dracula",
            author = "Bram Stoker",
            coverUrl = "https://www.gutenberg.org/cache/epub/345/pg345.cover.medium.jpg",
            epubDownloadUrl = "https://www.gutenberg.org/ebooks/345.epub3.images",
            source = OnlineCatalogSource.GUTENBERG,
            downloadCount = 65000,
            tag = "HORROR"
        ),
        OnlineBookItem(
            id = "gb-1661",
            title = "The Adventures of Sherlock Holmes",
            author = "Arthur Conan Doyle",
            coverUrl = "https://www.gutenberg.org/cache/epub/1661/pg1661.cover.medium.jpg",
            epubDownloadUrl = "https://www.gutenberg.org/ebooks/1661.epub3.images",
            source = OnlineCatalogSource.GUTENBERG,
            downloadCount = 61000,
            tag = "MYSTERY"
        ),
        OnlineBookItem(
            id = "gb-11",
            title = "Alice's Adventures in Wonderland",
            author = "Lewis Carroll",
            coverUrl = "https://www.gutenberg.org/cache/epub/11/pg11.cover.medium.jpg",
            epubDownloadUrl = "https://www.gutenberg.org/ebooks/11.epub3.images",
            source = OnlineCatalogSource.GUTENBERG,
            downloadCount = 52000,
            tag = "FANTASY"
        )
    )

    suspend fun searchBooks(query: String, source: OnlineCatalogSource = OnlineCatalogSource.ALL): List<OnlineBookItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext if (source == OnlineCatalogSource.ALL) curatedClassics
            else curatedClassics.filter { it.source == source }
        }

        val results = mutableListOf<OnlineBookItem>()

        if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.GUTENBERG) {
            results.addAll(searchGutenberg(query))
        }
        if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.STANDARD_EBOOKS) {
            results.addAll(searchStandardEbooks(query))
        }
        if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.INTERNET_ARCHIVE) {
            results.addAll(searchInternetArchive(query))
        }
        if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.OPEN_LIBRARY) {
            results.addAll(searchOpenLibrary(query))
        }

        if (results.isNotEmpty()) results.distinctBy { "${it.title.lowercase().trim()}::${it.author.lowercase().trim()}" }
        else curatedClassics.filter { source == OnlineCatalogSource.ALL || it.source == source }
    }

    private fun searchGutenberg(query: String): List<OnlineBookItem> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://gutendex.com/books/?search=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            }
            if (conn.responseCode != 200) return emptyList()

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val array = json.optJSONArray("results") ?: return emptyList()
            val list = mutableListOf<OnlineBookItem>()

            for (i in 0 until minOf(array.length(), 10)) {
                val item = array.getJSONObject(i)
                val id = item.optString("id", "")
                val title = item.optString("title", "Untitled")
                val authorsArray = item.optJSONArray("authors")
                val author = if (authorsArray != null && authorsArray.length() > 0) {
                    authorsArray.getJSONObject(0).optString("name", "Unknown")
                        .split(", ").reversed().joinToString(" ")
                } else "Unknown Author"

                val formats = item.optJSONObject("formats")
                val epubUrl = formats?.optString("application/epub+zip", "") ?: ""
                val coverUrl = formats?.optString("image/jpeg", "") ?: ""

                if (epubUrl.isNotBlank()) {
                    list.add(
                        OnlineBookItem(
                            id = "gb-$id",
                            title = title,
                            author = author,
                            coverUrl = coverUrl.ifBlank { "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg" },
                            epubDownloadUrl = epubUrl,
                            source = OnlineCatalogSource.GUTENBERG,
                            downloadCount = item.optInt("download_count", 0),
                            tag = "GUTENBERG"
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchStandardEbooks(query: String): List<OnlineBookItem> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://standardebooks.org/opds/all?query=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            }
            if (conn.responseCode != 200) return emptyList()

            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val list = mutableListOf<OnlineBookItem>()
            val entryMatches = "<entry>(.*?)</entry>".toRegex(RegexOption.DOT_MATCHES_ALL).findAll(xml)

            for (m in entryMatches.take(8)) {
                val entryXml = m.groupValues[1]
                val title = "<title>(.*?)</title>".toRegex().find(entryXml)?.groupValues?.get(1) ?: "Untitled"
                val author = "<name>(.*?)</name>".toRegex().find(entryXml)?.groupValues?.get(1) ?: "Unknown Author"
                val coverUrl = "<link[^>]+rel=[\"'][^\"']*image[^\"']*[\"'][^>]+href=[\"']([^\"']+)[\"']".toRegex().find(entryXml)?.groupValues?.get(1) ?: ""
                val epubUrl = "<link[^>]+type=[\"']application/epub\\+zip[\"'][^>]+href=[\"']([^\"']+)[\"']".toRegex().find(entryXml)?.groupValues?.get(1) ?: ""

                if (epubUrl.isNotBlank()) {
                    val fullEpubUrl = if (epubUrl.startsWith("http")) epubUrl else "https://standardebooks.org$epubUrl"
                    val fullCoverUrl = if (coverUrl.startsWith("http")) coverUrl else "https://standardebooks.org$coverUrl"
                    list.add(
                        OnlineBookItem(
                            id = "se-${list.size}",
                            title = title,
                            author = author,
                            coverUrl = fullCoverUrl,
                            epubDownloadUrl = fullEpubUrl,
                            source = OnlineCatalogSource.STANDARD_EBOOKS,
                            tag = "STANDARD EBOOKS"
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchInternetArchive(query: String): List<OnlineBookItem> {
        return try {
            val encoded = URLEncoder.encode("$query AND mediatype:texts AND format:EPUB", "UTF-8")
            val url = URL("https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier,title,creator,downloads&sort[]=downloads+desc&rows=8&page=1&output=json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            }
            if (conn.responseCode != 200) return emptyList()

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val docs = json.optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
            val list = mutableListOf<OnlineBookItem>()

            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val id = doc.optString("identifier", "")
                val title = doc.optString("title", "Untitled")
                val creator = doc.optString("creator", "Unknown Author")
                if (id.isNotBlank()) {
                    list.add(
                        OnlineBookItem(
                            id = "ia-$id",
                            title = title,
                            author = creator,
                            coverUrl = "https://archive.org/services/img/$id",
                            epubDownloadUrl = "https://archive.org/download/$id/$id.epub",
                            source = OnlineCatalogSource.INTERNET_ARCHIVE,
                            downloadCount = doc.optInt("downloads", 0),
                            tag = "ARCHIVE.ORG"
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchOpenLibrary(query: String): List<OnlineBookItem> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://openlibrary.org/search.json?q=$encoded&has_fulltext=true&limit=8")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            }
            if (conn.responseCode != 200) return emptyList()

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val docs = json.optJSONArray("docs") ?: return emptyList()
            val list = mutableListOf<OnlineBookItem>()

            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val title = doc.optString("title", "Untitled")
                val authorNames = doc.optJSONArray("author_name")
                val author = if (authorNames != null && authorNames.length() > 0) authorNames.getString(0) else "Unknown Author"
                val coverI = doc.optInt("cover_i", 0)
                val iaArray = doc.optJSONArray("ia")
                val iaId = if (iaArray != null && iaArray.length() > 0) iaArray.getString(0) else ""

                if (iaId.isNotBlank()) {
                    list.add(
                        OnlineBookItem(
                            id = "ol-$iaId",
                            title = title,
                            author = author,
                            coverUrl = if (coverI != 0) "https://covers.openlibrary.org/b/id/$coverI-M.jpg" else "https://archive.org/services/img/$iaId",
                            epubDownloadUrl = "https://archive.org/download/$iaId/$iaId.epub",
                            source = OnlineCatalogSource.OPEN_LIBRARY,
                            tag = "OPEN LIBRARY"
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun downloadEpubStream(downloadUrl: String): InputStream? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = downloadUrl
            var redirects = 0
            while (redirects < 6) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 12000
                    readTimeout = 18000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: break
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                    redirects++
                } else if (code == 200) {
                    return@withContext conn.inputStream
                } else {
                    break
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
