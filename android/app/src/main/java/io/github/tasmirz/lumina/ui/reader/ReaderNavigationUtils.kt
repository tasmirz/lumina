package io.github.tasmirz.lumina.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import io.github.tasmirz.lumina.model.Chapter

internal fun cleanAlpha(s: String): String = s.lowercase()
    .replace(Regex("[^a-z0-9\\s]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

internal fun findPageForLocation(
    pages: List<Pair<String, String>>,
    chapterIdx: Int,
    chapters: List<Chapter>,
    paraIdx: Int,
    topSnippet: String
): Int {
    if (pages.isEmpty()) return 0
    val targetChapter = chapters.getOrNull(chapterIdx)
    val chapterTitle = targetChapter?.title ?: ""

    fun clean(s: String) = s.replace("“", "\"")
        .replace("”", "\"")
        .replace("‘", "'")
        .replace("’", "'")
        .replace("—", "-")
        .replace("–", "-")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

    val cleanedSnippet = clean(topSnippet.trim())
    val alphaSnippet = cleanAlpha(topSnippet.trim())

    val chapterStarts = ArrayList<Int>()
    pages.forEachIndexed { idx, (_, content) ->
        if (content.startsWith("CHAPTER_START:::")) {
            chapterStarts.add(idx)
        }
    }
    val firstPageOfChap = chapterStarts.getOrNull(chapterIdx)
        ?: pages.indexOfFirst { it.first.equals(chapterTitle, ignoreCase = true) }
    val lastPageOfChap = if (chapterStarts.isNotEmpty() && chapterIdx in chapterStarts.indices) {
        val nextStart = chapterStarts.getOrNull(chapterIdx + 1) ?: pages.size
        (nextStart - 1).coerceAtLeast(firstPageOfChap)
    } else {
        pages.indexOfLast { it.first.equals(chapterTitle, ignoreCase = true) }
    }

    // 1. Try finding page by snippet within the target chapter range
    if (cleanedSnippet.isNotBlank() && firstPageOfChap in pages.indices && lastPageOfChap in pages.indices && firstPageOfChap <= lastPageOfChap) {
        for (len in listOf(40, 30, 20, 14)) {
            val snip = cleanedSnippet.take(len).trim()
            if (snip.length >= 12) {
                for (p in firstPageOfChap..lastPageOfChap) {
                    if (clean(pages[p].second).contains(snip)) return p
                }
            }
        }
        for (len in listOf(40, 30, 20, 14)) {
            val snip = alphaSnippet.take(len).trim()
            if (snip.length >= 12) {
                for (p in firstPageOfChap..lastPageOfChap) {
                    if (cleanAlpha(pages[p].second).contains(snip)) return p
                }
            }
        }
    }

    // Fallback: Global search across all pages by snippet (in case chapter title naming slightly differs)
    if (cleanedSnippet.isNotBlank()) {
        for (len in listOf(30, 20, 14)) {
            val snip = cleanedSnippet.take(len).trim()
            if (snip.length >= 12) {
                val foundGlobal = pages.indexOfFirst { clean(it.second).contains(snip) }
                if (foundGlobal >= 0) return foundGlobal
            }
        }
        for (len in listOf(30, 20, 14)) {
            val snip = alphaSnippet.take(len).trim()
            if (snip.length >= 12) {
                val foundGlobal = pages.indexOfFirst { cleanAlpha(it.second).contains(snip) }
                if (foundGlobal >= 0) return foundGlobal
            }
        }
    }

    // 2. Try finding page by paragraph text within target chapter range
    val rawPara = targetChapter?.paragraphs?.getOrNull(paraIdx)?.trim() ?: ""
    val cleanedPara = clean(rawPara)
    val alphaPara = cleanAlpha(rawPara)
    if (cleanedPara.isNotBlank() && firstPageOfChap in pages.indices && lastPageOfChap in pages.indices && firstPageOfChap <= lastPageOfChap) {
        for (len in listOf(35, 24, 16)) {
            val paraSnip = cleanedPara.take(len).trim()
            if (paraSnip.length >= 12) {
                for (p in firstPageOfChap..lastPageOfChap) {
                    if (clean(pages[p].second).contains(paraSnip)) return p
                }
            }
        }
        for (len in listOf(35, 24, 16)) {
            val paraSnip = alphaPara.take(len).trim()
            if (paraSnip.length >= 12) {
                for (p in firstPageOfChap..lastPageOfChap) {
                    if (cleanAlpha(pages[p].second).contains(paraSnip)) return p
                }
            }
        }
    }

    // 3. If it's the very start of chapter, return first page of chapter
    if (paraIdx == 0 && firstPageOfChap >= 0) {
        return firstPageOfChap
    }

    // 4. Estimate page within the chapter proportionally
    if (firstPageOfChap >= 0 && lastPageOfChap >= firstPageOfChap) {
        val chapPageCount = lastPageOfChap - firstPageOfChap + 1
        val totalParasInChap = maxOf(targetChapter?.paragraphs?.size ?: 1, 1)
        val estimatedOffset = ((paraIdx.toFloat() / totalParasInChap) * chapPageCount).toInt()
        return (firstPageOfChap + estimatedOffset).coerceIn(firstPageOfChap, lastPageOfChap)
    }

    return 0
}

internal fun findScrollIndexForLocation(
    chapterIdx: Int,
    paraIdx: Int,
    topSnippet: String,
    chapters: List<Chapter>,
    chapterOffsets: IntArray
): Int {
    if (chapters.isEmpty()) return 0
    val safeChapIdx = chapterIdx.coerceIn(0, chapters.size - 1)
    val chapter = chapters[safeChapIdx]
    val chapStartOffset = chapterOffsets.getOrElse(safeChapIdx) { 0 }

    if (chapter.paragraphs.isEmpty()) {
        return chapStartOffset
    }

    var safeParaIdx = paraIdx.coerceIn(0, chapter.paragraphs.size - 1)
    if (topSnippet.isNotBlank()) {
        val snippet = topSnippet.take(30).trim()
        val cleanSnip = cleanAlpha(snippet)
        val foundIdx = chapter.paragraphs.indexOfFirst { p ->
            val cp = cleanAlpha(p)
            cp.contains(cleanSnip.take(20)) || (cleanSnip.length >= 10 && cp.contains(cleanSnip.take(10)))
        }
        if (foundIdx >= 0) {
            safeParaIdx = foundIdx
        }
    }

    return chapStartOffset + 1 + safeParaIdx
}

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
