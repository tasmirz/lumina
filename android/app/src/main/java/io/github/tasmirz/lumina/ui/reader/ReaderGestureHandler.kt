package io.github.tasmirz.lumina.ui.reader

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import io.github.tasmirz.lumina.model.Chapter
import io.github.tasmirz.lumina.model.ReadingMode

/**
 * Two-finger pinch-to-zoom gesture detector for reader text scaling.
 * Anchors the visible reading location across font size changes.
 */
fun Modifier.readerPinchZoom(
    view: View,
    currentFontSize: Int,
    currentReadingMode: ReadingMode,
    currentIsPagedReading: Boolean,
    currentPages: List<Pair<String, String>>,
    currentChapters: List<Chapter>,
    chapterCumulativeParaOffsets: IntArray,
    pagerState: PagerState,
    listState: LazyListState,
    currentOnFontSizeChange: (Int) -> Unit,
    onAnchorUpdated: (snippet: String, chapterIdx: Int, paraIdx: Int) -> Unit,
    onPinchStateChanged: (isPinching: Boolean, requestSync: Boolean) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var cumulativeZoom = 1.0f
        var pinchStarted = false
        var currentTargetFontSize = currentFontSize

        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.any { it.isConsumed }) {
                    // Touch was consumed by a child element (e.g. image pinch or button)
                    continue
                }
                var pressedCount = 0
                var p0: PointerInputChange? = null
                var p1: PointerInputChange? = null

                for (i in event.changes.indices) {
                    val c = event.changes[i]
                    if (c.pressed) {
                        pressedCount++
                        if (p0 == null) {
                            p0 = c
                        } else if (p1 == null) {
                            p1 = c
                        }
                    }
                }

                if (pressedCount >= 2 && p0 != null && p1 != null) {
                    val currentDist = kotlin.math.hypot(p0.position.x - p1.position.x, p0.position.y - p1.position.y)
                    val prevDist = kotlin.math.hypot(p0.previousPosition.x - p1.previousPosition.x, p0.previousPosition.y - p1.previousPosition.y)
                    val avgTouchY = (p0.position.y + p1.position.y) / 2f

                    if (!pinchStarted) {
                        pinchStarted = true
                        onPinchStateChanged(true, false)
                        currentTargetFontSize = currentFontSize

                        if (currentIsPagedReading) {
                            val activePages = currentPages
                            val curPage = pagerState.currentPage
                            if (curPage in activePages.indices) {
                                val (cTitle, pageContent) = activePages[curPage]
                                val isChapterHeaderPage = pageContent.startsWith("CHAPTER_START:::") || pageContent.startsWith("TITLE:::")
                                val pageBody = if (isChapterHeaderPage) pageContent.substringAfterLast(":::") else pageContent
                                val cIdx = currentChapters.indexOfFirst { it.title == cTitle }.coerceAtLeast(0)
                                val chap = currentChapters.getOrNull(cIdx)

                                var targetSnippet = ""
                                var targetParaIdx = 0

                                val cleanExact = cleanAlpha(pageBody.take(60))
                                val foundIdx = chap?.paragraphs?.indexOfFirst { fullPara ->
                                    val cleanFull = cleanAlpha(fullPara)
                                    cleanFull.contains(cleanExact.take(25)) || cleanExact.contains(cleanFull.take(25))
                                } ?: -1
                                if (foundIdx >= 0) {
                                    targetParaIdx = foundIdx
                                    targetSnippet = chap?.paragraphs?.getOrNull(foundIdx)?.trim()?.take(60) ?: ""
                                }
                                if (targetSnippet.isBlank()) {
                                    targetSnippet = pageBody.trim().take(60)
                                }

                                if (targetSnippet.isNotBlank()) {
                                    onAnchorUpdated(targetSnippet, cIdx, targetParaIdx)
                                }
                            }
                        } else if (currentReadingMode == ReadingMode.SCROLL) {
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val viewportH = listState.layoutInfo.viewportSize.height
                            val targetY = if (viewportH > 0) viewportH * 0.4f else avgTouchY
                            val touchedItem = visibleItems.find { targetY >= it.offset && targetY <= it.offset + it.size }
                                ?: visibleItems.firstOrNull()

                            if (touchedItem != null) {
                                val itemIdx = touchedItem.index
                                val binIdx = chapterCumulativeParaOffsets.binarySearch(itemIdx)
                                val cIdx = if (binIdx >= 0) binIdx else (-binIdx - 2).coerceIn(0, currentChapters.size - 1)
                                val pIdx = (itemIdx - chapterCumulativeParaOffsets.getOrElse(cIdx) { 0 } - 1).coerceAtLeast(0)
                                val paraText = currentChapters.getOrNull(cIdx)?.paragraphs?.getOrNull(pIdx) ?: ""
                                val snip = paraText.trim().take(60)

                                if (snip.isNotBlank()) {
                                    onAnchorUpdated(snip, cIdx, pIdx)
                                }
                            }
                        }
                    }

                    if (prevDist > 0f) {
                        val scale = currentDist / prevDist
                        cumulativeZoom *= scale
                        if (cumulativeZoom > 1.08f) {
                            val newSize = (currentTargetFontSize + 1).coerceAtMost(36)
                            if (newSize != currentTargetFontSize) {
                                currentTargetFontSize = newSize
                                currentOnFontSizeChange(newSize)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            cumulativeZoom = 1.0f
                        } else if (cumulativeZoom < 0.92f) {
                            val newSize = (currentTargetFontSize - 1).coerceAtLeast(12)
                            if (newSize != currentTargetFontSize) {
                                currentTargetFontSize = newSize
                                currentOnFontSizeChange(newSize)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            cumulativeZoom = 1.0f
                        }
                    }

                    for (i in event.changes.indices) {
                        event.changes[i].consume()
                    }
                } else if (pinchStarted) {
                    // Touch release debounce: consume remaining pointer motion to prevent swipe fling
                    for (i in event.changes.indices) {
                        event.changes[i].consume()
                    }
                }
            } while (event.changes.any { it.pressed })
        } finally {
            if (pinchStarted) {
                onPinchStateChanged(false, true)
            }
        }
    }
}
