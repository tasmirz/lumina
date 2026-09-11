package io.github.tasmirz.lumina.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
            epubDownloadUrl = "https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice/downloads/jane-austen_pride-and-prejudice.epub?source=download",
            source = OnlineCatalogSource.STANDARD_EBOOKS,
            downloadCount = 120000,
            tag = "NOVEL"
        ),
        OnlineBookItem(
            id = "se-frankenstein",
            title = "Frankenstein",
            author = "Mary Wollstonecraft Shelley",
            coverUrl = "https://standardebooks.org/ebooks/mary-shelley/frankenstein/downloads/cover-thumbnail.jpg",
            epubDownloadUrl = "https://standardebooks.org/ebooks/mary-shelley/frankenstein/downloads/mary-shelley_frankenstein.epub?source=download",
            source = OnlineCatalogSource.STANDARD_EBOOKS,
            downloadCount = 95000,
            tag = "GOTHIC"
        ),
        OnlineBookItem(
            id = "se-dorian-gray",
            title = "The Picture of Dorian Gray",
            author = "Oscar Wilde",
            coverUrl = "https://standardebooks.org/ebooks/oscar-wilde/the-picture-of-dorian-gray/downloads/cover-thumbnail.jpg",
            epubDownloadUrl = "https://standardebooks.org/ebooks/oscar-wilde/the-picture-of-dorian-gray/downloads/oscar-wilde_the-picture-of-dorian-gray.epub?source=download",
            source = OnlineCatalogSource.STANDARD_EBOOKS,
            downloadCount = 88000,
            tag = "CLASSIC"
        ),
        OnlineBookItem(
            id = "se-moby-dick",
            title = "Moby Dick; Or, The Whale",
            author = "Herman Melville",
            coverUrl = "https://standardebooks.org/ebooks/herman-melville/moby-dick/downloads/cover-thumbnail.jpg",
            epubDownloadUrl = "https://standardebooks.org/ebooks/herman-melville/moby-dick/downloads/herman-melville_moby-dick.epub?source=download",
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

    private val nsfwKeywords = listOf(
        "erotica", "porn", "xxx", "erotic", "adult", "hentai", "nude", "nudity",
        "bdsm", "fetish", "smut", "sensual romance", "kink", "sex", "erotique", "swinger",
        "explicit", "nsfw", "playboy", "penthouse", "lust", "seduction", "taboo romance",
        "harem", "erotism", "erotismo", "sensual", "naked", "strip", "masturbat"
    )

    private fun isNsfw(title: String, author: String, tag: String): Boolean {
        val combined = "$title $author $tag".lowercase()
        return nsfwKeywords.any { combined.contains(it) }
    }

    suspend fun searchBooks(query: String, source: OnlineCatalogSource = OnlineCatalogSource.ALL): List<OnlineBookItem> = kotlinx.coroutines.coroutineScope {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@coroutineScope if (source == OnlineCatalogSource.ALL) curatedClassics
            else curatedClassics.filter { it.source == source }
        }

        val gutenbergDeferred: kotlinx.coroutines.Deferred<List<OnlineBookItem>>? = if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.GUTENBERG) {
            async(Dispatchers.IO) { kotlinx.coroutines.withTimeoutOrNull(3500) { searchGutenberg(cleanQuery) } ?: emptyList() }
        } else null

        val standardEbooksDeferred: kotlinx.coroutines.Deferred<List<OnlineBookItem>>? = if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.STANDARD_EBOOKS) {
            async(Dispatchers.IO) { kotlinx.coroutines.withTimeoutOrNull(3500) { searchStandardEbooks(cleanQuery) } ?: emptyList() }
        } else null

        val openLibraryDeferred: kotlinx.coroutines.Deferred<List<OnlineBookItem>>? = if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.OPEN_LIBRARY) {
            async(Dispatchers.IO) { kotlinx.coroutines.withTimeoutOrNull(3500) { searchOpenLibrary(cleanQuery) } ?: emptyList() }
        } else null

        val archiveDeferred: kotlinx.coroutines.Deferred<List<OnlineBookItem>>? = if (source == OnlineCatalogSource.ALL || source == OnlineCatalogSource.INTERNET_ARCHIVE) {
            async(Dispatchers.IO) { kotlinx.coroutines.withTimeoutOrNull(3500) { searchInternetArchive(cleanQuery) } ?: emptyList() }
        } else null

        val results = mutableListOf<OnlineBookItem>()
        standardEbooksDeferred?.await()?.let { results.addAll(it) }
        gutenbergDeferred?.await()?.let { results.addAll(it) }
        openLibraryDeferred?.await()?.let { results.addAll(it) }
        archiveDeferred?.await()?.let { results.addAll(it) }

        results
            .filterNot { isNsfw(it.title, it.author, it.tag) }
            .distinctBy { it.id }
    }

    private fun searchGutenberg(query: String): List<OnlineBookItem> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://www.gutenberg.org/ebooks/search/?query=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            }
            if (conn.responseCode != 200) return emptyList()

            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val list = mutableListOf<OnlineBookItem>()
            val regex = "<li class=\"booklink\".*?href=\"/ebooks/(\\d+)\".*?<span class=\"title\">(.*?)</span>(?:.*?<span class=\"subtitle\">(.*?)</span>)?".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))

            for (m in regex.findAll(html).take(15)) {
                val id = m.groupValues[1]
                val rawTitle = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                val author = m.groupValues[3].takeIf { it.isNotBlank() }?.replace(Regex("<[^>]+>"), "")?.trim() ?: "Public Domain"
                if (id.isNotBlank() && rawTitle.isNotBlank()) {
                    list.add(
                        OnlineBookItem(
                            id = "gb-$id",
                            title = rawTitle,
                            author = author,
                            coverUrl = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg",
                            epubDownloadUrl = "https://www.gutenberg.org/ebooks/$id.epub3.images",
                            source = OnlineCatalogSource.GUTENBERG,
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
            val url = URL("https://standardebooks.org/feeds/opds/all?query=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            }
            if (conn.responseCode != 200) return emptyList()

            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val list = mutableListOf<OnlineBookItem>()
            val entryMatches = "<entry>(.*?)</entry>".toRegex(RegexOption.DOT_MATCHES_ALL).findAll(xml)

            for (m in entryMatches.take(12)) {
                val entryXml = m.groupValues[1]
                val title = "<title>(.*?)</title>".toRegex().find(entryXml)?.groupValues?.get(1)?.trim() ?: "Untitled"
                val author = "<name>(.*?)</name>".toRegex().find(entryXml)?.groupValues?.get(1)?.trim() ?: "Unknown Author"

                // Extract cover thumbnail
                val coverMatch = "href=[\"']([^\"']+)[\"'][^>]*rel=[\"'][^\"']*image".toRegex().find(entryXml)
                    ?: "rel=[\"'][^\"']*image[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"']".toRegex().find(entryXml)
                val coverUrl = coverMatch?.groupValues?.get(1) ?: ""

                // Extract epub URL
                val epubMatch = "href=[\"']([^\"']+\\.epub[^\"']*)[\"']".toRegex().find(entryXml)
                    ?: "type=[\"']application/epub\\+zip[\"'][^>]*href=[\"']([^\"']+)[\"']".toRegex().find(entryXml)
                val epubUrl = epubMatch?.groupValues?.get(1) ?: ""

                if (epubUrl.isNotBlank()) {
                    val fullEpubUrl = if (epubUrl.startsWith("http")) epubUrl else "https://standardebooks.org$epubUrl"
                    val fullCoverUrl = if (coverUrl.startsWith("http")) coverUrl else if (coverUrl.isNotBlank()) "https://standardebooks.org$coverUrl" else ""
                    list.add(
                        OnlineBookItem(
                            id = "se-${title.hashCode()}",
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
            val encoded = URLEncoder.encode("title:($query) AND mediatype:texts AND format:(EPUB)", "UTF-8")
            val url = URL("https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier,title,creator,downloads,collection&sort[]=downloads+desc&rows=10&page=1&output=json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            }
            if (conn.responseCode != 200) return emptyList()

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val docs = json.optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
            val list = mutableListOf<OnlineBookItem>()

            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val id = doc.optString("identifier", "")
                val title = doc.optString("title", "Untitled").trim()
                val creator = doc.optString("creator", "Unknown Author").trim()
                if (id.isNotBlank() && title.length > 2 && !title.startsWith("item_") && !title.startsWith("urn:")) {
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
            val url = URL("https://openlibrary.org/search.json?q=$encoded&limit=10")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "LuminaEpubReader/1.0 (https://github.com/tasmirz/lumina)")
            }
            if (conn.responseCode != 200) return emptyList()

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val docs = json.optJSONArray("docs") ?: return emptyList()
            val list = mutableListOf<OnlineBookItem>()

            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val title = doc.optString("title", "Untitled").trim()
                val authorNames = doc.optJSONArray("author_name")
                val author = if (authorNames != null && authorNames.length() > 0) authorNames.getString(0).trim() else "Unknown Author"
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
            var currentUrl = downloadUrl.trim()
            if (currentUrl.contains("standardebooks.org") && !currentUrl.contains("source=download") && currentUrl.endsWith(".epub")) {
                currentUrl = if (currentUrl.contains("?")) "$currentUrl&source=download" else "$currentUrl?source=download"
            }
            var redirects = 0
            while (redirects < 6) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "application/epub+zip,application/octet-stream,*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: break
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                    redirects++
                } else if (code == 200) {
                    val contentType = conn.contentType ?: ""
                    // Handle Standard Ebooks "Your Download Has Started!" intermediate HTML meta-refresh
                    if (contentType.contains("html", ignoreCase = true) || contentType.contains("xhtml", ignoreCase = true)) {
                        val htmlBody = conn.inputStream.bufferedReader().use { it.readText() }
                        val refreshMatch = Regex("""meta[^>]+url=([^"'>\s]+)""", RegexOption.IGNORE_CASE).find(htmlBody)
                        val targetHref = refreshMatch?.groupValues?.getOrNull(1)
                            ?: Regex("""href="([^"]+\.epub(\?source=download)?)"""", RegexOption.IGNORE_CASE).find(htmlBody)?.groupValues?.getOrNull(1)
                        if (!targetHref.isNullOrBlank()) {
                            currentUrl = if (targetHref.startsWith("http")) targetHref else URL(url, targetHref).toString()
                            redirects++
                            continue
                        }
                    }
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
