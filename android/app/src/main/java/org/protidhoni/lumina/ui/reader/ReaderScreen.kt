package org.protidhoni.lumina.ui.reader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import org.protidhoni.lumina.data.AiProvider
import org.protidhoni.lumina.data.AssistantAction
import org.protidhoni.lumina.data.AssistantService
import org.protidhoni.lumina.model.BackgroundTexture
import org.protidhoni.lumina.model.Book
import org.protidhoni.lumina.model.Bookmark
import org.protidhoni.lumina.model.HighlightColor
import org.protidhoni.lumina.model.OrbActionItem
import org.protidhoni.lumina.model.ReadingMode
import org.protidhoni.lumina.model.TextAlignmentMode
import org.protidhoni.lumina.model.TypefaceMode
import org.protidhoni.lumina.ui.components.AssistantVoiceState
import org.protidhoni.lumina.ui.components.AsyncImageBitmap
import org.protidhoni.lumina.ui.components.FloatingAssistantOrb
import org.protidhoni.lumina.ui.components.TableOfContentsSheet
import org.protidhoni.lumina.ui.components.rememberBookImage
import org.protidhoni.lumina.util.CitationHelper
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Builds an AnnotatedString highlighting only the exact quote matches within the text,
 * preventing rectangular block coloring of the entire paragraph container.
 * Uses LinkAnnotation.Clickable for native click-to-view/edit/delete note support without breaking selection.
 */
fun buildHighlightedAnnotatedString(
    text: String,
    matchingBookmarks: List<Bookmark>,
    onBookmarkClick: ((Bookmark) -> Unit)? = null,
    isDropCap: Boolean = false,
    dropCapFontFamily: FontFamily = FontFamily.Serif,
    dropCapFontSize: TextUnit = TextUnit.Unspecified,
    dropCapColor: Color = Color.Unspecified,
    baseFontFamily: FontFamily,
    baseFontSize: TextUnit,
    baseTextColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        if (isDropCap && text.length > 40 && !text.startsWith("[IMG:")) {
            val dropChar = text.take(1)
            val rest = text.drop(1)
            withStyle(
                SpanStyle(
                    fontFamily = dropCapFontFamily,
                    fontSize = dropCapFontSize,
                    fontWeight = FontWeight.Medium,
                    color = dropCapColor
                )
            ) {
                append(dropChar)
            }
            withStyle(
                SpanStyle(
                    fontFamily = baseFontFamily,
                    fontSize = baseFontSize,
                    color = baseTextColor
                )
            ) {
                append(rest)
            }
        } else {
            withStyle(
                SpanStyle(
                    fontFamily = baseFontFamily,
                    fontSize = baseFontSize,
                    color = baseTextColor
                )
            ) {
                append(text)
            }
        }

        matchingBookmarks.forEach { bm ->
            val quote = bm.quote.trim()
            if (quote.isNotEmpty()) {
                if (text.contains(quote, ignoreCase = true)) {
                    var searchIndex = 0
                    while (searchIndex < text.length) {
                        val idx = text.indexOf(quote, searchIndex, ignoreCase = true)
                        if (idx == -1) break
                        val end = (idx + quote.length).coerceAtMost(text.length)
                        val bg = when (bm.color) {
                            HighlightColor.GOLD -> Color(0x66F59E0B)
                            HighlightColor.ROSE -> Color(0x66F43F5E)
                            HighlightColor.SAGE -> Color(0x6610B981)
                        }
                        addStyle(
                            SpanStyle(
                                background = bg,
                                textDecoration = if (bm.note.isNotBlank()) TextDecoration.Underline else TextDecoration.None
                            ),
                            start = idx,
                            end = end
                        )
                        if (onBookmarkClick != null) {
                            addLink(
                                clickable = LinkAnnotation.Clickable(
                                    tag = bm.id.toString(),
                                    linkInteractionListener = {
                                        onBookmarkClick(bm)
                                    }
                                ),
                                start = idx,
                                end = end
                            )
                        }
                        searchIndex = end
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onUpdateBookmark: (Bookmark) -> Unit = {},
    onLookupWord: (String) -> Unit,
    onOpenAppearance: () -> Unit = {},
    onOpenAdvancedSettings: () -> Unit = onOpenAppearance,
    onOpenBookmarks: () -> Unit = {},
    showFloatingAssistant: Boolean = true,
    onToggleFloatingAssistant: (Boolean) -> Unit = {},
    activeOrbActions: Set<OrbActionItem> = setOf(
        OrbActionItem.READING_MODE,
        OrbActionItem.TTS,
        OrbActionItem.NOTE,
        OrbActionItem.TOC,
        OrbActionItem.SETTINGS
    ),
    backgroundTexture: BackgroundTexture = BackgroundTexture.NONE,
    customBgUri: String = "",
    textAlignment: TextAlignmentMode = TextAlignmentMode.JUSTIFY,
    letterSpacing: Float = 0.2f,
    geminiApiKey: String = "",
    aiProvider: AiProvider = AiProvider.GEMINI,
    aiBaseUrl: String = "https://api.openai.com/v1",
    aiModel: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showControls by rememberSaveable { mutableStateOf(true) }
    var selectedText by remember { mutableStateOf("") }
    var selectedChapterTitle by rememberSaveable { mutableStateOf("") }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var selectedBookmarkForModal by remember { mutableStateOf<Bookmark?>(null) }
    var showBookmarkDetailModal by remember { mutableStateOf(false) }
    var showTocSheet by rememberSaveable { mutableStateOf(false) }
    var activeChapterTitle by rememberSaveable { mutableStateOf(book.chapters.firstOrNull()?.title ?: "Chapter 1") }

    // TTS Reader states
    var isTtsSpeaking by remember { mutableStateOf(false) }
    var speakingChapterIdx by rememberSaveable { mutableIntStateOf(book.currentChapter) }
    var speakingParaIdx by rememberSaveable { mutableIntStateOf(0) }

    // TextToSpeech Engine
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val isTtsInitialized = remember { mutableStateOf(false) }

    // Floating Voice Assistant State & Service
    val assistantService = remember { AssistantService(context) }
    var voiceState by remember { mutableStateOf(AssistantVoiceState.IDLE) }
    var voiceQuery by remember { mutableStateOf("") }
    var voiceResponse by remember { mutableStateOf("") }

    val speakNextPara = rememberUpdatedState {
        val tts = ttsRef.value
        if (tts != null && isTtsInitialized.value) {
            val chapter = book.chapters.getOrNull(speakingChapterIdx)
            if (chapter != null && speakingParaIdx < chapter.paragraphs.size) {
                val p = chapter.paragraphs[speakingParaIdx]
                if (p.startsWith("[IMG:") && p.endsWith("]")) {
                    speakingParaIdx++
                    if (speakingParaIdx < chapter.paragraphs.size) {
                        tts.speak(chapter.paragraphs[speakingParaIdx], TextToSpeech.QUEUE_FLUSH, null, "lumina_tts_$speakingParaIdx")
                        isTtsSpeaking = true
                    } else {
                        isTtsSpeaking = false
                    }
                } else {
                    tts.speak(p, TextToSpeech.QUEUE_FLUSH, null, "lumina_tts_$speakingParaIdx")
                    isTtsSpeaking = true
                }
            } else {
                isTtsSpeaking = false
            }
        }
    }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = ttsRef.value
                engine?.language = Locale.getDefault()
                engine?.setPitch(1.0f)
                engine?.setSpeechRate(0.95f)
                try {
                    val voices = engine?.voices
                    val naturalVoice = voices?.filter {
                        it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired
                    }?.maxByOrNull { it.quality } ?: voices?.firstOrNull { it.locale.language == Locale.ENGLISH.language }
                    if (naturalVoice != null) {
                        engine?.voice = naturalVoice
                    }
                } catch (_: Exception) {}
                isTtsInitialized.value = true
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isTtsSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                coroutineScope.launch {
                    val chapter = book.chapters.getOrNull(speakingChapterIdx)
                    if (chapter != null && speakingParaIdx + 1 < chapter.paragraphs.size) {
                        speakingParaIdx++
                        speakNextPara.value()
                    } else if (speakingChapterIdx + 1 < book.chapters.size) {
                        speakingChapterIdx++
                        speakingParaIdx = 0
                        speakNextPara.value()
                    } else {
                        isTtsSpeaking = false
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isTtsSpeaking = false
            }
        })
        ttsRef.value = tts

        onDispose {
            tts.stop()
            tts.shutdown()
            assistantService.stopListening()
        }
    }

    val fontFamily = when (typeface) {
        TypefaceMode.SERIF -> FontFamily.Serif
        TypefaceMode.SANS -> FontFamily.SansSerif
        TypefaceMode.MONO -> FontFamily.Monospace
        TypefaceMode.LITERARY -> FontFamily.Cursive
        TypefaceMode.DYSLEXIC -> FontFamily.SansSerif
        TypefaceMode.GEORGIA -> FontFamily.Serif
        TypefaceMode.GARAMOND -> FontFamily.Serif
        TypefaceMode.PALATINO -> FontFamily.Serif
        TypefaceMode.MERRIWEATHER -> FontFamily.Serif
        TypefaceMode.ROUNDED -> FontFamily.SansSerif
    }

    val contentTextAlign = if (textAlignment == TextAlignmentMode.START) TextAlign.Start else TextAlign.Justify

    val isUiVisible = showControls && !isFullscreen

    LaunchedEffect(isUiVisible) {
        onControlsVisibilityChange(isUiVisible)
    }

    // Hardware & Gesture Back Button Handling
    BackHandler(enabled = true) {
        if (isFullscreen) {
            onToggleFullscreen()
        } else {
            ttsRef.value?.stop()
            isTtsSpeaking = false
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

    // Intercept LocalClipboardManager so SelectionManager gives us the exact selected text
    val defaultClipboard = LocalClipboardManager.current
    val customClipboard = remember(defaultClipboard) {
        object : androidx.compose.ui.platform.ClipboardManager {
            override fun getText(): AnnotatedString? = defaultClipboard.getText()
            override fun setText(annotatedString: AnnotatedString) {
                defaultClipboard.setText(annotatedString)
                val str = annotatedString.text.trim()
                if (str.isNotBlank()) {
                    selectedText = str
                    showSelectionMenu = true
                }
            }
            override fun hasText(): Boolean = defaultClipboard.hasText()
        }
    }

    // Custom TextToolbar that delegates to customClipboard
    val defaultToolbar = LocalTextToolbar.current
    val customTextToolbar = remember(defaultToolbar) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = defaultToolbar.status

            override fun hide() {
                defaultToolbar.hide()
            }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                onCopyRequested?.invoke()
            }
        }
    }

    // Prepared pages for Paged Mode with smart character/sentence budgeting so text never overflows
    val pages = remember(book, fontSize) {
        val list = mutableListOf<Pair<String, String>>()
        val maxCharsPerPage = when {
            fontSize <= 14 -> 950
            fontSize <= 16 -> 800
            fontSize <= 18 -> 650
            fontSize <= 20 -> 520
            fontSize <= 22 -> 420
            else -> 340
        }

        book.chapters.forEach { chap ->
            list.add(Pair(chap.title, "TITLE:::${chap.title}:::${chap.subtitle}"))
            val currentBatch = StringBuilder()

            fun flushBatch() {
                val str = currentBatch.toString().trim()
                if (str.isNotEmpty()) {
                    list.add(Pair(chap.title, str))
                    currentBatch.clear()
                }
            }

            for (p in chap.paragraphs) {
                if (p.startsWith("[IMG:") && p.endsWith("]")) {
                    flushBatch()
                    list.add(Pair(chap.title, p))
                    continue
                }

                // If a single paragraph itself is larger than maxCharsPerPage, split into sentence-based pages
                if (p.length > maxCharsPerPage) {
                    flushBatch()
                    val sentences = p.split(Regex("(?<=[.!?])\\s+"))
                    val sentenceBatch = StringBuilder()
                    for (s in sentences) {
                        if (sentenceBatch.isNotEmpty() && sentenceBatch.length + s.length > maxCharsPerPage) {
                            list.add(Pair(chap.title, sentenceBatch.toString().trim()))
                            sentenceBatch.clear()
                        }
                        if (sentenceBatch.isNotEmpty()) sentenceBatch.append(" ")
                        sentenceBatch.append(s)
                    }
                    if (sentenceBatch.isNotEmpty()) {
                        list.add(Pair(chap.title, sentenceBatch.toString().trim()))
                    }
                } else {
                    if (currentBatch.isNotEmpty() && (currentBatch.length + p.length + 2) > maxCharsPerPage) {
                        flushBatch()
                    }
                    if (currentBatch.isNotEmpty()) {
                        currentBatch.append("\n\n")
                    }
                    currentBatch.append(p)
                }
            }
            flushBatch()
        }
        list
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = book.scrollPos)
    val pagerState = rememberPagerState(
        initialPage = book.currentPage.coerceIn(0, maxOf(pages.size - 1, 0)),
        pageCount = { pages.size }
    )

    val startVoiceAssistant: () -> Unit = {
        showControls = false
        onControlsVisibilityChange(false)
        voiceState = AssistantVoiceState.LISTENING
        voiceQuery = ""
        voiceResponse = ""
        assistantService.startListening(
            onReady = { voiceState = AssistantVoiceState.LISTENING },
            onPartialResult = { partial -> voiceQuery = partial },
            onResult = { query ->
                voiceQuery = query
                voiceState = AssistantVoiceState.THINKING
                val localAction = assistantService.parseLocalCommand(query, book.chapters.size)
                if (localAction != null) {
                    when (localAction) {
                        is AssistantAction.NextChapter -> {
                            val next = (speakingChapterIdx + 1).coerceAtMost(book.chapters.size - 1)
                            speakingChapterIdx = next
                            speakingParaIdx = 0
                            val chapTitle = book.chapters.getOrNull(next)?.title ?: "Chapter ${next + 1}"
                            activeChapterTitle = chapTitle
                            voiceResponse = "Moved to $chapTitle"
                            voiceState = AssistantVoiceState.RESPONDING
                            coroutineScope.launch {
                                var idx = 0
                                for (i in 0 until next) {
                                    idx += (book.chapters[i].paragraphs.size + 1)
                                }
                                listState.animateScrollToItem(idx)
                            }
                        }
                        is AssistantAction.PreviousChapter -> {
                            val prev = (speakingChapterIdx - 1).coerceAtLeast(0)
                            speakingChapterIdx = prev
                            speakingParaIdx = 0
                            val chapTitle = book.chapters.getOrNull(prev)?.title ?: "Chapter ${prev + 1}"
                            activeChapterTitle = chapTitle
                            voiceResponse = "Moved to $chapTitle"
                            voiceState = AssistantVoiceState.RESPONDING
                            coroutineScope.launch {
                                var idx = 0
                                for (i in 0 until prev) {
                                    idx += (book.chapters[i].paragraphs.size + 1)
                                }
                                listState.animateScrollToItem(idx)
                            }
                        }
                        is AssistantAction.NavigateChapter -> {
                            val target = localAction.targetIndex
                            speakingChapterIdx = target
                            speakingParaIdx = 0
                            val chapTitle = book.chapters.getOrNull(target)?.title ?: "Chapter ${target + 1}"
                            activeChapterTitle = chapTitle
                            voiceResponse = "Jumped to $chapTitle"
                            voiceState = AssistantVoiceState.RESPONDING
                            coroutineScope.launch {
                                var idx = 0
                                for (i in 0 until target) {
                                    idx += (book.chapters[i].paragraphs.size + 1)
                                }
                                listState.animateScrollToItem(idx)
                            }
                        }
                        is AssistantAction.ToggleTts -> {
                            if (localAction.play) {
                                speakNextPara.value()
                                voiceResponse = "Started reading aloud."
                            } else {
                                ttsRef.value?.stop()
                                isTtsSpeaking = false
                                voiceResponse = "Paused reading."
                            }
                            voiceState = AssistantVoiceState.RESPONDING
                        }
                        is AssistantAction.AddNote -> {
                            onAddBookmark(localAction.noteContent, HighlightColor.GOLD)
                            voiceResponse = "Note saved: \"${localAction.noteContent}\""
                            voiceState = AssistantVoiceState.RESPONDING
                        }
                        else -> {
                            voiceResponse = "Command processed."
                            voiceState = AssistantVoiceState.RESPONDING
                        }
                    }
                } else {
                    coroutineScope.launch {
                        val knownContext = buildString {
                            for (c in 0..speakingChapterIdx) {
                                val ch = book.chapters.getOrNull(c) ?: continue
                                appendLine("--- ${ch.title} ---")
                                appendLine(ch.paragraphs.filterNot { it.startsWith("[IMG:") }.joinToString(" "))
                            }
                        }
                        val ans = assistantService.queryAssistant(
                            provider = aiProvider,
                            apiKey = geminiApiKey,
                            baseUrl = aiBaseUrl,
                            modelName = aiModel,
                            bookTitle = book.title,
                            activeChapterTitle = activeChapterTitle,
                            knownContext = knownContext,
                            userQuery = query
                        )
                        voiceResponse = ans
                        voiceState = AssistantVoiceState.RESPONDING
                    }
                }
            },
            onError = { err ->
                voiceResponse = err
                voiceState = AssistantVoiceState.RESPONDING
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceAssistant()
        } else {
            Toast.makeText(context, "Microphone permission required for voice assistant", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(nestedScrollConnection)
    ) {
        // Optional Custom Background Image
        if (customBgUri.isNotBlank()) {
            AsyncImageBitmap(
                url = customBgUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.2f)
            )
        }

        // Optional Surface Texture Overlay (Grain, Parchment, Linen Canvas)
        when (backgroundTexture) {
            BackgroundTexture.GRAIN -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val onBg = Color.DarkGray.copy(alpha = 0.025f)
                    var x = 4f
                    while (x < size.width) {
                        var y = 4f
                        while (y < size.height) {
                            val dotAlpha = (((x.toInt() * 31 + y.toInt() * 17) % 100) / 100f) * 0.035f
                            drawCircle(
                                color = onBg.copy(alpha = dotAlpha),
                                radius = 0.8f,
                                center = Offset(x, y)
                            )
                            y += 12f
                        }
                        x += 12f
                    }
                }
            }
            BackgroundTexture.PARCHMENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFE8DCC4).copy(alpha = 0.12f),
                                    Color.Transparent,
                                    Color(0xFFDECBB0).copy(alpha = 0.16f)
                                )
                            )
                        )
                )
            }
            BackgroundTexture.LINEN -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineColor = Color.Gray.copy(alpha = 0.035f)
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = lineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 0.75f
                        )
                        x += 16f
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.75f
                        )
                        y += 16f
                    }
                }
            }
            BackgroundTexture.NONE -> {}
        }

        val progressBottomInset = max(
            max(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                WindowInsets.mandatorySystemGestures.asPaddingValues().calculateBottomPadding()
            ),
            16.dp
        )

        CompositionLocalProvider(
            LocalTextToolbar provides customTextToolbar,
            LocalClipboardManager provides customClipboard,
            LocalTextSelectionColors provides customSelectionColors
        ) {
            SelectionContainer {
                if (readingMode == ReadingMode.SCROLL) {
                    // Continuous Vertical Scroll Mode across all chapters
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
                        speakingChapterIdx = currentChap
                        onPositionChange(currentChap, 0, listState.firstVisibleItemIndex, overallProgress)
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = if (isUiVisible) 76.dp else 24.dp,
                            bottom = if (isUiVisible) 80.dp else 36.dp,
                            start = 22.dp,
                            end = 22.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        book.chapters.forEachIndexed { chapIdx, chapter ->
                            item(key = "chap-header-$chapIdx") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (chapIdx == 0) 8.dp else 40.dp, bottom = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
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
                                    Text(
                                        text = chapter.title,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (chapter.subtitle.isNotBlank() && !chapter.subtitle.startsWith("Part", ignoreCase = true)) {
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
                                val matchingBookmarks = remember(para, bookmarks) {
                                    bookmarks.filter { b ->
                                        val q = b.quote.trim()
                                        q.isNotBlank() && para.contains(q, ignoreCase = true)
                                    }
                                }
                                val isBeingSpoken = isTtsSpeaking && speakingChapterIdx == chapIdx && speakingParaIdx == pIdx

                                if (para.startsWith("[IMG:") && para.endsWith("]")) {
                                    // Inline illustration with safe image loading and placeholder
                                    val imgPath = para.removePrefix("[IMG:").removeSuffix("]")
                                    val bitmap = rememberBookImage(imgPath)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp)
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (showSelectionMenu) showSelectionMenu = false else showControls = !showControls
                                            },
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
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier
                                                    .fillMaxWidth(0.92f)
                                                    .height(160.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "Illustration",
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Text Paragraph — Highlights applied strictly to text spans, no block background
                                    val isDropCap = chapIdx == 0 && pIdx == 0 && para.length > 40 && !para.startsWith("[IMG:")
                                    val onBgColor = MaterialTheme.colorScheme.onBackground
                                    val secColor = MaterialTheme.colorScheme.secondary
                                    val annotatedText = remember(para, matchingBookmarks, isDropCap, fontSize, fontFamily, onBgColor) {
                                        buildHighlightedAnnotatedString(
                                            text = para,
                                            matchingBookmarks = matchingBookmarks,
                                            onBookmarkClick = { bm ->
                                                selectedBookmarkForModal = bm
                                                showBookmarkDetailModal = true
                                            },
                                            isDropCap = isDropCap,
                                            dropCapFontFamily = FontFamily.Serif,
                                            dropCapFontSize = (fontSize * 2.2f).sp,
                                            dropCapColor = secColor,
                                            baseFontFamily = fontFamily,
                                            baseFontSize = fontSize.sp,
                                            baseTextColor = onBgColor
                                        )
                                    }

                                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isBeingSpoken) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else Color.Transparent)
                                            .padding(vertical = 4.dp)
                                            .pointerInput(para, matchingBookmarks) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = false)
                                                    val up = waitForUpOrCancellation(pass = PointerEventPass.Main)
                                                    if (up != null) {
                                                        val layout = textLayoutResult
                                                        var hitBookmark: Bookmark? = null
                                                        if (layout != null && matchingBookmarks.isNotEmpty()) {
                                                            val offset = layout.getOffsetForPosition(up.position)
                                                            hitBookmark = matchingBookmarks.firstOrNull { bm ->
                                                                val quote = bm.quote.trim()
                                                                if (quote.isEmpty()) return@firstOrNull false
                                                                var sIdx = 0
                                                                while (sIdx < para.length) {
                                                                    val s = para.indexOf(quote, sIdx, ignoreCase = true)
                                                                    if (s == -1) break
                                                                    val e = (s + quote.length).coerceAtMost(para.length)
                                                                    if (offset in s until e) return@firstOrNull true
                                                                    sIdx = e
                                                                }
                                                                false
                                                            }
                                                        }
                                                        if (hitBookmark != null) {
                                                            selectedBookmarkForModal = hitBookmark
                                                            showBookmarkDetailModal = true
                                                        } else {
                                                            if (showSelectionMenu) {
                                                                showSelectionMenu = false
                                                            } else {
                                                                showControls = !showControls
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = annotatedText,
                                            onTextLayout = { textLayoutResult = it },
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
                } else {
                    // Paged Mode
                    LaunchedEffect(pagerState.currentPage) {
                        val progress = if (pages.isNotEmpty()) {
                            (((pagerState.currentPage + 1).toFloat() / pages.size) * 100).toInt().coerceIn(0, 100)
                        } else 0
                        val currentPageChapTitle = pages.getOrNull(pagerState.currentPage)?.first ?: "Chapter 1"
                        activeChapterTitle = currentPageChapTitle
                        val chapIdx = book.chapters.indexOfFirst { it.title == currentPageChapTitle }.coerceAtLeast(0)
                        speakingChapterIdx = chapIdx
                        onPositionChange(chapIdx, pagerState.currentPage, 0, progress)
                    }

                    val pagedBottomPadding = if (isUiVisible) {
                        val dockHeight = if (showNavBarInReader) 94.dp else 44.dp
                        progressBottomInset + 10.dp + dockHeight + 8.dp
                    } else {
                        progressBottomInset + 20.dp
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (isUiVisible) 80.dp else 24.dp,
                                    bottom = pagedBottomPadding,
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
                                        fontWeight = FontWeight.Medium,
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
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            modifier = Modifier
                                                .fillMaxWidth(0.92f)
                                                .height(200.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "Illustration",
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val matchingBookmarks = remember(content, bookmarks) {
                                    bookmarks.filter { b ->
                                        val q = b.quote.trim()
                                        q.isNotBlank() && content.contains(q, ignoreCase = true)
                                    }
                                }
                                val onBgColor = MaterialTheme.colorScheme.onBackground
                                val annotatedContent = remember(content, matchingBookmarks, fontSize, fontFamily, onBgColor) {
                                    buildHighlightedAnnotatedString(
                                        text = content,
                                        matchingBookmarks = matchingBookmarks,
                                        onBookmarkClick = { bm ->
                                            selectedBookmarkForModal = bm
                                            showBookmarkDetailModal = true
                                        },
                                        isDropCap = false,
                                        baseFontFamily = fontFamily,
                                        baseFontSize = fontSize.sp,
                                        baseTextColor = onBgColor
                                    )
                                }

                                var pagedTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .clip(RoundedCornerShape(8.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .pointerInput(content, matchingBookmarks) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = false)
                                                val up = waitForUpOrCancellation(pass = PointerEventPass.Main)
                                                if (up != null) {
                                                    val layout = pagedTextLayoutResult
                                                    var hitBookmark: Bookmark? = null
                                                    if (layout != null && matchingBookmarks.isNotEmpty()) {
                                                        val offset = layout.getOffsetForPosition(up.position)
                                                        hitBookmark = matchingBookmarks.firstOrNull { bm ->
                                                            val quote = bm.quote.trim()
                                                            if (quote.isEmpty()) return@firstOrNull false
                                                            var sIdx = 0
                                                            while (sIdx < content.length) {
                                                                val s = content.indexOf(quote, sIdx, ignoreCase = true)
                                                                if (s == -1) break
                                                                val e = (s + quote.length).coerceAtMost(content.length)
                                                                if (offset in s until e) return@firstOrNull true
                                                                sIdx = e
                                                            }
                                                            false
                                                        }
                                                    }
                                                    if (hitBookmark != null) {
                                                        selectedBookmarkForModal = hitBookmark
                                                        showBookmarkDetailModal = true
                                                    } else {
                                                        if (showSelectionMenu) {
                                                            showSelectionMenu = false
                                                        } else {
                                                            showControls = !showControls
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Text(
                                        text = annotatedContent,
                                        onTextLayout = { pagedTextLayoutResult = it },
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

        // TOP HEADER BAR: Distraction-Free Header (Zero buttons, clean title & chapter)
        AnimatedVisibility(
            visible = isUiVisible && voiceState == AssistantVoiceState.IDLE,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTocSheet = true },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = book.title,
                                fontFamily = FontFamily.Serif,
                                fontSize = 14.sp,
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
                    }

                    // Delicate hairline divider underneath the header bar
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                }
            }
        }





        // Highlight & Note Inspector / Editor Sheet
        if (showBookmarkDetailModal && selectedBookmarkForModal != null) {
            val bm = selectedBookmarkForModal!!
            var noteDraft by rememberSaveable(bm.id) { mutableStateOf(bm.note) }
            var currentHighlightColor by rememberSaveable(bm.id) { mutableStateOf(bm.color) }

            ModalBottomSheet(
                onDismissRequest = { showBookmarkDetailModal = false },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Highlight & Note",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { showBookmarkDetailModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val markColor = when (currentHighlightColor) {
                        HighlightColor.GOLD -> Color(0xFFD4AF37)
                        HighlightColor.ROSE -> Color(0xFFE5B7B7)
                        HighlightColor.SAGE -> Color(0xFFB2C2B2)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(48.dp)
                                    .background(markColor, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "“${bm.quote}”",
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${bm.chapter} • ${bm.timestamp}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Highlight Color",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(HighlightColor.GOLD, Color(0xFFD4AF37), "Gold"),
                            Triple(HighlightColor.ROSE, Color(0xFFE5B7B7), "Rose"),
                            Triple(HighlightColor.SAGE, Color(0xFFB2C2B2), "Sage")
                        ).forEach { (colorKey, colorVal, _) ->
                            val isSelected = currentHighlightColor == colorKey
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        currentHighlightColor = colorKey
                                        val updated = bm.copy(color = colorKey, note = noteDraft)
                                        selectedBookmarkForModal = updated
                                        onUpdateBookmark(updated)
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Personal Note",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        placeholder = { Text("Write a note, thought, or reflection...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onRemoveBookmark(bm.id)
                                showBookmarkDetailModal = false
                                Toast.makeText(context, "Highlight removed", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }

                        Button(
                            onClick = {
                                val updated = bm.copy(color = currentHighlightColor, note = noteDraft.trim())
                                selectedBookmarkForModal = updated
                                onUpdateBookmark(updated)
                                showBookmarkDetailModal = false
                                Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save Note")
                        }
                    }
                }
            }
        }

        // Floating Assistant Orb (movable, long-press voice assistant, smart inward arc, drag-to-delete bin)
        if (showFloatingAssistant && !isFullscreen) {
            FloatingAssistantOrb(
                readingMode = readingMode,
                onToggleReadingMode = {
                    val nextMode = if (readingMode == ReadingMode.SCROLL) ReadingMode.PAGED else ReadingMode.SCROLL
                    onModeChange(nextMode)
                },
                isTtsPlaying = isTtsSpeaking,
                onToggleTts = {
                    if (isTtsSpeaking) {
                        ttsRef.value?.stop()
                        isTtsSpeaking = false
                    } else {
                        speakNextPara.value()
                    }
                },
                onOpenToc = { showTocSheet = true },
                onOpenNote = onOpenBookmarks,
                onOpenSettings = onOpenAppearance,
                onStartVoiceListening = {
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        startVoiceAssistant()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onDismissOrb = {
                    onToggleFloatingAssistant(false)
                    Toast.makeText(context, "Assistant dismissed. Re-enable anytime from Settings.", Toast.LENGTH_SHORT).show()
                },
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                orbActions = activeOrbActions,
                voiceState = voiceState,
                voiceQuery = voiceQuery,
                voiceResponse = voiceResponse,
                onDismissVoiceDialog = {
                    voiceState = AssistantVoiceState.IDLE
                    assistantService.stopListening()
                },
                onOpenAdvancedSettings = onOpenAdvancedSettings
            )
        }

        // UNIFIED FLOATING BOTTOM DOCK: Progress Bar Extension + Navigation Bar with matching width (380.dp)
        AnimatedVisibility(
            visible = isUiVisible && voiceState == AssistantVoiceState.IDLE,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = progressBottomInset + 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Progress Bar Extension: Clickable to expand/collapse the nav row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleNavBar() }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${book.progress}%",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { (book.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (book.readTimeLeft.isNotBlank()) book.readTimeLeft else activeChapterTitle,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Expandable Navigation Row directly attached below progress bar
                    AnimatedVisibility(
                        visible = showNavBarInReader,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Library
                                IconButton(onClick = onBackToLibrary) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = "Library",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // 2. Table of Contents
                                IconButton(onClick = { showTocSheet = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = "Contents",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // 3. Highlights & Bookmarks
                                IconButton(onClick = onOpenBookmarks) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmarks,
                                        contentDescription = "Highlights",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // 4. Appearance & Style
                                IconButton(onClick = onOpenAppearance) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Appearance",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection Menu Pill (Rendered strictly on top of bottom dock, zIndex = 200f)
        val dockHeight = if (showNavBarInReader) 94.dp else 44.dp
        val selectionMenuBottomPadding = if (isUiVisible) {
            progressBottomInset + 10.dp + dockHeight + 14.dp
        } else {
            progressBottomInset + 20.dp
        }

        AnimatedVisibility(
            visible = showSelectionMenu && selectedText.isNotBlank(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(200f)
                .padding(bottom = selectionMenuBottomPadding, start = 14.dp, end = 14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
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
                        // Note button: directly inspect or attach note
                        IconButton(
                            onClick = {
                                showSelectionMenu = false
                                val existing = activeBookmark
                                if (existing != null) {
                                    selectedBookmarkForModal = existing
                                } else {
                                    val mark = Bookmark(
                                        bookTitle = book.title,
                                        chapter = selectedChapterTitle.ifBlank { activeChapterTitle },
                                        quote = selectedText,
                                        color = HighlightColor.GOLD,
                                        timestamp = "Just now"
                                    )
                                    onAddBookmark(selectedText, HighlightColor.GOLD)
                                    selectedBookmarkForModal = mark
                                }
                                showBookmarkDetailModal = true
                            }
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = "Add Note / Inspect", tint = MaterialTheme.colorScheme.secondary)
                        }

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
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Selected text", selectedText))
                                showSelectionMenu = false
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text")
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

        // Table of Contents Sheet
        if (showTocSheet) {
            TableOfContentsSheet(
                chapters = book.chapters,
                bookTitle = book.title,
                currentChapterIndex = book.chapters.indexOfFirst { it.title == activeChapterTitle }.coerceAtLeast(0),
                onSelectChapter = { idx ->
                    activeChapterTitle = book.chapters.getOrNull(idx)?.title ?: "Chapter ${idx + 1}"
                    speakingChapterIdx = idx
                    speakingParaIdx = 0
                    if (readingMode == ReadingMode.SCROLL) {
                        var targetIdx = 0
                        for (c in 0 until idx) {
                            targetIdx += (book.chapters[c].paragraphs.size + 1)
                        }
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetIdx)
                        }
                    } else {
                        val targetPage = pages.indexOfFirst { it.first == activeChapterTitle }.coerceAtLeast(0)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                    showTocSheet = false
                },
                onDismiss = { showTocSheet = false }
            )
        }
    }
}
