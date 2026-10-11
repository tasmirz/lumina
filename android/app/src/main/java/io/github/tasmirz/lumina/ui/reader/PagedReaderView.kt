package io.github.tasmirz.lumina.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
 * Paged Reading View Engine: Renders paginated content with HorizontalPager,
 * chapter opening headers, golden ratio / vertical centering / adaptive spacing,
 * and page navigation tap zones.
 */
@Composable
fun PagedReaderView(
    pagerState: PagerState,
    pages: List<Pair<String, String>>,
    book: Book,
    readingMode: ReadingMode,
    isLandscape: Boolean,
    fontSize: Int,
    fontFamily: FontFamily,
    lineHeightMultiplier: Float,
    letterSpacing: Float,
    contentTextAlign: TextAlign,
    pagedSwipeThreshold: Float,
    horizontalPadding: Int,
    verticalPadding: Int,
    statusBarTopInset: Dp,
    progressBottomInset: Dp,
    effectiveStartPadding: Dp,
    effectiveEndPadding: Dp,
    isUiVisible: Boolean,
    isAutoScrolling: Boolean,
    verticallyCenterPages: Boolean,
    adaptiveVerticalSpacing: Boolean,
    goldenRatioChapterOpeners: Boolean,
    bookmarksByChapter: Map<String, List<Bookmark>>,
    showInBookSearchDialog: Boolean,
    inBookSearchQuery: String,
    inBookSearchResults: List<SceneMatch>,
    inBookCurrentMatchIndex: Int,
    currentImeOffsetPx: Float,
    showTtsDock: Boolean,
    isTtsSpeaking: Boolean,
    inlineImageWidthFraction: Float,
    gestureDoubleTap: GestureAction,
    gestureSingleTap: GestureAction,
    onExecuteGesture: (GestureAction) -> Unit,
    onToggleControls: () -> Unit,
    onOpenLightbox: (Bitmap, String) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onTtsPlayFromChapter: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        HorizontalPager(
            state = pagerState,
            key = { pageIdx -> "paged_page_$pageIdx" },
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = pagedSwipeThreshold.coerceIn(0.04f, 0.50f)
            ),
            pageSpacing = (horizontalPadding * 1.5f).dp.coerceAtLeast(32.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (isLandscape) {
                        statusBarTopInset + 36.dp + (verticalPadding * 0.25f).dp
                    } else {
                        if (readingMode == ReadingMode.PAGED || readingMode == ReadingMode.PAGED_SCROLL) statusBarTopInset + 66.dp + (verticalPadding * 0.5f).dp
                        else if (isUiVisible) statusBarTopInset + 66.dp + (verticalPadding * 0.5f).dp
                        else (verticalPadding * 0.5f).dp
                    },
                    bottom = if (isLandscape) {
                        progressBottomInset + 36.dp + (verticalPadding * 0.25f).dp
                    } else {
                        if (readingMode == ReadingMode.PAGED || readingMode == ReadingMode.PAGED_SCROLL) progressBottomInset + 48.dp + (verticalPadding * 0.35f).dp
                        else if (isUiVisible) progressBottomInset + 64.dp + (verticalPadding * 0.35f).dp
                        else progressBottomInset + 24.dp + (verticalPadding * 0.35f).dp
                    },
                    start = effectiveStartPadding,
                    end = effectiveEndPadding
                )
        ) { pageIdx ->
            val (chapTitle, content) = pages.getOrElse(pageIdx) { "" to "" }
            val isChapterHeaderPage = content.startsWith("CHAPTER_START:::") || content.startsWith("TITLE:::")
            val (cTitle, cSub, bodyText) = if (isChapterHeaderPage) {
                val prefix = if (content.startsWith("CHAPTER_START:::")) "CHAPTER_START:::" else "TITLE:::"
                val rawParts = content.removePrefix(prefix).split(":::", limit = 3)
                Triple(
                    rawParts.getOrNull(0)?.ifBlank { chapTitle } ?: chapTitle,
                    rawParts.getOrNull(1) ?: "",
                    rawParts.getOrNull(2) ?: ""
                )
            } else {
                Triple("", "", "")
            }

            if (isChapterHeaderPage && bodyText.isBlank()) {
                // Title-only cover page
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(gestureDoubleTap, gestureSingleTap) {
                            detectTapGestures(
                                onDoubleTap = { onExecuteGesture(gestureDoubleTap) },
                                onTap = { offset ->
                                    val pageWidth = size.width
                                    if (offset.x < pageWidth * 0.20f) {
                                        onPrevPage()
                                    } else if (offset.x > pageWidth * 0.80f) {
                                        onNextPage()
                                    } else {
                                        onToggleControls()
                                    }
                                }
                            )
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val eyebrowText = if (cSub.isNotBlank() && cSub.startsWith("Part", ignoreCase = true)) {
                        cSub.uppercase()
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cTitle,
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (cSub.isNotBlank() && !cSub.startsWith("Part", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = cSub,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (content.startsWith("[TABLE:") && content.endsWith("]")) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Generous top clear area for effortless horizontal page swiping
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.15f)
                    )
                    ReaderTableCard(
                        tableToken = content,
                        isPagedMode = (readingMode == ReadingMode.PAGED),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Generous bottom clear area for effortless horizontal page swiping
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.15f)
                    )
                }
            } else if (content.startsWith("[IMG:") && content.endsWith("]")) {
                val imgPath = content.removePrefix("[IMG:").removeSuffix("]")
                val bitmap = rememberBookImage(imgPath, isInlineImage = true)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    InlineReaderImage(
                        bitmap = bitmap,
                        imgPath = imgPath,
                        widthFraction = inlineImageWidthFraction,
                        onOpenLightbox = onOpenLightbox,
                        onTap = onToggleControls
                    )
                }
            } else if (content == "[DIVIDER]" || content.trim() == "⁂" || content.trim() == "* * *" || content.trim() == "***") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ReaderDivider()
                }
            } else if (content.startsWith("[VERSE:") && content.endsWith("]")) {
                val verseContent = content.removePrefix("[VERSE:").removeSuffix("]")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    ReaderVerseCard(
                        verse = verseContent,
                        fontSize = fontSize.sp,
                        fontFamily = fontFamily,
                        textColor = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else if (content.startsWith("[QUOTE:") && content.endsWith("]")) {
                val quoteContent = content.removePrefix("[QUOTE:").removeSuffix("]")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    ReaderQuoteCard(
                        quote = quoteContent,
                        fontSize = fontSize.sp,
                        fontFamily = fontFamily,
                        textColor = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                val pageText = if (isChapterHeaderPage) bodyText else content
                val chapterBookmarks = remember(bookmarksByChapter, chapTitle) {
                    bookmarksByChapter[chapTitle.trim().lowercase()] ?: emptyList()
                }
                val matchingBookmarks = remember(pageText, chapterBookmarks) {
                    if (chapterBookmarks.isEmpty()) emptyList()
                    else chapterBookmarks.filter { b ->
                        val q = b.quote.trim()
                        q.isNotBlank() && pageText.contains(q, ignoreCase = true)
                    }
                }
                val onBgColor = MaterialTheme.colorScheme.onBackground
                val isDropCap = isChapterHeaderPage
                val currentMatch = inBookSearchResults.getOrNull(inBookCurrentMatchIndex)
                val isActiveMatch = showInBookSearchDialog && inBookSearchQuery.isNotBlank() &&
                    currentMatch != null && currentMatch.chapterTitle.equals(chapTitle, ignoreCase = true) &&
                    (pageText.contains(currentMatch.snippet.replace("...", "").trim().take(15), ignoreCase = true) ||
                     (inBookSearchQuery.isNotBlank() && pageText.contains(inBookSearchQuery.trim(), ignoreCase = true)))
                val activeSearchQ = if (showInBookSearchDialog) inBookSearchQuery.trim() else ""

                val pagedInlineImages = remember(pageText) { extractInlineImages(pageText) }
                val pagedInlineContentMap = rememberInlineContentMap(pagedInlineImages, fontSize)

                // Effective blocks handling Option 3 (Adaptive Vertical Justification)
                val pagedBlocks = remember(pageText, adaptiveVerticalSpacing) {
                    val base = splitPageIntoBlocks(pageText)
                    if (adaptiveVerticalSpacing && base.size == 1 && base.first() is PageBlock.Text) {
                        val raw = (base.first() as PageBlock.Text).text
                        if (raw.contains("\n\n")) {
                            raw.split("\n\n").filter { it.isNotBlank() }.map { PageBlock.Text(it.trim()) }
                        } else base
                    } else base
                }

                val isImeOpenInSearch = showInBookSearchDialog && currentImeOffsetPx > 0f
                val pagedScrollModifier = if (readingMode == ReadingMode.PAGED_SCROLL || isImeOpenInSearch) {
                    Modifier.verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .then(pagedScrollModifier)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .pointerInput(gestureDoubleTap, gestureSingleTap) {
                            detectTapGestures(
                                onDoubleTap = if (gestureDoubleTap != GestureAction.NONE) {
                                    { onExecuteGesture(gestureDoubleTap) }
                                } else null,
                                onTap = { offset ->
                                    if (isAutoScrolling) {
                                        onExecuteGesture(GestureAction.NONE)
                                    } else {
                                        val pageWidth = size.width
                                        if (offset.x < pageWidth * 0.20f) {
                                            onPrevPage()
                                        } else if (offset.x > pageWidth * 0.80f) {
                                            onNextPage()
                                        } else {
                                            onExecuteGesture(gestureSingleTap)
                                        }
                                    }
                                }
                            )
                        }
                        .then(
                            if (showTtsDock || isTtsSpeaking) {
                                val pagedChapIdx = book.chapters.indexOfFirst { it.title == chapTitle }.coerceAtLeast(0)
                                Modifier.clickable {
                                    onTtsPlayFromChapter(pagedChapIdx)
                                }
                            } else Modifier
                        )
                ) {
                    // Option 2: Vertically Center Short Pages
                    val isShortUnderfilledPage = !isChapterHeaderPage && bodyText.isNotBlank() && pageText.length < 450
                    val shouldCenter = verticallyCenterPages && readingMode == ReadingMode.PAGED && (
                        isChapterHeaderPage || isShortUnderfilledPage
                    )

                    // Option 4: Golden Ratio Chapter Openers (~25-33% down the screen)
                    val shouldGoldenRatio = goldenRatioChapterOpeners && !shouldCenter && readingMode == ReadingMode.PAGED && isChapterHeaderPage

                    if (shouldCenter) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else if (shouldGoldenRatio) {
                        Spacer(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxWidth()
                        )
                    }

                    if (isChapterHeaderPage) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val eyebrowText = if (cSub.isNotBlank() && cSub.startsWith("Part", ignoreCase = true)) {
                                cSub.uppercase()
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
                            Text(
                                text = cTitle,
                                fontFamily = FontFamily.Serif,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (cSub.isNotBlank() && !cSub.startsWith("Part", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cSub,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    pagedBlocks.forEachIndexed { blockIdx, block ->
                        if (adaptiveVerticalSpacing && readingMode == ReadingMode.PAGED && pagedBlocks.size > 1 && blockIdx > 0) {
                            Spacer(
                                modifier = Modifier
                                    .weight(0.25f)
                                    .heightIn(min = 6.dp)
                            )
                        }

                        when (block) {
                            is PageBlock.Text -> {
                                val blockAnnotated = remember(block.text, matchingBookmarks, fontSize, fontFamily, onBgColor, isDropCap, activeSearchQ, isActiveMatch) {
                                    buildHighlightedAnnotatedString(
                                        text = block.text,
                                        matchingBookmarks = matchingBookmarks,
                                        onBookmarkClick = onBookmarkClick,
                                        isDropCap = isDropCap && blockIdx == 0,
                                        baseFontFamily = fontFamily,
                                        baseFontSize = fontSize.sp,
                                        baseTextColor = onBgColor,
                                        searchQuery = activeSearchQ,
                                        isActiveSearchMatch = isActiveMatch
                                    )
                                }
                                SelectionContainer(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = blockAnnotated,
                                        inlineContent = pagedInlineContentMap,
                                        fontFamily = fontFamily,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * lineHeightMultiplier).sp,
                                        letterSpacing = letterSpacing.sp,
                                        textAlign = contentTextAlign,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            is PageBlock.Table -> {
                                ReaderTableCard(
                                    tableToken = block.token,
                                    isPagedMode = (readingMode == ReadingMode.PAGED),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    val bottomWeight = if (shouldGoldenRatio) 0.65f else 1f
                    val tapSpacerModifier = if (readingMode == ReadingMode.PAGED_SCROLL) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                    } else {
                        Modifier
                            .weight(bottomWeight)
                            .fillMaxWidth()
                    }
                    Spacer(modifier = tapSpacerModifier)
                    if (readingMode == ReadingMode.PAGED_SCROLL) {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
