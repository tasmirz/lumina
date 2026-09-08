package io.github.tasmirz.lumina.util

import androidx.compose.ui.text.AnnotatedString
import io.github.tasmirz.lumina.model.Chapter

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

    fun getOrCompute(
        bookId: String,
        chapters: List<Chapter>,
        fontSize: Int,
        isLandscape: Boolean = false,
        isStrictPaged: Boolean = false
    ): List<Pair<String, String>> {
        val key = "v12_${bookId}_${chapters.size}_${fontSize}_${if (isLandscape) "land" else "port"}_${if (isStrictPaged) "strict" else "scroll"}"
        val cached = cache.get(key)
        if (cached != null) return cached

        val list = mutableListOf<Pair<String, String>>()

        if (isStrictPaged) {
            // ═════════════════════════════════════════════════════════════════
            // STRICT PAGED ENGINE (Zero overflow, line-budgeted, seamless continuation)
            // ═════════════════════════════════════════════════════════════════
            val (charsPerLine, maxLines) = if (isLandscape) {
                when {
                    fontSize <= 13 -> Pair(78, 12)
                    fontSize <= 14 -> Pair(72, 11)
                    fontSize <= 15 -> Pair(68, 10)
                    fontSize <= 16 -> Pair(64, 10)
                    fontSize <= 17 -> Pair(60, 9)
                    fontSize <= 18 -> Pair(56, 9)
                    fontSize <= 20 -> Pair(50, 8)
                    fontSize <= 22 -> Pair(44, 8)
                    fontSize <= 24 -> Pair(38, 7)
                    else -> Pair(34, 7)
                }
            } else {
                when {
                    fontSize <= 13 -> Pair(46, 25)
                    fontSize <= 14 -> Pair(43, 23)
                    fontSize <= 15 -> Pair(41, 21)
                    fontSize <= 16 -> Pair(39, 20)
                    fontSize <= 17 -> Pair(37, 19)
                    fontSize <= 18 -> Pair(36, 18)
                    fontSize <= 19 -> Pair(34, 17)
                    fontSize <= 20 -> Pair(32, 17)
                    fontSize <= 21 -> Pair(30, 16)
                    fontSize <= 23 -> Pair(28, 15)
                    fontSize <= 25 -> Pair(25, 14)
                    else -> Pair(22, 13)
                }
            }

            val headerLinesCost = if (isLandscape) 3 else when {
                fontSize <= 14 -> 6
                fontSize <= 18 -> 5
                else -> 4
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
                    val minFill = (target * 0.72f).toInt()

                    if (currentBatch.isEmpty()) {
                        currentBatch.append(p)
                    } else if (currentBatch.length + p.length + 2 <= target) {
                        currentBatch.append("\n\n").append(p)
                    } else if (currentBatch.length < minFill && p.length <= (target - currentBatch.length + 120)) {
                        // Underfilled page and p fits with small overflow: keep on this page and scroll
                        currentBatch.append("\n\n").append(p)
                        flushBatch(isChapterHeader = isFirstPageOfChapter)
                        isFirstPageOfChapter = false
                    } else {
                        flushBatch(isChapterHeader = isFirstPageOfChapter)
                        isFirstPageOfChapter = false
                        currentBatch.append(p)
                    }
                }

                flushBatch(isChapterHeader = isFirstPageOfChapter)
            }
        }

        cache.put(key, list)
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

        // 1. Find all sentence end candidates <= maxChars
        var lastSentenceIdx = -1
        var i = 0
        while (i < candidateLimit) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?') {
                var next = i + 1
                if (next < len && (text[next] == '"' || text[next] == '”' || text[next] == '\'' || text[next] == '’')) {
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

        // If we found a sentence ending that fills at least 45% of maxChars, use it!
        if (lastSentenceIdx >= (maxChars * 0.45f).toInt()) {
            val partA = text.substring(0, lastSentenceIdx).trimEnd()
            val partB = text.substring(lastSentenceIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 2. Find clause boundary (; : — or comma) <= maxChars
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

        // If clause fills at least 55% of maxChars, use it
        if (lastClauseIdx >= (maxChars * 0.55f).toInt()) {
            val partA = text.substring(0, lastClauseIdx).trimEnd()
            val partB = text.substring(lastClauseIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // If we had a sentence boundary earlier (even if < 45%), prefer it over chopping words if > 25 chars
        if (lastSentenceIdx >= 25) {
            val partA = text.substring(0, lastSentenceIdx).trimEnd()
            val partB = text.substring(lastSentenceIdx).trimStart()
            if (partA.isNotEmpty() && partB.isNotEmpty()) {
                return Pair(partA, partB)
            }
        }

        // 3. Word boundary: last space before candidateLimit
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

    fun invalidate(bookId: String) {
        val snapshot = cache.snapshot()
        for (k in snapshot.keys) {
            if (k.contains(bookId)) {
                cache.remove(k)
            }
        }
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
