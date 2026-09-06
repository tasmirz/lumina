package org.protidhoni.lumina.data

import android.content.Context
import org.protidhoni.lumina.model.Book
import org.protidhoni.lumina.model.Chapter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

object EpubParser {

    /**
     * Parses an EPUB InputStream into a Book domain model.
     * Robustly extracts OPF metadata (title, author, cover, spine order) and unpacks covers & inline images.
     */
    fun parseEpub(inputStream: InputStream, filename: String, context: Context? = null): Book {
        val bookId = "custom-${System.currentTimeMillis()}"
        val entries = mutableMapOf<String, ByteArray>()

        // 1. Read all zip entries into memory with normalized forward slashes
        try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normalizedName = entry.name.replace('\\', '/').removePrefix("/")
                    entries[normalizedName] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        fun findEntryKey(targetPath: String): String? {
            val clean = try { URLDecoder.decode(targetPath.split("#")[0].split("?")[0].removePrefix("/"), "UTF-8") } catch (_: Exception) { targetPath.removePrefix("/") }
            val filenameOnly = clean.substringAfterLast('/')

            // 1. Exact match
            entries.keys.firstOrNull { it.equals(clean, ignoreCase = true) }?.let { return it }
            // 2. Relative suffix match
            entries.keys.firstOrNull { it.endsWith("/$clean", ignoreCase = true) }?.let { return it }
            // 3. Filename match
            entries.keys.firstOrNull { it.substringAfterLast('/').equals(filenameOnly, ignoreCase = true) }?.let { return it }
            return null
        }

        // 2. Locate OPF file path from META-INF/container.xml
        var opfPath = ""
        val containerKey = findEntryKey("META-INF/container.xml")
        if (containerKey != null) {
            val containerXml = String(entries[containerKey] ?: ByteArray(0), Charsets.UTF_8)
            val rootfileMatch = "full-path=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(containerXml)
            if (rootfileMatch != null) {
                opfPath = rootfileMatch.groupValues[1].replace('\\', '/').removePrefix("/")
            }
        }
        if (opfPath.isBlank()) {
            opfPath = entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) } ?: ""
        }

        val opfEntryKey = if (opfPath.isNotBlank()) findEntryKey(opfPath) else null
        val opfContent = if (opfEntryKey != null) String(entries[opfEntryKey] ?: ByteArray(0), Charsets.UTF_8) else ""
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast('/') else ""

        // 3. Extract Metadata: Title & Author
        var title = ""
        var author = ""
        var coverIdFromMeta = ""

        if (opfContent.isNotBlank()) {
            val titleMatch = "<dc:title[^>]*>([^<]+)</dc:title>".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
            if (titleMatch != null) {
                title = decodeHtmlEntities(titleMatch.groupValues[1].trim())
            }

            val authorMatch = "<dc:creator[^>]*>([^<]+)</dc:creator>".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
            if (authorMatch != null) {
                author = decodeHtmlEntities(authorMatch.groupValues[1].trim())
            }

            val metaCoverMatch = "<meta[^>]+name=[\"']cover[\"'][^>]+content=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
                ?: "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']cover[\"']".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
            if (metaCoverMatch != null) {
                coverIdFromMeta = metaCoverMatch.groupValues[1]
            }
        }

        if (title.isBlank() || title.startsWith("Document:", ignoreCase = true)) {
            title = filename.removeSuffix(".epub").replace("-", " ").replace("_", " ")
                .split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        if (author.isBlank()) {
            author = "Unknown Author"
        }

        // 4. Manifest parsing: id -> href & media-type & properties
        data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)
        val manifestItems = mutableMapOf<String, ManifestItem>()

        if (opfContent.isNotBlank()) {
            val itemRegex = "<item\\s+([^>]+)>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            itemRegex.findAll(opfContent).forEach { match ->
                val attrs = match.groupValues[1]
                val id = "\\bid=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                val href = "\\bhref=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                val mediaType = "\\bmedia-type=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                val properties = "\\bproperties=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifestItems[id] = ManifestItem(id, href, mediaType, properties)
                }
            }
        }

        // 5. Extract Cover Image with Comprehensive Fallbacks
        var coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80"
        var coverHref = ""

        // Tier 1: Check OPF <meta name="cover" content="id">
        if (coverIdFromMeta.isNotBlank() && manifestItems.containsKey(coverIdFromMeta)) {
            val metaItem = manifestItems[coverIdFromMeta]
            if (metaItem != null) {
                if (metaItem.mediaType.startsWith("image/")) {
                    coverHref = metaItem.href
                } else {
                    // Meta cover points to an XHTML cover page (e.g. cover.xhtml)
                    val fullCoverPagePath = resolveZipPath(opfDir, metaItem.href)
                    val coverPageKey = findEntryKey(fullCoverPagePath)
                    if (coverPageKey != null) {
                        val html = String(entries[coverPageKey] ?: ByteArray(0), Charsets.UTF_8)
                        val imgMatch = "<(?:img|image)\\s+[^>]*(?:src|href|xlink:href)=[\"']([^\"']+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
                        if (imgMatch != null) {
                            val pageDir = if (coverPageKey.contains("/")) coverPageKey.substringBeforeLast('/') else ""
                            coverHref = resolveZipPath(pageDir, imgMatch.groupValues[1])
                        }
                    }
                }
            }
        }

        // Tier 2: Check manifest item with properties="cover-image" (EPUB 3)
        if (coverHref.isBlank()) {
            coverHref = manifestItems.values.firstOrNull {
                it.properties.contains("cover-image", ignoreCase = true) && it.mediaType.startsWith("image/")
            }?.href ?: ""
        }

        // Tier 3: Check manifest item with id containing "cover" and image media-type
        if (coverHref.isBlank()) {
            coverHref = manifestItems.values.firstOrNull {
                (it.id.equals("cover-image", ignoreCase = true) || it.id.equals("cover", ignoreCase = true) || it.id.contains("cover", ignoreCase = true)) &&
                it.mediaType.startsWith("image/")
            }?.href ?: ""
        }

        // Tier 4: Check manifest item with href containing "cover" and image media-type
        if (coverHref.isBlank()) {
            coverHref = manifestItems.values.firstOrNull {
                it.href.contains("cover", ignoreCase = true) && it.mediaType.startsWith("image/")
            }?.href ?: ""
        }

        // Tier 5: Check first spine item XHTML page for an illustration
        if (coverHref.isBlank()) {
            val firstSpineItem = manifestItems.values.firstOrNull {
                (it.href.contains("cover", ignoreCase = true) || it.href.contains("title", ignoreCase = true)) &&
                (it.mediaType.contains("html") || it.href.endsWith(".xhtml") || it.href.endsWith(".html"))
            }
            if (firstSpineItem != null) {
                val fullSpinePage = resolveZipPath(opfDir, firstSpineItem.href)
                val pageKey = findEntryKey(fullSpinePage)
                if (pageKey != null) {
                    val html = String(entries[pageKey] ?: ByteArray(0), Charsets.UTF_8)
                    val imgMatch = "<(?:img|image)\\s+[^>]*(?:src|href|xlink:href)=[\"']([^\"']+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
                    if (imgMatch != null) {
                        val pageDir = if (pageKey.contains("/")) pageKey.substringBeforeLast('/') else ""
                        coverHref = resolveZipPath(pageDir, imgMatch.groupValues[1])
                    }
                }
            }
        }

        // Tier 6: Check raw zip entries for any image file with "cover" in name
        if (coverHref.isBlank()) {
            val coverEntry = entries.keys.firstOrNull { key ->
                val lower = key.lowercase()
                lower.contains("cover") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
            }
            if (coverEntry != null) {
                coverHref = coverEntry
            }
        }

        // Tier 7: Pick very first image in manifest or zip
        if (coverHref.isBlank()) {
            coverHref = manifestItems.values.firstOrNull { it.mediaType.startsWith("image/") }?.href
                ?: entries.keys.firstOrNull { key ->
                    val lower = key.lowercase()
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                } ?: ""
        }

        // Extract and persist cover image to app storage
        if (coverHref.isNotBlank() && context != null) {
            val fullCoverPath = resolveZipPath(opfDir, coverHref)
            val coverKey = findEntryKey(fullCoverPath)
            if (coverKey != null) {
                val coverBytes = entries[coverKey]
                if (coverBytes != null && coverBytes.isNotEmpty()) {
                    try {
                        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                        val ext = if (coverKey.endsWith(".png", ignoreCase = true)) "png" else "jpg"
                        val coverFile = File(coversDir, "${bookId}_cover.$ext")
                        FileOutputStream(coverFile).use { it.write(coverBytes) }
                        coverUrl = coverFile.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 6. Spine parsing: Ordered sequence of chapters
        val spineHrefs = mutableListOf<String>()
        if (opfContent.isNotBlank()) {
            val itemrefRegex = "<itemref\\s+[^>]*idref=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
            itemrefRegex.findAll(opfContent).forEach { match ->
                val idref = match.groupValues[1]
                val item = manifestItems[idref]
                if (item != null && (item.mediaType.contains("html") || item.href.endsWith(".html") || item.href.endsWith(".xhtml") || item.href.endsWith(".htm"))) {
                    spineHrefs.add(item.href)
                }
            }
        }

        val chapterPaths = if (spineHrefs.isNotEmpty()) {
            spineHrefs.map { resolveZipPath(opfDir, it) }
        } else {
            entries.keys.filter {
                it.endsWith(".xhtml", ignoreCase = true) ||
                it.endsWith(".html", ignoreCase = true) ||
                it.endsWith(".htm", ignoreCase = true)
            }.sorted()
        }

        // 7. Extract Content & Inline Images per Chapter
        val imagesDir = if (context != null) File(context.filesDir, "books/$bookId/images").apply { mkdirs() } else null
        val extractedChapters = mutableListOf<Chapter>()

        chapterPaths.forEachIndexed { chapIdx, fullPath ->
            val entryKey = findEntryKey(fullPath)
            if (entryKey != null) {
                val rawHtml = String(entries[entryKey] ?: ByteArray(0), Charsets.UTF_8)
                val currentFileDir = if (entryKey.contains("/")) entryKey.substringBeforeLast('/') else ""

                // Extract Chapter Title
                var chapterTitle = ""
                val h1Match = "<h[1-3][^>]*>([^<]+)</h[1-3]>".toRegex(RegexOption.IGNORE_CASE).find(rawHtml)
                if (h1Match != null) {
                    chapterTitle = decodeHtmlEntities(h1Match.groupValues[1].trim())
                }
                if (chapterTitle.isBlank()) {
                    val titleTagMatch = "<title[^>]*>([^<]+)</title>".toRegex(RegexOption.IGNORE_CASE).find(rawHtml)
                    if (titleTagMatch != null) {
                        val t = decodeHtmlEntities(titleTagMatch.groupValues[1].trim())
                        if (!t.equals(title, ignoreCase = true)) {
                            chapterTitle = t
                        }
                    }
                }
                if (chapterTitle.isBlank()) {
                    chapterTitle = "Chapter ${chapIdx + 1}"
                }

                // Process inline images: replace <img ...> and <image ...> with [IMG:path]
                var processedHtml = rawHtml
                if (imagesDir != null) {
                    val imgRegex = "<(?:img|image)\\s+[^>]*(?:src|href|xlink:href)=[\"']([^\"']+)[\"'][^>]*>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    processedHtml = imgRegex.replace(rawHtml) { m ->
                        val imgSrc = m.groupValues[1]
                        val fullImgPath = resolveZipPath(currentFileDir, imgSrc)
                        val imgEntryKey = findEntryKey(fullImgPath)
                        if (imgEntryKey != null) {
                            val imgBytes = entries[imgEntryKey]
                            if (imgBytes != null && imgBytes.isNotEmpty()) {
                                try {
                                    val safeFileName = imgEntryKey.substringAfterLast('/').replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                                    val localImgFile = File(imagesDir, "${bookId}_$safeFileName")
                                    if (!localImgFile.exists()) {
                                        FileOutputStream(localImgFile).use { it.write(imgBytes) }
                                    }
                                    "\n\n[IMG:${localImgFile.absolutePath}]\n\n"
                                } catch (_: Exception) {
                                    ""
                                }
                            } else ""
                        } else ""
                    }
                }

                // Convert HTML to clean readable paragraphs
                val paras = cleanHtmlToParagraphs(processedHtml)
                if (paras.isNotEmpty()) {
                    val wordCount = paras.joinToString(" ").split("\\s+".toRegex()).size
                    val readTimeMins = maxOf(wordCount / 200, 1)

                    extractedChapters.add(
                        Chapter(
                            title = chapterTitle,
                            subtitle = "Section ${chapIdx + 1}",
                            readTime = "$readTimeMins mins",
                            paragraphs = paras
                        )
                    )
                }
            }
        }

        val finalChapters = if (extractedChapters.isNotEmpty()) {
            extractedChapters
        } else {
            listOf(
                Chapter(
                    title = "Chapter 1",
                    subtitle = "Opening",
                    readTime = "5 mins",
                    paragraphs = listOf("Unable to extract text from this EPUB file or file is empty.")
                )
            )
        }

        return Book(
            id = bookId,
            title = title,
            author = author,
            coverUrl = coverUrl,
            lastRead = "Just added",
            progress = 0,
            readTimeLeft = "${finalChapters.size * 8}m left",
            chapters = finalChapters
        )
    }

    private fun resolveZipPath(baseDir: String, relativeHref: String): String {
        val cleanHref = try { URLDecoder.decode(relativeHref.split("#")[0].split("?")[0], "UTF-8") } catch (_: Exception) { relativeHref.split("#")[0].split("?")[0] }
        if (cleanHref.startsWith("/")) return cleanHref.removePrefix("/")
        if (baseDir.isBlank()) return cleanHref

        val parts = "$baseDir/$cleanHref".split("/").filter { it.isNotBlank() }
        val resolved = mutableListOf<String>()
        for (part in parts) {
            if (part == "..") {
                if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
            } else if (part != ".") {
                resolved.add(part)
            }
        }
        return resolved.joinToString("/")
    }

    private fun cleanHtmlToParagraphs(html: String): List<String> {
        val text = html
            .replace("(?i)<script.*?>.*?</script>".toRegex(), "")
            .replace("(?i)<style.*?>.*?</style>".toRegex(), "")
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            .replace("(?i)</(p|div|h[1-6]|section|article|blockquote|li|tr)>".toRegex(), "\n\n")
            .replace("<[^>]+>".toRegex(), "")

        val rawBlocks = text.split("\n\n")
        val result = mutableListOf<String>()

        for (raw in rawBlocks) {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[IMG:") && trimmed.endsWith("]")) {
                result.add(trimmed)
                continue
            }
            // CRITICAL: Replace internal raw newlines and excessive whitespace with a single space
            // This prevents broken, jagged lines in the reading canvas!
            val normalized = decodeHtmlEntities(trimmed)
                .replace("\\s+".toRegex(), " ")
                .trim()

            if (normalized.isNotBlank() && normalized.length > 2) {
                result.add(normalized)
            }
        }
        return result
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&#8212;", "—")
            .replace("&ndash;", "–")
            .replace("&#8211;", "–")
            .replace("&hellip;", "…")
            .replace("&#8230;", "…")
            .replace("&lsquo;", "‘")
            .replace("&#8216;", "‘")
            .replace("&rsquo;", "’")
            .replace("&#8217;", "’")
            .replace("&ldquo;", "“")
            .replace("&#8220;", "“")
            .replace("&rdquo;", "”")
            .replace("&#8221;", "”")
            .replace("\r", "")
    }
}
