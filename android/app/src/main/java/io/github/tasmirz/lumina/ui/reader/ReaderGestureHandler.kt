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
    currentFontSize: () -> Int,
    currentReadingMode: () -> ReadingMode,
    currentIsPagedReading: () -> Boolean,
    currentPages: () -> List<Pair<String, String>>,
    currentChapters: () -> List<Chapter>,
    chapterCumulativeParaOffsets: () -> IntArray,
    pagerState: PagerState,
    listState: LazyListState,
    currentOnFontSizeChange: (Int) -> Unit,
    onAnchorUpdated: (snippet: String, chapterIdx: Int, paraIdx: Int) -> Unit,
    onPinchStateChanged: (isPinching: Boolean, requestSync: Boolean) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        var cumulativeZoom = 1.0f
        var pinchStarted = false
        var currentTargetFontSize = currentFontSize()
        var trackedPointerId0: androidx.compose.ui.input.pointer.PointerId? = null
        var trackedPointerId1: androidx.compose.ui.input.pointer.PointerId? = null

        try {
            while (true) {
                // Pass Initial intercepts multi-touch pinches before child composables (SelectionContainer, HorizontalPager) consume them
                val event = awaitPointerEvent(PointerEventPass.Initial)

                val pressedChanges = event.changes.filter { it.pressed }
                val pressedCount = pressedChanges.size

                if (pressedCount == 0) {
                    // All fingers lifted
                    break
                }

                var p0: PointerInputChange? = null
                var p1: PointerInputChange? = null

                if (pressedCount >= 2) {
                    if (trackedPointerId0 != null && trackedPointerId1 != null) {
                        p0 = pressedChanges.find { it.id == trackedPointerId0 }
                        p1 = pressedChanges.find { it.id == trackedPointerId1 }
                    }
                    if (p0 == null || p1 == null) {
                        p0 = pressedChanges[0]
                        p1 = pressedChanges[1]
                        trackedPointerId0 = p0.id
                        trackedPointerId1 = p1.id
                    }
                } else {
                    trackedPointerId0 = null
                    trackedPointerId1 = null
                }

                if (pressedCount >= 2 && p0 != null && p1 != null) {
                    // Consume pointers immediately on 2-finger gesture so children (HorizontalPager scroll, text selection) do not hijack the pinch
                    for (i in event.changes.indices) {
                        event.changes[i].consume()
                    }

                    val currentDist = kotlin.math.hypot(p0.position.x - p1.position.x, p0.position.y - p1.position.y)
                    val prevDist = kotlin.math.hypot(p0.previousPosition.x - p1.previousPosition.x, p0.previousPosition.y - p1.previousPosition.y)
                    val avgTouchY = (p0.position.y + p1.position.y) / 2f

                    if (!pinchStarted) {
                        pinchStarted = true
                        onPinchStateChanged(true, false)
                        currentTargetFontSize = currentFontSize()

                        if (currentIsPagedReading()) {
                            val activePages = currentPages()
                            val curPage = pagerState.currentPage
                            if (curPage in activePages.indices) {
                                val (cTitle, pageContent) = activePages[curPage]
                                val isChapterHeaderPage = pageContent.startsWith("CHAPTER_START:::") || pageContent.startsWith("TITLE:::")
                                val pageBody = if (isChapterHeaderPage) pageContent.substringAfterLast(":::") else pageContent
                                val chapters = currentChapters()
                                val cIdx = chapters.indexOfFirst { it.title == cTitle }.coerceAtLeast(0)
                                val chap = chapters.getOrNull(cIdx)

                                val blocks = pageBody.split("\n\n").filter { it.isNotBlank() }
                                val viewportH = size.height.toFloat()
                                val touchFraction = if (viewportH > 0f) {
                                    ((avgTouchY / viewportH) - 0.08f) / 0.80f
                                } else {
                                    0.5f
                                }.coerceIn(0f, 1f)

                                val touchedBlock = if (blocks.isEmpty()) {
                                    pageBody
                                } else if (blocks.size == 1) {
                                    blocks[0]
                                } else {
                                    val totalChars = blocks.sumOf { it.length }.coerceAtLeast(1)
                                    val targetChar = (touchFraction * totalChars).toInt()
                                    var accum = 0
                                    var selected = blocks.first()
                                    for (b in blocks) {
                                        accum += b.length
                                        if (accum >= targetChar) {
                                            selected = b
                                            break
                                        }
                                    }
                                    selected
                                }

                                var targetSnippet = ""
                                var targetParaIdx = 0

                                val cleanExact = cleanAlpha(touchedBlock.take(60))
                                val foundIdx = chap?.paragraphs?.indexOfFirst { fullPara ->
                                    val cleanFull = cleanAlpha(fullPara)
                                    cleanFull.contains(cleanExact.take(25)) || cleanExact.contains(cleanFull.take(25))
                                } ?: -1
                                if (foundIdx >= 0) {
                                    targetParaIdx = foundIdx
                                    targetSnippet = chap?.paragraphs?.getOrNull(foundIdx)?.trim()?.take(60) ?: ""
                                }
                                if (targetSnippet.isBlank()) {
                                    targetSnippet = touchedBlock.trim().take(60)
                                }

                                if (targetSnippet.isNotBlank()) {
                                    onAnchorUpdated(targetSnippet, cIdx, targetParaIdx)
                                }
                            }
                        } else if (currentReadingMode() == ReadingMode.SCROLL) {
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val viewportH = listState.layoutInfo.viewportSize.height
                            val targetY = if (viewportH > 0) avgTouchY else 0f
                            val touchedItem = visibleItems.find { targetY >= it.offset && targetY <= it.offset + it.size }
                                ?: visibleItems.firstOrNull()

                            if (touchedItem != null) {
                                val itemIdx = touchedItem.index
                                val offsets = chapterCumulativeParaOffsets()
                                val chapters = currentChapters()
                                val binIdx = offsets.binarySearch(itemIdx)
                                val cIdx = if (binIdx >= 0) binIdx else (-binIdx - 2).coerceIn(0, chapters.size - 1)
                                val pIdx = (itemIdx - offsets.getOrElse(cIdx) { 0 } - 1).coerceAtLeast(0)
                                val paraText = chapters.getOrNull(cIdx)?.paragraphs?.getOrNull(pIdx) ?: ""
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
                        // Responsive 2.0% threshold for immediate zoom feedback on touch screens
                        if (cumulativeZoom > 1.020f) {
                            val newSize = (currentTargetFontSize + 1).coerceAtMost(48)
                            if (newSize != currentTargetFontSize) {
                                currentTargetFontSize = newSize
                                currentOnFontSizeChange(newSize)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            cumulativeZoom = 1.0f
                        } else if (cumulativeZoom < 0.980f) {
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
            }
        } finally {
            if (pinchStarted) {
                onPinchStateChanged(false, true)
            }
        }
    }
}
