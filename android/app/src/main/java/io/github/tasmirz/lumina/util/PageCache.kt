package io.github.tasmirz.lumina.util

import androidx.compose.ui.text.AnnotatedString
import io.github.tasmirz.lumina.data.db.LuminaDatabaseHelper
import io.github.tasmirz.lumina.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

private data class PageQueueItem(val text: String, val isContinuation: Boolean)

object PageCache {
    // Cache up to 16 book pagination sets in memory
    private val cache = SimpleLruCache<String, List<Pair<String, String>>>(16)

    private fun getPersistentCacheFile(context: android.content.Context?, key: String): java.io.File? {
        return try {
            val dir = io.github.tasmirz.lumina.data.LuminaStorageManager.getPersistentPagesCacheDirectory(context)
            java.io.File(dir, "$key.json")
        } catch (_: Exception) { null }
    }

    // Bumped to v32_ to support customizable pagedSafeLinesToRemove
    private const val CACHE_VERSION = "v32_"

    data class DeviceMetrics(
        val charsPerLine: Int,
        val maxLines: Int,
        val headerLinesCost: Int,
        val baseTargetChars: Int
    )

    fun calculateDeviceMetrics(
        fontSize: Int,
        isLandscape: Boolean = false,
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        horizontalPaddingDp: Int = 20,
        verticalPaddingDp: Int = 16,
        lineHeightMultiplier: Float = 1.45f,
        paragraphSpacingMultiplier: Float = 1.2f,
        safeLinesToRemove: Int = 0
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
            48 + (verticalPaddingDp * 0.5f).toInt()
        } else {
            154 + (verticalPaddingDp * 0.7f).toInt()
        }

        // 2-3% bottom safety buffer to guarantee the last line never touches the dock or gets cut off
        val bottomSafetyBufferDp = (screenH * 0.025f).coerceIn(12f, 28f)
        val usableW = (screenW - horizontalMargin).coerceAtLeast(180)
        val usableH = (screenH - verticalMargin - bottomSafetyBufferDp).coerceAtLeast(180f)

        // Dynamic line height in dp: font size * effective line height multiplier + glyph descent/ascent
        val effectiveMultiplier = lineHeightMultiplier.coerceIn(1.1f, 2.2f)
        val lineHeightDp = (fontSize * effectiveMultiplier * 1.08f).coerceAtLeast(14f)

        // Dynamic average character width in dp for serif/sans-serif book typography
        val avgCharWidthDp = (fontSize * 0.44f).coerceAtLeast(4.5f)

        val charsPerLine = (usableW / avgCharWidthDp).toInt().coerceIn(18, 180)
        val rawMaxLines = (usableH / lineHeightDp).toInt().coerceIn(4, 80)
        val maxLines = (rawMaxLines - safeLinesToRemove.coerceIn(0, 10)).coerceAtLeast(2)

        val headerHeightDp = if (isLandscape) 64f else 96f
        val headerLinesCost = kotlin.math.ceil(headerHeightDp / lineHeightDp).toInt().coerceIn(2, 6)

        // Base target characters for non-strict scroll engine (~90% density)
        val baseTargetChars = (charsPerLine * maxLines * 0.90f).toInt().coerceAtLeast(200)

        return DeviceMetrics(
            charsPerLine = charsPerLine,
            maxLines = maxLines,
            headerLinesCost = headerLinesCost,
            baseTargetChars = baseTargetChars
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
        safeLinesToRemove: Int = 0
    ): String {
        val orientation = if (isLandscape) "land" else "port"
        val mode = if (isStrictPaged) "strict" else "scroll"
        val dims = if (screenWidthDp > 0 && screenHeightDp > 0) "_${screenWidthDp}x${screenHeightDp}_p${horizontalPaddingDp}_${verticalPaddingDp}_ps${(paragraphSpacingMultiplier * 10).toInt()}_sl${safeLinesToRemove}" else ""
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
        safeLinesToRemove: Int = 0
    ): List<Pair<String, String>>? {
        val key = getCacheKey(bookId, chaptersCount, fontSize, isLandscape, isStrictPaged, screenWidthDp, screenHeightDp, horizontalPaddingDp, verticalPaddingDp, paragraphSpacingMultiplier, safeLinesToRemove)
        return cache.get(key)
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
        dbHelper: LuminaDatabaseHelper? = null,
        context: android.content.Context? = null
    ): List<Pair<String, String>> {
        val key = getCacheKey(bookId, chapters.size, fontSize, isLandscape, isStrictPaged, screenWidthDp, screenHeightDp, horizontalPaddingDp, verticalPaddingDp, paragraphSpacingMultiplier, safeLinesToRemove)
        val cached = cache.get(key)
        if (cached != null) return cached

        if (dbHelper != null) {
            val fromDb = dbHelper.getPageCache(key)
            if (fromDb != null && fromDb.isNotEmpty()) {
                cache.put(key, fromDb)
                return fromDb
            }
        }

        val pFile = getPersistentCacheFile(context, key)
        if (pFile != null && pFile.exists() && pFile.length() > 0L && dbHelper != null) {
            try {
                val json = pFile.readText()
                val fromFile = dbHelper.deserializePages(json)
                if (fromFile.isNotEmpty()) {
                    cache.put(key, fromFile)
                    dbHelper.savePageCache(key, bookId, -1, fromFile)
                    return fromFile
                }
            } catch (_: Exception) {}
        }

        val list = computePages(
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
            safeLinesToRemove = safeLinesToRemove
        )
        cache.put(key, list)
        if (dbHelper != null && list.isNotEmpty()) {
            dbHelper.savePageCache(key, bookId, -1, list)
            if (pFile != null) {
                try { pFile.writeText(dbHelper.serializePages(list)) } catch (_: Exception) {}
            }
        }
        return list
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
        safeLinesToRemove: Int = 0
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
            safeLinesToRemove = safeLinesToRemove
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
        dbHelper: LuminaDatabaseHelper? = null,
        context: android.content.Context? = null,
        activeChapterIndex: Int = 0,
        onActiveChapterReady: ((List<Pair<String, String>>) -> Unit)? = null
    ): List<Pair<String, String>> = withContext(Dispatchers.Default) {
        val key = getCacheKey(bookId, chapters.size, fontSize, isLandscape, isStrictPaged, screenWidthDp, screenHeightDp, horizontalPaddingDp, verticalPaddingDp, paragraphSpacingMultiplier, safeLinesToRemove)
        val cached = cache.get(key)
        if (cached != null) {
            onActiveChapterReady?.invoke(cached)
            return@withContext cached
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
        if (pFile != null && pFile.exists() && pFile.length() > 0L && dbHelper != null) {
            try {
                val json = pFile.readText()
                val fromFile = dbHelper.deserializePages(json)
                if (fromFile.isNotEmpty()) {
                    cache.put(key, fromFile)
                    dbHelper.savePageCache(key, bookId, -1, fromFile)
                    onActiveChapterReady?.invoke(fromFile)
                    return@withContext fromFile
                }
            } catch (_: Exception) {}
        }

        // Fast path: compute active chapter first for immediate UI rendering
        if (onActiveChapterReady != null && activeChapterIndex in chapters.indices) {
            val activeChapter = chapters[activeChapterIndex]
            val activePages = computeChapterPages(
                chapter = activeChapter,
                fontSize = fontSize,
                isLandscape = isLandscape,
                isStrictPaged = isStrictPaged,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                horizontalPaddingDp = horizontalPaddingDp,
                verticalPaddingDp = verticalPaddingDp,
                lineHeightMultiplier = lineHeightMultiplier,
                paragraphSpacingMultiplier = paragraphSpacingMultiplier,
                safeLinesToRemove = safeLinesToRemove
            )
            if (activePages.isNotEmpty()) {
                onActiveChapterReady(activePages)
            }
        }

        val list = computePages(
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
            safeLinesToRemove = safeLinesToRemove
        )
        cache.put(key, list)
        if (dbHelper != null && list.isNotEmpty()) {
            dbHelper.savePageCache(key, bookId, -1, list)
            if (pFile != null) {
                try { pFile.writeText(dbHelper.serializePages(list)) } catch (_: Exception) {}
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
        safeLinesToRemove: Int = 0
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
            safeLinesToRemove = safeLinesToRemove
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
                        flushBatch()
                        list.add(Pair(chap.title, p))
                        continue
                    }

                    val pageLineBudget = if (isFirstPageOfChapter) {
                        (maxLines - headerLinesCost).coerceAtLeast(if (isLandscape) 2 else 4).toFloat()
                    } else {
                        maxLines.toFloat()
                    }

                    val availableLines = pageLineBudget - currentLines

                    // A new paragraph consumes blank line space before it (\n\n) + paragraph spacing multiplier
                    // If currentBatch is empty or this item is continuing a broken paragraph, no blank line is consumed.
                    val blankLinesBefore = if (currentBatch.isEmpty() || item.isContinuation) 0f else paragraphGapLines
                    val textLines = kotlin.math.ceil(p.length.toFloat() / effectiveCharsPerLine).coerceAtLeast(1f)
                    val totalLinesNeeded = blankLinesBefore + textLines

                    if (totalLinesNeeded <= availableLines) {
                        // Entire item fits on the current page!
                        if (currentBatch.isNotEmpty()) {
                            currentBatch.append(if (item.isContinuation) " " else "\n\n")
                        }
                        currentBatch.append(p)
                        currentLines += totalLinesNeeded
                    } else {
                        // It does not fit entirely.
                        val linesForThisText = availableLines - blankLinesBefore

                        if (linesForThisText >= 1f) {
                            // Break text to fill remaining lines on current page
                            val maxCharsToFit = (linesForThisText * effectiveCharsPerLine).toInt()
                            val (partA, partB) = breakText(p, maxCharsToFit, effectiveCharsPerLine.toInt())

                            if (partA.isNotEmpty() && partB.isNotEmpty()) {
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

                paragraphs.forEach { p ->
                    if (p.startsWith("[IMG:") && p.endsWith("]")) {
                        flushBatch(isChapterHeader = isFirstPageOfChapter)
                        isFirstPageOfChapter = false
                        list.add(Pair(chap.title, p))
                        return@forEach
                    }

                    val target = if (isFirstPageOfChapter) firstPageTarget else targetCharsPerPage

                    if (currentBatch.isEmpty()) {
                        currentBatch.append(p)
                        if (currentBatch.length >= target) {
                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                        }
                    } else if (currentBatch.length < target) {
                        // Keep appending until page reaches or exceeds target budget so bottom is never empty
                        currentBatch.append("\n\n").append(p)
                        if (currentBatch.length >= target) {
                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                        }
                    } else {
                        flushBatch(isChapterHeader = isFirstPageOfChapter)
                        isFirstPageOfChapter = false
                        currentBatch.append(p)
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

        if (bestSentenceIdx >= finalLineStart) {
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

        if (bestClauseIdx >= finalLineStart) {
            val partA = text.substring(0, bestClauseIdx).trimEnd()
            val partB = text.substring(bestClauseIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 3. Word boundary: last space before candidateLimit (fills final line up to last word)
        val lastSpace = text.lastIndexOf(' ', candidateLimit)
        if (lastSpace > 0) {
            val partA = text.substring(0, lastSpace).trimEnd()
            val partB = text.substring(lastSpace).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 4. Hard cut fallback
        val cut = candidateLimit.coerceIn(1, len - 1)
        val partA = text.substring(0, cut)
        val partB = text.substring(cut).trimStart()
        return Pair(partA, if (partB.isNotEmpty()) partB else text.substring(cut))
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
