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
        val key = "v11_${bookId}_${chapters.size}_${fontSize}_${if (isLandscape) "land" else "port"}_${if (isStrictPaged) "strict" else "scroll"}"
        val cached = cache.get(key)
        if (cached != null) return cached

        val list = mutableListOf<Pair<String, String>>()

        if (isStrictPaged) {
            // ═════════════════════════════════════════════════════════════════
            // STRICT PAGED ENGINE (Zero overflow, no scroll needed, fully fits)
            // ═════════════════════════════════════════════════════════════════
            val baseTarget = when {
                fontSize <= 13 -> 950
                fontSize <= 14 -> 860
                fontSize <= 15 -> 780
                fontSize <= 16 -> 710
                fontSize <= 17 -> 640
                fontSize <= 18 -> 580
                fontSize <= 19 -> 530
                fontSize <= 20 -> 480
                fontSize <= 21 -> 440
                fontSize <= 23 -> 380
                fontSize <= 25 -> 330
                else -> 280
            }
            val targetCharsPerPage = if (isLandscape) {
                (baseTarget * 0.85f).toInt().coerceAtLeast(300)
            } else baseTarget

            val firstPageTarget = if (isLandscape) {
                (targetCharsPerPage * 0.75f).toInt().coerceAtLeast(200)
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
                val currentBatch = StringBuilder(targetCharsPerPage + 100)

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

                    val maxTarget = if (isFirstPageOfChapter) firstPageTarget else targetCharsPerPage
                    val pieces = splitIntoPieces(p, maxTarget)
                    var pieceIdx = 0
                    while (pieceIdx < pieces.size) {
                        val piece = pieces[pieceIdx]
                        val isFirstPieceOfPara = pieceIdx == 0
                        val target = if (isFirstPageOfChapter) firstPageTarget else targetCharsPerPage
                        val separator = if (currentBatch.isEmpty()) "" else if (isFirstPieceOfPara) "\n\n" else " "
                        val overhead = if (separator == "\n\n") 45 else 0

                        if (currentBatch.isNotEmpty() && currentBatch.length + piece.length + separator.length + overhead > target) {
                            flushBatch(isChapterHeader = isFirstPageOfChapter)
                            isFirstPageOfChapter = false
                        }

                        if (currentBatch.isNotEmpty()) {
                            currentBatch.append(if (isFirstPieceOfPara) "\n\n" else " ")
                        }
                        currentBatch.append(piece)
                        pieceIdx++
                    }
                }

                flushBatch(isChapterHeader = isFirstPageOfChapter)
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
