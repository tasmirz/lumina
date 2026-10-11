package io.github.tasmirz.lumina.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.GestureAction
import io.github.tasmirz.lumina.model.ReadingMode
import io.github.tasmirz.lumina.model.SceneMatch
import io.github.tasmirz.lumina.ui.components.rememberBookImage

/**
 * Continuous Scroll Reading View Engine: Renders all chapters and body paragraphs
 * in a seamless LazyColumn with chapter title headers, bookmark highlights,
 * search term highlights, and inline illustration pinch-to-zoom.
 */
@Composable
fun ScrollReaderView(
    listState: LazyListState,
    book: Book,
    readingMode: ReadingMode,
    fontSize: Int,
    fontFamily: FontFamily,
    lineHeightMultiplier: Float,
    letterSpacing: Float,
    contentTextAlign: TextAlign,
    verticalPadding: Int,
    progressBottomInset: Dp,
    effectiveImePadding: Dp,
    scrollHorizontalPaddingStart: Dp,
    scrollHorizontalPaddingEnd: Dp,
    bookmarksByChapter: Map<String, List<Bookmark>>,
    speakingChapterIdx: Int,
    speakingParaIdx: Int,
    isTtsSpeaking: Boolean,
    inlineImageWidthFraction: Float,
    inBookSearchQuery: String,
    inBookSearchResults: List<SceneMatch>,
    inBookCurrentMatchIndex: Int,
    gestureDoubleTap: GestureAction,
    gestureSingleTap: GestureAction,
    onExecuteGesture: (GestureAction) -> Unit,
    onToggleControls: () -> Unit,
    onOpenLightbox: (Bitmap, String) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onTtsPlayFromParagraph: (chapIdx: Int, pIdx: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SelectionContainer(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = (76 + verticalPadding).dp,
                bottom = 120.dp + progressBottomInset + effectiveImePadding + verticalPadding.dp,
                start = scrollHorizontalPaddingStart,
                end = scrollHorizontalPaddingEnd
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            book.chapters.forEachIndexed { chapIdx, chapter ->
                val chapterBookmarks = bookmarksByChapter[chapter.title.trim().lowercase()] ?: emptyList()
                item(key = "chap-header-$chapIdx", contentType = "chap_header") {
                    DisableSelection {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (chapIdx == 0) 16.dp else 48.dp, bottom = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (chapIdx > 0) {
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                            val isSameAsChap = chapter.title.equals(book.title, ignoreCase = true) ||
                                    chapter.title.equals(chapter.subtitle, ignoreCase = true)
                            val showEyebrow = !isSameAsChap && (chapIdx > 0 || (chapter.subtitle.isNotBlank() && chapter.subtitle.startsWith("Part", ignoreCase = true)))
                            if (showEyebrow) {
                                val eyebrowText = if (chapter.subtitle.isNotBlank() && chapter.subtitle.startsWith("Part", ignoreCase = true)) {
                                    chapter.subtitle.uppercase()
                                } else {
                                    book.title.uppercase()
                                }
                                Text(
                                    text = eyebrowText,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = chapter.title,
                                fontFamily = FontFamily.Serif,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (chapter.subtitle.isNotBlank() && !chapter.subtitle.startsWith("Part", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = chapter.subtitle,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = chapter.paragraphs,
                    key = { pIdx, _ -> "chap-${chapIdx}-para-${pIdx}" },
                    contentType = { _, _ -> "paragraph" }
                ) { pIdx, para ->
                    val matchingBookmarks = remember(para, chapterBookmarks) {
                        if (chapterBookmarks.isEmpty()) {
                            emptyList()
                        } else {
                            chapterBookmarks.filter { b ->
                                val q = b.quote.trim()
                                q.isNotBlank() && para.contains(q, ignoreCase = true)
                            }
                        }
                    }
                    val isBeingSpoken = if (isTtsSpeaking) speakingChapterIdx == chapIdx && speakingParaIdx == pIdx else false

                    val isDuplicateTitlePara = pIdx == 0 && (
                        para.trim().equals(book.title.trim(), ignoreCase = true) ||
                        para.trim().equals(chapter.title.trim(), ignoreCase = true)
                    )
                    if (isDuplicateTitlePara) {
                        // Suppress redundant duplicated title in body paragraph
                    } else if (para.startsWith("[TABLE:") && para.endsWith("]")) {
                        ReaderTableCard(tableToken = para)
                    } else if (para.startsWith("[IMG:") && para.endsWith("]")) {
                        val imgPath = para.removePrefix("[IMG:").removeSuffix("]")
                        val bitmap = rememberBookImage(imgPath, isInlineImage = true)
                        InlineReaderImage(
                            bitmap = bitmap,
                            imgPath = imgPath,
                            widthFraction = inlineImageWidthFraction,
                            onOpenLightbox = onOpenLightbox,
                            onTap = onToggleControls
                        )
                    } else if (para == "[DIVIDER]" || para.trim() == "⁂" || para.trim() == "* * *" || para.trim() == "***") {
                        ReaderDivider()
                    } else if (para.startsWith("[VERSE:") && para.endsWith("]")) {
                        val verseContent = para.removePrefix("[VERSE:").removeSuffix("]")
                        ReaderVerseCard(
                            verse = verseContent,
                            fontSize = fontSize.sp,
                            fontFamily = fontFamily,
                            textColor = MaterialTheme.colorScheme.onBackground
                        )
                    } else if (para.startsWith("[QUOTE:") && para.endsWith("]")) {
                        val quoteContent = para.removePrefix("[QUOTE:").removeSuffix("]")
                        ReaderQuoteCard(
                            quote = quoteContent,
                            fontSize = fontSize.sp,
                            fontFamily = fontFamily,
                            textColor = MaterialTheme.colorScheme.onBackground
                        )
                    } else {
                        val flowPara = remember(para) {
                            if (!para.startsWith("[TABLE:") && !para.startsWith("[IMG:") && !para.startsWith("[VERSE:") && !para.startsWith("[QUOTE:") && para != "[DIVIDER]" && para.contains("\n")) {
                                para.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
                            } else {
                                para
                            }
                        }
                        val isDropCap = chapIdx == 0 && pIdx == 0 && flowPara.length > 40 && !flowPara.startsWith("[IMG:") && !flowPara.startsWith("[TABLE:") && !flowPara.startsWith("[VERSE:") && !flowPara.startsWith("[QUOTE:") && flowPara != "[DIVIDER]"
                        val onBgColor = MaterialTheme.colorScheme.onBackground
                        val secColor = MaterialTheme.colorScheme.secondary
                        val activeSearchQ = if (inBookSearchQuery.length >= 2) inBookSearchQuery else ""
                        val isActiveMatch = inBookSearchResults.getOrNull(inBookCurrentMatchIndex)?.let {
                            it.chapterIndex == chapIdx && it.paragraphIndex == pIdx
                        } ?: false
                        val hasFormatting = matchingBookmarks.isNotEmpty() || isDropCap || activeSearchQ.isNotEmpty() ||
                            flowPara.contains("[sub]") || flowPara.contains("[sup]") ||
                            flowPara.contains("[INLINE_IMG:")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isBeingSpoken) {
                                        Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    } else {
                                        Modifier.padding(bottom = (fontSize * 0.45f).dp)
                                    }
                                )
                                .then(
                                    if (isTtsSpeaking) {
                                        Modifier.clickable {
                                            onTtsPlayFromParagraph(chapIdx, pIdx)
                                        }
                                    } else Modifier
                                )
                        ) {
                            if (hasFormatting) {
                                val inlineImages = remember(flowPara) { extractInlineImages(flowPara) }
                                val inlineContentMap = rememberInlineContentMap(inlineImages, fontSize)
                                val annotatedText = remember(
                                    flowPara, matchingBookmarks, isDropCap, fontSize, fontFamily, onBgColor,
                                    activeSearchQ, isActiveMatch
                                ) {
                                    buildHighlightedAnnotatedString(
                                        text = flowPara,
                                        matchingBookmarks = matchingBookmarks,
                                        onBookmarkClick = onBookmarkClick,
                                        isDropCap = isDropCap,
                                        dropCapFontFamily = FontFamily.Serif,
                                        dropCapFontSize = (fontSize * 2.2f).sp,
                                        dropCapColor = secColor,
                                        baseFontFamily = fontFamily,
                                        baseFontSize = fontSize.sp,
                                        baseTextColor = onBgColor,
                                        searchQuery = activeSearchQ,
                                        isActiveSearchMatch = isActiveMatch
                                    )
                                }
                                Text(
                                    text = annotatedText,
                                    inlineContent = inlineContentMap,
                                    lineHeight = (fontSize * lineHeightMultiplier).sp,
                                    letterSpacing = letterSpacing.sp,
                                    textAlign = contentTextAlign,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = flowPara,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize.sp,
                                    color = onBgColor,
                                    lineHeight = (fontSize * lineHeightMultiplier).sp,
                                    letterSpacing = letterSpacing.sp,
                                    textAlign = contentTextAlign,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            item(key = "bottom-empty-space", contentType = "bottom_space") {
                DisableSelection {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .pointerInput(gestureDoubleTap, gestureSingleTap) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onExecuteGesture(gestureDoubleTap)
                                    },
                                    onTap = {
                                        onExecuteGesture(gestureSingleTap)
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}
