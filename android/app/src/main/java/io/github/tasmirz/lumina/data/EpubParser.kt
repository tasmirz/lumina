package io.github.tasmirz.lumina.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.Chapter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

object EpubParser {

    /**
     * Compresses and scales down cover images to optimized WebP/JPEG for fast, zero-jank gallery rendering.
     */
    fun compressAndSaveCover(
        inputBytes: ByteArray,
        destFile: File,
        maxDimension: Int = 640,
        quality: Int = 82
    ): Boolean {
        if (inputBytes.isEmpty()) return false
        return try {
            val boundsOpt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, boundsOpt)
            val origW = boundsOpt.outWidth
            val origH = boundsOpt.outHeight
            if (origW <= 0 || origH <= 0) {
                destFile.writeBytes(inputBytes)
                return true
            }

            var sampleSize = 1
            while (origW / sampleSize > maxDimension * 2 || origH / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOpt = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, decodeOpt)
                ?: run {
                    destFile.writeBytes(inputBytes)
                    return true
                }

            val finalBitmap = if (decoded.width > maxDimension || decoded.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
                val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                if (scaled != decoded) {
                    decoded.recycle()
                }
                scaled
            } else {
                decoded
            }

            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            val compressFormat = if (destFile.name.endsWith(".jpg", ignoreCase = true) || destFile.name.endsWith(".jpeg", ignoreCase = true)) {
                Bitmap.CompressFormat.JPEG
            } else {
                format
            }

            FileOutputStream(destFile).use { outStream ->
                finalBitmap.compress(compressFormat, quality, outStream)
                outStream.flush()
            }
            finalBitmap.recycle()
            true
        } catch (_: Throwable) {
            try {
                destFile.writeBytes(inputBytes)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun compressExistingCoverFile(file: File, maxDimension: Int = 640, quality: Int = 82): Boolean {
        if (!file.exists() || file.length() < 100 * 1024L) return false
        return try {
            val bytes = file.readBytes()
            val temp = File(file.parentFile, "${file.name}.tmp")
            val ok = compressAndSaveCover(bytes, temp, maxDimension, quality)
            if (ok && temp.exists() && temp.length() > 0L && temp.length() < file.length()) {
                temp.renameTo(file)
                true
            } else {
                temp.delete()
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

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
                                    val bytes = zip.getInputStream(coverZipEntry).use { it.readBytes() }
                                    compressAndSaveCover(bytes, coverFile, maxDimension = 640, quality = 82)
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

        if (title.isBlank() || title.startsWith("Document:", ignoreCase = true) || title.equals("Unnamed", ignoreCase = true) || title.equals("Untitled", ignoreCase = true)) {
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
    fun parseEpub(file: File, context: Context? = null, bookId: String? = null): Book {
        return file.inputStream().use { stream ->
            parseEpub(stream, file.name, context, bookId)
        }
    }

    fun parseEpub(inputStream: InputStream, filename: String, context: Context? = null, bookId: String? = null): Book {
        val resolvedBookId = bookId ?: "custom-${System.currentTimeMillis()}"
        val entries = mutableMapOf<String, ByteArray>()
        val extractedImages = mutableMapOf<String, String>() // normalized zip entry name -> absolute local file path

        val imagesDir = if (context != null) {
            File(context.filesDir, "books/$resolvedBookId/images").apply { mkdirs() }
        } else {
            File(System.getProperty("java.io.tmpdir", "/tmp"), "lumina_books/$resolvedBookId/images").apply { mkdirs() }
        }

        // 1. Memory-efficient entry streaming: selectively buffer text/structure in RAM & stream images directly to disk
        // Prevents RAM blowup on graphic/image-rich EPUBs (e.g. hundreds of high-res formulas/diagrams)
        try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normalizedName = entry.name.replace('\\', '/').removePrefix("/")
                    val lower = normalizedName.lowercase()
                    val isTextOrStructure = lower.endsWith(".xml") || lower.endsWith(".opf") || lower.endsWith(".ncx") ||
                            lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                    val isImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg")

                    if (isTextOrStructure) {
                        entries[normalizedName] = zip.readBytes()
                    } else if (isImage && imagesDir != null) {
                        try {
                            val safeFileName = normalizedName.replace('/', '_').replace('\\', '_').replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                            val localImgFile = File(imagesDir, "${resolvedBookId}_$safeFileName")
                            FileOutputStream(localImgFile).use { out ->
                                zip.copyTo(out)
                            }
                            if (localImgFile.exists() && localImgFile.length() > 0L) {
                                extractedImages[normalizedName] = localImgFile.absolutePath
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
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
            extractedImages.keys.firstOrNull { it.equals(clean, ignoreCase = true) }?.let { return it }

            // 2. Relative suffix match
            entries.keys.firstOrNull { it.endsWith("/$clean", ignoreCase = true) }?.let { return it }
            extractedImages.keys.firstOrNull { it.endsWith("/$clean", ignoreCase = true) }?.let { return it }

            // 3. Filename match
            entries.keys.firstOrNull { it.substringAfterLast('/').equals(filenameOnly, ignoreCase = true) }?.let { return it }
            extractedImages.keys.firstOrNull { it.substringAfterLast('/').equals(filenameOnly, ignoreCase = true) }?.let { return it }
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

        if (title.isBlank() || title.startsWith("Document:", ignoreCase = true) || title.equals("Unnamed", ignoreCase = true) || title.equals("Untitled", ignoreCase = true)) {
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

        val allImageKeys = entries.keys + extractedImages.keys

        // Tier 6: Zip entry with "cover" in name
        if (coverEntryKey == null) {
            coverEntryKey = allImageKeys.firstOrNull { key ->
                val lower = key.lowercase()
                lower.contains("cover") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
            }
        }

        // Tier 7: Zip entry with "title", "front", or "jacket"
        if (coverEntryKey == null) {
            coverEntryKey = allImageKeys.firstOrNull { key ->
                val lower = key.lowercase()
                (lower.contains("title") || lower.contains("front") || lower.contains("jacket")) &&
                (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))
            }
        }

        // Tier 8: First image found in manifest or zip
        if (coverEntryKey == null) {
            coverEntryKey = manifestItems.values.firstOrNull { it.mediaType.startsWith("image/") }?.let { resolveZipEntry(opfDir, it.href) }
                ?: allImageKeys.firstOrNull { key ->
                    val lower = key.lowercase()
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                }
        }

        // Extract and persist cover image to app storage
        if (coverEntryKey != null) {
            val coverBytes = entries[coverEntryKey]
                ?: extractedImages[coverEntryKey]?.let { File(it).takeIf { f -> f.exists() }?.readBytes() }
            if (coverBytes != null && coverBytes.isNotEmpty() && context != null) {
                try {
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                    val ext = if (coverEntryKey.endsWith(".png", ignoreCase = true)) "png" else "jpg"
                    val coverFile = File(coversDir, "${resolvedBookId}_cover.$ext")
                    compressAndSaveCover(coverBytes, coverFile, maxDimension = 640, quality = 82)
                    if (coverFile.exists() && coverFile.length() > 0L) {
                        coverUrl = coverFile.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (coverUrl.isBlank() && coverEntryKey != null) {
                coverUrl = extractedImages[coverEntryKey] ?: ""
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

                // Process inline images: replace <img ...> and <image ...> with image tokens
                var processedHtml = rawHtml
                val imgRegex = "<(?:img|image)\\s+[^>]*(?:xlink:href|src|href)=[\"']([^\"']+)[\"'][^>]*>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                processedHtml = imgRegex.replace(rawHtml) { m ->
                    val imgSrc = m.groupValues[1]
                    val imgEntryKey = resolveZipEntry(currentFileDir, imgSrc)
                    if (imgEntryKey != null) {
                        val localPath = extractedImages[imgEntryKey]
                        if (localPath != null && File(localPath).exists()) {
                            "__IMG_TOKEN_START__${localPath}__IMG_TOKEN_END__"
                        } else {
                            val imgBytes = entries[imgEntryKey]
                            if (imgBytes != null && imgBytes.isNotEmpty() && imagesDir != null) {
                                try {
                                    val safeFileName = imgEntryKey.substringAfterLast('/').replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                                    val localImgFile = File(imagesDir, "${resolvedBookId}_$safeFileName")
                                    if (!localImgFile.exists()) {
                                        FileOutputStream(localImgFile).use { it.write(imgBytes) }
                                    }
                                    "__IMG_TOKEN_START__${localImgFile.absolutePath}__IMG_TOKEN_END__"
                                } catch (_: Exception) {
                                    ""
                                }
                            } else ""
                        }
                    } else ""
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
                    val monolithicChapterRegex = "^(?i)(?:chapter\\s+\\d+|prologue|epilogue|act\\s+\\d+|part\\s+\\d+|volume\\s+\\d+|book\\s+\\d+)(?:\\s*[:\\-—\\s].*)?$".toRegex()
                    val internalHeadingsCount = if (cleanedParas.size > 150) {
                        cleanedParas.count { monolithicChapterRegex.matches(it.trim()) }
                    } else 0

                    if (internalHeadingsCount >= 2) {
                        // Monolithic novel: file contains hundreds or thousands of chapters concatenated in a single HTML!
                        // Split into distinct sub-chapters so each chapter stays small and SQLite CursorWindow 2MB limit is never exceeded.
                        var subTitle = chapterTitle
                        val subParas = mutableListOf<String>()

                        fun flushSubChapter() {
                            if (subParas.isNotEmpty()) {
                                val wc = subParas
                                    .filterNot { it.startsWith("[IMG:") || it.startsWith("[TABLE:") || it == "[DIVIDER]" }
                                    .map {
                                        var p = it
                                        if (p.startsWith("[VERSE:") && p.endsWith("]")) p = p.removePrefix("[VERSE:").removeSuffix("]")
                                        else if (p.startsWith("[QUOTE:") && p.endsWith("]")) p = p.removePrefix("[QUOTE:").removeSuffix("]")
                                        p.replace("\\[/?(sub|sup)\\]".toRegex(), "")
                                    }
                                    .joinToString(" ")
                                    .split("\\s+".toRegex()).size
                                val rt = maxOf(wc / 200, 1)
                                val subText = if (currentPart.isNotBlank()) currentPart else "Section ${extractedChapters.size + 1}"
                                extractedChapters.add(
                                    Chapter(
                                        title = deduplicateRepeatedHeading(subTitle),
                                        subtitle = subText,
                                        readTime = "$rt mins",
                                        paragraphs = subParas.toList()
                                    )
                                )
                                subParas.clear()
                            }
                        }

                        for (p in cleanedParas) {
                            val trimmed = p.trim()
                            if (monolithicChapterRegex.matches(trimmed)) {
                                if (subParas.size >= 5) {
                                    flushSubChapter()
                                    subTitle = trimmed
                                } else {
                                    subTitle = trimmed
                                }
                            } else {
                                subParas.add(p)
                            }
                        }
                        flushSubChapter()
                    } else {
                        val wordCount = cleanedParas
                            .filterNot { it.startsWith("[IMG:") || it.startsWith("[TABLE:") || it == "[DIVIDER]" }
                            .map {
                                var p = it
                                if (p.startsWith("[VERSE:") && p.endsWith("]")) p = p.removePrefix("[VERSE:").removeSuffix("]")
                                else if (p.startsWith("[QUOTE:") && p.endsWith("]")) p = p.removePrefix("[QUOTE:").removeSuffix("]")
                                p.replace("\\[/?(sub|sup)\\]".toRegex(), "")
                            }
                            .joinToString(" ")
                            .split("\\s+".toRegex()).size
                        val readTimeMins = maxOf(wordCount / 200, 1)

                        val subtitleText = if (currentPart.isNotBlank()) currentPart else "Section ${extractedChapters.size + 1}"

                        if (extractedChapters.isNotEmpty() &&
                            extractedChapters.last().title.equals(chapterTitle, ignoreCase = true) &&
                            extractedChapters.last().subtitle.equals(subtitleText, ignoreCase = true)) {
                            val prev = extractedChapters.last()
                            val combinedParas = prev.paragraphs + cleanedParas
                            val wordCount = combinedParas
                                .filterNot { it.startsWith("[IMG:") || it.startsWith("[TABLE:") || it == "[DIVIDER]" }
                                .map {
                                    var p = it
                                    if (p.startsWith("[VERSE:") && p.endsWith("]")) p = p.removePrefix("[VERSE:").removeSuffix("]")
                                    else if (p.startsWith("[QUOTE:") && p.endsWith("]")) p = p.removePrefix("[QUOTE:").removeSuffix("]")
                                    p.replace("\\[/?(sub|sup)\\]".toRegex(), "")
                                }
                                .joinToString(" ")
                                .split("\\s+".toRegex()).size
                            val readTimeMins = maxOf(wordCount / 200, 1)
                            extractedChapters[extractedChapters.size - 1] = prev.copy(
                                paragraphs = combinedParas,
                                readTime = "$readTimeMins mins"
                            )
                        } else {
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
            id = resolvedBookId,
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

    internal fun parseHtmlTable(tableHtml: String): String? {
        return try {
            val headers = mutableListOf<String>()
            val rows = mutableListOf<List<String>>()

            // 1. Caption extraction
            val captionRegex = "<caption\\b[^>]*>(.*?)</caption>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val captionMatch = captionRegex.find(tableHtml)
            val captionText = captionMatch?.let { cleanCellText(it.groupValues[1]) } ?: ""

            // 2. Thead extraction (supports both <th> and <td> cells inside <thead>)
            val theadRegex = "<thead\\b[^>]*>(.*?)</thead>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val theadMatch = theadRegex.find(tableHtml)
            val cellRegex = "<(th|td)\\b([^>]*)>(.*?)</\\1>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val trRegex = "<tr\\b[^>]*>(.*?)</tr>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

            if (theadMatch != null) {
                val theadHtml = theadMatch.groupValues[1]
                val theadTrs = trRegex.findAll(theadHtml).map { it.groupValues[1] }.toList()
                for (tr in theadTrs) {
                    val theadCells = cellRegex.findAll(tr).map { cleanCellText(it.groupValues[3]) }.toList()
                    if (theadCells.isNotEmpty() && theadCells.any { it.isNotBlank() }) {
                        headers.addAll(theadCells)
                        break
                    }
                }
            }

            // 3. Scan data rows (strip <thead> from tableHtml if headers were already populated from thead)
            val bodyHtml = if (headers.isNotEmpty() && theadMatch != null) {
                theadRegex.replace(tableHtml, "")
            } else {
                tableHtml
            }

            val allRows = trRegex.findAll(bodyHtml).map { it.groupValues[1] }.toList()
            if (allRows.isEmpty() && headers.isEmpty()) return null

            var startRowIdx = 0
            if (headers.isEmpty() && allRows.isNotEmpty()) {
                val thRegex = "<th\\b[^>]*>(.*?)</th>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val firstThs = thRegex.findAll(allRows[0]).map { cleanCellText(it.groupValues[1]) }.toList()
                if (firstThs.isNotEmpty() && firstThs.any { it.isNotBlank() }) {
                    headers.addAll(firstThs)
                    startRowIdx = 1
                }
            }

            // 4. Robust 2D Grid tracking for rowspan and colspan
            val grid = mutableMapOf<Pair<Int, Int>, String>()
            var maxCol = 0
            val rowspanRegex = "rowspan=[\"']?(\\d+)[\"']?".toRegex(RegexOption.IGNORE_CASE)
            val colspanRegex = "colspan=[\"']?(\\d+)[\"']?".toRegex(RegexOption.IGNORE_CASE)

            for (rIdx in 0 until (allRows.size - startRowIdx)) {
                val rowHtml = allRows[rIdx + startRowIdx]
                var cIdx = 0
                for (cellMatch in cellRegex.findAll(rowHtml)) {
                    val attrs = cellMatch.groupValues[2]
                    val cellContent = cleanCellText(cellMatch.groupValues[3])

                    val rSpan = rowspanRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 50) ?: 1
                    val cSpan = colspanRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 20) ?: 1

                    while (grid.containsKey(Pair(rIdx, cIdx))) {
                        cIdx++
                    }

                    for (dr in 0 until rSpan) {
                        for (dc in 0 until cSpan) {
                            grid[Pair(rIdx + dr, cIdx + dc)] = cellContent
                            maxCol = maxOf(maxCol, cIdx + dc + 1)
                        }
                    }
                    cIdx += cSpan
                }
            }

            val totalRows = allRows.size - startRowIdx
            for (r in 0 until totalRows) {
                val rowCells = mutableListOf<String>()
                for (c in 0 until maxCol) {
                    rowCells.add(grid[Pair(r, c)] ?: "")
                }
                if (rowCells.any { it.isNotBlank() }) {
                    rows.add(rowCells)
                }
            }

            if (headers.isEmpty() && rows.isEmpty()) return null

            val json = JSONObject()
            if (captionText.isNotBlank()) {
                json.put("caption", captionText)
            }
            val headerArray = JSONArray()
            headers.forEach { headerArray.put(it) }
            json.put("headers", headerArray)

            val rowsArray = JSONArray()
            rows.forEach { row ->
                val rowArr = JSONArray()
                row.forEach { rowArr.put(it) }
                rowsArray.put(rowArr)
            }
            json.put("rows", rowsArray)

            json.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun cleanLatexMath(raw: String): String {
        if (!raw.contains("\\") && !raw.contains("_{") && !raw.contains("^{") && !raw.contains("$")) {
            return raw
        }

        var s = raw
        // Strip surrounding math delimiters $...$ or $$...$$ or \(...\)
        s = s.replace(Regex("^\\s*\\$\\$?(.*?)\\$\\$?\\s*$"), "$1")
        s = s.replace(Regex("\\\\\\((.*?)\\\\\\)"), "$1")
        s = s.replace(Regex("\\\\\\[(.*?)\\\\\\]"), "$1")

        // Clean common font wrappers \text{...}, \mathrm{...}, \mathbf{...}, \mathit{...}
        s = s.replace(Regex("\\\\(?:text|mathrm|mathbf|mathit|textbf|textit)\\{([^}]+)\\}"), "$1")

        // Greek letters & math symbols
        s = s.replace("\\lambda", "λ")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\delta", "δ")
            .replace("\\epsilon", "ε")
            .replace("\\varepsilon", "ε")
            .replace("\\zeta", "ζ")
            .replace("\\eta", "η")
            .replace("\\theta", "θ")
            .replace("\\iota", "ι")
            .replace("\\kappa", "κ")
            .replace("\\mu", "μ")
            .replace("\\nu", "ν")
            .replace("\\xi", "ξ")
            .replace("\\pi", "π")
            .replace("\\rho", "ρ")
            .replace("\\sigma", "σ")
            .replace("\\tau", "τ")
            .replace("\\phi", "φ")
            .replace("\\chi", "χ")
            .replace("\\psi", "ψ")
            .replace("\\omega", "ω")
            .replace("\\Gamma", "Γ")
            .replace("\\Delta", "Δ")
            .replace("\\Theta", "Θ")
            .replace("\\Lambda", "Λ")
            .replace("\\Xi", "Ξ")
            .replace("\\Pi", "Π")
            .replace("\\Sigma", "Σ")
            .replace("\\Phi", "Φ")
            .replace("\\Psi", "Ψ")
            .replace("\\Omega", "Ω")
            .replace("\\ell", "ℓ")
            .replace("\\nabla", "∇")
            .replace("\\partial", "∂")
            .replace("\\infty", "∞")
            .replace("\\times", "×")
            .replace("\\cdot", "·")
            .replace("\\pm", "±")
            .replace("\\mp", "∓")
            .replace(Regex("\\\\(?:le|leq)\\b"), "≤")
            .replace(Regex("\\\\(?:ge|geq)\\b"), "≥")
            .replace(Regex("\\\\(?:ne|neq)\\b"), "≠")
            .replace("\\approx", "≈")
            .replace("\\sum", "∑")
            .replace("\\prod", "∏")
            .replace("\\int", "∫")
            .replace("\\in", "∈")
            .replace("\\notin", "∉")
            .replace("\\subset", "⊂")
            .replace("\\cup", "∪")
            .replace("\\cap", "∩")
            .replace(Regex("\\\\(?:to|rightarrow)\\b"), "→")
            .replace(Regex("\\\\(?:gets|leftarrow)\\b"), "←")
            .replace("\\Rightarrow", "⇒")
            .replace("\\Leftarrow", "⇐")
            .replace("\\iff", "⇔")

        // Blackboard bold: \mathbb{R} -> ℝ, etc.
        s = s.replace(Regex("\\\\mathbb\\{([A-Z])\\}")) { m ->
            when (m.groupValues[1]) {
                "R" -> "ℝ"
                "C" -> "ℂ"
                "N" -> "ℕ"
                "Z" -> "ℤ"
                "Q" -> "ℚ"
                "E" -> "𝔼"
                "P" -> "ℙ"
                else -> m.groupValues[1]
            }
        }

        // Caligraphic letters: \mathcal{P} -> 𝒫, etc.
        s = s.replace(Regex("\\\\mathcal\\{([A-Z])\\}")) { m ->
            when (m.groupValues[1]) {
                "L" -> "ℒ"
                "P" -> "𝒫"
                "C" -> "𝒞"
                "D" -> "𝒟"
                "N" -> "𝒩"
                "M" -> "ℳ"
                "H" -> "ℋ"
                "F" -> "ℱ"
                "B" -> "ℬ"
                "R" -> "ℛ"
                else -> m.groupValues[1]
            }
        }

        // Subscripts: _{...} and _[a-zA-Z0-9]
        if (s.contains("_{")) {
            s = s.replace(Regex("(?<=\\S)_\\{([^}]+)\\}")) { m ->
                formatSubscript("", m.groupValues[1])
            }
        }
        if (s.contains("^{")) {
            s = s.replace(Regex("(?<=\\S)\\^\\{([^}]+)\\}")) { m ->
                formatSuperscript("", m.groupValues[1])
            }
        }
        if (s.contains("_")) {
            val mathSubRegex = "([ℒℓ𝔼𝕍ℝα-ωΑ-Ω∇∂λg]|[a-zA-Z]{1,6})_([a-zA-Z0-9]{1,6})\\b".toRegex()
            s = mathSubRegex.replace(s) { m ->
                formatSubscript(m.groupValues[1], m.groupValues[2])
            }
        }
        if (s.contains("^")) {
            val mathSupRegex = "([ℒℓ𝔼𝕍ℝα-ωΑ-Ω∇∂λg]|[a-zA-Z0-9]{1,6})\\^([a-zA-Z0-9]{1,4})\\b".toRegex()
            s = mathSupRegex.replace(s) { m ->
                formatSuperscript(m.groupValues[1], m.groupValues[2])
            }
        }

        // Clean up remaining lone backslashes before plain words (e.g. \min -> min, \max -> max)
        s = s.replace(Regex("\\\\([a-zA-Z]+)"), "$1")

        return s.trim()
    }

    private fun cleanCellText(cellHtml: String): String {
        var html = cellHtml
        val imgMatch = "__IMG_TOKEN_START__(.*?)__IMG_TOKEN_END__|<(?:img|image)\\s+[^>]*(?:xlink:href|src|href)=[\"']([^\"']+)[\"'][^>]*>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val extractedImgToken = if (imgMatch != null) {
            val path = imgMatch.groupValues[1].ifBlank { imgMatch.groupValues[2] }.trim()
            if (path.isNotBlank()) "[IMG:$path]" else null
        } else null
        if (imgMatch != null) {
            html = html.replace(imgMatch.value, " ")
        }

        val mathRegex = "<math\\b[^>]*>(.*?)</math>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val mathProcessed = mathRegex.replace(html) { match ->
            transformMathML(match.value)
        }
        val supRegex = "(?i)<sup\\b[^>]*>(.*?)</sup>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val subRegex = "(?i)<sub\\b[^>]*>(.*?)</sub>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val supProcessed = supRegex.replace(mathProcessed) { match ->
            val inner = match.groupValues[1].replace("<[^>]+>".toRegex(), "").trim()
            val unicode = if (inner.length <= 3) toUnicodeSuperscript(inner) else null
            unicode ?: "[sup]$inner[/sup]"
        }
        val subSupProcessed = subRegex.replace(supProcessed) { match ->
            val inner = match.groupValues[1].replace("<[^>]+>".toRegex(), "").trim()
            val unicode = if (inner.length == 1) toUnicodeSubscript(inner) else null
            unicode ?: "[sub]$inner[/sub]"
        }
        val htmlWithBreaks = subSupProcessed
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "___BR_TOKEN___")
            .replace("</(?:p|div|tr|li|blockquote)>".toRegex(RegexOption.IGNORE_CASE), "___BR_TOKEN___")
            .replace("<[^>]+>".toRegex(), " ")
        val decoded = decodeHtmlEntities(htmlWithBreaks)
            .replace("[ \t\r\n]+".toRegex(), " ")
            .replace("___BR_TOKEN___", "\n")
        val cleanedLines = decoded.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val cleaned = cleanLatexMath(cleanedLines.joinToString("\n").trim())
        return if (extractedImgToken != null) {
            if (cleaned.isNotBlank()) "$extractedImgToken $cleaned" else extractedImgToken
        } else {
            cleaned
        }
    }

    internal fun cleanHtmlToParagraphs(html: String): List<String> {
        // 0. Drop cap / Initial letter un-wrapping to prevent word fragmentation (e.g. "T o", "I T")
        val dropcapRegex = "(?i)<span\\b[^>]*class=[\"'][^\"']*(?:dropcap|letra|initial)[^\"']*[\"'][^>]*>\\s*([^<]+?)\\s*</span>\\s*".toRegex()
        val dropcapCleaned = dropcapRegex.replace(html) { match ->
            match.groupValues[1].trim()
        }

        // 1. Strip ebook page-number markers (<span class="x-ebookmaker-pageno">, epub:type="pagebreak")
        val pagenoRegex = "(?i)<(span|div|a)\\b[^>]*(?:class=[\"'][^\"']*(?:pageno|page-number|pagebreak)[^\"']*|epub:type=[\"'][^\"']*pagebreak[\"'])[^>]*>.*?</\\1>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val pagenoCleaned = pagenoRegex.replace(dropcapCleaned, "")
        val emptyPageAnchorRegex = "(?i)<(span|div|a)\\b[^>]*(?:class=[\"'][^\"']*(?:pageno|page-number|pagebreak)[^\"']*|epub:type=[\"'][^\"']*pagebreak[\"'])[^>]*/>".toRegex()
        val pagesPurged = emptyPageAnchorRegex.replace(pagenoCleaned, "")

        // 2. Transform MathML tags into clean readable text before stripping general HTML tags
        val mathRegex = "<math\\b[^>]*>(.*?)</math>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val mathProcessed = mathRegex.replace(pagesPurged) { match ->
            val fullTag = match.value
            val isBlock = fullTag.contains("display=[\"']block[\"']".toRegex(RegexOption.IGNORE_CASE))
            val transformed = transformMathML(fullTag)
            if (isBlock) "\n\n$transformed\n\n" else " $transformed "
        }

        // 3. Convert HTML superscripts (<sup>) and subscripts (<sub>) to clean markup
        val supRegex = "(?i)<sup\\b[^>]*>(.*?)</sup>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val subRegex = "(?i)<sub\\b[^>]*>(.*?)</sub>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val supProcessed = supRegex.replace(mathProcessed) { match ->
            val inner = match.groupValues[1].replace("<[^>]+>".toRegex(), "").trim()
            val unicode = if (inner.length <= 3) toUnicodeSuperscript(inner) else null
            unicode ?: "[sup]$inner[/sup]"
        }
        val subSupProcessed = subRegex.replace(supProcessed) { match ->
            val inner = match.groupValues[1].replace("<[^>]+>".toRegex(), "").trim()
            val unicode = if (inner.length == 1) toUnicodeSubscript(inner) else null
            unicode ?: "[sub]$inner[/sub]"
        }

        // 4. Parse HTML tables into structured JSON tokens
        val tableRegex = "<table\\b[^>]*>(.*?)</table>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val tableProcessed = tableRegex.replace(subSupProcessed) { match ->
            val json = parseHtmlTable(match.value)
            if (json != null) {
                val encoded = try { URLEncoder.encode(json, "UTF-8") } catch (_: Exception) { json }
                "\n\n__TABLE_TOKEN_START__${encoded}__TABLE_TOKEN_END__\n\n"
            } else {
                match.value
            }
        }

        // 5. Scene Dividers / Thematic breaks (<hr>, asterisms, thoughtbreaks, or long runs of underscores/hyphens)
        val dividerRegex = "(?i)(?:<hr\\b[^>]*>|<(?:p|div)\\b[^>]*class=[\"'][^\"']*(?:asterism|thoughtbreak|scene-break)[^\"']*[^>]*>.*?</(?:p|div)>|_{4,}|-{4,})".toRegex(RegexOption.DOT_MATCHES_ALL)
        val dividerProcessed = dividerRegex.replace(tableProcessed, "\n\n__DIVIDER_TOKEN__\n\n")

        // Ensure "Abstract" preceded by any rule, underscores, or dashes starts cleanly as its own paragraph
        val abstractRegex = "(?i)(?:[—_\\-]{2,}|__DIVIDER_TOKEN__)\\s*(Abstract[—–:\\s])".toRegex()
        val abstractProcessed = abstractRegex.replace(dividerProcessed) { match ->
            "\n\n__DIVIDER_TOKEN__\n\n${match.groupValues[1]}"
        }

        // 6. Verse / Drama / Poetry / Preformatted code (<pre>, <code>, .code, .drama, .poem, .verse, .stanza)
        val verseRegex = "(?i)<(pre|div|p|blockquote)\\b[^>]*(?:class=[\"'][^\"']*(?:drama|poem|verse|stanza|code|sourcecode)[^\"']*|pre)[^>]*>(.*?)</\\1>|<code\\b[^>]*>(.*?)</code>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val verseProcessed = verseRegex.replace(abstractProcessed) { match ->
            val rawInner = match.groupValues[2].ifEmpty { match.groupValues[3] }
            val brToNewline = rawInner.replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            val stripped = brToNewline.replace("<[^>]+>".toRegex(), "")
            val decoded = decodeHtmlEntities(stripped)
            val verseLines = decoded.lines()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
            if (verseLines.isNotEmpty()) {
                val encoded = try { URLEncoder.encode(verseLines.joinToString("\n"), "UTF-8") } catch (_: Exception) { verseLines.joinToString("\n") }
                "\n\n__VERSE_TOKEN_START__${encoded}__VERSE_TOKEN_END__\n\n"
            } else ""
        }

        // 7. Epistolary Letters & Blockquotes (<blockquote>, .letter, .blockquot, .epistle)
        val quoteRegex = "(?i)<(blockquote|div|p)\\b[^>]*(?:class=[\"'][^\"']*(?:letter|blockquot|epistle|telegram)[^\"']*|blockquote)[^>]*>(.*?)</\\1>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val quoteProcessed = quoteRegex.replace(verseProcessed) { match ->
            val rawInner = match.groupValues[2]
            val brToNewline = rawInner.replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            val stripped = brToNewline.replace("<[^>]+>".toRegex(), " ")
            val decoded = decodeHtmlEntities(stripped)
            val quoteLines = decoded.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (quoteLines.isNotEmpty()) {
                val encoded = try { URLEncoder.encode(quoteLines.joinToString("\n"), "UTF-8") } catch (_: Exception) { quoteLines.joinToString("\n") }
                "\n\n__QUOTE_TOKEN_START__${encoded}__QUOTE_TOKEN_END__\n\n"
            } else ""
        }

        // 8. Format ordered (<ol>) and unordered (<ul>) lists with bullets/numbering
        var listsProcessed = quoteProcessed
        val olRegex = "<ol\\b[^>]*>(.*?)</ol>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        listsProcessed = olRegex.replace(listsProcessed) { olMatch ->
            var idx = 1
            val liRegex = "<li\\b[^>]*>(.*?)</li>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            liRegex.replace(olMatch.groupValues[1]) { liMatch ->
                "\n\n${idx++}. " + liMatch.groupValues[1] + "\n\n"
            }
        }
        val ulRegex = "<ul\\b[^>]*>(.*?)</ul>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        listsProcessed = ulRegex.replace(listsProcessed) { ulMatch ->
            val liRegex = "<li\\b[^>]*>(.*?)</li>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            liRegex.replace(ulMatch.groupValues[1]) { liMatch ->
                "\n\n• " + liMatch.groupValues[1] + "\n\n"
            }
        }
        val standaloneLiRegex = "<li\\b[^>]*>(.*?)</li>".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        listsProcessed = standaloneLiRegex.replace(listsProcessed) { liMatch ->
            "\n\n• " + liMatch.groupValues[1] + "\n\n"
        }

        // 9. Strip scripts, styles, heads, and convert block containers to paragraph breaks
        val text = listsProcessed
            .replace("(?i)<script.*?>.*?</script>".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .replace("(?i)<style.*?>.*?</style>".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .replace("(?i)<head.*?>.*?</head>".toRegex(RegexOption.DOT_MATCHES_ALL), "")
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            .replace("(?i)</?(p|div|h[1-6]|section|article|blockquote|li|ol|ul|tr|td|th|header|footer|nav|title|table|figure|figcaption|hr)>".toRegex(), "\n\n")
            .replace("(?i)</(a|span|em|i|b|strong|code)>(?=[\\p{L}\\p{N}])".toRegex(), " ")
            .replace("(?i)</(a|span|em|i|b|strong|code)>".toRegex(), "")
            .replace("<[^>]+>".toRegex(), " ")

        val tokenImgRegex = "__IMG_TOKEN_START__(.*?)__IMG_TOKEN_END__".toRegex(RegexOption.DOT_MATCHES_ALL)
        val tokenTableRegex = "__TABLE_TOKEN_START__(.*?)__TABLE_TOKEN_END__".toRegex(RegexOption.DOT_MATCHES_ALL)
        val tokenDividerRegex = "__DIVIDER_TOKEN__".toRegex()
        val tokenVerseRegex = "__VERSE_TOKEN_START__(.*?)__VERSE_TOKEN_END__".toRegex(RegexOption.DOT_MATCHES_ALL)
        val tokenQuoteRegex = "__QUOTE_TOKEN_START__(.*?)__QUOTE_TOKEN_END__".toRegex(RegexOption.DOT_MATCHES_ALL)

        val textWithImgs = tokenImgRegex.replace(text) { m -> "__IMG_TOKEN_START__${m.groupValues[1]}__IMG_TOKEN_END__" }
        val textWithTables = tokenTableRegex.replace(textWithImgs) { m ->
            val decoded = try { URLDecoder.decode(m.groupValues[1], "UTF-8") } catch (_: Exception) { m.groupValues[1] }
            "\n\n[TABLE:$decoded]\n\n"
        }
        val textWithDividers = tokenDividerRegex.replace(textWithTables) { "\n\n[DIVIDER]\n\n" }
        val textWithVerses = tokenVerseRegex.replace(textWithDividers) { m ->
            val decoded = try { URLDecoder.decode(m.groupValues[1], "UTF-8") } catch (_: Exception) { m.groupValues[1] }
            "\n\n[VERSE:$decoded]\n\n"
        }
        val textWithTokens = tokenQuoteRegex.replace(textWithVerses) { m ->
            val decoded = try { URLDecoder.decode(m.groupValues[1], "UTF-8") } catch (_: Exception) { m.groupValues[1] }
            "\n\n[QUOTE:$decoded]\n\n"
        }

        val rawBlocks = textWithTokens.split("\n\n")
        val result = mutableListOf<String>()

        val imgExtractRegex = "\\[IMG:([^\\]]+)\\]".toRegex()
        val tokenImgExtractRegex = "__IMG_TOKEN_START__(.*?)__IMG_TOKEN_END__".toRegex()

        for (raw in rawBlocks) {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) continue

            if (trimmed.startsWith("[TABLE:") && trimmed.endsWith("]")) {
                result.add(trimmed)
            } else if (trimmed == "[DIVIDER]" || isSceneDividerGlyph(trimmed)) {
                result.add("[DIVIDER]")
            } else if (trimmed.startsWith("[VERSE:") && trimmed.endsWith("]")) {
                result.add(trimmed)
            } else if (trimmed.startsWith("[QUOTE:") && trimmed.endsWith("]")) {
                result.add(trimmed)
            } else if (trimmed.startsWith("__IMG_TOKEN_START__") && trimmed.endsWith("__IMG_TOKEN_END__") &&
                !trimmed.removePrefix("__IMG_TOKEN_START__").removeSuffix("__IMG_TOKEN_END__").contains("__IMG_TOKEN_START__")
            ) {
                // Isolated block image (figure, illustration, full equation)
                val path = trimmed.removePrefix("__IMG_TOKEN_START__").removeSuffix("__IMG_TOKEN_END__")
                result.add("[IMG:$path]")
            } else if (trimmed.startsWith("[IMG:") && trimmed.endsWith("]") &&
                !trimmed.removePrefix("[IMG:").removeSuffix("]").contains("[IMG:")
            ) {
                result.add(trimmed)
            } else if (trimmed.contains("__IMG_TOKEN_START__") || trimmed.contains("[IMG:")) {
                // Paragraph containing inline images or math symbols: preserve entire paragraph flow
                var processedPara = tokenImgExtractRegex.replace(trimmed) { m -> "[INLINE_IMG:${m.groupValues[1]}]" }
                processedPara = imgExtractRegex.replace(processedPara) { m -> "[INLINE_IMG:${m.groupValues[1]}]" }
                val lines = decodeHtmlEntities(processedPara)
                    .lines()
                    .map { it.replace("[ \t]+".toRegex(), " ").trim() }
                    .filter { it.isNotBlank() }
                val normalized = lines.joinToString(" ")
                var cleanPara = normalized.replace("[ \t]+".toRegex(), " ")
                cleanPara = cleanPara.replace("\\[INLINE_IMG:([^\\]]+)\\]\\s+([,.:;?!)\\]])".toRegex(), "[INLINE_IMG:$1]$2")
                cleanPara = cleanPara.replace("([(\\[])\\s+\\[INLINE_IMG:([^\\]]+)\\]".toRegex(), "$1[INLINE_IMG:$2]")
                cleanPara = cleanPara.replace("([\\p{L}\\p{N}])\\[INLINE_IMG:".toRegex(), "$1 [INLINE_IMG:")
                cleanPara = cleanPara.replace("\\]([\\p{L}\\p{N}])".toRegex(), "] $1")
                val normalizedBengali = normalizeBengaliVowels(cleanPara)
                val dedup = deduplicateRepeatedHeading(normalizedBengali)
                if (dedup.isNotBlank()) {
                    result.add(dedup)
                }
            } else {
                val lines = decodeHtmlEntities(trimmed)
                    .lines()
                    .map { it.replace("[ \t]+".toRegex(), " ").trim() }
                    .filter { it.isNotBlank() }
                val normalized = lines.joinToString(" ")
                val normalizedBengali = normalizeBengaliVowels(normalized)
                val dedup = deduplicateRepeatedHeading(normalizedBengali)
                if (dedup.isNotBlank() && dedup.length > 1) {
                    result.add(dedup)
                }
            }
        }
        return result
    }

    fun normalizeBengaliVowels(text: String): String {
        if (text.isEmpty()) return text
        // Swaps visual-order misplaced vowels: ি (U+09BF), ে (U+09C7), ৈ (U+09C8) followed by a consonant or conjunct
        val pattern = "([\\u09BF\\u09C7\\u09C8])([\\u0985-\\u09B9](?:\\u09CD[\\u0985-\\u09B9])?)".toRegex()
        var res = pattern.replace(text) { m ->
            m.groupValues[2] + m.groupValues[1]
        }
        // Fix trailing reph তর্ -> র্ত, ডর্ -> র্ড
        val trailingReph = "([\\u0985-\\u09B9])(\\u09B0\\u09CD)".toRegex()
        res = trailingReph.replace(res) { m ->
            m.groupValues[2] + m.groupValues[1]
        }
        return res
    }

    fun isSceneDividerGlyph(text: String): Boolean {
        val t = text.trim()
        if (t == "⁂" || t == "* * *" || t == "***" || t == "---" || t == "— — —" || t == "• • •" || t == "~ ~ ~" || t == "*  *  *") return true
        if (t.matches("^[_\u2014\\-—]{3,}$".toRegex())) return true
        if (t.length in 3..20 && "^(?:[\\*⁂•~\\-—_]\\s*){3,7}$".toRegex().matches(t)) return true
        return false
    }

    fun isHeadingOnly(para: String, chapterTitle: String, bookTitle: String, currentPart: String = ""): Boolean {
        val trimmed = para.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.startsWith("[IMG:") || trimmed.startsWith("[TABLE:")) return false
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

        // Match titles when numeral or adventure prefixes differ (e.g. "A SCANDAL IN BOHEMIA" vs "I. A SCANDAL IN BOHEMIA")
        val normChapWithoutPrefix = normChap
            .replaceFirst("^(adventure|chapter|part|section|book|canto)".toRegex(), "")
            .replaceFirst("^[0-9ivxlcdm]+".toRegex(), "")
        val normParaWithoutPrefix = normPara
            .replaceFirst("^(adventure|chapter|part|section|book|canto)".toRegex(), "")
            .replaceFirst("^[0-9ivxlcdm]+".toRegex(), "")
        if (normChapWithoutPrefix.isNotEmpty() && (normPara == normChapWithoutPrefix || normParaWithoutPrefix == normChapWithoutPrefix)) return true

        if (trimmed.length > 80) return false

        // Standalone chapter numeral / Roman numeral or number word (e.g. "I.", "1.", "IV", "One")
        val standaloneNumeralPattern = "^(?i)(?:[0-9ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty)[.:\\s—–-]*$".toRegex()
        if (standaloneNumeralPattern.matches(trimmed)) return true

        val chapterPattern = "^(?i)(chapter|part|section|book|canto|adventure|story)\\s+([0-9ivxlcdm]+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)[.:\\s—–-]*$".toRegex()
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

    fun decodeHtmlEntities(input: String): String {
        if (input.isEmpty()) return input
        var text = input.replace("\r", "")
        if (text.contains("&amp;#")) {
            text = text.replace("&amp;#", "&#")
        }

        // 1. Standard named entities
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
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
            .replace("&times;", "×")
            .replace("&divide;", "÷")
            .replace("&plusmn;", "±")
            .replace("&minus;", "−")
            .replace("&middot;", "·")
            .replace("&bull;", "•")
            .replace("&radic;", "√")
            .replace("&infin;", "∞")
            .replace("&ne;", "≠")
            .replace("&le;", "≤")
            .replace("&ge;", "≥")
            .replace("&sum;", "∑")
            .replace("&prod;", "∏")
            .replace("&alpha;", "α")
            .replace("&beta;", "β")
            .replace("&gamma;", "γ")
            .replace("&theta;", "θ")
            .replace("&pi;", "π")
            .replace("&sigma;", "σ")
            .replace("&omega;", "ω")

        // 2. Hexadecimal numeric entities: &#x...; or &#X...;
        val hexRegex = "&#[xX]([0-9a-fA-F]{1,6});".toRegex()
        text = hexRegex.replace(text) { match ->
            try {
                val cp = match.groupValues[1].toInt(16)
                if (Character.isValidCodePoint(cp)) {
                    String(Character.toChars(cp))
                } else match.value
            } catch (_: Exception) { match.value }
        }

        // 3. Decimal numeric entities: &#...;
        val decRegex = "&#([0-9]{1,7});".toRegex()
        text = decRegex.replace(text) { match ->
            try {
                val cp = match.groupValues[1].toInt(10)
                if (Character.isValidCodePoint(cp)) {
                    String(Character.toChars(cp))
                } else match.value
            } catch (_: Exception) { match.value }
        }

        return text
    }

    internal data class MathNode(
        val tag: String,
        val attributes: Map<String, String> = emptyMap(),
        val text: String = "",
        val children: List<MathNode> = emptyList()
    )

    fun transformMathML(mathXml: String): String {
        return try {
            val root = parseMathXml(mathXml)
            var evaluated = evalMathNode(root)
            if (evaluated.contains("_{")) {
                evaluated = "(?<=\\S)_\\{([^}]+)\\}".toRegex().replace(evaluated) { m ->
                    formatSubscript("", m.groupValues[1])
                }
            }
            if (evaluated.contains("^{")) {
                evaluated = "(?<=\\S)\\^\\{([^}]+)\\}".toRegex().replace(evaluated) { m ->
                    formatSuperscript("", m.groupValues[1])
                }
            }
            if (evaluated.contains("_")) {
                val mathSubRegex = "([ℒℓ𝔼𝕍ℝα-ωΑ-Ω∇∂λg]|[a-zA-Z]{1,6})_([a-zA-Z0-9]{1,6})\\b".toRegex()
                evaluated = mathSubRegex.replace(evaluated) { m ->
                    formatSubscript(m.groupValues[1], m.groupValues[2])
                }
            }
            evaluated
                .replace("[ \t]+".toRegex(), " ")
                .replace("( ", "(")
                .replace(" )", ")")
                .replace("[ ", "[")
                .replace(" ]", "]")
                .replace("{ ", "{")
                .replace(" }", "}")
                .replace(" ,", ",")
                .replace(" ;", ";")
                .replace("(- ", "(-")
                .replace("(-", "(-")
                .replace("(− ", "(−")
                .replace("(−", "(−")
                .replace("[- ", "[-")
                .replace("[-", "[-")
                .replace("[− ", "[−")
                .replace("[−", "[−")
                .replace(", - ", ", -")
                .replace(", − ", ", −")
                .replace("; - ", "; -")
                .replace("; − ", "; −")
                .replace("\u00A0", " ")
                .trim()
        } catch (_: Exception) {
            // Safe fallback
            decodeHtmlEntities(mathXml.replace("<[^>]+>".toRegex(), " ")).replace("\\s+".toRegex(), " ").trim()
        }
    }

    private fun parseMathXml(xml: String): MathNode {
        val tagRegex = "<(/?)(\\w+)([^>]*?)(\\s*/?)>".toRegex()
        class Frame(val tag: String, val attrs: Map<String, String>, val children: MutableList<MathNode>)
        val rootFrame = Frame("root", emptyMap(), mutableListOf())
        val stack = ArrayDeque<Frame>()
        stack.addLast(rootFrame)

        var lastIndex = 0
        tagRegex.findAll(xml).forEach { match ->
            val textBetween = xml.substring(lastIndex, match.range.first)
            if (textBetween.isNotBlank()) {
                stack.last().children.add(MathNode("#text", text = textBetween))
            }
            lastIndex = match.range.last + 1

            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            val rawAttrs = match.groupValues[3]
            val isSelfClosing = match.groupValues[4].endsWith("/")

            if (isClosing) {
                if (stack.size > 1) {
                    var popIdx = stack.size - 1
                    while (popIdx > 0 && stack[popIdx].tag != tagName) {
                        popIdx--
                    }
                    if (popIdx > 0) {
                        while (stack.size - 1 > popIdx) {
                            val unclosed = stack.removeLast()
                            stack.last().children.add(MathNode(unclosed.tag, unclosed.attrs, children = unclosed.children))
                        }
                        val matched = stack.removeLast()
                        stack.last().children.add(MathNode(matched.tag, matched.attrs, children = matched.children))
                    }
                }
            } else {
                val attrMap = mutableMapOf<String, String>()
                val attrRegex = "(\\w+)=[\"']([^\"']*)[\"']".toRegex()
                attrRegex.findAll(rawAttrs).forEach {
                    attrMap[it.groupValues[1].lowercase()] = it.groupValues[2]
                }
                val node = MathNode(tagName, attrMap)
                if (isSelfClosing) {
                    stack.last().children.add(node)
                } else {
                    stack.addLast(Frame(tagName, attrMap, mutableListOf()))
                }
            }
        }
        val remainingText = xml.substring(lastIndex)
        if (remainingText.isNotBlank()) {
            stack.last().children.add(MathNode("#text", text = remainingText))
        }
        while (stack.size > 1) {
            val unclosed = stack.removeLast()
            stack.last().children.add(MathNode(unclosed.tag, unclosed.attrs, children = unclosed.children))
        }
        return rootFrame.children.firstOrNull { it.tag == "math" } ?: MathNode("math", children = rootFrame.children)
    }

    private fun formatMathOp(op: String): String {
        return when (op) {
            "(", "[", "{", "⟨" -> op
            ")", "]", "}", "⟩" -> op
            ",", ";" -> "$op "
            "′", "″", "‴", "'", "\"" -> op
            "|", "‖" -> op
            "!", "¬" -> op
            "^" -> "^"
            "=", "+", "−", "-", "±", "∓", "·", "×", "*", "/", "÷" -> " $op "
            "≠", "≈", "≡", "<", ">", "≤", "≥", "≪", "≫" -> " $op "
            "→", "←", "↔", "⇒", "⇔", "⟹", "⟺" -> " $op "
            "∈", "∉", "⊂", "⊆", "∪", "∩" -> " $op "
            else -> " $op "
        }
    }

    private fun toUnicodeSubscript(s: String): String? {
        val map = mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
            '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
            '+' to '₊', '-' to '₋', '−' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
            'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
            'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ',
            's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
            'T' to 'ₜ'
        )
        val sb = StringBuilder()
        for (c in s) {
            val sub = map[c] ?: return null
            sb.append(sub)
        }
        return sb.toString()
    }

    private fun formatSubscript(base: String, sub: String): String {
        val cleanSub = sub.replace(" ", "").removeSurrounding("{", "}")
        if (cleanSub.isBlank()) return base
        if (cleanSub.length == 1) {
            val unicode = toUnicodeSubscript(cleanSub)
            if (unicode != null) return "$base$unicode"
        }
        return "$base[sub]$cleanSub[/sub]"
    }

    private fun toUnicodeSuperscript(s: String): String? {
        val map = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', '−' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
            'n' to 'ⁿ', 'i' to 'ⁱ', 'T' to 'ᵀ', 't' to 'ᵗ', '*' to '٭',
            'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ',
            'g' to 'ᵍ', 'h' to 'ʰ', 'j' to 'ʲ', 'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ',
            'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 'u' to 'ᵘ', 'v' to 'ᵛ',
            'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
        )
        val sb = StringBuilder()
        for (c in s) {
            val sup = map[c] ?: return null
            sb.append(sup)
        }
        return sb.toString()
    }

    private fun formatSuperscript(base: String, sup: String): String {
        if (sup == "′" || sup == "'") return "$base′"
        val cleanSup = sup.replace(" ", "").removeSurrounding("{", "}")
        if (cleanSup.isBlank()) return base
        val unicode = toUnicodeSuperscript(cleanSup)
        return if (unicode != null && cleanSup.length <= 3) {
            "$base$unicode"
        } else {
            "$base[sup]$cleanSup[/sup]"
        }
    }

    private fun evalMathNode(node: MathNode): String {
        return when (node.tag) {
            "#text" -> decodeHtmlEntities(node.text)
            "mi", "mn", "mtext" -> {
                val raw = if (node.children.isNotEmpty()) {
                    node.children.joinToString("") { evalMathNode(it) }
                } else {
                    decodeHtmlEntities(node.text)
                }.trim()
                val txt = raw.removePrefix("\\")
                if (txt in listOf("cos", "sin", "tan", "log", "ln", "det", "min", "max", "exp", "arg", "argmax", "argmin")) {
                    "$txt "
                } else {
                    txt
                }
            }
            "mo" -> {
                val txt = if (node.children.isNotEmpty()) {
                    node.children.joinToString("") { evalMathNode(it) }
                } else {
                    decodeHtmlEntities(node.text)
                }.trim()
                formatMathOp(txt)
            }
            "mrow", "mstyle", "mpadded", "mphantom", "merror", "semantics", "math" -> {
                node.children.filterNot { it.tag in listOf("annotation", "annotation-xml") }
                    .joinToString("") { evalMathNode(it) }
            }
            "mspace" -> {
                if (node.attributes["linebreak"] == "newline") "\n" else " "
            }
            "msub" -> {
                val base = evalMathNode(node.children.getOrNull(0) ?: MathNode("#text")).trim()
                val sub = evalMathNode(node.children.getOrNull(1) ?: MathNode("#text")).trim()
                formatSubscript(base, sub)
            }
            "msup" -> {
                val base = evalMathNode(node.children.getOrNull(0) ?: MathNode("#text")).trim()
                val sup = evalMathNode(node.children.getOrNull(1) ?: MathNode("#text")).trim()
                formatSuperscript(base, sup)
            }
            "msubsup" -> {
                val base = evalMathNode(node.children.getOrNull(0) ?: MathNode("#text")).trim()
                val sub = evalMathNode(node.children.getOrNull(1) ?: MathNode("#text")).trim()
                val sup = evalMathNode(node.children.getOrNull(2) ?: MathNode("#text")).trim()
                formatSubscript(base, sub) + formatSuperscript("", sup)
            }
            "mfrac" -> {
                val num = evalMathNode(node.children.getOrNull(0) ?: MathNode("#text")).trim()
                val den = evalMathNode(node.children.getOrNull(1) ?: MathNode("#text")).trim()
                if (num.length <= 3 && den.length <= 3 && !num.contains(" ") && !den.contains(" ")) {
                    "$num/$den"
                } else {
                    val cleanNum = if (num.contains(" ") && !num.startsWith("(") && !num.endsWith(")")) "($num)" else num
                    val cleanDen = if (den.contains(" ") && !den.startsWith("(") && !den.endsWith(")")) "($den)" else den
                    "$cleanNum / $cleanDen"
                }
            }
            "msqrt" -> {
                val inner = node.children.joinToString("") { evalMathNode(it) }.trim()
                "√($inner)"
            }
            "mroot" -> {
                val base = evalMathNode(node.children.getOrNull(0) ?: MathNode("#text")).trim()
                val idx = evalMathNode(node.children.getOrNull(1) ?: MathNode("#text")).trim()
                "${idx}√($base)"
            }
            "mfenced" -> {
                val open = node.attributes["open"] ?: "("
                val close = node.attributes["close"] ?: ")"
                val sep = node.attributes["separators"] ?: ","
                val inner = node.children.joinToString("$sep ") { evalMathNode(it).trim() }
                "$open$inner$close"
            }
            "mtable" -> {
                val rows = node.children.filter { it.tag == "mtr" }.map { tr ->
                    tr.children.filter { it.tag == "mtd" }.map { td ->
                        evalMathNode(td).trim()
                    }
                }
                if (rows.isEmpty()) ""
                else if (rows.size == 1) rows[0].joinToString("   ")
                else rows.joinToString("\n") { row -> row.joinToString("   ") }
            }
            "mover", "munder", "munderover" -> {
                val base = evalMathNode(node.children.getOrNull(0) ?: MathNode("#text")).trim()
                val overOrUnder = evalMathNode(node.children.getOrNull(1) ?: MathNode("#text")).trim()
                val third = node.children.getOrNull(2)?.let { evalMathNode(it).trim() }
                if (node.tag == "mover") {
                    when (overOrUnder) {
                        "^", "̂", "\u0302", "\u02C6" -> "$basê"
                        "→", "\u2192" -> "$base⃗"
                        "¯", "-", "_" -> "$basē"
                        else -> "$base ($overOrUnder)"
                    }
                } else if (third != null) {
                    if (base in listOf("∏", "∑", "∫")) {
                        "${base}_{$overOrUnder}^{$third}"
                    } else {
                        "$base ($overOrUnder to $third)"
                    }
                } else {
                    "$base ($overOrUnder)"
                }
            }
            else -> {
                node.children.joinToString("") { evalMathNode(it) }
            }
        }
    }
}
