package io.github.tasmirz.lumina.data

import android.content.Context
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.Chapter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

object EpubParser {

    /**
     * Ultra-fast lightweight metadata-only parser.
     * Uses ZipFile random access to extract ONLY container.xml, content.opf, and the cover image.
     * Completes in ~3-5ms without loading chapter content or decompressing the entire archive.
     */
    fun parseBookMetadata(file: File, context: Context? = null): Book {
        val filename = file.name
        val bookId = "epub-" + (file.nameWithoutExtension.hashCode().toLong() and 0xFFFFFFFFL).toString()
        var title = ""
        var author = "Unknown Author"
        var language = "en"
        var coverUrl = ""

        try {
            java.util.zip.ZipFile(file).use { zip ->
                var containerEntry = zip.getEntry("META-INF/container.xml")
                if (containerEntry == null) {
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.equals("META-INF/container.xml", ignoreCase = true)) {
                            containerEntry = e
                            break
                        }
                    }
                }

                var opfPath = ""
                if (containerEntry != null) {
                    val xml = zip.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).readText()
                    val match = "full-path=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(xml)
                    if (match != null) {
                        opfPath = match.groupValues[1].replace('\\', '/').removePrefix("/")
                    }
                }

                if (opfPath.isBlank()) {
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.endsWith(".opf", ignoreCase = true)) {
                            opfPath = e.name
                            break
                        }
                    }
                }

                if (opfPath.isNotBlank()) {
                    var opfEntry = zip.getEntry(opfPath)
                    if (opfEntry == null) {
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (e.name.equals(opfPath, ignoreCase = true)) {
                                opfEntry = e
                                break
                            }
                        }
                    }

                    if (opfEntry != null) {
                        val opfContent = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).readText()
                        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast('/') else ""

                        val titleMatch = "<dc:title[^>]*>([^<]+)</dc:title>".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
                        if (titleMatch != null) {
                            title = decodeHtmlEntities(titleMatch.groupValues[1].trim())
                        }

                        val authorMatch = "<dc:creator[^>]*>([^<]+)</dc:creator>".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
                        if (authorMatch != null) {
                            author = decodeHtmlEntities(authorMatch.groupValues[1].trim())
                        }

                        val langMatch = "<dc:language[^>]*>([^<]+)</dc:language>".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
                        if (langMatch != null) {
                            val rawLang = langMatch.groupValues[1].trim().lowercase().split("-", "_")[0].trim()
                            language = if (rawLang.length == 2) rawLang else "en"
                        }

                        val metaCoverMatch = "<meta[^>]+name=[\"']cover[\"'][^>]+content=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
                            ?: "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']cover[\"']".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
                        val coverId = metaCoverMatch?.groupValues?.get(1) ?: ""

                        var coverHref = ""
                        val itemRegex = "<item\\s+([^>]+)>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        for (itemMatch in itemRegex.findAll(opfContent)) {
                            val attrs = itemMatch.groupValues[1]
                            val id = "\\bid=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                            val href = "\\bhref=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                            val mediaType = "\\bmedia-type=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                            val props = "\\bproperties=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""

                            if (coverId.isNotBlank() && id.equals(coverId, ignoreCase = true) && mediaType.startsWith("image/")) {
                                coverHref = href
                                break
                            } else if (props.contains("cover-image", ignoreCase = true) && mediaType.startsWith("image/")) {
                                coverHref = href
                                break
                            } else if (coverHref.isBlank() && (id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true)) && mediaType.startsWith("image/")) {
                                coverHref = href
                            }
                        }

                        if (coverHref.isNotBlank() && context != null) {
                            val fullCoverPath = resolveZipPath(opfDir, coverHref)
                            var coverZipEntry = zip.getEntry(fullCoverPath)
                            if (coverZipEntry == null) {
                                val entries = zip.entries()
                                while (entries.hasMoreElements()) {
                                    val e = entries.nextElement()
                                    if (e.name.equals(fullCoverPath, ignoreCase = true) || e.name.substringAfterLast('/').equals(coverHref.substringAfterLast('/'), ignoreCase = true)) {
                                        coverZipEntry = e
                                        break
                                    }
                                }
                            }
                            if (coverZipEntry != null) {
                                val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                                val ext = if (coverZipEntry.name.endsWith(".png", ignoreCase = true)) "png" else "jpg"
                                val coverFile = File(coversDir, "${bookId}_cover.$ext")
                                if (!coverFile.exists() || coverFile.length() == 0L) {
                                    zip.getInputStream(coverZipEntry).use { input ->
                                        FileOutputStream(coverFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                                if (coverFile.exists() && coverFile.length() > 0L) {
                                    coverUrl = coverFile.absolutePath
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (title.isBlank() || title.startsWith("Document:", ignoreCase = true)) {
            val rawName = filename.replace(Regex("^\\d{10,14}_"), "").removeSuffix(".epub")
            title = rawName.replace("-", " ").replace("_", " ")
                .split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }

        return Book(
            id = bookId,
            title = title,
            author = author,
            coverUrl = coverUrl,
            chapters = emptyList(),
            filePath = file.absolutePath,
            fileSize = file.length(),
            language = language,
            isDownloaded = false
        )
    }

    /**
     * Parses an EPUB InputStream into a Book domain model.
     * Robustly extracts OPF metadata, Table of Contents (EPUB 2 NCX & EPUB 3 Nav), covers, and inline images.
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
            val clean = try {
                URLDecoder.decode(targetPath.split("#")[0].split("?")[0].removePrefix("/"), "UTF-8")
            } catch (_: Exception) {
                targetPath.split("#")[0].split("?")[0].removePrefix("/")
            }
            val filenameOnly = clean.substringAfterLast('/')

            // 1. Exact match
            entries.keys.firstOrNull { it.equals(clean, ignoreCase = true) }?.let { return it }
            // 2. Relative suffix match
            entries.keys.firstOrNull { it.endsWith("/$clean", ignoreCase = true) }?.let { return it }
            // 3. Filename match
            entries.keys.firstOrNull { it.substringAfterLast('/').equals(filenameOnly, ignoreCase = true) }?.let { return it }
            return null
        }

        fun resolveZipEntry(baseDir: String, href: String): String? {
            val cleanHref = href.split("#")[0].split("?")[0].trim().removePrefix("/")
            // 1. Check direct entry key
            findEntryKey(cleanHref)?.let { return it }
            // 2. Resolved relative path
            val resolved = resolveZipPath(baseDir, cleanHref)
            findEntryKey(resolved)?.let { return it }
            // 3. Filename fallback
            findEntryKey(cleanHref.substringAfterLast('/'))?.let { return it }
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

        // 3. Extract Metadata: Title, Author & Language
        var title = ""
        var author = ""
        var language = "en"
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

            val langMatch = "<dc:language[^>]*>([^<]+)</dc:language>".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
            if (langMatch != null) {
                val rawLang = langMatch.groupValues[1].trim().lowercase()
                val code = rawLang.split("-", "_")[0].trim()
                language = when (code) {
                    "eng" -> "en"
                    "spa" -> "es"
                    "fra", "fre" -> "fr"
                    "deu", "ger" -> "de"
                    "ita" -> "it"
                    "por" -> "pt"
                    "rus" -> "ru"
                    "ben" -> "bn"
                    "hin" -> "hi"
                    "zho", "chi" -> "zh"
                    "jpn" -> "ja"
                    "kor" -> "ko"
                    "ara" -> "ar"
                    else -> if (code.length == 2) code else "en"
                }
            }

            val metaCoverMatch = "<meta[^>]+name=[\"']cover[\"'][^>]+content=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
                ?: "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']cover[\"']".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
            if (metaCoverMatch != null) {
                coverIdFromMeta = metaCoverMatch.groupValues[1]
            }
        }

        if (title.isBlank() || title.startsWith("Document:", ignoreCase = true)) {
            val rawName = filename.replace(Regex("^\\d{10,14}_"), "").removeSuffix(".epub")
            title = rawName.replace("-", " ").replace("_", " ")
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

        // 5. Extract Cover Image with Comprehensive Multi-Tier Detection
        var coverUrl = ""
        var coverEntryKey: String? = null

        // Tier 1: OPF <meta name="cover" content="id">
        if (coverIdFromMeta.isNotBlank() && manifestItems.containsKey(coverIdFromMeta)) {
            val metaItem = manifestItems[coverIdFromMeta]
            if (metaItem != null) {
                if (metaItem.mediaType.startsWith("image/")) {
                    coverEntryKey = resolveZipEntry(opfDir, metaItem.href)
                } else {
                    // Meta cover points to an XHTML cover page (e.g. cover.xhtml)
                    val coverPageKey = resolveZipEntry(opfDir, metaItem.href)
                    if (coverPageKey != null) {
                        val html = String(entries[coverPageKey] ?: ByteArray(0), Charsets.UTF_8)
                        val pageDir = if (coverPageKey.contains("/")) coverPageKey.substringBeforeLast('/') else ""
                        val imgMatch = "<(?:img|image)\\s+[^>]*(?:xlink:href|src|href)=[\"']([^\"']+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
                        if (imgMatch != null) {
                            coverEntryKey = resolveZipEntry(pageDir, imgMatch.groupValues[1])
                        }
                    }
                }
            }
        }

        // Tier 2: Manifest item with properties="cover-image" (EPUB 3)
        if (coverEntryKey == null) {
            val ep3Item = manifestItems.values.firstOrNull {
                it.properties.contains("cover-image", ignoreCase = true) && it.mediaType.startsWith("image/")
            }
            if (ep3Item != null) {
                coverEntryKey = resolveZipEntry(opfDir, ep3Item.href)
            }
        }

        // Tier 3: OPF <guide><reference type="cover" ...>
        if (coverEntryKey == null && opfContent.isNotBlank()) {
            val guideMatch = "<reference[^>]+type=[\"']cover[\"'][^>]+href=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(opfContent)
            if (guideMatch != null) {
                val gHref = guideMatch.groupValues[1]
                val gKey = resolveZipEntry(opfDir, gHref)
                if (gKey != null) {
                    if (gKey.endsWith(".xhtml", true) || gKey.endsWith(".html", true) || gKey.endsWith(".htm", true)) {
                        val html = String(entries[gKey] ?: ByteArray(0), Charsets.UTF_8)
                        val pageDir = if (gKey.contains("/")) gKey.substringBeforeLast('/') else ""
                        val imgMatch = "<(?:img|image)\\s+[^>]*(?:xlink:href|src|href)=[\"']([^\"']+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
                        if (imgMatch != null) {
                            coverEntryKey = resolveZipEntry(pageDir, imgMatch.groupValues[1])
                        }
                    } else {
                        coverEntryKey = gKey
                    }
                }
            }
        }

        // Tier 4: Manifest item with id or href containing "cover" and image type
        if (coverEntryKey == null) {
            val item = manifestItems.values.firstOrNull {
                (it.id.contains("cover", ignoreCase = true) || it.href.contains("cover", ignoreCase = true)) &&
                it.mediaType.startsWith("image/")
            }
            if (item != null) {
                coverEntryKey = resolveZipEntry(opfDir, item.href)
            }
        }

        // Tier 5: First spine XHTML page image
        if (coverEntryKey == null) {
            val firstSpineItem = manifestItems.values.firstOrNull {
                (it.href.contains("cover", ignoreCase = true) || it.href.contains("title", ignoreCase = true)) &&
                (it.mediaType.contains("html") || it.href.endsWith(".xhtml") || it.href.endsWith(".html"))
            }
            if (firstSpineItem != null) {
                val pageKey = resolveZipEntry(opfDir, firstSpineItem.href)
                if (pageKey != null) {
                    val html = String(entries[pageKey] ?: ByteArray(0), Charsets.UTF_8)
                    val pageDir = if (pageKey.contains("/")) pageKey.substringBeforeLast('/') else ""
                    val imgMatch = "<(?:img|image)\\s+[^>]*(?:xlink:href|src|href)=[\"']([^\"']+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
                    if (imgMatch != null) {
                        coverEntryKey = resolveZipEntry(pageDir, imgMatch.groupValues[1])
                    }
                }
            }
        }

        // Tier 6: Zip entry with "cover" in name
        if (coverEntryKey == null) {
            coverEntryKey = entries.keys.firstOrNull { key ->
                val lower = key.lowercase()
                lower.contains("cover") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
            }
        }

        // Tier 7: Zip entry with "title", "front", or "jacket"
        if (coverEntryKey == null) {
            coverEntryKey = entries.keys.firstOrNull { key ->
                val lower = key.lowercase()
                (lower.contains("title") || lower.contains("front") || lower.contains("jacket")) &&
                (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
            }
        }

        // Tier 8: First image found in manifest or zip
        if (coverEntryKey == null) {
            coverEntryKey = manifestItems.values.firstOrNull { it.mediaType.startsWith("image/") }?.let { resolveZipEntry(opfDir, it.href) }
                ?: entries.keys.firstOrNull { key ->
                    val lower = key.lowercase()
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                }
        }

        // Extract and persist cover image to app storage
        if (coverEntryKey != null && context != null) {
            val coverBytes = entries[coverEntryKey]
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                try {
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                    val ext = if (coverEntryKey.endsWith(".png", ignoreCase = true)) "png" else "jpg"
                    val coverFile = File(coversDir, "${bookId}_cover.$ext")
                    FileOutputStream(coverFile).use { it.write(coverBytes) }
                    coverUrl = coverFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 6. Table of Contents (TOC) Extraction: EPUB 2 (NCX) & EPUB 3 (Nav)
        data class TocEntry(val title: String, val fullHref: String, val filePath: String, val anchor: String = "")
        val tocList = mutableListOf<TocEntry>()

        // 6A. Parse EPUB 2 NCX (toc.ncx)
        val ncxManifestItem = manifestItems.values.firstOrNull {
            it.mediaType.contains("ncx", ignoreCase = true) || it.id.equals("ncx", ignoreCase = true) || it.href.endsWith(".ncx", ignoreCase = true)
        }
        val ncxKey = ncxManifestItem?.let { resolveZipEntry(opfDir, it.href) } ?: entries.keys.firstOrNull { it.endsWith(".ncx", ignoreCase = true) }
        if (ncxKey != null) {
            val ncxXml = String(entries[ncxKey] ?: ByteArray(0), Charsets.UTF_8)
            val ncxDir = if (ncxKey.contains("/")) ncxKey.substringBeforeLast('/') else ""
            val navPointRegex = "<navPoint[^>]*>.*?<navLabel>\\s*<text>([^<]+)</text>\\s*</navLabel>\\s*<content\\s+src=[\"']([^\"']+)[\"']".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            navPointRegex.findAll(ncxXml).forEach { match ->
                val rawTitle = decodeHtmlEntities(match.groupValues[1].trim())
                    .replace("(?i)(Part\\s+\\w+)(Chapter\\s+\\d+)".toRegex(), "$1 — $2")
                val cleanTitle = deduplicateRepeatedHeading(rawTitle)
                val rawSrc = match.groupValues[2].trim()
                val cleanSrc = try { URLDecoder.decode(rawSrc, "UTF-8") } catch (_: Exception) { rawSrc }
                val targetFile = cleanSrc.substringBefore('#')
                val anchor = cleanSrc.substringAfter('#', "")
                val resolvedTarget = resolveZipPath(ncxDir, targetFile)
                if (cleanTitle.isNotBlank()) {
                    tocList.add(TocEntry(cleanTitle, cleanSrc, resolvedTarget, anchor))
                }
            }
        }

        // 6B. Parse EPUB 3 nav.xhtml if NCX had no entries
        if (tocList.isEmpty()) {
            val navItem = manifestItems.values.firstOrNull { it.properties.contains("nav", ignoreCase = true) }
                ?: manifestItems.values.firstOrNull { it.href.endsWith("nav.xhtml", ignoreCase = true) || it.href.endsWith("toc.xhtml", ignoreCase = true) }
            val navKey = navItem?.let { resolveZipEntry(opfDir, it.href) } ?: entries.keys.firstOrNull { it.endsWith("nav.xhtml", ignoreCase = true) || it.endsWith("toc.xhtml", ignoreCase = true) }
            if (navKey != null) {
                val navHtml = String(entries[navKey] ?: ByteArray(0), Charsets.UTF_8)
                val navDir = if (navKey.contains("/")) navKey.substringBeforeLast('/') else ""
                val aRegex = "<a\\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*>([^<]+)</a>".toRegex(RegexOption.IGNORE_CASE)
                aRegex.findAll(navHtml).forEach { match ->
                    val rawSrc = match.groupValues[1].trim()
                    val rawTitle = decodeHtmlEntities(match.groupValues[2].trim())
                        .replace("(?i)(Part\\s+\\w+)(Chapter\\s+\\d+)".toRegex(), "$1 — $2")
                    val cleanTitle = deduplicateRepeatedHeading(rawTitle)
                    val cleanSrc = try { URLDecoder.decode(rawSrc, "UTF-8") } catch (_: Exception) { rawSrc }
                    val targetFile = cleanSrc.substringBefore('#')
                    val anchor = cleanSrc.substringAfter('#', "")
                    val resolvedTarget = resolveZipPath(navDir, targetFile)
                    if (cleanTitle.isNotBlank()) {
                        tocList.add(TocEntry(cleanTitle, cleanSrc, resolvedTarget, anchor))
                    }
                }
            }
        }

        // 7. Spine parsing: Ordered sequence of chapters
        val guideTocHrefs = mutableSetOf<String>()
        val guideCoverHrefs = mutableSetOf<String>()
        if (opfContent.isNotBlank()) {
            val guideRegex = "<reference[^>]+type=[\"']([^\"']+)[\"'][^>]+href=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
            guideRegex.findAll(opfContent).forEach { match ->
                val type = match.groupValues[1].lowercase()
                val href = match.groupValues[2].split("#")[0].split("?")[0].trim()
                if (type == "toc" || type.contains("contents")) {
                    guideTocHrefs.add(href)
                } else if (type == "cover") {
                    guideCoverHrefs.add(href)
                }
            }
        }

        val spineHrefs = mutableListOf<String>()
        if (opfContent.isNotBlank()) {
            val itemrefRegex = "<itemref\\s+([^>]+)>".toRegex(RegexOption.IGNORE_CASE)
            itemrefRegex.findAll(opfContent).forEach { match ->
                val attrs = match.groupValues[1]
                val idref = "\\bidref=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                val linear = "\\blinear=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.lowercase() ?: "yes"
                val item = manifestItems[idref]
                if (item != null && (item.mediaType.contains("html") || item.href.endsWith(".html") || item.href.endsWith(".xhtml") || item.href.endsWith(".htm"))) {
                    val cleanHref = item.href.split("#")[0].split("?")[0].trim()
                    val fileName = cleanHref.substringAfterLast('/').lowercase()

                    // Exclude EPUB 3 Navigation document
                    if (item.properties.contains("nav", ignoreCase = true)) return@forEach

                    // Exclude TOC from <guide>
                    if (cleanHref in guideTocHrefs || guideTocHrefs.any { cleanHref.endsWith(it) || it.endsWith(cleanHref) }) return@forEach

                    // Exclude TOC/Nav files by filename heuristics
                    if (fileName.contains("toc") || fileName.contains("nav") || fileName == "contents.xhtml" || fileName == "contents.html") return@forEach

                    // Exclude non-linear cover pages
                    if (linear == "no" && (fileName.contains("cover") || cleanHref in guideCoverHrefs)) return@forEach

                    spineHrefs.add(item.href)
                }
            }
        }

        val chapterPaths = if (spineHrefs.isNotEmpty()) {
            spineHrefs.map { resolveZipPath(opfDir, it) }
        } else {
            entries.keys.filter {
                (it.endsWith(".xhtml", ignoreCase = true) ||
                it.endsWith(".html", ignoreCase = true) ||
                it.endsWith(".htm", ignoreCase = true)) &&
                !it.contains("toc", ignoreCase = true) &&
                !it.contains("nav", ignoreCase = true)
            }.sorted()
        }

        // 8. Extract Content & Inline Images per Chapter
        val imagesDir = if (context != null) File(context.filesDir, "books/$bookId/images").apply { mkdirs() } else null
        val extractedChapters = mutableListOf<Chapter>()
        var currentPart = ""

        chapterPaths.forEachIndexed { chapIdx, fullPath ->
            val entryKey = resolveZipEntry(opfDir, fullPath)
            if (entryKey != null) {
                val rawHtml = String(entries[entryKey] ?: ByteArray(0), Charsets.UTF_8)
                val currentFileDir = if (entryKey.contains("/")) entryKey.substringBeforeLast('/') else ""

                // Double-check if this file is an embedded TOC page
                if (rawHtml.contains("epub:type=[\"']toc[\"']".toRegex(RegexOption.IGNORE_CASE)) ||
                    (rawHtml.contains("class=[\"'][^\"']*toc[^\"']*[\"']".toRegex(RegexOption.IGNORE_CASE)) && rawHtml.contains("<a\\s+href".toRegex(RegexOption.IGNORE_CASE)))) {
                    return@forEachIndexed
                }

                // Match official Chapter Title from TOC
                val matchedToc = tocList.firstOrNull { toc ->
                    val tocKey = resolveZipEntry("", toc.filePath) ?: findEntryKey(toc.filePath)
                    tocKey != null && tocKey.equals(entryKey, ignoreCase = true)
                } ?: tocList.firstOrNull {
                    it.filePath.substringAfterLast('/').equals(entryKey.substringAfterLast('/'), ignoreCase = true)
                }

                var chapterTitle = matchedToc?.title ?: ""

                // If not in TOC, check HTML headings
                if (chapterTitle.isBlank()) {
                    val h1Match = "<h[1-3][^>]*>([^<]+)</h[1-3]>".toRegex(RegexOption.IGNORE_CASE).find(rawHtml)
                    if (h1Match != null) {
                        chapterTitle = decodeHtmlEntities(h1Match.groupValues[1].trim())
                    }
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
                chapterTitle = deduplicateRepeatedHeading(chapterTitle)
                if (chapterTitle.isBlank()) {
                    chapterTitle = "Chapter ${chapIdx + 1}"
                }

                // Process inline images: replace <img ...> and <image ...> with [IMG:path]
                var processedHtml = rawHtml
                if (imagesDir != null) {
                    val imgRegex = "<(?:img|image)\\s+[^>]*(?:xlink:href|src|href)=[\"']([^\"']+)[\"'][^>]*>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    processedHtml = imgRegex.replace(rawHtml) { m ->
                        val imgSrc = m.groupValues[1]
                        val imgEntryKey = resolveZipEntry(currentFileDir, imgSrc)
                        if (imgEntryKey != null) {
                            val imgBytes = entries[imgEntryKey]
                            if (imgBytes != null && imgBytes.isNotEmpty()) {
                                try {
                                    val safeFileName = imgEntryKey.substringAfterLast('/').replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                                    val localImgFile = File(imagesDir, "${bookId}_$safeFileName")
                                    if (!localImgFile.exists()) {
                                        FileOutputStream(localImgFile).use { it.write(imgBytes) }
                                    }
                                    "\n\n__IMG_TOKEN_START__${localImgFile.absolutePath}__IMG_TOKEN_END__\n\n"
                                } catch (_: Exception) {
                                    ""
                                }
                            } else ""
                        } else ""
                    }
                }

                // Convert HTML to clean readable paragraphs with isolated images
                val paras = cleanHtmlToParagraphs(processedHtml)

                // Detect standalone Part/Section divider page (e.g. only contains "Part One")
                if (paras.size <= 1) {
                    val singleText = paras.firstOrNull()?.trim() ?: ""
                    if (singleText.length in 1..40 && "^(?i)(part|book|volume|section)\\s+\\w+".toRegex().matches(singleText)) {
                        currentPart = deduplicateRepeatedHeading(singleText)
                        return@forEachIndexed
                    }
                    if (singleText.length in 1..30 && (singleText.contains("cover", ignoreCase = true) || singleText.equals(title, ignoreCase = true))) {
                        return@forEachIndexed
                    }
                }

                // Filter out fallback TOC link list
                if (paras.size > 8) {
                    val headingMatchCount = paras.count { p ->
                        p.startsWith("Chapter", ignoreCase = true) || p.startsWith("Part", ignoreCase = true)
                    }
                    if (headingMatchCount > paras.size * 0.5) {
                        return@forEachIndexed
                    }
                }

                // Strip leading heading paragraphs matching chapter title, part name, or book title
                val cleanedParas = paras.toMutableList()
                while (cleanedParas.isNotEmpty() && isHeadingOnly(cleanedParas[0], chapterTitle, title, currentPart)) {
                    cleanedParas.removeAt(0)
                }

                if (cleanedParas.isNotEmpty()) {
                    val wordCount = cleanedParas.filterNot { it.startsWith("[IMG:") }.joinToString(" ").split("\\s+".toRegex()).size
                    val readTimeMins = maxOf(wordCount / 200, 1)

                    val subtitleText = if (currentPart.isNotBlank()) currentPart else "Section ${extractedChapters.size + 1}"

                    extractedChapters.add(
                        Chapter(
                            title = chapterTitle,
                            subtitle = subtitleText,
                            readTime = "$readTimeMins mins",
                            paragraphs = cleanedParas
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
            chapters = finalChapters,
            language = language
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
            .replace("(?i)<script.*?>.*?</script>".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .replace("(?i)<style.*?>.*?</style>".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .replace("(?i)<head.*?>.*?</head>".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            .replace("(?i)</?(p|div|h[1-6]|section|article|blockquote|li|ol|ul|tr|td|th|header|footer|nav|title|table)>".toRegex(), "\n\n")
            .replace("(?i)</(a|span|em|i|b|strong)>".toRegex(), " ")
            .replace("<[^>]+>".toRegex(), " ")

        val tokenRegex = "__IMG_TOKEN_START__(.*?)__IMG_TOKEN_END__".toRegex()
        val textWithImgs = tokenRegex.replace(text) { m -> "\n\n[IMG:${m.groupValues[1]}]\n\n" }

        val rawBlocks = textWithImgs.split("\n\n")
        val result = mutableListOf<String>()

        val imgExtractRegex = "\\[IMG:([^\\]]+)\\]".toRegex()

        for (raw in rawBlocks) {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) continue

            if (trimmed.contains("[IMG:")) {
                var lastIdx = 0
                imgExtractRegex.findAll(trimmed).forEach { match ->
                    val before = trimmed.substring(lastIdx, match.range.first).trim()
                    if (before.isNotBlank()) {
                        val norm = decodeHtmlEntities(before).replace("\\s+".toRegex(), " ").trim()
                        val dedup = deduplicateRepeatedHeading(norm)
                        if (dedup.length > 1) result.add(dedup)
                    }
                    result.add("[IMG:${match.groupValues[1]}]")
                    lastIdx = match.range.last + 1
                }
                val after = trimmed.substring(lastIdx).trim()
                if (after.isNotBlank()) {
                    val norm = decodeHtmlEntities(after).replace("\\s+".toRegex(), " ").trim()
                    val dedup = deduplicateRepeatedHeading(norm)
                    if (dedup.length > 1) result.add(dedup)
                }
            } else {
                val normalized = decodeHtmlEntities(trimmed)
                    .replace("\\s+".toRegex(), " ")
                    .trim()

                val dedup = deduplicateRepeatedHeading(normalized)
                if (dedup.isNotBlank() && dedup.length > 1) {
                    result.add(dedup)
                }
            }
        }
        return result
    }

    fun isHeadingOnly(para: String, chapterTitle: String, bookTitle: String, currentPart: String = ""): Boolean {
        val trimmed = para.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.length > 80) return false
        val normPara = trimmed.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
        val normChap = chapterTitle.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
        val normBook = bookTitle.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
        val normPart = currentPart.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()

        if (normPara.isEmpty()) return true
        if (normPara == normChap || normPara == normBook || (normPart.isNotEmpty() && normPara == normPart)) return true
        if (normChap.isNotEmpty() && normPara.replace(normChap, "").isEmpty()) return true
        if (normPart.isNotEmpty() && normPara.replace(normPart, "").isEmpty()) return true
        if (normBook.isNotEmpty() && normPara.replace(normBook, "").isEmpty()) return true
        if (normPart.isNotEmpty() && normChap.isNotEmpty() && (normPara == normPart + normChap || normPara == normChap + normPart)) return true

        val chapterPattern = "^(?i)(chapter|part|section|book|canto)\\s+([0-9ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)[.:\\s—–-]*$".toRegex()
        if (chapterPattern.matches(trimmed)) return true

        return false
    }

    fun deduplicateRepeatedHeading(text: String): String {
        val trimmed = text.trim()
        val len = trimmed.length
        if (len >= 6) {
            // Check exact half repeat: "Part TwoPart Two" or "Chapter 1Chapter 1"
            if (len % 2 == 0) {
                val half = len / 2
                val first = trimmed.substring(0, half).trim()
                val second = trimmed.substring(half).trim()
                if (first.equals(second, ignoreCase = true)) {
                    return first
                }
            }
            // Check repeated words separated by whitespace: "Part Two Part Two"
            val words = trimmed.split("\\s+".toRegex())
            if (words.size >= 2 && words.size % 2 == 0) {
                val halfWords = words.size / 2
                val firstHalf = words.subList(0, halfWords).joinToString(" ")
                val secondHalf = words.subList(halfWords, words.size).joinToString(" ")
                if (firstHalf.equals(secondHalf, ignoreCase = true)) {
                    return firstHalf
                }
            }
        }
        return trimmed
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
