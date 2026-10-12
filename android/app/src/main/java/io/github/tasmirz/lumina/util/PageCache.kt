package io.github.tasmirz.lumina.util

import androidx.compose.ui.text.AnnotatedString
import io.github.tasmirz.lumina.data.db.LuminaDatabaseHelper
import io.github.tasmirz.lumina.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * High-performance, thread-safe LRU cache using standard LinkedHashMap.
 * Fully compatible with both Android runtime and JVM unit tests without stubs.
 */
class SimpleLruCache<K, V>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(key: K): V? = synchronized(map) { map[key] }

    fun put(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }

    fun remove(key: K): V? = synchronized(map) { map.remove(key) }

    fun evictAll() {
        synchronized(map) { map.clear() }
    }

    fun snapshot(): Map<K, V> = synchronized(map) { HashMap(map) }
}

private data class PageQueueItem(val text: String, val isContinuation: Boolean = false)
private val INLINE_IMG_REGEX = Regex("\\[INLINE_IMG:[^\\]]+\\]")

object PageCache {
    // Cache up to 16 book pagination sets in memory
    private val cache = SimpleLruCache<String, List<Pair<String, String>>>(16)

    @Volatile
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun deserializePages(json: String?): List<Pair<String, String>> {
        if (json.isNullOrBlank()) return emptyList()
        val list = ArrayList<Pair<String, String>>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Pair(obj.optString("title", ""), obj.optString("text", "")))
            }
        } catch (_: Exception) {}
        return list
    }

    fun serializePages(pages: List<Pair<String, String>>): String {
        val array = org.json.JSONArray()
        for ((title, text) in pages) {
            val obj = org.json.JSONObject()
            obj.put("title", title)
            obj.put("text", text)
            array.put(obj)
        }
        return array.toString()
    }

    private fun getPersistentCacheFile(context: android.content.Context?, key: String): java.io.File? {
        val ctx = context ?: appContext
        return try {
            val dir = io.github.tasmirz.lumina.data.LuminaStorageManager.getPersistentPagesCacheDirectory(ctx)
            val exact = java.io.File(dir, "$key.json")
            if (exact.exists() && exact.length() > 0L) return exact

            val prefix = if (key.contains("_strict")) key.substringBefore("_strict") + "_strict" else key.substringBefore("_scroll") + "_scroll"
            val matching = dir.listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".json") }
            matching?.maxByOrNull { it.lastModified() } ?: exact
        } catch (_: Exception) { null }
    }

    // Bumped to v55_ to calibrate inline math, table horizontal scroll, visual filters and paged bottom clearance
    private const val CACHE_VERSION = "v56_"

    data class DeviceMetrics(
        val charsPerLine: Int,
        val maxLines: Int,
        val headerLinesCost: Int,
        val baseTargetChars: Int,
        val lineHeightDp: Float = 22f
    )

    fun estimateHeaderLinesCost(
        title: String,
        subtitle: String,
        charsPerLine: Int,
        lineHeightDp: Float,
        isLandscape: Boolean
    ): Float {
        if (isLandscape) {
            val titleLines = kotlin.math.ceil(title.length.toFloat() / (charsPerLine * 0.65f).coerceAtLeast(14f)).coerceAtLeast(1f)
            val subLines = if (subtitle.isNotBlank()) kotlin.math.ceil(subtitle.length.toFloat() / charsPerLine.coerceAtLeast(18).toFloat()).coerceAtLeast(1f) else 0f
            val totalHeaderDp = 20f + (titleLines * 26f) + (subLines * 16f) + 16f
            return (totalHeaderDp / lineHeightDp).coerceIn(2f, 8f)
        } else {
            // Eyebrow text (book title or subtitle Part...): on portrait, 11sp letter spacing 2sp
            // takes at least 2 lines, or up to 4 lines if title is long (>40 chars)
            val eyebrowLines = if (title.length > 50 || subtitle.length > 50) 3.5f else 2.0f
            val eyebrowDp = eyebrowLines * 16f

            // Chapter title at 24sp (takes ~0.65 of standard charsPerLine)
            val titleLines = kotlin.math.ceil(title.length.toFloat() / (charsPerLine * 0.65f).coerceAtLeast(12f)).coerceAtLeast(1f)
            val titleDp = titleLines * 28f

            // Subtitle at 13sp
            val subLines = if (subtitle.isNotBlank() && !subtitle.startsWith("Part", ignoreCase = true)) {
                kotlin.math.ceil(subtitle.length.toFloat() / charsPerLine.coerceAtLeast(16).toFloat()).coerceAtLeast(1f)
            } else 0f
            val subDp = subLines * 16f

            // Padding: top 4.dp + bottom 10.dp = 14.dp, spacers = 10.dp -> ~24.dp
            val spacingDp = 24f
            val totalHeaderDp = eyebrowDp + titleDp + subDp + spacingDp
            val cost = kotlin.math.ceil(totalHeaderDp / lineHeightDp).toFloat()
            return cost.coerceIn(2f, 12f)
        }
    }

    fun estimateTableLines(tableToken: String): Float {
        return try {
            val jsonStr = tableToken.removePrefix("[TABLE:").removeSuffix("]")
            val obj = JSONObject(jsonStr)
            val rows = obj.optJSONArray("rows")
            val rowCount = rows?.length() ?: 0
            val headers = obj.optJSONArray("headers")
            val headerCount = headers?.length() ?: 0
            val caption = obj.optString("caption").ifBlank { obj.optString("title") }
            val lines = 2.0f + (if (headerCount > 0) 1.5f else 0f) + (if (caption.isNotBlank()) 1.2f else 0f) + (rowCount * 1.4f)
            lines.coerceAtLeast(3f)
        } catch (_: Exception) {
            val rowCount = Regex("\\[\\s*\"[^\"]*\"").findAll(tableToken).count()
            val lines = 3.5f + (rowCount * 1.4f)
            lines.coerceAtLeast(3f)
        }
    }

    fun estimateTableMinLines(tableToken: String): Float {
        return try {
            val jsonStr = tableToken.removePrefix("[TABLE:").removeSuffix("]")
            val obj = JSONObject(jsonStr)
            val headers = obj.optJSONArray("headers")
            val headerCount = headers?.length() ?: 0
            val caption = obj.optString("caption").ifBlank { obj.optString("title") }
            val baseLines = 2.0f + (if (headerCount > 0) 1.5f else 0f) + (if (caption.isNotBlank()) 1.2f else 0f)
            baseLines + (2 * 1.4f)
        } catch (_: Exception) {
            5.5f
        }
    }

    fun trySplitTable(
        tableToken: String,
        availableLinesForTable: Float
    ): Pair<String, String>? {
        return try {
            val jsonStr = tableToken.removePrefix("[TABLE:").removeSuffix("]")
            val obj = JSONObject(jsonStr)
            val headers = obj.optJSONArray("headers")
            val rows = obj.optJSONArray("rows") ?: return null
            val totalRows = rows.length()
            if (totalRows <= 1) return null

            val caption = obj.optString("caption").ifBlank { obj.optString("title") }
            val isCont = obj.optBoolean("isContinuation", false)
            val hasHeaders = headers != null && headers.length() > 0

            val baseLines = 2.0f + (if (hasHeaders) 1.5f else 0f) + (if (caption.isNotBlank()) 1.2f else 0f)
            val linesPerRow = 1.4f

            val availableForRows = availableLinesForTable - baseLines
            if (availableForRows < linesPerRow * 2f) {
                return null
            }

            var rowsForPartA = kotlin.math.floor(availableForRows / linesPerRow).toInt()
            if (rowsForPartA >= totalRows) {
                return null
            }

            if (totalRows - rowsForPartA == 1 && rowsForPartA > 2) {
                rowsForPartA--
            }

            if (rowsForPartA < 2) return null

            val objA = JSONObject().apply {
                if (headers != null) put("headers", headers)
                val rowsA = JSONArray()
                for (i in 0 until rowsForPartA) {
                    rowsA.put(rows.getJSONArray(i))
                }
                put("rows", rowsA)
                if (caption.isNotBlank()) put("caption", caption)
                if (isCont) put("isContinuation", true)
            }

            val objB = JSONObject().apply {
                if (headers != null) put("headers", headers)
                val rowsB = JSONArray()
                for (i in rowsForPartA until totalRows) {
                    rowsB.put(rows.getJSONArray(i))
                }
                put("rows", rowsB)
                if (caption.isNotBlank()) put("caption", caption)
                put("isContinuation", true)
            }

            Pair("[TABLE:${objA.toString()}]", "[TABLE:${objB.toString()}]")
        } catch (_: Exception) {
            null
        }
    }

    fun isHeadingCandidate(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("[TABLE:") || trimmed.startsWith("[IMG:") || trimmed.startsWith("[VERSE:") || trimmed.startsWith("[QUOTE:")) {
            return false
        }
        if (trimmed.contains("\n\n")) return false
        if (isDivider(trimmed)) return false

        // Markdown headings (# Heading)
        if (trimmed.startsWith("#")) return true

        // Explicit structural chapter/section/part keywords
        val lower = trimmed.lowercase()
        if (lower.startsWith("chapter ") || lower.startsWith("section ") || lower.startsWith("part ") || lower.startsWith("act ") || lower.startsWith("scene ")) {
            return true
        }

        // Section headers ending with colon (<= 120 chars)
        if (trimmed.endsWith(":") && trimmed.length <= 120) return true

        // All-caps titles (e.g. "THE PRINCIPLES OF NEWSPEAK", <= 100 chars)
        if (trimmed.length in 3..100 && trimmed == trimmed.uppercase() && trimmed.count { it.isLetter() } >= 3) {
            return true
        }

        // Short title without terminal sentence punctuation (no period, question mark, exclamation, or dialogue quotes)
        val lastChar = trimmed.last()
        if (trimmed.length <= 75 && lastChar !in ".!?\"'”’)" && trimmed.count { it == ' ' } <= 9) {
            if (!trimmed.startsWith("“") && !trimmed.startsWith("\"") && !trimmed.startsWith("—")) {
                return true
            }
        }

        return false
    }

    fun estimateImageLines(imageToken: String, lineHeightDp: Float): Float {
        val rawPath = imageToken.removePrefix("[IMG:").removeSuffix("]").trim()
        val cleanPath = rawPath.removePrefix("file://").replace("\\/", "/")
        val decodedPath = try { java.net.URLDecoder.decode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
        val file = if (java.io.File(cleanPath).exists()) java.io.File(cleanPath) else java.io.File(decodedPath)
        if (file.exists() && file.length() > 0) {
            try {
                val opt = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opt)
                if (opt.outWidth > 0 && opt.outHeight > 0) {
                    val ar = opt.outWidth.toFloat() / opt.outHeight.toFloat()
                    // Single-line inline or display equations (very wide aspect ratio or small height)
                    if (opt.outHeight <= 60 || ar >= 4.0f) {
                        val hDp = (opt.outHeight * 0.35f).coerceIn(14f, 32f)
                        return (hDp / lineHeightDp).coerceIn(0.9f, 1.6f)
                    } else if (opt.outHeight <= 140 || ar >= 2.2f) {
                        val hDp = (opt.outHeight * 0.40f).coerceIn(24f, 54f)
                        return (hDp / lineHeightDp).coerceIn(1.4f, 2.6f)
                    } else if (opt.outHeight <= 360) {
                        val hDp = (opt.outHeight * 0.45f).coerceIn(54f, 140f)
                        return (hDp / lineHeightDp).coerceIn(2.6f, 6.0f)
                    } else {
                        // Capped to max image display height in InlineReaderImage (heightIn(max = 280.dp))
                        val hDp = 220f
                        return (hDp / lineHeightDp).coerceIn(6.0f, 10.0f)
                    }
                }
            } catch (_: Exception) {}
        }
        val lower = rawPath.lowercase()
        if (lower.contains("eq") || lower.contains("math") || lower.contains("formula") || lower.contains("tex")) {
            return 1.4f
        }
        return 3.0f
    }

    fun isDivider(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed == "[DIVIDER]" || trimmed == "⁂" || trimmed == "* * *" || trimmed == "***"
    }

    fun isFigureCaption(text: String): Boolean {
        val trimmed = text.trim().replace('\u00A0', ' ')
        if (trimmed.isEmpty() || trimmed.startsWith("[TABLE:") || trimmed.startsWith("[IMG:") || trimmed.startsWith("[VERSE:") || trimmed.startsWith("[QUOTE:")) {
            return false
        }
        if (trimmed.length > 380) return false
        val regex = "^(?:Figure|Fig\\.?|Illustration|Diagram|Photo|Image|Plate|Chart|Table)\\s*[:\\d\\.\\-].*".toRegex(RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(trimmed) ||
                trimmed.startsWith("Figure", ignoreCase = true) ||
                trimmed.startsWith("Fig.", ignoreCase = true) ||
                trimmed.startsWith("Fig ", ignoreCase = true) ||
                trimmed.startsWith("Illustration", ignoreCase = true) ||
                trimmed.startsWith("Diagram", ignoreCase = true) ||
                trimmed.startsWith("Photo ", ignoreCase = true) ||
                trimmed.startsWith("Image ", ignoreCase = true)
    }

    fun calculateDeviceMetrics(
        fontSize: Int,
        isLandscape: Boolean = false,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        lineHeightMultiplier: Float = 1.45f,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0,
        pagedBottomClearanceDp: Int = 28
    ): DeviceMetrics {
        val screenW = if (screenWidthDp > 0) screenWidthDp else if (isLandscape) 820 else 392
        val screenH = if (screenHeightDp > 0) screenHeightDp else if (isLandscape) 392 else 820

        // Dynamic horizontal margin deduction (side padding, cutouts)
        val horizontalMargin = if (isLandscape) {
            maxOf((horizontalPaddingDp * 3.6f).toInt(), 112)
        } else {
            (horizontalPaddingDp * 2).coerceIn(24, 80)
        }

        // Dynamic vertical margin deduction (status bar, top bar, bottom dock, reading controls)
        val verticalMargin = if (isLandscape) {
            56 + (verticalPaddingDp * 0.3f).toInt()
        } else {
            88 + pagedBottomClearanceDp.coerceIn(0, 100) + (verticalPaddingDp * 0.35f).toInt()
        }

        // Bottom safety buffer: 0.8% in portrait, 0.5% in landscape to ensure maximum usable reading height
        val bottomSafetyBufferDp = if (isLandscape) {
            (screenH * 0.005f).coerceIn(2f, 8f)
        } else {
            (screenH * 0.008f).coerceIn(4f, 10f)
        }
        val usableW = (screenW - horizontalMargin).coerceAtLeast(180)
        val usableH = (screenH - verticalMargin - bottomSafetyBufferDp).coerceAtLeast(140f)

        // Dynamic line height in dp: font size * effective line height multiplier + glyph descent/ascent
        val effectiveMultiplier = lineHeightMultiplier.coerceIn(1.1f, 2.2f)
        val lineHeightDp = (fontSize * effectiveMultiplier).coerceAtLeast(14f)

        // Dynamic average character width in dp for serif/sans-serif book typography
        val avgCharWidthDp = (fontSize * 0.44f).coerceAtLeast(4.5f)

        val charsPerLine = (usableW / avgCharWidthDp).toInt().coerceIn(18, 180)
        val rawMaxLines = (usableH / lineHeightDp).toInt().coerceIn(4, 80)
        val maxLines = (rawMaxLines - safeLinesToRemove.coerceIn(0, 10)).coerceAtLeast(2)

        val headerHeightDp = if (isLandscape) 64f else 96f
        val headerLinesCost = kotlin.math.ceil(headerHeightDp / lineHeightDp).toInt().coerceIn(if (isLandscape) 2 else 3, 6)

        // Base target characters for non-strict scroll engine (~90% density)
        val baseTargetChars = (charsPerLine * maxLines * 0.90f).toInt().coerceAtLeast(200)

        return DeviceMetrics(
            charsPerLine = charsPerLine,
            maxLines = maxLines,
            headerLinesCost = headerLinesCost,
            baseTargetChars = baseTargetChars,
            lineHeightDp = lineHeightDp
        )
    }

    private fun getCacheKey(
        bookId: String,
        chaptersCount: Int,
        fontSize: Int,
        isLandscape: Boolean,
        isStrictPaged: Boolean,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0,
        fillPageToBottom: Boolean = true,
        allowTableSplitting: Boolean = true,
        pagedBottomClearanceDp: Int = 28
    ): String {
        val orientation = if (isLandscape) "land" else "port"
        val mode = if (isStrictPaged) "strict" else "scroll"
        val dims = if (screenWidthDp > 0 && screenHeightDp > 0) "_${screenWidthDp}x${screenHeightDp}_p${horizontalPaddingDp}_${verticalPaddingDp}_ps${(paragraphSpacingMultiplier * 10).toInt()}_sl${safeLinesToRemove}_fp${if (fillPageToBottom) 1 else 0}_ts${if (allowTableSplitting) 1 else 0}_bc${pagedBottomClearanceDp}" else ""
        return "${CACHE_VERSION}${bookId}_${chaptersCount}_${fontSize}_${orientation}_${mode}${dims}"
    }

    fun getCached(
        bookId: String,
        chaptersCount: Int,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0,
        fillPageToBottom: Boolean = true,
        allowTableSplitting: Boolean = true,
        pagedBottomClearanceDp: Int = 28,
        context: android.content.Context? = null
    ): List<Pair<String, String>>? {
        val key = getCacheKey(bookId, chaptersCount, fontSize, isLandscape, isStrictPaged, screenWidthDp, screenHeightDp, horizontalPaddingDp, verticalPaddingDp, paragraphSpacingMultiplier, safeLinesToRemove, fillPageToBottom, allowTableSplitting, pagedBottomClearanceDp)
        val inMem = cache.get(key)
        if (inMem != null) return inMem

        val prefix = if (key.contains("_strict")) key.substringBefore("_strict") + "_strict" else key.substringBefore("_scroll") + "_scroll"
        for ((k, v) in cache.snapshot()) {
            if (k.startsWith(prefix) && v.isNotEmpty()) {
                cache.put(key, v)
                return v
            }
        }
        return null
    }

    fun getOrCompute(
        bookId: String,
        chapters: List<Chapter>,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        lineHeightMultiplier: Float = 1.45f,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0,
        fillPageToBottom: Boolean = true,
        allowTableSplitting: Boolean = true,
        pagedBottomClearanceDp: Int = 28,
        dbHelper: LuminaDatabaseHelper? = null,
        context: android.content.Context? = null
    ): List<Pair<String, String>> = kotlinx.coroutines.runBlocking {
        getOrComputeAsync(
            bookId = bookId,
            chapters = chapters,
            fontSize = fontSize,
            isLandscape = isLandscape,
            isStrictPaged = isStrictPaged,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            horizontalPaddingDp = horizontalPaddingDp,
            verticalPaddingDp = verticalPaddingDp,
            lineHeightMultiplier = lineHeightMultiplier,
            paragraphSpacingMultiplier = paragraphSpacingMultiplier,
            safeLinesToRemove = safeLinesToRemove,
            fillPageToBottom = fillPageToBottom,
            allowTableSplitting = allowTableSplitting,
            pagedBottomClearanceDp = pagedBottomClearanceDp,
            dbHelper = dbHelper,
            context = context
        )
    }

    fun computeChapterPages(
        chapter: Chapter,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        lineHeightMultiplier: Float = 1.45f,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0,
        fillPageToBottom: Boolean = true,
        allowTableSplitting: Boolean = true,
        pagedBottomClearanceDp: Int = 28
    ): List<Pair<String, String>> {
        return computePages(
            chapters = listOf(chapter),
            fontSize = fontSize,
            isLandscape = isLandscape,
            isStrictPaged = isStrictPaged,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            horizontalPaddingDp = horizontalPaddingDp,
            verticalPaddingDp = verticalPaddingDp,
            lineHeightMultiplier = lineHeightMultiplier,
            paragraphSpacingMultiplier = paragraphSpacingMultiplier,
            safeLinesToRemove = safeLinesToRemove,
            fillPageToBottom = fillPageToBottom,
            allowTableSplitting = allowTableSplitting,
            pagedBottomClearanceDp = pagedBottomClearanceDp
        )
    }

    suspend fun getOrComputeAsync(
        bookId: String,
        chapters: List<Chapter>,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        lineHeightMultiplier: Float = 1.45f,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0,
        fillPageToBottom: Boolean = true,
        allowTableSplitting: Boolean = true,
        pagedBottomClearanceDp: Int = 28,
        dbHelper: LuminaDatabaseHelper? = null,
        context: android.content.Context? = null,
        activeChapterIndex: Int = 0,
        onActiveChapterReady: ((List<Pair<String, String>>) -> Unit)? = null
    ): List<Pair<String, String>> = withContext(Dispatchers.Default) {
        val key = getCacheKey(bookId, chapters.size, fontSize, isLandscape, isStrictPaged, screenWidthDp, screenHeightDp, horizontalPaddingDp, verticalPaddingDp, paragraphSpacingMultiplier, safeLinesToRemove, fillPageToBottom, allowTableSplitting, pagedBottomClearanceDp)
        val cached = cache.get(key)
        if (cached != null) {
            onActiveChapterReady?.invoke(cached)
            return@withContext cached
        }

        // Fast in-memory high-tolerance prefix match
        val prefix = if (key.contains("_strict")) key.substringBefore("_strict") + "_strict" else key.substringBefore("_scroll") + "_scroll"
        for ((k, v) in cache.snapshot()) {
            if (k.startsWith(prefix) && v.isNotEmpty()) {
                cache.put(key, v)
                onActiveChapterReady?.invoke(v)
                return@withContext v
            }
        }

        if (dbHelper != null) {
            val fromDb = dbHelper.getPageCache(key)
            if (fromDb != null && fromDb.isNotEmpty()) {
                cache.put(key, fromDb)
                onActiveChapterReady?.invoke(fromDb)
                return@withContext fromDb
            }
        }

        // Persistent file cache check (survives app reinstalls and updates)
        val pFile = getPersistentCacheFile(context, key)
        if (pFile != null && pFile.exists() && pFile.length() > 0L) {
            val t0 = System.currentTimeMillis()
            try {
                val json = pFile.readText()
                val fromFile = dbHelper?.deserializePages(json) ?: deserializePages(json)
                if (fromFile.isNotEmpty()) {
                    cache.put(key, fromFile)
                    if (dbHelper != null) {
                        try { dbHelper.savePageCache(key, bookId, -1, fromFile) } catch (_: Exception) {}
                    }
                    io.github.tasmirz.lumina.util.LuminaLog.perf("PageCache.getOrComputeAsync", System.currentTimeMillis() - t0, "Loaded ${fromFile.size} pages from persistent file")
                    onActiveChapterReady?.invoke(fromFile)
                    return@withContext fromFile
                }
            } catch (_: Exception) {}
        }

        // Cache completely missed — ensure full chapter paragraphs are loaded if chapters only had metadata
        val fullChapters = if (chapters.any { it.paragraphs.isNotEmpty() }) {
            chapters
        } else {
            dbHelper?.getAllChaptersForBook(bookId) ?: chapters
        }

        val list = computePages(
            chapters = fullChapters,
            fontSize = fontSize,
            isLandscape = isLandscape,
            isStrictPaged = isStrictPaged,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            horizontalPaddingDp = horizontalPaddingDp,
            verticalPaddingDp = verticalPaddingDp,
            lineHeightMultiplier = lineHeightMultiplier,
            paragraphSpacingMultiplier = paragraphSpacingMultiplier,
            safeLinesToRemove = safeLinesToRemove,
            fillPageToBottom = fillPageToBottom,
            allowTableSplitting = allowTableSplitting,
            pagedBottomClearanceDp = pagedBottomClearanceDp
        )
        if (list.isNotEmpty()) {
            cache.put(key, list)
            onActiveChapterReady?.invoke(list)
            if (dbHelper != null) {
                try { dbHelper.savePageCache(key, bookId, -1, list) } catch (_: Exception) {}
            }
            if (pFile != null) {
                try { pFile.writeText(serializePages(list)) } catch (_: Exception) {}
            }
        }
        return@withContext list
    }

    fun computePages(
        chapters: List<Chapter>,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        lineHeightMultiplier: Float = 1.45f,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0,
        fillPageToBottom: Boolean = true,
        allowTableSplitting: Boolean = true,
        pagedBottomClearanceDp: Int = 28
    ): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val metrics = calculateDeviceMetrics(
            fontSize = fontSize,
            isLandscape = isLandscape,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            horizontalPaddingDp = horizontalPaddingDp,
            verticalPaddingDp = verticalPaddingDp,
            lineHeightMultiplier = lineHeightMultiplier,
            paragraphSpacingMultiplier = paragraphSpacingMultiplier,
            safeLinesToRemove = safeLinesToRemove,
            pagedBottomClearanceDp = pagedBottomClearanceDp
        )

        if (isStrictPaged) {
            // ═════════════════════════════════════════════════════════════════
            // STRICT PAGED ENGINE (Zero overflow, dynamically calibrated to device screen)
            // ═════════════════════════════════════════════════════════════════
            val charsPerLine = metrics.charsPerLine
            val maxLines = metrics.maxLines
            val headerLinesCost = metrics.headerLinesCost

            // Word-wrapping ragged right line efficiency factor (~91% of theoretical character capacity per line)
            val effectiveCharsPerLine = (charsPerLine * 0.91f).coerceAtLeast(14f)

            // When a new paragraph is appended with "\n\n", calculate the exact vertical line height gap
            // Paragraph gap = 1 blank line * (paragraphSpacingMultiplier / lineHeightMultiplier)
            val paragraphGapLines = ((paragraphSpacingMultiplier.coerceIn(1.0f, 2.5f) / lineHeightMultiplier.coerceIn(1.1f, 2.2f)) * 1.0f).coerceIn(1.0f, 2.5f)

            chapters.forEach { chap ->
                val paragraphs = chap.paragraphs
                if (paragraphs.isEmpty() || paragraphs.all { it.isBlank() }) {
                    list.add(Pair(chap.title, "CHAPTER_START:::${chap.title}:::${chap.subtitle}:::"))
                    return@forEach
                }

                // Combine near-empty front-matter spine items (e.g. lone URL + divider, or copyright snippet with < 100 chars)
                val isVeryShortFrontMatter = !chap.title.contains("Chapter", ignoreCase = true) &&
                    !chap.title.contains("Part", ignoreCase = true) &&
                    paragraphs.size <= 2 &&
                    paragraphs.sumOf { it.length } < 100
                if (isVeryShortFrontMatter && list.isNotEmpty() && list.size <= 4) {
                    val lastPage = list.removeAt(list.size - 1)
                    list.add(Pair(lastPage.first, lastPage.second + "\n\n" + paragraphs.joinToString("\n\n")))
                    return@forEach
                }

                val chapHeaderLinesCost = estimateHeaderLinesCost(
                    title = chap.title,
                    subtitle = chap.subtitle,
                    charsPerLine = charsPerLine,
                    lineHeightDp = metrics.lineHeightDp,
                    isLandscape = isLandscape
                )

                var isFirstPageOfChapter = true
                val currentBatch = StringBuilder()
                var currentLines = 0f

                val queue = ArrayDeque<PageQueueItem>()
                for (p in paragraphs) {
                    val trimmed = p.trim()
                    if (trimmed.isNotEmpty()) {
                        queue.add(PageQueueItem(trimmed, isContinuation = false))
                    }
                }

                fun flushBatch() {
                    val str = currentBatch.toString().trim()
                    if (str.isNotEmpty() || isFirstPageOfChapter) {
                        if (isFirstPageOfChapter) {
                            list.add(Pair(chap.title, "CHAPTER_START:::${chap.title}:::${chap.subtitle}:::$str"))
                        } else {
                            list.add(Pair(chap.title, str))
                        }
                        currentBatch.clear()
                        currentLines = 0f
                        isFirstPageOfChapter = false
                    }
                }

                while (queue.isNotEmpty()) {
                    val item = queue.removeFirst()
                    val p = item.text
                    if (p.startsWith("[IMG:") && p.endsWith("]")) {
                        val pageLineBudget = if (isFirstPageOfChapter) {
                            (maxLines - chapHeaderLinesCost).coerceAtLeast(if (isLandscape) 2f else 3f)
                        } else {
                            maxLines.toFloat()
                        }
                        val availableLines = pageLineBudget - currentLines
                        val blankLinesBefore = if (currentBatch.isEmpty()) 0f else paragraphGapLines
                        val imgLines = estimateImageLines(p, metrics.lineHeightDp)
                        val totalLinesNeeded = blankLinesBefore + imgLines

                        val batchStr = currentBatch.toString().trimEnd()
                        val lastSep = batchStr.lastIndexOf("\n\n")
                        val candidateHeading = (if (lastSep >= 0) batchStr.substring(lastSep + 2) else batchStr).trim()
                        val hasHeadingBefore = candidateHeading.isNotBlank() && (
                            isFigureCaption(candidateHeading) ||
                            (isHeadingCandidate(candidateHeading) && candidateHeading.startsWith("Figure", ignoreCase = true))
                        )
                        val isIntroductoryColon = candidateHeading.isNotBlank() && candidateHeading.endsWith(":") && candidateHeading.length <= 160

                        if (totalLinesNeeded <= availableLines) {
                            // Fits on current page with introducing text (equations, formulas, diagrams)
                            if (currentBatch.isNotEmpty()) {
                                currentBatch.append("\n\n")
                            }
                            currentBatch.append(p)
                            currentLines += totalLinesNeeded

                            val nextItem = queue.firstOrNull()
                            if (nextItem != null && isFigureCaption(nextItem.text)) {
                                val capLines = 1.5f
                                if (availableLines - totalLinesNeeded >= capLines) {
                                    queue.removeFirst()
                                    currentBatch.append("\n\n")
                                    currentBatch.append(nextItem.text)
                                    currentLines += capLines + paragraphGapLines
                                }
                            }
                            continue
                        } else if (hasHeadingBefore || isIntroductoryColon) {
                            // Pull back the heading or introductory colon sentence so it accompanies the image/formula to the next page!
                            if (lastSep >= 0) {
                                currentBatch.setLength(lastSep)
                            } else {
                                currentBatch.clear()
                            }
                            flushBatch()
                            queue.addFirst(item)
                            queue.addFirst(PageQueueItem(candidateHeading, isContinuation = false))
                            continue
                        } else {
                            if (currentBatch.isNotEmpty()) {
                                flushBatch()
                                queue.addFirst(item)
                                continue
                            } else {
                                // currentBatch is empty: Image starts at top of page.
                                if (imgLines < pageLineBudget) {
                                    currentBatch.append(p)
                                    currentLines += imgLines
                                    val nextItem = queue.firstOrNull()
                                    if (nextItem != null && isFigureCaption(nextItem.text)) {
                                        queue.removeFirst()
                                        currentBatch.append("\n\n").append(nextItem.text)
                                        currentLines += 1.5f + paragraphGapLines
                                    }
                                    continue
                                } else {
                                    // Oversized full-page diagram
                                    val nextItem = queue.firstOrNull()
                                    if (nextItem != null && isFigureCaption(nextItem.text)) {
                                        queue.removeFirst()
                                        list.add(Pair(chap.title, "$p\n\n${nextItem.text}"))
                                    } else {
                                        list.add(Pair(chap.title, p))
                                    }
                                    continue
                                }
                            }
                        }
                    }

                    if (p.startsWith("[TABLE:") && p.endsWith("]")) {
                        val pageLineBudget = if (isFirstPageOfChapter) {
                            (maxLines - chapHeaderLinesCost).coerceAtLeast(if (isLandscape) 2f else 3f)
                        } else {
                            maxLines.toFloat()
                        }
                        val availableLines = pageLineBudget - currentLines
                        val blankLinesBefore = if (currentBatch.isEmpty()) 0f else paragraphGapLines
                        val tableLines = estimateTableLines(p)
                        val totalLinesNeeded = blankLinesBefore + tableLines

                        if (totalLinesNeeded <= availableLines) {
                            // Table fits on the current page!
                            if (currentBatch.isNotEmpty()) {
                                currentBatch.append("\n\n")
                            }
                            currentBatch.append(p)
                            currentLines += totalLinesNeeded
                            continue
                        } else {
                            if (allowTableSplitting) {
                                val availableForTable = availableLines - blankLinesBefore
                                val splitResult = trySplitTable(p, availableForTable)
                                if (splitResult != null) {
                                    val (partA, partB) = splitResult
                                    if (currentBatch.isNotEmpty()) {
                                        currentBatch.append("\n\n")
                                    }
                                    currentBatch.append(partA)
                                    flushBatch()
                                    queue.addFirst(PageQueueItem(partB, isContinuation = true))
                                    continue
                                }
                            }

                            if (currentBatch.isNotEmpty()) {
                                // Reactive Pullback: Check if currentBatch ends with an orphan heading / divider
                                val batchStr = currentBatch.toString().trimEnd()
                                val lastSep = batchStr.lastIndexOf("\n\n")
                                if (lastSep >= 0) {
                                    val candidateHeading = batchStr.substring(lastSep + 2).trim()
                                    if (isHeadingCandidate(candidateHeading)) {
                                        val textBeforeHeading = batchStr.substring(0, lastSep).trimEnd()
                                        val prevSep = textBeforeHeading.lastIndexOf("\n\n")
                                        val prevPara = (if (prevSep >= 0) textBeforeHeading.substring(prevSep + 2) else textBeforeHeading).trim()
                                        if (isDivider(prevPara) && prevSep >= 0) {
                                            // Preceded by substantive text + divider + heading.
                                            // Pull back both the divider and the heading so they accompany the table to the next page!
                                            currentBatch.setLength(prevSep)
                                            queue.addFirst(item)
                                            queue.addFirst(PageQueueItem(candidateHeading, isContinuation = false))
                                            queue.addFirst(PageQueueItem(prevPara, isContinuation = false))
                                            flushBatch()
                                            continue
                                        } else {
                                            // Pull back the heading so it accompanies the table to the next page!
                                            currentBatch.setLength(lastSep)
                                            queue.addFirst(item)
                                            queue.addFirst(PageQueueItem(candidateHeading, isContinuation = false))
                                            flushBatch()
                                            continue
                                        }
                                    }
                                } else {
                                    // candidateHeading is the ONLY item in currentBatch (at top of page)
                                    val candidateHeading = batchStr.trim()
                                    if (isHeadingCandidate(candidateHeading)) {
                                        // Heading is alone at top of page. Rather than flushing the heading alone onto an empty page,
                                        // keep heading and table together on this page (with scroll if needed) so the heading is never orphaned!
                                        currentBatch.append("\n\n").append(p)
                                        flushBatch()
                                        continue
                                    }
                                }

                                // If heading was already at top of page (lastSep == -1) and table is oversized,
                                // keep them together on this page with vertical scrolling so the heading is preserved!
                                if (tableLines > pageLineBudget) {
                                    currentBatch.append("\n\n").append(p)
                                    flushBatch()
                                    continue
                                }

                                flushBatch()
                                queue.addFirst(item)
                                continue
                            } else {
                                if (allowTableSplitting) {
                                    val splitResult = trySplitTable(p, pageLineBudget)
                                    if (splitResult != null) {
                                        val (partA, partB) = splitResult
                                        currentBatch.append(partA)
                                        flushBatch()
                                        queue.addFirst(PageQueueItem(partB, isContinuation = true))
                                        continue
                                    }
                                }

                                if (tableLines <= pageLineBudget) {
                                    currentBatch.append(p)
                                    currentLines += tableLines
                                    continue
                                } else {
                                    // Oversized table occupies full page
                                    list.add(Pair(chap.title, p))
                                    continue
                                }
                            }
                        }
                    }

                    // Convert quotes, verses, and scene dividers to inline flow
                    val effectiveP = if (p.startsWith("[QUOTE:") && p.endsWith("]")) {
                        val q = p.removePrefix("[QUOTE:").removeSuffix("]").trim()
                        "“$q”"
                    } else if (p.startsWith("[VERSE:") && p.endsWith("]")) {
                        p.removePrefix("[VERSE:").removeSuffix("]").trim()
                    } else if (p == "[DIVIDER]") {
                        "[DIVIDER]"
                    } else {
                        p
                    }

                    val pageLineBudget = if (isFirstPageOfChapter) {
                        (maxLines - chapHeaderLinesCost).coerceAtLeast(if (isLandscape) 2f else 3f)
                    } else {
                        maxLines.toFloat()
                    }

                    val availableLines = pageLineBudget - currentLines

                    // A new paragraph consumes blank line space before it (\n\n) + paragraph spacing multiplier
                    // If currentBatch is empty or this item is continuing a broken paragraph, no blank line is consumed.
                    val blankLinesBefore = if (currentBatch.isEmpty() || item.isContinuation) 0f else paragraphGapLines
                    val cleanPForLen = if (effectiveP.contains("[INLINE_IMG:")) effectiveP.replace(INLINE_IMG_REGEX, "    ") else effectiveP
                    val textLines = kotlin.math.ceil(cleanPForLen.length.toFloat() / effectiveCharsPerLine).coerceAtLeast(1f)
                    val totalLinesNeeded = blankLinesBefore + textLines

                    // ── Proactive Keep-With-Next Lookahead ──
                    if (currentBatch.isNotEmpty() && !item.isContinuation) {
                        // 1. Heading followed immediately by a table or subsequent text
                        if (isHeadingCandidate(effectiveP) && queue.isNotEmpty()) {
                            val nextItem = queue.first()
                            if (nextItem.text.startsWith("[TABLE:") && nextItem.text.endsWith("]")) {
                                val minTableLines = paragraphGapLines + if (allowTableSplitting) {
                                    estimateTableMinLines(nextItem.text)
                                } else {
                                    estimateTableLines(nextItem.text)
                                }
                                if (totalLinesNeeded + minTableLines > availableLines) {
                                    // Heading and table cannot fit together on this page.
                                    // Flush current page so heading and table start together on the next page.
                                    flushBatch()
                                    queue.addFirst(item)
                                    continue
                                }
                            } else if (!nextItem.text.startsWith("[IMG:")) {
                                // Heading followed by regular text: require at least 2 lines of next text on the same page
                                val minNextTextLines = paragraphGapLines + 2f
                                if (totalLinesNeeded + minNextTextLines > availableLines) {
                                    flushBatch()
                                    queue.addFirst(item)
                                    continue
                                }
                            }
                        }

                        // 2. Divider followed by heading & table, or divider followed by table
                        if (isDivider(effectiveP) && queue.isNotEmpty()) {
                            val next1 = queue.first().text
                            if (next1.startsWith("[TABLE:") && next1.endsWith("]")) {
                                val minTableLines = paragraphGapLines + if (allowTableSplitting) {
                                    estimateTableMinLines(next1)
                                } else {
                                    estimateTableLines(next1)
                                }
                                if (totalLinesNeeded + minTableLines > availableLines) {
                                    flushBatch()
                                    queue.addFirst(item)
                                    continue
                                }
                            } else if (isHeadingCandidate(next1)) {
                                val nextHeadingLines = paragraphGapLines + kotlin.math.ceil(next1.length.toFloat() / effectiveCharsPerLine).coerceAtLeast(1f)
                                val next2 = queue.elementAtOrNull(1)?.text
                                val subsequentLines = if (next2 != null && next2.startsWith("[TABLE:") && next2.endsWith("]")) {
                                    paragraphGapLines + if (allowTableSplitting) estimateTableMinLines(next2) else estimateTableLines(next2)
                                } else {
                                    paragraphGapLines + 2f
                                }
                                if (totalLinesNeeded + nextHeadingLines + subsequentLines > availableLines) {
                                    flushBatch()
                                    queue.addFirst(item)
                                    continue
                                }
                            }
                        }
                    }

                    if (totalLinesNeeded <= availableLines) {
                        // Entire item fits on the current page!
                        if (currentBatch.isNotEmpty()) {
                            currentBatch.append(if (item.isContinuation) " " else "\n\n")
                        }
                        currentBatch.append(effectiveP)
                        currentLines += totalLinesNeeded
                    } else {
                        // It does not fit entirely.
                        val linesForThisText = availableLines - blankLinesBefore

                        if (fillPageToBottom && linesForThisText >= 1f) {
                            if (!item.isContinuation && isHeadingCandidate(effectiveP) && currentBatch.isNotEmpty()) {
                                flushBatch()
                                queue.addFirst(item)
                                continue
                            }
                            // Break text to fill remaining lines on current page
                            val maxCharsToFit = (linesForThisText * effectiveCharsPerLine).toInt()
                            if (maxCharsToFit < 35 && currentBatch.isNotEmpty()) {
                                // Orphan protection: fitting < 35 characters at the very bottom of a page leaves
                                // an awkward orphan label/fragment. Flush current page and start on the next page.
                                flushBatch()
                                queue.addFirst(item)
                                continue
                            }
                            val (partA, partB) = breakText(effectiveP, maxCharsToFit, effectiveCharsPerLine.toInt())

                            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                                if (!item.isContinuation && partA.length < 35 && currentBatch.isNotEmpty()) {
                                    // Orphan protection: fitting < 35 characters of a new paragraph at the very bottom
                                    // leaves an awkward orphan label/fragment. Flush current page and start on the next page.
                                    flushBatch()
                                    queue.addFirst(item)
                                    continue
                                }
                                if (currentBatch.isNotEmpty()) {
                                    currentBatch.append(if (item.isContinuation) " " else "\n\n")
                                }
                                currentBatch.append(partA)
                                currentLines += blankLinesBefore + kotlin.math.ceil(partA.length.toFloat() / effectiveCharsPerLine).coerceAtLeast(1f)
                                flushBatch()
                                queue.addFirst(PageQueueItem(partB, isContinuation = true))
                                continue
                            }
                        }

                        // If linesForThisText < 1 or breakText couldn't split:
                        if (currentBatch.isNotEmpty()) {
                            // Flush current page and start this item fresh on next page
                            flushBatch()
                            queue.addFirst(item)
                        } else {
                            // currentBatch is empty, but item exceeds pageLineBudget (giant text).
                            // Force break to fit pageLineBudget!
                            val maxCharsToFit = (pageLineBudget * effectiveCharsPerLine).toInt()
                            val (partA, partB) = breakText(p, maxCharsToFit, effectiveCharsPerLine.toInt())
                            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                                currentBatch.append(partA)
                                flushBatch()
                                queue.addFirst(PageQueueItem(partB, isContinuation = true))
                            } else {
                                val cut = maxCharsToFit.coerceIn(1, p.length - 1)
                                currentBatch.append(p.substring(0, cut))
                                flushBatch()
                                queue.addFirst(PageQueueItem(p.substring(cut).trimStart(), isContinuation = true))
                            }
                        }
                    }
                }

                flushBatch()

                // Front-matter orphan consolidation: Merge trailing orphan page (< 140 chars with URL/divider/copyright) into previous page
                val isFrontMatter = !chap.title.contains("Chapter", ignoreCase = true) &&
                    !chap.title.contains("Part", ignoreCase = true) &&
                    !chap.title.contains("Book", ignoreCase = true) &&
                    !chap.title.contains("Act", ignoreCase = true)
                if (isFrontMatter && list.size >= 2) {
                    val lastContent = list.last().second.removePrefix("CHAPTER_START:::").removePrefix("TITLE:::").trim()
                    if (lastContent.length < 140 && (lastContent.contains("www.") || lastContent.contains("http") || lastContent.contains("[DIVIDER]") || lastContent.contains("©") || lastContent.contains("Copyright"))) {
                        val orphan = list.removeAt(list.size - 1)
                        val prev = list.removeAt(list.size - 1)
                        list.add(Pair(prev.first, prev.second + "\n\n" + orphan.second))
                    }
                }
            }
        } else {
            // ═════════════════════════════════════════════════════════════════
            // PAGED + SCROLL ENGINE (Whole paragraphs preserved; overflows scrollable)
            // ═════════════════════════════════════════════════════════════════
            val targetCharsPerPage = metrics.baseTargetChars
            val firstPageTarget = if (isLandscape) {
                (targetCharsPerPage * 0.75f).toInt().coerceAtLeast(250)
            } else {
                (targetCharsPerPage - (metrics.charsPerLine * metrics.headerLinesCost)).coerceAtLeast((targetCharsPerPage * 0.70f).toInt())
            }

            chapters.forEach { chap ->
                val paragraphs = chap.paragraphs
                if (paragraphs.isEmpty()) {
                    list.add(Pair(chap.title, "CHAPTER_START:::${chap.title}:::${chap.subtitle}:::"))
                    return@forEach
                }

                val chapFirstPageTarget = if (isLandscape) {
                    (targetCharsPerPage * 0.75f).toInt().coerceAtLeast(250)
                } else {
                    val cost = estimateHeaderLinesCost(chap.title, chap.subtitle, metrics.charsPerLine, metrics.lineHeightDp, isLandscape)
                    (targetCharsPerPage - (metrics.charsPerLine * cost).toInt()).coerceAtLeast((targetCharsPerPage * 0.35f).toInt())
                }

                var isFirstPageOfChapter = true
                val currentBatch = StringBuilder(targetCharsPerPage + 300)

                fun flushBatch(isChapterHeader: Boolean = false) {
                    val str = currentBatch.toString().trim()
                    if (str.isNotEmpty() || isChapterHeader) {
                        if (isChapterHeader) {
                            list.add(Pair(chap.title, "CHAPTER_START:::${chap.title}:::${chap.subtitle}:::$str"))
                        } else {
                            list.add(Pair(chap.title, str))
                        }
                        currentBatch.clear()
                    }
                }

                var pIdx = 0
                while (pIdx < paragraphs.size) {
                    val p = paragraphs[pIdx]
                    if (p.startsWith("[IMG:") && p.endsWith("]")) {
                        val imgLines = estimateImageLines(p, metrics.lineHeightDp)
                        val imgChars = (imgLines * metrics.charsPerLine).toInt()
                        val target = if (isFirstPageOfChapter) chapFirstPageTarget else targetCharsPerPage
                        if (imgLines <= 8f && currentBatch.length + imgChars <= target) {
                            if (currentBatch.isNotEmpty()) currentBatch.append("\n\n")
                            currentBatch.append(p)
                            val nextP = paragraphs.getOrNull(pIdx + 1)
                            if (nextP != null && isFigureCaption(nextP) && currentBatch.length + imgChars + (1.5f * metrics.charsPerLine).toInt() <= target) {
                                currentBatch.append("\n\n").append(nextP)
                                pIdx += 2
                            } else {
                                pIdx += 1
                            }
                            continue
                        } else {
                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                            val nextP = paragraphs.getOrNull(pIdx + 1)
                            if (nextP != null && isFigureCaption(nextP)) {
                                list.add(Pair(chap.title, "$p\n\n$nextP"))
                                pIdx += 2
                            } else {
                                list.add(Pair(chap.title, p))
                                pIdx += 1
                            }
                            continue
                        }
                    }

                    if (p.startsWith("[TABLE:") && p.endsWith("]")) {
                        val tableChars = (estimateTableLines(p) * metrics.charsPerLine).toInt()
                        val target = if (isFirstPageOfChapter) chapFirstPageTarget else targetCharsPerPage
                        if (currentBatch.length + tableChars <= target) {
                            if (currentBatch.isNotEmpty()) currentBatch.append("\n\n")
                            currentBatch.append(p)
                            pIdx += 1
                            continue
                        } else {
                            val batchStr = currentBatch.toString().trimEnd()
                            val lastSep = batchStr.lastIndexOf("\n\n")
                            if (lastSep >= 0) {
                                val candidateHeading = batchStr.substring(lastSep + 2).trim()
                                if (isHeadingCandidate(candidateHeading)) {
                                    val textBefore = batchStr.substring(0, lastSep).trimEnd()
                                    val prevSep = textBefore.lastIndexOf("\n\n")
                                    val prevPara = (if (prevSep >= 0) textBefore.substring(prevSep + 2) else textBefore).trim()
                                    if (isDivider(prevPara) && prevSep >= 0) {
                                        currentBatch.setLength(prevSep)
                                        flushBatch(isChapterHeader = isFirstPageOfChapter)
                                        isFirstPageOfChapter = false
                                        currentBatch.append(prevPara).append("\n\n").append(candidateHeading).append("\n\n").append(p)
                                        pIdx += 1
                                        continue
                                    } else {
                                        currentBatch.setLength(lastSep)
                                        flushBatch(isChapterHeader = isFirstPageOfChapter)
                                        isFirstPageOfChapter = false
                                        currentBatch.append(candidateHeading).append("\n\n").append(p)
                                        pIdx += 1
                                        continue
                                    }
                                }
                            }

                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                            if (tableChars <= targetCharsPerPage) {
                                currentBatch.append(p)
                                pIdx += 1
                                continue
                            } else {
                                list.add(Pair(chap.title, p))
                                pIdx += 1
                                continue
                            }
                        }
                    }

                    val effectiveP = if (p.startsWith("[QUOTE:") && p.endsWith("]")) {
                        val q = p.removePrefix("[QUOTE:").removeSuffix("]").trim()
                        "“$q”"
                    } else if (p.startsWith("[VERSE:") && p.endsWith("]")) {
                        p.removePrefix("[VERSE:").removeSuffix("]").trim()
                    } else if (p == "[DIVIDER]") {
                        "[DIVIDER]"
                    } else {
                        p
                    }
                    pIdx += 1
                    val target = if (isFirstPageOfChapter) chapFirstPageTarget else targetCharsPerPage

                    if (currentBatch.isEmpty()) {
                        currentBatch.append(effectiveP)
                        if (currentBatch.length >= target) {
                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                        }
                    } else if (currentBatch.length < target) {
                        // Keep appending until page reaches or exceeds target budget so bottom is never empty
                        currentBatch.append("\n\n").append(effectiveP)
                        if (currentBatch.length >= target) {
                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                        }
                    } else {
                        flushBatch(isChapterHeader = isFirstPageOfChapter)
                        isFirstPageOfChapter = false
                        currentBatch.append(effectiveP)
                        if (currentBatch.length >= target) {
                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                        }
                    }
                }

                flushBatch(isChapterHeader = isFirstPageOfChapter)
            }
        }

        return list
    }

    /**
     * Splits text into two parts (partA, partB) such that partA fills the available line budget
     * down to the final line window [maxChars - charsPerLine, maxChars], preventing both
     * overflow and premature blank space at the bottom of the page.
     *
     * Hierarchy within final line window:
     * 1. Sentence boundary (. ! ?)
     * 2. Clause boundary (; : — ,)
     * 3. Word boundary (space)
     * 4. Hard cut fallback
     */
    fun breakText(text: String, maxChars: Int, charsPerLine: Int = 40): Pair<String, String> {
        if (text.length <= maxChars || maxChars <= 0) {
            return Pair(text, "")
        }

        val len = text.length
        val candidateLimit = maxChars.coerceAtMost(len)
        val finalLineStart = (candidateLimit - charsPerLine).coerceAtLeast(0)

        // 1. Sentence boundary on the final line
        var bestSentenceIdx = -1
        var i = finalLineStart
        while (i < candidateLimit) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                var next = i + 1
                while (next < len && (text[next] == '"' || text[next] == '”' || text[next] == '\'' || text[next] == '’' || text[next] == ')' || text[next] == ']')) {
                    next++
                }
                if (next < len && text[next].isWhitespace()) {
                    if (next <= candidateLimit) {
                        bestSentenceIdx = next
                    }
                }
            }
            i++
        }

        // Helper to check if a split index falls inside an unclosed markdown/image token like [INLINE_IMG:...] or [sub]...[/sub]
        val hasBrackets = text.contains('[')
        fun isInsideToken(index: Int): Boolean {
            if (!hasBrackets) return false
            val openBracket = text.lastIndexOf('[', index)
            if (openBracket != -1) {
                val closeBracket = text.indexOf(']', openBracket)
                if (closeBracket != -1 && index in openBracket..closeBracket) {
                    return true
                }
            }
            return false
        }

        if (bestSentenceIdx >= finalLineStart && !isInsideToken(bestSentenceIdx)) {
            val partA = text.substring(0, bestSentenceIdx).trimEnd()
            val partB = text.substring(bestSentenceIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 2. Clause boundary (; : — or comma) on the final line
        var bestClauseIdx = -1
        i = finalLineStart
        while (i < candidateLimit) {
            val c = text[i]
            if (c == ';' || c == ':' || c == '—' || c == ',') {
                var next = i + 1
                if (next < len && text[next].isWhitespace()) {
                    if (next <= candidateLimit) {
                        bestClauseIdx = next
                    }
                }
            }
            i++
        }

        if (bestClauseIdx >= finalLineStart && !isInsideToken(bestClauseIdx)) {
            val partA = text.substring(0, bestClauseIdx).trimEnd()
            val partB = text.substring(bestClauseIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 3. Word boundary: last space before candidateLimit (fills final line up to last word)
        var lastSpace = text.lastIndexOf(' ', candidateLimit)
        while (lastSpace > 0 && isInsideToken(lastSpace)) {
            lastSpace = text.lastIndexOf(' ', lastSpace - 1)
        }
        if (lastSpace > 0) {
            val partA = text.substring(0, lastSpace).trimEnd()
            val partB = text.substring(lastSpace).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 4. Hard cut fallback
        val cut = candidateLimit.coerceIn(1, len - 1)
        var safeCut = cut
        val openBracket = text.lastIndexOf('[', safeCut)
        val closeBracket = if (openBracket != -1) text.indexOf(']', openBracket) else -1
        if (openBracket != -1 && closeBracket != -1 && safeCut in openBracket..closeBracket) {
            if (openBracket > 0) safeCut = openBracket
        }
        val partA = text.substring(0, safeCut)
        val partB = text.substring(safeCut).trimStart()
        return Pair(partA, if (partB.isNotEmpty()) partB else text.substring(safeCut))
    }

    /**
     * Splits text recursively down to sentence, clause, or word level so no piece
     * exceeds maxPieceLength.
     */
    private fun splitIntoPieces(text: String, maxPieceLength: Int): List<String> {
        val sentences = splitIntoSentences(text)
        val pieces = ArrayList<String>(sentences.size)
        for (s in sentences) {
            if (s.length <= maxPieceLength) {
                pieces.add(s)
            } else {
                val clauses = splitByClauses(s, maxPieceLength)
                for (c in clauses) {
                    if (c.length <= maxPieceLength) {
                        pieces.add(c)
                    } else {
                        val words = splitByWords(c, maxPieceLength)
                        pieces.addAll(words)
                    }
                }
            }
        }
        return pieces
    }

    /**
     * Splits a long sentence at punctuation clause boundaries (;, :, —, or commas).
     */
    private fun splitByClauses(sentence: String, maxLen: Int): List<String> {
        if (sentence.length <= maxLen) return listOf(sentence)
        val clauses = ArrayList<String>()
        var start = 0
        var i = 0
        val len = sentence.length
        while (i < len) {
            val c = sentence[i]
            if (c == ';' || c == ':' || c == '—' || (c == ',' && i - start >= 20)) {
                val next = i + 1
                if (next < len && sentence[next].isWhitespace()) {
                    val sub = sentence.substring(start, next).trim()
                    if (sub.isNotEmpty()) clauses.add(sub)
                    start = next + 1
                    while (start < len && sentence[start].isWhitespace()) start++
                    i = start - 1
                }
            }
            i++
        }
        if (start < len) {
            val remaining = sentence.substring(start).trim()
            if (remaining.isNotEmpty()) clauses.add(remaining)
        }
        return if (clauses.isEmpty()) listOf(sentence) else clauses
    }

    /**
     * Fallback splitter by words when a clause still exceeds maxLen.
     */
    private fun splitByWords(clause: String, maxLen: Int): List<String> {
        if (clause.length <= maxLen) return listOf(clause)
        val words = clause.split(' ')
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        for (w in words) {
            if (w.isEmpty()) continue
            if (current.isNotEmpty() && current.length + w.length + 1 > maxLen) {
                chunks.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(" ")
            current.append(w)
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return if (chunks.isEmpty()) listOf(clause) else chunks
    }

    /**
     * Fast, linear O(N) sentence splitter with zero Regex allocations.
     */
    private fun splitIntoSentences(text: String): List<String> {
        if (text.length <= 80) return listOf(text)
        val sentences = ArrayList<String>()
        var start = 0
        var i = 0
        val len = text.length
        while (i < len) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                var nextNonWs = i + 1
                if (nextNonWs < len && (text[nextNonWs] == '"' || text[nextNonWs] == '”' || text[nextNonWs] == '\'')) {
                    nextNonWs++
                }
                if (nextNonWs < len && text[nextNonWs].isWhitespace()) {
                    val s = text.substring(start, nextNonWs).trim()
                    if (s.isNotEmpty()) {
                        sentences.add(s)
                    }
                    while (nextNonWs < len && text[nextNonWs].isWhitespace()) {
                        nextNonWs++
                    }
                    start = nextNonWs
                    i = nextNonWs - 1
                }
            }
            i++
        }
        if (start < len) {
            val remaining = text.substring(start).trim()
            if (remaining.isNotEmpty()) {
                sentences.add(remaining)
            }
        }
        return if (sentences.isEmpty()) listOf(text) else sentences
    }

    fun invalidate(bookId: String, dbHelper: LuminaDatabaseHelper? = null) {
        val snapshot = cache.snapshot()
        for (k in snapshot.keys) {
            if (k.contains(bookId)) {
                cache.remove(k)
            }
        }
        dbHelper?.clearPageCacheForBook(bookId)
    }

    fun clear() {
        cache.evictAll()
    }
}

object AnnotatedTextCache {
    private val cache = SimpleLruCache<String, AnnotatedString>(400)

    fun get(key: String): AnnotatedString? = cache.get(key)

    fun put(key: String, value: AnnotatedString) {
        cache.put(key, value)
    }

    fun getOrCompute(key: String, compute: () -> AnnotatedString): AnnotatedString {
        val cached = cache.get(key)
        if (cached != null) return cached
        val computed = compute()
        cache.put(key, computed)
        return computed
    }

    fun clear() {
        cache.evictAll()
    }
}
