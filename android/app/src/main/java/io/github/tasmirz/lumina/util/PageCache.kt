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

    fun getCached(
        bookId: String,
        chaptersCount: Int,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false
    ): List<Pair<String, String>>? {
        val key = "v16_${bookId}_${chaptersCount}_${fontSize}_${if (isLandscape) "land" else "port"}_${if (isStrictPaged) "strict" else "scroll"}"
        return cache.get(key)
    }

    fun getOrCompute(
        bookId: String,
        chapters: List<Chapter>,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false,
        dbHelper: LuminaDatabaseHelper? = null,
        context: android.content.Context? = null
    ): List<Pair<String, String>> {
        val key = "v16_${bookId}_${chapters.size}_${fontSize}_${if (isLandscape) "land" else "port"}_${if (isStrictPaged) "strict" else "scroll"}"
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

        val list = computePages(chapters, fontSize, isLandscape, isStrictPaged)
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
        isStrictPaged: Boolean = false
    ): List<Pair<String, String>> {
        return computePages(listOf(chapter), fontSize, isLandscape, isStrictPaged)
    }

    suspend fun getOrComputeAsync(
        bookId: String,
        chapters: List<Chapter>,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false,
        dbHelper: LuminaDatabaseHelper? = null,
        context: android.content.Context? = null,
        activeChapterIndex: Int = 0,
        onActiveChapterReady: ((List<Pair<String, String>>) -> Unit)? = null
    ): List<Pair<String, String>> = withContext(Dispatchers.Default) {
        val key = "v16_${bookId}_${chapters.size}_${fontSize}_${if (isLandscape) "land" else "port"}_${if (isStrictPaged) "strict" else "scroll"}"
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
            val activePages = computeChapterPages(activeChapter, fontSize, isLandscape, isStrictPaged)
            if (activePages.isNotEmpty()) {
                onActiveChapterReady(activePages)
            }
        }

        val list = computePages(chapters, fontSize, isLandscape, isStrictPaged)
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
        isStrictPaged: Boolean = false
    ): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()

        if (isStrictPaged) {
            // ═════════════════════════════════════════════════════════════════
            // STRICT PAGED ENGINE (Zero overflow, line-budgeted, seamless continuation)
            // ═════════════════════════════════════════════════════════════════
            val (charsPerLine, maxLines) = if (isLandscape) {
                when {
                    fontSize <= 13 -> Pair(78, 11)
                    fontSize <= 14 -> Pair(72, 10)
                    fontSize <= 15 -> Pair(68, 9)
                    fontSize <= 16 -> Pair(64, 9)
                    fontSize <= 17 -> Pair(60, 8)
                    fontSize <= 18 -> Pair(56, 8)
                    fontSize <= 20 -> Pair(50, 7)
                    fontSize <= 22 -> Pair(44, 7)
                    fontSize <= 24 -> Pair(38, 6)
                    else -> Pair(34, 6)
                }
            } else {
                when {
                    fontSize <= 13 -> Pair(46, 23)
                    fontSize <= 14 -> Pair(43, 21)
                    fontSize <= 15 -> Pair(41, 20)
                    fontSize <= 16 -> Pair(39, 19)
                    fontSize <= 17 -> Pair(37, 18)
                    fontSize <= 18 -> Pair(36, 17)
                    fontSize <= 19 -> Pair(34, 16)
                    fontSize <= 20 -> Pair(32, 15)
                    fontSize <= 21 -> Pair(30, 14)
                    fontSize <= 23 -> Pair(28, 13)
                    fontSize <= 25 -> Pair(25, 12)
                    else -> Pair(22, 11)
                }
            }

            val headerLinesCost = if (isLandscape) 3 else when {
                fontSize <= 14 -> 8
                fontSize <= 18 -> 7
                else -> 6
            }

            chapters.forEach { chap ->
                val paragraphs = chap.paragraphs
                if (paragraphs.isEmpty() || paragraphs.all { it.isBlank() }) {
                    list.add(Pair(chap.title, "CHAPTER_START:::${chap.title}:::${chap.subtitle}:::"))
                    return@forEach
                }

                var isFirstPageOfChapter = true
                val currentBatch = StringBuilder()
                var currentLines = 0

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
                        currentLines = 0
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
                        (maxLines - headerLinesCost).coerceAtLeast(6)
                    } else {
                        maxLines
                    }

                    val availableLines = pageLineBudget - currentLines

                    // A new paragraph consumes 1 blank line before it (\n\n).
                    // If currentBatch is empty or this item is continuing a broken paragraph, no blank line is consumed.
                    val blankLinesBefore = if (currentBatch.isEmpty() || item.isContinuation) 0 else 1
                    val textLines = kotlin.math.ceil(p.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
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

                        if (linesForThisText >= 2) {
                            // Break text to fill remaining lines on current page
                            val maxCharsToFit = linesForThisText * charsPerLine
                            val (partA, partB) = breakText(p, maxCharsToFit)

                            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                                if (currentBatch.isNotEmpty()) {
                                    currentBatch.append(if (item.isContinuation) " " else "\n\n")
                                }
                                currentBatch.append(partA)
                                currentLines += blankLinesBefore + kotlin.math.ceil(partA.length.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
                                flushBatch()
                                queue.addFirst(PageQueueItem(partB, isContinuation = true))
                                continue
                            }
                        }

                        // If linesForThisText < 2 or breakText couldn't split:
                        if (currentBatch.isNotEmpty()) {
                            // Flush current page and start this item fresh on next page
                            flushBatch()
                            queue.addFirst(item)
                        } else {
                            // currentBatch is empty, but item exceeds pageLineBudget (giant text).
                            // Force break to fit pageLineBudget!
                            val maxCharsToFit = pageLineBudget * charsPerLine
                            val (partA, partB) = breakText(p, maxCharsToFit)
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
            val baseTarget = when {
                fontSize <= 13 -> 1020
                fontSize <= 14 -> 930
                fontSize <= 15 -> 840
                fontSize <= 16 -> 760
                fontSize <= 17 -> 690
                fontSize <= 18 -> 630
                fontSize <= 19 -> 570
                fontSize <= 21 -> 500
                fontSize <= 23 -> 420
                fontSize <= 25 -> 360
                else -> 300
            }
            val targetCharsPerPage = if (isLandscape) {
                (baseTarget * 0.85f).toInt().coerceAtLeast(350)
            } else baseTarget

            val firstPageTarget = if (isLandscape) {
                (targetCharsPerPage * 0.75f).toInt().coerceAtLeast(220)
            } else {
                (targetCharsPerPage - 70).coerceAtLeast((targetCharsPerPage * 0.88f).toInt())
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
     * Splits text into two parts (partA, partB) such that partA fits within maxChars.
     * Hierarchy:
     * 1. Sentence boundary (. ! ?)
     * 2. Clause boundary (; : — ,)
     * 3. Word boundary (space)
     * 4. Hard cut fallback
     */
    private fun breakText(text: String, maxChars: Int): Pair<String, String> {
        if (text.length <= maxChars || maxChars <= 0) {
            return Pair(text, "")
        }

        val len = text.length
        val candidateLimit = maxChars.coerceAtMost(len)

        // 1. Find all sentence end candidates <= candidateLimit
        var lastSentenceIdx = -1
        var i = 0
        while (i < candidateLimit) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                var next = i + 1
                while (next < len && (text[next] == '"' || text[next] == '”' || text[next] == '\'' || text[next] == '’' || text[next] == ')' || text[next] == ']')) {
                    next++
                }
                if (next < len && text[next].isWhitespace()) {
                    if (next <= candidateLimit) {
                        lastSentenceIdx = next
                    }
                }
            }
            i++
        }

        // Sentence boundary must fill at least 72% of candidateLimit to avoid premature page cutoffs
        if (lastSentenceIdx >= (candidateLimit * 0.72f).toInt()) {
            val partA = text.substring(0, lastSentenceIdx).trimEnd()
            val partB = text.substring(lastSentenceIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 2. Find clause boundary (; : — or comma) <= candidateLimit
        var lastClauseIdx = -1
        i = 0
        while (i < candidateLimit) {
            val c = text[i]
            if (c == ';' || c == ':' || c == '—' || c == ',') {
                var next = i + 1
                if (next < len && text[next].isWhitespace()) {
                    if (next <= candidateLimit) {
                        lastClauseIdx = next
                    }
                }
            }
            i++
        }

        // Clause boundary must fill at least 78% of candidateLimit
        if (lastClauseIdx >= (candidateLimit * 0.78f).toInt()) {
            val partA = text.substring(0, lastClauseIdx).trimEnd()
            val partB = text.substring(lastClauseIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 3. Word boundary: last space before candidateLimit (fills > 50% to prevent blank page)
        val lastSpace = text.lastIndexOf(' ', candidateLimit)
        if (lastSpace > (candidateLimit * 0.50f).toInt()) {
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
