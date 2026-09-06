package org.protidhoni.lumina.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.protidhoni.lumina.model.Book
import org.protidhoni.lumina.model.Bookmark
import org.protidhoni.lumina.model.HighlightColor
import org.protidhoni.lumina.model.ReadingMode
import org.protidhoni.lumina.model.TypefaceMode
import org.protidhoni.lumina.ui.components.rememberBookImage
import org.protidhoni.lumina.util.CitationHelper
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    book: Book,
    bookmarks: List<Bookmark>,
    readingMode: ReadingMode,
    fontSize: Int,
    typeface: TypefaceMode,
    lineHeightMultiplier: Float,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    showNavBarInReader: Boolean,
    onToggleNavBar: () -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit,
    onModeChange: (ReadingMode) -> Unit,
    onBackToLibrary: () -> Unit,
    onPositionChange: (chapterIdx: Int, pageIdx: Int, scrollPos: Int, progressPct: Int) -> Unit,
    onAddBookmark: (String, HighlightColor) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onLookupWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showControls by rememberSaveable { mutableStateOf(true) }
    var selectedText by remember { mutableStateOf("") }
    var selectedChapterTitle by rememberSaveable { mutableStateOf("") }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var activeChapterTitle by rememberSaveable { mutableStateOf(book.chapters.firstOrNull()?.title ?: "Chapter 1") }

    // Only two fonts across entire app: Serif for reading/titles, SansSerif for UI
    val fontFamily = when (typeface) {
        TypefaceMode.SERIF -> FontFamily.Serif
        TypefaceMode.SANS -> FontFamily.SansSerif
        TypefaceMode.MONO -> FontFamily.SansSerif
    }

    val isUiVisible = showControls && !isFullscreen

    LaunchedEffect(isUiVisible) {
        onControlsVisibilityChange(isUiVisible)
    }

    // Hardware & Gesture Back Button Handling:
    // If in fullscreen, exit fullscreen first. Otherwise, go back to Library.
    BackHandler(enabled = true) {
        if (isFullscreen) {
            onToggleFullscreen()
        } else {
            onBackToLibrary()
        }
    }

    // Auto-hide UI on scroll down, reveal on scroll up
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -15f) {
                        showControls = false
                        showSelectionMenu = false
                    } else if (available.y > 15f) {
                        showControls = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    // Active bookmark check
    val activeBookmark = remember(selectedText, bookmarks) {
        if (selectedText.isBlank()) null
        else bookmarks.find { it.quote.trim() == selectedText.trim() || selectedText.contains(it.quote.trim()) }
    }

    // Soft Gold selection highlight
    val customSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.secondary,
        backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(nestedScrollConnection)
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides customSelectionColors) {
            SelectionContainer {
                if (readingMode == ReadingMode.SCROLL) {
                    // Continuous Vertical Scroll Mode across all chapters
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = book.scrollPos)
                    val totalParas = remember(book) { maxOf(book.chapters.sumOf { it.paragraphs.size }, 1) }

                    LaunchedEffect(listState.firstVisibleItemIndex) {
                        val overallProgress = ((listState.firstVisibleItemIndex.toFloat() / totalParas) * 100).toInt().coerceIn(0, 100)
                        var accum = 0
                        var currentChap = 0
                        for (i in book.chapters.indices) {
                            val count = book.chapters[i].paragraphs.size + 1
                            if (listState.firstVisibleItemIndex < accum + count) {
                                currentChap = i
                                break
                            }
                            accum += count
                        }
                        activeChapterTitle = book.chapters.getOrNull(currentChap)?.title ?: "Chapter 1"
                        onPositionChange(currentChap, 0, listState.firstVisibleItemIndex, overallProgress)
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = if (isUiVisible) 76.dp else 24.dp,
                            bottom = if (isUiVisible) 90.dp else 40.dp,
                            start = 22.dp,
                            end = 22.dp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (showSelectionMenu) {
                                            showSelectionMenu = false
                                        } else {
                                            showControls = !showControls
                                        }
                                    }
                                )
                            }
                    ) {
                        book.chapters.forEachIndexed { chapIdx, chapter ->
                            item(key = "chap-header-$chapIdx") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (chapIdx == 0) 8.dp else 40.dp, bottom = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = chapter.title.uppercase(),
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        letterSpacing = 2.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = book.title,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (chapter.subtitle.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = chapter.subtitle,
                                            fontFamily = FontFamily.Serif,
                                            fontStyle = FontStyle.Italic,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(
                                        modifier = Modifier.width(48.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            itemsIndexed(
                                items = chapter.paragraphs,
                                key = { pIdx, _ -> "chap-${chapIdx}-para-${pIdx}" }
                            ) { pIdx, para ->
                                val matchingBookmark = bookmarks.firstOrNull {
                                    it.quote.trim() == para.trim() || para.contains(it.quote.trim())
                                }
                                val highlightBg = when (matchingBookmark?.color) {
                                    HighlightColor.GOLD -> Color(0x3DF59E0B)
                                    HighlightColor.ROSE -> Color(0x3DF43F5E)
                                    HighlightColor.SAGE -> Color(0x3D10B981)
                                    null -> if (selectedText == para && showSelectionMenu) Color(0x26D4AF37) else Color.Transparent
                                }

                                if (para.startsWith("[IMG:") && para.endsWith("]")) {
                                    // Inline illustration
                                    val imgPath = para.removePrefix("[IMG:").removeSuffix("]")
                                    val bitmap = rememberBookImage(imgPath)
                                    if (bitmap != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Illustration",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxWidth(0.92f)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        }
                                    }
                                } else {
                                    // Text Paragraph
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(highlightBg)
                                            .padding(vertical = 6.dp)
                                            .pointerInput(para) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        selectedText = para
                                                        selectedChapterTitle = chapter.title
                                                        showSelectionMenu = true
                                                    },
                                                    onDoubleTap = {
                                                        selectedText = para
                                                        selectedChapterTitle = chapter.title
                                                        showSelectionMenu = true
                                                    },
                                                    onTap = {
                                                        if (showSelectionMenu) {
                                                            showSelectionMenu = false
                                                        } else {
                                                            showControls = !showControls
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        // Drop cap on first paragraph of chapter 1 if long enough
                                        if (chapIdx == 0 && pIdx == 0 && para.length > 40 && !para.startsWith("[IMG:")) {
                                            val dropChar = para.take(1)
                                            val rest = para.drop(1)
                                            val annotatedString = buildAnnotatedString {
                                                withStyle(
                                                    SpanStyle(
                                                        fontFamily = FontFamily.Serif,
                                                        fontSize = (fontSize * 2.2f).sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                ) {
                                                    append(dropChar)
                                                }
                                                withStyle(
                                                    SpanStyle(
                                                        fontFamily = fontFamily,
                                                        fontSize = fontSize.sp,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                ) {
                                                    append(rest)
                                                }
                                            }
                                            Text(
                                                text = annotatedString,
                                                lineHeight = (fontSize * lineHeightMultiplier).sp,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Text(
                                                text = para,
                                                fontFamily = fontFamily,
                                                fontSize = fontSize.sp,
                                                lineHeight = (fontSize * lineHeightMultiplier).sp,
                                                textAlign = TextAlign.Start,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Paged Mode
                    val pages = remember(book, fontSize) {
                        val list = mutableListOf<Pair<String, String>>()
                        book.chapters.forEach { chap ->
                            list.add(Pair(chap.title, "TITLE:::${chap.title}:::${chap.subtitle}"))
                            val chunkSize = if (fontSize >= 22) 1 else 2
                            var i = 0
                            while (i < chap.paragraphs.size) {
                                val p = chap.paragraphs[i]
                                if (p.startsWith("[IMG:") && p.endsWith("]")) {
                                    list.add(Pair(chap.title, p))
                                    i++
                                } else {
                                    val textBatch = mutableListOf<String>()
                                    while (i < chap.paragraphs.size && textBatch.size < chunkSize && !(chap.paragraphs[i].startsWith("[IMG:") && chap.paragraphs[i].endsWith("]"))) {
                                        textBatch.add(chap.paragraphs[i])
                                        i++
                                    }
                                    if (textBatch.isNotEmpty()) {
                                        list.add(Pair(chap.title, textBatch.joinToString("\n\n")))
                                    }
                                }
                            }
                        }
                        list
                    }

                    val pagerState = rememberPagerState(
                        initialPage = book.currentPage.coerceIn(0, maxOf(pages.size - 1, 0)),
                        pageCount = { pages.size }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        val progress = if (pages.isNotEmpty()) {
                            (((pagerState.currentPage + 1).toFloat() / pages.size) * 100).toInt().coerceIn(0, 100)
                        } else 0
                        val currentPageChapTitle = pages.getOrNull(pagerState.currentPage)?.first ?: "Chapter 1"
                        activeChapterTitle = currentPageChapTitle
                        val chapIdx = book.chapters.indexOfFirst { it.title == currentPageChapTitle }.coerceAtLeast(0)
                        onPositionChange(chapIdx, pagerState.currentPage, 0, progress)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (isUiVisible) 80.dp else 24.dp,
                                    bottom = if (isUiVisible) 80.dp else 24.dp,
                                    start = 22.dp,
                                    end = 22.dp
                                )
                        ) { pageIdx ->
                            val (chapTitle, content) = pages[pageIdx]
                            if (content.startsWith("TITLE:::")) {
                                val parts = content.split(":::")
                                val cTitle = parts.getOrNull(1) ?: chapTitle
                                val cSub = parts.getOrNull(2) ?: ""
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showControls = !showControls },
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = cTitle.uppercase(),
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        letterSpacing = 2.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = book.title,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (cSub.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = cSub,
                                            fontStyle = FontStyle.Italic,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else if (content.startsWith("[IMG:") && content.endsWith("]")) {
                                val imgPath = content.removePrefix("[IMG:").removeSuffix("]")
                                val bitmap = rememberBookImage(imgPath)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showControls = !showControls },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Illustration",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth(0.94f)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }
                            } else {
                                val matchingBookmark = bookmarks.firstOrNull {
                                    it.quote.trim() == content.trim() || content.contains(it.quote.trim())
                                }
                                val highlightBg = when (matchingBookmark?.color) {
                                    HighlightColor.GOLD -> Color(0x3DF59E0B)
                                    HighlightColor.ROSE -> Color(0x3DF43F5E)
                                    HighlightColor.SAGE -> Color(0x3D10B981)
                                    null -> if (selectedText == content && showSelectionMenu) Color(0x26D4AF37) else Color.Transparent
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(highlightBg)
                                        .padding(6.dp)
                                        .pointerInput(content) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    selectedText = content
                                                    selectedChapterTitle = chapTitle
                                                    showSelectionMenu = true
                                                },
                                                onDoubleTap = {
                                                    selectedText = content
                                                    selectedChapterTitle = chapTitle
                                                    showSelectionMenu = true
                                                },
                                                onTap = {
                                                    showControls = !showControls
                                                    showSelectionMenu = false
                                                }
                                            )
                                        }
                                ) {
                                    Text(
                                        text = content,
                                        fontFamily = fontFamily,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * lineHeightMultiplier).sp,
                                        textAlign = TextAlign.Start,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Page Navigation Tap Zones
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .align(Alignment.CenterStart)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                        }
                                    }
                                }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .align(Alignment.CenterEnd)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        if (pagerState.currentPage < pages.size - 1) {
                                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        // TOP HEADER BAR: Matches Reference Screenshot Exactly
        // Left: '<' (ArrowBackIos), Center: Title (Serif, Medium) & Chapter (SansSerif, Normal), Right: [ Scroll | Paged ] segmented pill
        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        // Left: Back arrow (< chevron)
                        IconButton(
                            onClick = onBackToLibrary,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                                .size(38.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = "Library",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Center: Stacked Title (line 1) & Chapter (line 2) - Exactly horizontally centered on screen
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.56f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = book.title,
                                fontFamily = FontFamily.Serif,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = activeChapterTitle,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }

                        // Right: Mode Toggler (Scroll <-> Paged)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val nextMode = if (readingMode == ReadingMode.SCROLL) ReadingMode.PAGED else ReadingMode.SCROLL
                                    onModeChange(nextMode)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (readingMode == ReadingMode.SCROLL) Icons.Filled.SwapVert else Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Toggle Reading Mode",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (readingMode == ReadingMode.SCROLL) "Scroll" else "Paged",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Delicate hairline divider underneath the header bar
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
            }
        }

        // Selection Menu Pill
        AnimatedVisibility(
            visible = showSelectionMenu,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isUiVisible) 76.dp else 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Highlight:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD4AF37))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.GOLD)
                                    showSelectionMenu = false
                                    Toast.makeText(context, "Added Gold highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5B7B7))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.ROSE)
                                    showSelectionMenu = false
                                    Toast.makeText(context, "Added Rose highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB2C2B2))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.SAGE)
                                    showSelectionMenu = false
                                    Toast.makeText(context, "Added Sage highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeBookmark != null) {
                            IconButton(
                                onClick = {
                                    onRemoveBookmark(activeBookmark.id)
                                    showSelectionMenu = false
                                    Toast.makeText(context, "Removed highlight", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Highlight", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        IconButton(
                            onClick = {
                                val formatted = CitationHelper.formatCitation(
                                    quote = selectedText,
                                    author = book.author,
                                    bookTitle = book.title,
                                    chapterTitle = selectedChapterTitle.ifBlank { activeChapterTitle }
                                )
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Citation", formatted))
                                showSelectionMenu = false
                                Toast.makeText(context, "Copied with citation reference!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.FormatQuote, contentDescription = "Cite Quote")
                        }

                        IconButton(
                            onClick = {
                                showSelectionMenu = false
                                val firstWord = selectedText.trim().split("\\s+".toRegex()).firstOrNull()?.replace("[^a-zA-Z]".toRegex(), "") ?: selectedText
                                onLookupWord(firstWord)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Define Word")
                        }

                        IconButton(onClick = { showSelectionMenu = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Menu")
                        }
                    }
                }
            }
        }

        // BOTTOM PROGRESS DOCK: Clean Progress Bar & Reading Stats (Clickable to toggle Nav Bar)
        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleNavBar() },
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (book.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${book.progress}% completed",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = book.readTimeLeft,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
