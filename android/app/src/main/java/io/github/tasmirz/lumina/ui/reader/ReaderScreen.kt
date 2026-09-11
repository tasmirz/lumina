package io.github.tasmirz.lumina.ui.reader

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import io.github.tasmirz.lumina.data.EdgeTtsService
import io.github.tasmirz.lumina.data.LuminaAudioService
import io.github.tasmirz.lumina.model.Chapter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import io.github.tasmirz.lumina.model.GestureAction
import io.github.tasmirz.lumina.model.SceneMatch
import io.github.tasmirz.lumina.model.OrbSize
import io.github.tasmirz.lumina.model.OrbMenuSize
import io.github.tasmirz.lumina.model.OrbColor
import io.github.tasmirz.lumina.ui.components.CharacterGuideSheet
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FormatSize
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
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.data.AssistantAction
import io.github.tasmirz.lumina.data.AssistantResponse
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import io.github.tasmirz.lumina.data.AiProvider
import io.github.tasmirz.lumina.data.AssistantService
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.HighlightColor
import io.github.tasmirz.lumina.model.OrbActionItem
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.model.ThemeVariant
import io.github.tasmirz.lumina.model.ReadingMode
import io.github.tasmirz.lumina.model.TextAlignmentMode
import io.github.tasmirz.lumina.model.TypefaceMode
import io.github.tasmirz.lumina.ui.components.AssistantChatSheet
import io.github.tasmirz.lumina.ui.components.AssistantVoiceState
import io.github.tasmirz.lumina.ui.components.AsyncImageBitmap
import io.github.tasmirz.lumina.ui.components.FloatingAssistantOrb
import io.github.tasmirz.lumina.ui.components.TableOfContentsSheet
import io.github.tasmirz.lumina.ui.components.rememberBookImage
import io.github.tasmirz.lumina.util.CitationHelper
import io.github.tasmirz.lumina.util.PageCache
import io.github.tasmirz.lumina.util.AnnotatedTextCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.launch
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

    // 1. Try finding page by snippet within the target chapter (multiple lengths)
    if (cleanedSnippet.isNotBlank()) {
        for (len in listOf(40, 30, 20, 14, 10)) {
            val snip = cleanedSnippet.take(len).trim()
            if (snip.length >= 8) {
                val found = pages.indexOfFirst { (title, content) ->
                    (chapterTitle.isBlank() || title.equals(chapterTitle, ignoreCase = true)) &&
                        clean(content).contains(snip)
                }
                if (found >= 0) return found
            }
        }
        for (len in listOf(40, 30, 20, 14, 10)) {
            val snip = alphaSnippet.take(len).trim()
            if (snip.length >= 8) {
                val found = pages.indexOfFirst { (title, content) ->
                    (chapterTitle.isBlank() || title.equals(chapterTitle, ignoreCase = true)) &&
                        cleanAlpha(content).contains(snip)
                }
                if (found >= 0) return found
            }
        }

        // Global search across all pages by snippet (in case chapter title naming slightly differs)
        for (len in listOf(30, 20, 14, 10)) {
            val snip = cleanedSnippet.take(len).trim()
            if (snip.length >= 8) {
                val foundGlobal = pages.indexOfFirst { clean(it.second).contains(snip) }
                if (foundGlobal >= 0) return foundGlobal
            }
        }
        for (len in listOf(30, 20, 14, 10)) {
            val snip = alphaSnippet.take(len).trim()
            if (snip.length >= 8) {
                val foundGlobal = pages.indexOfFirst { cleanAlpha(it.second).contains(snip) }
                if (foundGlobal >= 0) return foundGlobal
            }
        }
    }

    // 2. Try finding page by paragraph text
    val rawPara = targetChapter?.paragraphs?.getOrNull(paraIdx)?.trim() ?: ""
    val cleanedPara = clean(rawPara)
    val alphaPara = cleanAlpha(rawPara)
    if (cleanedPara.isNotBlank()) {
        for (len in listOf(35, 24, 14, 8)) {
            val paraSnip = cleanedPara.take(len).trim()
            if (paraSnip.length >= 6) {
                val foundByPara = pages.indexOfFirst { (title, content) ->
                    (chapterTitle.isBlank() || title.equals(chapterTitle, ignoreCase = true)) &&
                        clean(content).contains(paraSnip)
                }
                if (foundByPara >= 0) return foundByPara
            }
        }
        for (len in listOf(35, 24, 14, 8)) {
            val paraSnip = alphaPara.take(len).trim()
            if (paraSnip.length >= 6) {
                val foundByPara = pages.indexOfFirst { (title, content) ->
                    (chapterTitle.isBlank() || title.equals(chapterTitle, ignoreCase = true)) &&
                        cleanAlpha(content).contains(paraSnip)
                }
                if (foundByPara >= 0) return foundByPara
            }
        }
        // Global search by paragraph
        for (len in listOf(24, 14, 8)) {
            val paraSnip = cleanedPara.take(len).trim()
            if (paraSnip.length >= 6) {
                val foundGlobalPara = pages.indexOfFirst { clean(it.second).contains(paraSnip) }
                if (foundGlobalPara >= 0) return foundGlobalPara
            }
        }
        for (len in listOf(24, 14, 8)) {
            val paraSnip = alphaPara.take(len).trim()
            if (paraSnip.length >= 6) {
                val foundGlobalPara = pages.indexOfFirst { cleanAlpha(it.second).contains(paraSnip) }
                if (foundGlobalPara >= 0) return foundGlobalPara
            }
        }
    }

    // 3. If it's the very start of chapter, return first page of chapter
    if (paraIdx == 0 && chapterTitle.isNotBlank()) {
        val firstPage = pages.indexOfFirst { it.first.equals(chapterTitle, ignoreCase = true) }
        if (firstPage >= 0) return firstPage
    }

    // 4. Estimate page within the chapter proportionally
    val firstPageOfChap = pages.indexOfFirst { it.first.equals(chapterTitle, ignoreCase = true) }
    if (firstPageOfChap >= 0) {
        val lastPageOfChap = pages.indexOfLast { it.first.equals(chapterTitle, ignoreCase = true) }
        val chapPageCount = lastPageOfChap - firstPageOfChap + 1
        val totalParasInChap = maxOf(targetChapter?.paragraphs?.size ?: 1, 1)
        val estimatedOffset = ((paraIdx.toFloat() / totalParasInChap) * chapPageCount).toInt()
        return (firstPageOfChap + estimatedOffset).coerceIn(firstPageOfChap, lastPageOfChap)
    }

    return 0
}

private fun findScrollIndexForLocation(
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

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
    onAddBookmark: (String, HighlightColor, Int, String) -> Unit = { _, _, _, _ -> },
    onRemoveBookmark: (Long) -> Unit,
    onUpdateBookmark: (Bookmark) -> Unit = {},
    onLookupWord: (String) -> Unit,
    onOpenAppearance: () -> Unit = {},
    onOpenAdvancedSettings: () -> Unit = onOpenAppearance,
    onOpenBookmarks: () -> Unit = {},
    showFloatingAssistant: Boolean = true,
    onToggleFloatingAssistant: (Boolean) -> Unit = {},
    activeOrbActions: Set<OrbActionItem> = OrbActionItem.entries.toSet(),
    backgroundTexture: BackgroundTexture = BackgroundTexture.NONE,
    customBgUri: String = "",
    textAlignment: TextAlignmentMode = TextAlignmentMode.JUSTIFY,
    letterSpacing: Float = 0.2f,
    geminiApiKey: String = "",
    aiProvider: AiProvider = AiProvider.GEMINI,
    aiBaseUrl: String = "https://api.openai.com/v1",
    aiModel: String = "",
    repository: BookRepository? = null,
    onThemeFamilyChange: (ThemeFamily) -> Unit = {},
    onThemeVariantChange: (ThemeVariant) -> Unit = {},
    onFontSizeChange: (Int) -> Unit = { repository?.setFontSize(it) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Distraction-Free Reading: Hide the notification/status bar in reading mode
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            val window = context.findActivity()?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // Settings collected from repository
    val horizontalPaddingState = repository?.horizontalPadding?.collectAsState(initial = 22)
    val horizontalPadding = horizontalPaddingState?.value ?: 22
    val verticalPaddingState = repository?.verticalPadding?.collectAsState(initial = 0)
    val verticalPadding = verticalPaddingState?.value ?: 0
    val paragraphSpacingState = repository?.paragraphSpacingMultiplier?.collectAsState(initial = 1.2f)
    val paragraphSpacingMultiplier = paragraphSpacingState?.value ?: 1.2f
    val assistantOrbStyleState = repository?.assistantOrbStyle?.collectAsState(initial = "DOCK_DOT")
    val assistantOrbStyle = assistantOrbStyleState?.value ?: "DOCK_DOT"
    val spoilerShieldState = repository?.spoilerShield?.collectAsState(initial = true)
    val spoilerShield = spoilerShieldState?.value ?: true
    val autoScrollSpeedState = repository?.autoScrollSpeed?.collectAsState(initial = 1.0f)
    val autoScrollSpeed: Float = autoScrollSpeedState?.value ?: 1.0f
    val orbSizeState = repository?.orbSize?.collectAsState(initial = OrbSize.NANO)
    val orbSize = orbSizeState?.value ?: OrbSize.NANO
    val orbMenuSizeState = repository?.orbMenuSize?.collectAsState(initial = OrbMenuSize.MEDIUM)
    val orbMenuSize = orbMenuSizeState?.value ?: OrbMenuSize.MEDIUM
    val orbEdgeSnapState = repository?.orbEdgeSnap?.collectAsState(initial = true)
    val orbEdgeSnap = orbEdgeSnapState?.value ?: true
    val orbColorState = repository?.orbColor?.collectAsState(initial = OrbColor.THEME)
    val orbColor = orbColorState?.value ?: OrbColor.THEME
    val orbOpacityState = repository?.orbOpacity?.collectAsState(initial = 0.85f)
    val orbOpacity = orbOpacityState?.value ?: 0.85f
    val autoStartMicState = repository?.autoStartMic?.collectAsState(initial = true)
    val autoStartMic = autoStartMicState?.value ?: true

    // Persistent portrait and landscape dock positions
    val orbPortraitXState = repository?.orbPortraitX?.collectAsState(initial = -1f)
    val orbPortraitX = orbPortraitXState?.value ?: -1f
    val orbPortraitYState = repository?.orbPortraitY?.collectAsState(initial = -1f)
    val orbPortraitY = orbPortraitYState?.value ?: -1f
    val orbLandscapeXState = repository?.orbLandscapeX?.collectAsState(initial = -1f)
    val orbLandscapeX = orbLandscapeXState?.value ?: -1f
    val orbLandscapeYState = repository?.orbLandscapeY?.collectAsState(initial = -1f)
    val orbLandscapeY = orbLandscapeYState?.value ?: -1f

    val themeVariantState = repository?.themeVariant?.collectAsState(initial = ThemeVariant.LIGHT)
    val currentThemeVariant = themeVariantState?.value ?: ThemeVariant.LIGHT
    val bookmarksByChapter = remember(bookmarks) {
        bookmarks.groupBy { it.chapter.trim().lowercase() }
    }

    var showControls by rememberSaveable { mutableStateOf(true) }
    var selectedText by remember { mutableStateOf("") }
    var selectedChapterTitle by rememberSaveable { mutableStateOf("") }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var selectedBookmarkForModal by remember { mutableStateOf<Bookmark?>(null) }
    var showBookmarkDetailModal by remember { mutableStateOf(false) }
    var showTocSheet by rememberSaveable { mutableStateOf(false) }
    var activeChapterTitle by rememberSaveable { mutableStateOf(book.chapters.firstOrNull()?.title ?: "Chapter 1") }
    var showInBookSearchDialog by rememberSaveable { mutableStateOf(false) }
    var inBookSearchMode by rememberSaveable { mutableStateOf(InBookSearchMode.PLAIN) }
    var inBookSearchQuery by rememberSaveable { mutableStateOf("") }
    var inBookSearchResults by remember { mutableStateOf<List<SceneMatch>>(emptyList()) }
    var inBookCurrentMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    var isSearchingInBook by remember { mutableStateOf(false) }

    var transientFontBadge by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(transientFontBadge) {
        if (transientFontBadge != null) {
            delay(1500)
            transientFontBadge = null
        }
    }

    var showReadTillFeedback by remember { mutableStateOf(false) }
    var readTillFeedbackText by remember { mutableStateOf("") }
    LaunchedEffect(showReadTillFeedback) {
        if (showReadTillFeedback) {
            delay(1800)
            showReadTillFeedback = false
        }
    }
    var isLongPressingProgress by remember { mutableStateOf(false) }
    val progressHoldAnim = remember { Animatable(0f) }
    var isScrubbingProgress by remember { mutableStateOf(false) }
    var scrubProgressPct by remember { mutableFloatStateOf(book.progress.toFloat()) }
    var currentProgressPct by rememberSaveable(book.id) { mutableIntStateOf(book.progress) }
    val readTillMapState = repository?.readTillMap?.collectAsState(initial = emptyMap())

    // TTS Reader states
    var showTtsDock by rememberSaveable { mutableStateOf(false) }
    var ttsSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var speakingChapterIdx by rememberSaveable(book.id) { mutableIntStateOf(book.currentChapter) }
    var speakingParaIdx by rememberSaveable(book.id) { mutableIntStateOf(0) }

    // Auto-scroll state
    var isAutoScrolling by remember { mutableStateOf(false) }

    // Repository states
    val disableAiState = repository?.disableAi?.collectAsState(initial = false)
    val disableAi = disableAiState?.value ?: false

    val disableTtsState = repository?.disableTts?.collectAsState(initial = false)
    val disableTts = disableTtsState?.value ?: false

    val ttsEngineState = repository?.ttsEngine?.collectAsState(initial = "EDGE_NEURAL")
    val ttsEngine = ttsEngineState?.value ?: "EDGE_NEURAL"

    val ttsEdgeVoiceState = repository?.ttsEdgeVoice?.collectAsState(initial = "en-US-JennyNeural")
    val ttsEdgeVoice = ttsEdgeVoiceState?.value ?: "en-US-JennyNeural"

    val disableSttState = repository?.disableStt?.collectAsState(initial = false)
    val disableStt = disableSttState?.value ?: false

    var showAssistantChatSheet by rememberSaveable { mutableStateOf(false) }
    var assistantAutoStartVoice by rememberSaveable { mutableStateOf(false) }

    // Floating Voice Assistant State & Service
    val assistantService = remember { AssistantService(context) }
    var voiceState by remember { mutableStateOf(AssistantVoiceState.IDLE) }
    var voiceQuery by remember { mutableStateOf("") }
    var voiceResponse by remember { mutableStateOf("") }

    // Audio Playback Service state & controls
    val audioPlaybackState by LuminaAudioService.playbackState.collectAsState()
    val isTtsSpeaking = audioPlaybackState.isPlaying && audioPlaybackState.bookId == book.id

    LaunchedEffect(audioPlaybackState) {
        if (audioPlaybackState.bookId == book.id) {
            speakingChapterIdx = audioPlaybackState.chapterIndex
            speakingParaIdx = audioPlaybackState.paragraphIndex
            if (audioPlaybackState.isPlaying) {
                showTtsDock = true
            }
        }
    }

    fun stopAllAudio() {
        LuminaAudioService.stop(context)
    }

    fun triggerSpeakNextPara() {
        if (disableTts) {
            Toast.makeText(context, "TTS Audio Engine is disabled in Settings", Toast.LENGTH_SHORT).show()
            stopAllAudio()
            return
        }
        val chapter = book.chapters.getOrNull(speakingChapterIdx)
        if (chapter == null || speakingParaIdx >= chapter.paragraphs.size) {
            return
        }
        LuminaAudioService.startOrUpdate(
            context = context,
            bookId = book.id,
            bookTitle = book.title,
            chapterIndex = speakingChapterIdx,
            chapterTitle = chapter.title,
            paragraphIndex = speakingParaIdx,
            paragraphs = chapter.paragraphs,
            isEdgeTts = ttsEngine == "EDGE_NEURAL",
            voice = ttsEdgeVoice,
            speed = ttsSpeed
        )
    }

    val speakNextPara = rememberUpdatedState { triggerSpeakNextPara() }

    LaunchedEffect(showTtsDock) {
        if (!showTtsDock && isTtsSpeaking) {
            stopAllAudio()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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
        if (showInBookSearchDialog) {
            showInBookSearchDialog = false
            inBookSearchQuery = ""
            inBookSearchResults = emptyList()
            inBookCurrentMatchIndex = 0
        } else if (isFullscreen) {
            onToggleFullscreen()
        } else if (showTtsDock) {
            showTtsDock = false
            stopAllAudio()
        } else {
            stopAllAudio()
            onBackToLibrary()
        }
    }

    // Auto-hide UI on scroll down, reveal on scroll up
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -50f && showControls) {
                        showControls = false
                    } else if (available.y > 50f && !showControls) {
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

    // Intercept LocalClipboardManager and LocalClipboard so SelectionManager gives us the exact selected text
    val defaultClipboardManager = LocalClipboardManager.current
    var cachedClipboardText by remember { mutableStateOf<AnnotatedString?>(null) }
    val customClipboardManager = remember(defaultClipboardManager) {
        object : androidx.compose.ui.platform.ClipboardManager {
            override fun getText(): AnnotatedString? = cachedClipboardText ?: defaultClipboardManager.getText()
            override fun setText(annotatedString: AnnotatedString) {
                cachedClipboardText = annotatedString
                val str = annotatedString.text.trim()
                if (str.isNotBlank()) {
                    selectedText = str
                    selectedChapterTitle = activeChapterTitle
                    showSelectionMenu = true
                }
            }
            override fun hasText(): Boolean = cachedClipboardText != null || defaultClipboardManager.hasText()
        }
    }

    val defaultClipboard = LocalClipboard.current
    val customClipboard = remember(defaultClipboard) {
        object : androidx.compose.ui.platform.Clipboard {
            override val nativeClipboard: android.content.ClipboardManager
                get() = defaultClipboard.nativeClipboard

            override suspend fun getClipEntry(): androidx.compose.ui.platform.ClipEntry? {
                return cachedClipboardText?.let {
                    androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("text", it.text))
                } ?: defaultClipboard.getClipEntry()
            }

            override suspend fun setClipEntry(clipEntry: androidx.compose.ui.platform.ClipEntry?) {
                val clipData = clipEntry?.clipData
                val str = if (clipData != null && clipData.itemCount > 0) {
                    clipData.getItemAt(0)?.text?.toString()?.trim()
                } else null
                if (!str.isNullOrBlank()) {
                    cachedClipboardText = AnnotatedString(str)
                    selectedText = str
                    selectedChapterTitle = activeChapterTitle
                    showSelectionMenu = true
                }
                defaultClipboard.setClipEntry(clipEntry)
            }
        }
    }

    // Custom TextToolbar that delegates to customClipboard
    val defaultToolbar = LocalTextToolbar.current
    var suppressToolbarHide by remember { mutableStateOf(false) }
    var lastSelectionTimestamp by remember { mutableLongStateOf(0L) }
    var activeReleaseSelectionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val customTextToolbar = remember(defaultToolbar) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = if (showSelectionMenu) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            override fun hide() {
                if (System.currentTimeMillis() - lastSelectionTimestamp < 300L) return
                if (!suppressToolbarHide) {
                    showSelectionMenu = false
                    selectedText = ""
                }
            }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                android.util.Log.d("LuminaToolbar", "showMenu() called! rect=$rect, onCopy=${onCopyRequested != null}")
                lastSelectionTimestamp = System.currentTimeMillis()
                activeReleaseSelectionAction = onCopyRequested

                // Extract selected text from SelectionManager without triggering toolbarCopy() / onRelease()
                val manager = (onCopyRequested as? kotlin.jvm.internal.CallableReference)?.boundReceiver
                val selectedFromManager = try {
                    val method = manager?.javaClass?.methods?.firstOrNull { it.name.startsWith("getSelectedText") }
                    method?.isAccessible = true
                    (method?.invoke(manager) as? AnnotatedString)?.text?.trim()
                } catch (_: Throwable) { null }

                if (selectedFromManager.isNullOrBlank()) {
                    // Fallback: invoke copy$foundation (which copies without releasing selection)
                    try {
                        val copyMethod = manager?.javaClass?.methods?.firstOrNull {
                            it.name.startsWith("copy") && it.parameterCount == 0
                        }
                        copyMethod?.isAccessible = true
                        copyMethod?.invoke(manager)
                    } catch (_: Throwable) {}
                }

                val textFromClip = selectedFromManager
                    ?: cachedClipboardText?.text?.trim()
                    ?: customClipboardManager.getText()?.text?.trim()
                val systemClip = try {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                        ?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                } catch (_: Throwable) { null }

                val resolved = when {
                    !textFromClip.isNullOrBlank() -> textFromClip
                    !systemClip.isNullOrBlank() -> systemClip
                    selectedText.isNotBlank() -> selectedText
                    else -> "Selected text"
                }
                android.util.Log.d("LuminaToolbar", "showMenu resolved text='$resolved'")
                selectedText = resolved
                selectedChapterTitle = activeChapterTitle
                showSelectionMenu = true
            }
        }
    }

    val gestureDoubleTap by (repository?.gestureDoubleTap?.collectAsState(initial = GestureAction.TOGGLE_AUTOSCROLL) ?: remember { mutableStateOf(GestureAction.TOGGLE_AUTOSCROLL) })
    val gestureTripleTap by (repository?.gestureTripleTap?.collectAsState(initial = GestureAction.SUMMON_ORB) ?: remember { mutableStateOf(GestureAction.SUMMON_ORB) })
    val gestureSingleTap by (repository?.gestureSingleTap?.collectAsState(initial = GestureAction.TOGGLE_BARS) ?: remember { mutableStateOf(GestureAction.TOGGLE_BARS) })
    val gestureTtsTap by (repository?.gestureTtsTap?.collectAsState(initial = GestureAction.TTS_READ_ALOUD) ?: remember { mutableStateOf(GestureAction.TTS_READ_ALOUD) })

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val isStrictPaged = readingMode == ReadingMode.PAGED
    val isPagedReading = readingMode == ReadingMode.PAGED || readingMode == ReadingMode.PAGED_SCROLL
    var showCharacterGuideSheet by rememberSaveable { mutableStateOf(false) }

    // Camera notch cutout insets for landscape reading canvas
    val cutoutPaddingValues = WindowInsets.displayCutout.asPaddingValues()
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val cutoutStart = cutoutPaddingValues.calculateStartPadding(layoutDirection)
    val cutoutEnd = cutoutPaddingValues.calculateEndPadding(layoutDirection)

    val landscapeSidePaddingStart = maxOf((horizontalPadding * 1.8f).dp.coerceAtLeast(56.dp), cutoutStart + 20.dp)
    val landscapeSidePaddingEnd = maxOf((horizontalPadding * 1.8f).dp.coerceAtLeast(56.dp), cutoutEnd + 20.dp)

    val effectiveStartPadding = if (isLandscape) landscapeSidePaddingStart else horizontalPadding.dp
    val effectiveEndPadding = if (isLandscape) landscapeSidePaddingEnd else horizontalPadding.dp

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    val currentConfigKey = "${fontSize}_${isLandscape}_${screenWidthDp}_${screenHeightDp}"
    var pagesConfigKey by remember { mutableStateOf(currentConfigKey) }

    // Prepared pages for Paged Mode with smart character/sentence budgeting so text never overflows
    // Non-blocking in-memory cache lookup on composition; background precomputation via getOrComputeAsync
    var pages by remember(book.id, book.chapters.size, fontSize, readingMode, isLandscape, screenWidthDp, screenHeightDp, horizontalPadding, verticalPadding, lineHeightMultiplier, paragraphSpacingMultiplier) {
        val cached = if (isPagedReading) {
            PageCache.getCached(
                bookId = book.id,
                chaptersCount = book.chapters.size,
                fontSize = fontSize,
                isLandscape = isLandscape,
                isStrictPaged = isStrictPaged,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                horizontalPaddingDp = horizontalPadding,
                verticalPaddingDp = verticalPadding,
                paragraphSpacingMultiplier = paragraphSpacingMultiplier
            )
        } else null
        if (cached != null) {
            pagesConfigKey = currentConfigKey
        }
        mutableStateOf(cached ?: emptyList())
    }

    LaunchedEffect(book.id, book.chapters.size, fontSize, readingMode, isLandscape, isPagedReading, screenWidthDp, screenHeightDp, horizontalPadding, verticalPadding, lineHeightMultiplier, paragraphSpacingMultiplier) {
        if (isPagedReading && (pages.isEmpty() || pagesConfigKey != currentConfigKey) && book.chapters.isNotEmpty()) {
            val allPages = PageCache.getOrComputeAsync(
                bookId = book.id,
                chapters = book.chapters,
                fontSize = fontSize,
                isLandscape = isLandscape,
                isStrictPaged = isStrictPaged,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                horizontalPaddingDp = horizontalPadding,
                verticalPaddingDp = verticalPadding,
                lineHeightMultiplier = lineHeightMultiplier,
                paragraphSpacingMultiplier = paragraphSpacingMultiplier,
                dbHelper = repository?.dbHelper,
                activeChapterIndex = book.currentChapter,
                onActiveChapterReady = { activePages ->
                    if (pages.isEmpty() || pagesConfigKey != currentConfigKey) {
                        pages = activePages
                        pagesConfigKey = currentConfigKey
                    }
                }
            )
            pages = allPages
            pagesConfigKey = currentConfigKey
        }
    }

    val chapterCumulativeParaOffsets = remember(book.id, book.chapters.size) {
        val offsets = IntArray(book.chapters.size)
        var accum = 0
        for (i in book.chapters.indices) {
            offsets[i] = accum
            accum += (book.chapters[i].paragraphs.size + 1)
        }
        offsets
    }
    val totalParas = remember(book.id, book.chapters.size) { maxOf(book.chapters.sumOf { it.paragraphs.size }, 1) }
    val totalScrollItems = remember(book.id, book.chapters.size) {
        book.chapters.sumOf { it.paragraphs.size + 1 }
    }

    val initialChapIdx = remember(book.id, book.scrollPos) {
        val binIdx = chapterCumulativeParaOffsets.binarySearch(book.scrollPos)
        if (binIdx >= 0) binIdx else (-binIdx - 2).coerceIn(0, maxOf(0, book.chapters.size - 1))
    }
    val initialParaIdx = remember(book.id, book.scrollPos, initialChapIdx) {
        (book.scrollPos - chapterCumulativeParaOffsets.getOrElse(initialChapIdx) { 0 } - 1).coerceAtLeast(0)
    }
    val initialSnippet = remember(book.id, initialChapIdx, initialParaIdx) {
        book.chapters.getOrNull(initialChapIdx)?.paragraphs?.getOrNull(initialParaIdx)?.trim()?.take(40) ?: ""
    }

    var currentVisibleChapterIdx by remember { mutableIntStateOf(initialChapIdx) }
    var currentVisibleParaIdx by remember { mutableIntStateOf(initialParaIdx) }
    var currentTopSnippet by remember { mutableStateOf(initialSnippet) }

    // Persistent reading anchor that survives font zooming and screen orientation changes
    var anchorSnippet by rememberSaveable { mutableStateOf(initialSnippet) }
    var anchorChapterIdx by rememberSaveable { mutableIntStateOf(initialChapIdx) }
    var anchorParaIdx by rememberSaveable { mutableIntStateOf(initialParaIdx) }

    var isPinching by remember { mutableStateOf(false) }
    var activePagedTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var activeTextBoundsInRoot by remember { mutableStateOf<Rect?>(null) }

    var prevReadingMode by remember { mutableStateOf(readingMode) }
    var lastAnchoredFontSize by remember { mutableIntStateOf(fontSize) }
    var lastAnchoredLandscape by remember { mutableStateOf(isLandscape) }
    var pendingTargetSync by remember { mutableStateOf(false) }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = book.scrollPos)
    val pagerState = rememberPagerState(
        initialPage = book.currentPage.coerceIn(0, maxOf(pages.size - 1, 0)),
        pageCount = { pages.size }
    )

    // Detect font zoom or screen rotation changes to trigger re-anchoring to the exact reading position
    LaunchedEffect(fontSize, isLandscape) {
        if (fontSize != lastAnchoredFontSize || isLandscape != lastAnchoredLandscape) {
            lastAnchoredFontSize = fontSize
            lastAnchoredLandscape = isLandscape
            pendingTargetSync = true
        }
    }

    LaunchedEffect(readingMode) {
        if (readingMode == prevReadingMode) return@LaunchedEffect
        prevReadingMode = readingMode

        if (readingMode == ReadingMode.SCROLL) {
            val targetScroll = findScrollIndexForLocation(
                chapterIdx = anchorChapterIdx,
                paraIdx = anchorParaIdx,
                topSnippet = anchorSnippet,
                chapters = book.chapters,
                chapterOffsets = chapterCumulativeParaOffsets
            )
            listState.scrollToItem(targetScroll.coerceIn(0, maxOf(0, totalScrollItems - 1)))
        } else {
            if (pages.isNotEmpty()) {
                val targetPage = findPageForLocation(
                    pages = pages,
                    chapterIdx = anchorChapterIdx,
                    chapters = book.chapters,
                    paraIdx = anchorParaIdx,
                    topSnippet = anchorSnippet
                )
                pagerState.scrollToPage(targetPage.coerceIn(0, maxOf(0, pages.size - 1)))
            } else {
                pendingTargetSync = true
            }
        }
    }

    // When pages or pendingTargetSync changes, restore pager to the exact anchored paragraph/snippet
    LaunchedEffect(pages, pagesConfigKey, pendingTargetSync, isPinching, currentConfigKey) {
        if (isPagedReading && pendingTargetSync && !isPinching && pages.isNotEmpty() && pagesConfigKey == currentConfigKey) {
            val targetPage = findPageForLocation(
                pages = pages,
                chapterIdx = anchorChapterIdx,
                chapters = book.chapters,
                paraIdx = anchorParaIdx,
                topSnippet = anchorSnippet
            )
            if (targetPage in pages.indices) {
                pagerState.scrollToPage(targetPage)
            }
            pendingTargetSync = false
        }
    }

    fun executeGestureAction(action: GestureAction, chapIdx: Int = speakingChapterIdx, pIdx: Int = speakingParaIdx) {
        when (action) {
            GestureAction.TOGGLE_AUTOSCROLL -> {
                isAutoScrolling = !isAutoScrolling
                if (isAutoScrolling) {
                    showTtsDock = false
                    showControls = false
                    stopAllAudio()
                }
                Toast.makeText(context, if (isAutoScrolling) "Auto-scroll started" else "Auto-scroll stopped", Toast.LENGTH_SHORT).show()
            }
            GestureAction.SUMMON_ORB -> {
                onToggleFloatingAssistant(true)
                Toast.makeText(context, "Assistant Orb summoned", Toast.LENGTH_SHORT).show()
            }
            GestureAction.TOGGLE_BARS -> {
                if (showSelectionMenu) showSelectionMenu = false else showControls = !showControls
            }
            GestureAction.TTS_READ_ALOUD -> {
                speakingChapterIdx = chapIdx
                speakingParaIdx = pIdx
                showTtsDock = true
                val chapter = book.chapters.getOrNull(chapIdx)
                if (chapter != null) {
                    LuminaAudioService.startOrUpdate(
                        context = context,
                        bookId = book.id,
                        bookTitle = book.title,
                        chapterIndex = chapIdx,
                        chapterTitle = chapter.title,
                        paragraphIndex = pIdx,
                        paragraphs = chapter.paragraphs,
                        isEdgeTts = ttsEngine == "EDGE_NEURAL",
                        voice = ttsEdgeVoice,
                        speed = ttsSpeed
                    )
                }
            }
            GestureAction.IN_BOOK_SEARCH -> {
                showInBookSearchDialog = true
            }
            GestureAction.PREVIOUS_CHAPTER -> {
                stopAllAudio()
                val prev = (chapIdx - 1).coerceAtLeast(0)
                speakingChapterIdx = prev
                speakingParaIdx = 0
                activeChapterTitle = book.chapters.getOrNull(prev)?.title ?: ""
                if (readingMode == ReadingMode.SCROLL) {
                    var target = 0
                    for (i in 0 until prev) {
                        target += (book.chapters[i].paragraphs.size + 1)
                    }
                    coroutineScope.launch { listState.animateScrollToItem(target) }
                } else {
                    val pageIdx = pages.indexOfFirst { it.first == activeChapterTitle }
                    if (pageIdx != -1) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pageIdx) }
                    }
                }
            }
            GestureAction.NEXT_CHAPTER -> {
                stopAllAudio()
                val next = (chapIdx + 1).coerceAtMost(book.chapters.size - 1)
                speakingChapterIdx = next
                speakingParaIdx = 0
                activeChapterTitle = book.chapters.getOrNull(next)?.title ?: ""
                if (readingMode == ReadingMode.SCROLL) {
                    var target = 0
                    for (i in 0 until next) {
                        target += (book.chapters[i].paragraphs.size + 1)
                    }
                    coroutineScope.launch { listState.animateScrollToItem(target) }
                } else {
                    val pageIdx = pages.indexOfFirst { it.first == activeChapterTitle }
                    if (pageIdx != -1) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pageIdx) }
                    }
                }
            }
            GestureAction.ADD_BOOKMARK -> {
                val preview = book.chapters.getOrNull(chapIdx)?.paragraphs?.getOrNull(pIdx)?.take(60) ?: "Bookmark"
                val activePage = if (readingMode != ReadingMode.SCROLL) pagerState.currentPage + 1 else book.currentPage + 1
                onAddBookmark(preview, HighlightColor.GOLD, activePage, "")
                Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
            }
            GestureAction.NONE -> {}
        }
    }

    // Auto-scroll loop for hands-free reading in both Continuous Scroll and Paged modes
    LaunchedEffect(isAutoScrolling, autoScrollSpeed, readingMode) {
        if (!isAutoScrolling) return@LaunchedEffect
        if (readingMode == ReadingMode.SCROLL) {
            while (isAutoScrolling) {
                try {
                    val consumed = listState.scrollBy(2f)
                    if (consumed == 0f && !listState.canScrollForward) {
                        isAutoScrolling = false
                        break
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Touch/gesture intervened; yield briefly and continue without terminating auto-scroll loop
                    kotlinx.coroutines.delay(100L)
                    continue
                } catch (_: Exception) {}
                val delayMs = (25L / autoScrollSpeed.coerceIn(0.5f, 3.0f)).toLong().coerceAtLeast(8L)
                kotlinx.coroutines.delay(delayMs)
            }
        } else {
            // Paged modes (PAGED, PAGED_SCROLL): pacing-based page auto-advancement
            while (isAutoScrolling) {
                val pageIntervalMs = (6000L / autoScrollSpeed.coerceIn(0.5f, 4.0f)).toLong()
                kotlinx.coroutines.delay(pageIntervalMs)
                if (!isAutoScrolling) break
                if (pagerState.currentPage < pagerState.pageCount - 1) {
                    try {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        kotlinx.coroutines.delay(500L)
                    } catch (_: Exception) {}
                } else if (pagerState.currentPage >= pagerState.pageCount - 1) {
                    isAutoScrolling = false
                    break
                }
            }
        }
    }

    val handleAssistantAction: (AssistantAction) -> Unit = { action ->
        when (action) {
            is AssistantAction.SwitchTheme -> {
                action.themeFamily?.let { tf ->
                    try {
                        val family = ThemeFamily.valueOf(tf.uppercase())
                        onThemeFamilyChange(family)
                    } catch (_: Exception) {}
                }
                action.mode?.let { m ->
                    try {
                        val variant = ThemeVariant.valueOf(m.uppercase())
                        onThemeVariantChange(variant)
                    } catch (_: Exception) {}
                }
                Toast.makeText(context, "Theme switched to ${action.themeFamily ?: action.mode}", Toast.LENGTH_SHORT).show()
            }
            is AssistantAction.CreateTheme -> {
                repository?.createAndApplyCustomTheme(action.name, action.bgColor, action.textColor, action.accentColor)
                onThemeFamilyChange(ThemeFamily.CUSTOM)
                Toast.makeText(context, "Theme '${action.name}' created and applied!", Toast.LENGTH_SHORT).show()
            }
            is AssistantAction.UpdateTheme -> {
                val updated = repository?.updateAndApplyCustomTheme(action.name, action.bgColor, action.textColor, action.accentColor)
                if (updated != null) {
                    onThemeFamilyChange(ThemeFamily.CUSTOM)
                    Toast.makeText(context, "Theme '${action.name}' updated and applied!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Theme '${action.name}' not found to update", Toast.LENGTH_SHORT).show()
                }
            }
            is AssistantAction.JumpToScene -> {
                coroutineScope.launch {
                    val matches = repository?.searchScenes(book.id, action.query) ?: emptyList()
                    val target = matches.firstOrNull()
                    if (target != null) {
                        val chapIdx = book.chapters.indexOfFirst { it.title.equals(target.chapterTitle, ignoreCase = true) }
                        if (chapIdx != -1) {
                            speakingChapterIdx = chapIdx
                            speakingParaIdx = target.paragraphIndex
                            activeChapterTitle = target.chapterTitle
                            if (readingMode == ReadingMode.SCROLL) {
                                var itemIdx = 0
                                for (i in 0 until chapIdx) {
                                    itemIdx += (book.chapters[i].paragraphs.size + 1)
                                }
                                val focusOffsetPx = (screenHeightPx * 0.22f).roundToInt().coerceIn(120, 450)
                                listState.animateScrollToItem(itemIdx.coerceAtLeast(0), scrollOffset = -focusOffsetPx)
                            }
                            Toast.makeText(context, "Jumped to scene: ${target.snippet.take(45)}...", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Scene not found in book", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            is AssistantAction.ControlTts -> {
                when (action.action.lowercase()) {
                    "play", "start", "resume" -> {
                        showTtsDock = true
                        triggerSpeakNextPara()
                    }
                    "stop", "pause" -> {
                        stopAllAudio()
                    }
                    "next" -> {
                        LuminaAudioService.nextParagraph(context)
                    }
                    "prev", "previous" -> {
                        LuminaAudioService.prevParagraph(context)
                    }
                }
            }
            is AssistantAction.ToggleAutoScroll -> {
                isAutoScrolling = action.enable
                if (isAutoScrolling) {
                    showTtsDock = false
                    stopAllAudio()
                }
                Toast.makeText(context, if (action.enable) "Auto-scroll started" else "Auto-scroll stopped", Toast.LENGTH_SHORT).show()
            }
            is AssistantAction.NextChapter -> {
                stopAllAudio()
                val next = (speakingChapterIdx + 1).coerceAtMost(book.chapters.size - 1)
                speakingChapterIdx = next
                speakingParaIdx = 0
                val chapTitle = book.chapters.getOrNull(next)?.title ?: "Chapter ${next + 1}"
                activeChapterTitle = chapTitle
                coroutineScope.launch {
                    if (readingMode == ReadingMode.SCROLL) {
                        var idx = 0
                        for (i in 0 until next) {
                            idx += (book.chapters[i].paragraphs.size + 1)
                        }
                        if (kotlin.math.abs(listState.firstVisibleItemIndex - idx) > 3) {
                            listState.scrollToItem(idx)
                        } else {
                            listState.animateScrollToItem(idx)
                        }
                    } else {
                        val targetPage = pages.indexOfFirst { it.first == chapTitle }.coerceAtLeast(0)
                        if (kotlin.math.abs(pagerState.currentPage - targetPage) > 3) {
                            pagerState.scrollToPage(targetPage)
                        } else {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                }
            }
            is AssistantAction.PreviousChapter -> {
                stopAllAudio()
                val prev = (speakingChapterIdx - 1).coerceAtLeast(0)
                speakingChapterIdx = prev
                speakingParaIdx = 0
                val chapTitle = book.chapters.getOrNull(prev)?.title ?: "Chapter ${prev + 1}"
                activeChapterTitle = chapTitle
                coroutineScope.launch {
                    if (readingMode == ReadingMode.SCROLL) {
                        var idx = 0
                        for (i in 0 until prev) {
                            idx += (book.chapters[i].paragraphs.size + 1)
                        }
                        if (kotlin.math.abs(listState.firstVisibleItemIndex - idx) > 3) {
                            listState.scrollToItem(idx)
                        } else {
                            listState.animateScrollToItem(idx)
                        }
                    } else {
                        val targetPage = pages.indexOfFirst { it.first == chapTitle }.coerceAtLeast(0)
                        if (kotlin.math.abs(pagerState.currentPage - targetPage) > 3) {
                            pagerState.scrollToPage(targetPage)
                        } else {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                }
            }
            is AssistantAction.NavigateChapter -> {
                stopAllAudio()
                val target = action.targetIndex.coerceIn(0, book.chapters.size - 1)
                speakingChapterIdx = target
                speakingParaIdx = 0
                val chapTitle = book.chapters.getOrNull(target)?.title ?: "Chapter ${target + 1}"
                activeChapterTitle = chapTitle
                coroutineScope.launch {
                    if (readingMode == ReadingMode.SCROLL) {
                        var idx = 0
                        for (i in 0 until target) {
                            idx += (book.chapters[i].paragraphs.size + 1)
                        }
                        if (kotlin.math.abs(listState.firstVisibleItemIndex - idx) > 3) {
                            listState.scrollToItem(idx)
                        } else {
                            listState.animateScrollToItem(idx)
                        }
                    } else {
                        val targetPage = pages.indexOfFirst { it.first == chapTitle }.coerceAtLeast(0)
                        if (kotlin.math.abs(pagerState.currentPage - targetPage) > 3) {
                            pagerState.scrollToPage(targetPage)
                        } else {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                }
            }
            is AssistantAction.ToggleTts -> {
                if (action.play) {
                    showTtsDock = true
                    speakNextPara.value()
                } else {
                    stopAllAudio()
                }
            }
            is AssistantAction.AddNote -> {
                val activePage = if (readingMode != ReadingMode.SCROLL) pagerState.currentPage + 1 else book.currentPage + 1
                onAddBookmark(action.noteContent, HighlightColor.GOLD, activePage, "")
            }
            is AssistantAction.Answer -> {}
        }
    }

    val startVoiceAssistant: () -> Unit = {
        if (disableAi) {
            Toast.makeText(context, "AI Assistant is disabled in Settings", Toast.LENGTH_SHORT).show()
        } else {
            showControls = false
            onControlsVisibilityChange(false)
            voiceQuery = ""
            voiceResponse = ""
            if (autoStartMic && !disableStt) {
                voiceState = AssistantVoiceState.LISTENING
                assistantService.startListening(
                    onReady = { voiceState = AssistantVoiceState.LISTENING },
                    onPartialResult = { partial -> voiceQuery = partial },
                onResult = { query ->
                    voiceQuery = query
                    voiceState = AssistantVoiceState.THINKING
                    val localAction = assistantService.parseLocalCommand(query, book.chapters.size)
                    if (localAction != null) {
                        handleAssistantAction(localAction)
                        voiceResponse = when (localAction) {
                            is AssistantAction.NextChapter -> "Moved to next chapter."
                            is AssistantAction.PreviousChapter -> "Moved to previous chapter."
                            is AssistantAction.NavigateChapter -> "Jumped to chapter ${localAction.targetIndex + 1}."
                            is AssistantAction.ToggleTts -> if (localAction.play) "Started reading aloud." else "Paused reading."
                            is AssistantAction.AddNote -> "Note saved: \"${localAction.noteContent}\""
                            is AssistantAction.SwitchTheme -> "Theme switched to ${localAction.themeFamily ?: localAction.mode}."
                            is AssistantAction.JumpToScene -> "Searching scene in book..."
                            is AssistantAction.ControlTts -> "TTS command executed."
                            is AssistantAction.ToggleAutoScroll -> if (localAction.enable) "Auto-scroll started." else "Auto-scroll paused."
                            is AssistantAction.CreateTheme -> "Custom theme \"${localAction.name}\" applied."
                            is AssistantAction.UpdateTheme -> "Custom theme \"${localAction.name}\" updated."
                            is AssistantAction.Answer -> localAction.text
                        }
                        voiceState = AssistantVoiceState.RESPONDING
                    } else {
                        coroutineScope.launch {
                            val knownContext = buildString {
                                val maxChap = if (spoilerShield) speakingChapterIdx else (book.chapters.size - 1)
                                for (c in 0..maxChap) {
                                    val ch = book.chapters.getOrNull(c) ?: continue
                                    appendLine("--- ${ch.title} ---")
                                    appendLine(ch.paragraphs.filterNot { it.startsWith("[IMG:") }.joinToString(" "))
                                }
                            }
                            val response = assistantService.queryAssistant(
                                provider = aiProvider,
                                apiKey = geminiApiKey,
                                baseUrl = aiBaseUrl,
                                modelName = aiModel,
                                bookTitle = book.title,
                                activeChapterTitle = activeChapterTitle,
                                knownContext = knownContext,
                                userQuery = query,
                                spoilerShield = spoilerShield
                            )
                            voiceResponse = response.answerText
                            voiceState = AssistantVoiceState.RESPONDING
                            response.executedAction?.let { handleAssistantAction(it) }
                        }
                    }
                },
                onError = { err ->
                    voiceResponse = "Sorry, couldn't hear that: $err"
                    voiceState = AssistantVoiceState.RESPONDING
                }
            )
        } else {
            voiceState = AssistantVoiceState.IDLE
        }
    }
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

    val view = LocalView.current

    val progressBottomInset = max(
        max(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            WindowInsets.mandatorySystemGestures.asPaddingValues().calculateBottomPadding()
        ),
        16.dp
    )
    val statusBarTopInset = max(
        max(
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        ),
        14.dp
    )

    val currentFontSize by rememberUpdatedState(fontSize)
    val currentReadingMode by rememberUpdatedState(readingMode)
    val currentIsPagedReading by rememberUpdatedState(isPagedReading)
    val currentPages by rememberUpdatedState(pages)
    val currentChapters by rememberUpdatedState(book.chapters)
    val currentOnFontSizeChange by rememberUpdatedState(onFontSizeChange)
    val currentTopInsetPx = with(density) { statusBarTopInset.toPx() }
    val currentBottomInsetPx = with(density) { progressBottomInset.toPx() }
    val currentHeaderOffsetPx = with(density) { 90.dp.toPx() }

    CompositionLocalProvider(
        LocalTextToolbar provides customTextToolbar,
        LocalClipboard provides customClipboard,
        LocalClipboardManager provides customClipboardManager,
        LocalTextSelectionColors provides customSelectionColors
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var cumulativeZoom = 1.0f
                        var pinchStarted = false
                        var currentTargetFontSize = currentFontSize

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val downPointers = event.changes.filter { it.pressed }
                                if (downPointers.size >= 2) {
                                    val p0 = downPointers[0].position
                                    val p1 = downPointers[1].position
                                    val prevP0 = downPointers[0].previousPosition
                                    val prevP1 = downPointers[1].previousPosition
                                    val currentDist = (p0 - p1).getDistance()
                                    val prevDist = (prevP0 - prevP1).getDistance()

                                    val avgTouchX = (p0.x + p1.x) / 2f
                                    val avgTouchY = (p0.y + p1.y) / 2f

                                    if (!pinchStarted) {
                                        pinchStarted = true
                                        isPinching = true

                                        val activePages = currentPages
                                        val curPage = pagerState.currentPage
                                        if (currentIsPagedReading && curPage in activePages.indices) {
                                            val (cTitle, pageContent) = activePages[curPage]
                                            val isChapterHeaderPage = pageContent.startsWith("CHAPTER_START:::") || pageContent.startsWith("TITLE:::")
                                            val pageBody = if (isChapterHeaderPage) pageContent.substringAfterLast(":::") else pageContent
                                            val cIdx = currentChapters.indexOfFirst { it.title == cTitle }.coerceAtLeast(0)
                                            val chap = currentChapters.getOrNull(cIdx)

                                            var targetSnippet = ""
                                            var targetParaIdx = 0

                                            val rawParas = pageBody.split(Regex("\n\n+|\n")).map { it.trim() }.filter { it.isNotEmpty() }
                                            if (rawParas.isNotEmpty()) {
                                                val headerOffsetPx = if (isChapterHeaderPage) currentHeaderOffsetPx else 0f
                                                val effectiveTop = currentTopInsetPx + headerOffsetPx
                                                val usableHeight = (screenHeightPx - effectiveTop - currentBottomInsetPx).coerceAtLeast(100f)
                                                val touchFraction = ((avgTouchY - effectiveTop) / usableHeight).coerceIn(0f, 0.999f)

                                                val weights = rawParas.map { it.length.toFloat().coerceAtLeast(20f) }
                                                val totalWeight = weights.sum().coerceAtLeast(1f)
                                                val targetThreshold = touchFraction * totalWeight

                                                var accumWeight = 0f
                                                var selectedPara = rawParas.first()
                                                for (i in rawParas.indices) {
                                                    accumWeight += weights[i]
                                                    if (accumWeight >= targetThreshold || i == rawParas.size - 1) {
                                                        selectedPara = rawParas[i]
                                                        break
                                                    }
                                                }

                                                val exactPara = selectedPara.trim()
                                                if (exactPara.isNotBlank()) {
                                                    targetSnippet = exactPara.take(80)
                                                    val cleanExact = cleanAlpha(exactPara)
                                                    val foundIdx = chap?.paragraphs?.indexOfFirst { fullPara ->
                                                        val cleanFull = cleanAlpha(fullPara)
                                                        cleanFull.contains(cleanExact.take(30)) || cleanExact.contains(cleanFull.take(30)) ||
                                                        (cleanExact.length >= 15 && cleanFull.contains(cleanExact.take(15)))
                                                    } ?: -1
                                                    if (foundIdx >= 0) {
                                                        targetParaIdx = foundIdx
                                                    }
                                                }
                                            }

                                            if (targetSnippet.isBlank() && pageBody.isNotBlank()) {
                                                targetSnippet = pageBody.trim().take(80)
                                            }

                                            if (targetSnippet.isNotBlank()) {
                                                anchorSnippet = targetSnippet
                                                anchorChapterIdx = cIdx
                                                anchorParaIdx = targetParaIdx
                                                currentTopSnippet = targetSnippet
                                            }
                                        } else if (currentReadingMode == ReadingMode.SCROLL) {
                                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                                            val touchedItem = visibleItems.find { avgTouchY >= it.offset && avgTouchY <= it.offset + it.size }
                                                ?: visibleItems.firstOrNull()

                                            if (touchedItem != null) {
                                                val itemIdx = touchedItem.index
                                                val binIdx = chapterCumulativeParaOffsets.binarySearch(itemIdx)
                                                val cIdx = if (binIdx >= 0) binIdx else (-binIdx - 2).coerceIn(0, currentChapters.size - 1)
                                                val pIdx = (itemIdx - chapterCumulativeParaOffsets.getOrElse(cIdx) { 0 } - 1).coerceAtLeast(0)
                                                val paraText = currentChapters.getOrNull(cIdx)?.paragraphs?.getOrNull(pIdx) ?: ""
                                                val snip = paraText.trim().take(80)

                                                anchorSnippet = snip
                                                anchorChapterIdx = cIdx
                                                anchorParaIdx = pIdx
                                                currentTopSnippet = snip

                                                // Center the paragraph immediately at touch start
                                                val viewportH = listState.layoutInfo.viewportSize.height
                                                val centerOffset = (viewportH - touchedItem.size) / 2
                                                coroutineScope.launch {
                                                    listState.scrollToItem(itemIdx, scrollOffset = -centerOffset)
                                                }
                                            }
                                        }
                                    }

                                    if (prevDist > 0f) {
                                        val scale = currentDist / prevDist
                                        cumulativeZoom *= scale
                                        if (cumulativeZoom > 1.15f) {
                                            val newSize = (currentTargetFontSize + 1).coerceAtMost(36)
                                            if (newSize != currentTargetFontSize) {
                                                currentTargetFontSize = newSize
                                                currentOnFontSizeChange(newSize)
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                            cumulativeZoom = 1.0f
                                        } else if (cumulativeZoom < 0.85f) {
                                            val newSize = (currentTargetFontSize - 1).coerceAtLeast(12)
                                            if (newSize != currentTargetFontSize) {
                                                currentTargetFontSize = newSize
                                                currentOnFontSizeChange(newSize)
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                            cumulativeZoom = 1.0f
                                        }
                                    }
                                    downPointers.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            // Touch end / zoom pinch end: Bring to its screen!
                            if (pinchStarted) {
                                isPinching = false
                                pendingTargetSync = true

                                if (currentReadingMode == ReadingMode.SCROLL) {
                                    val targetScroll = chapterCumulativeParaOffsets.getOrElse(anchorChapterIdx) { 0 } + anchorParaIdx + 1
                                    val viewportH = listState.layoutInfo.viewportSize.height
                                    val centerOffset = (viewportH * 0.35f).roundToInt()
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetScroll.coerceIn(0, maxOf(0, totalScrollItems - 1)), scrollOffset = -centerOffset)
                                    }
                                }
                            }
                        }
                    }
                }
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
                val grainShader = remember {
                    val tile = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(tile)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        isAntiAlias = false
                    }
                    var x = 2
                    while (x < 48) {
                        var y = 2
                        while (y < 48) {
                            val dotAlpha = (((x * 31 + y * 17) % 100) / 100f) * 0.035f * 255f
                            paint.alpha = dotAlpha.toInt().coerceIn(0, 255)
                            canvas.drawCircle(x.toFloat(), y.toFloat(), 0.8f, paint)
                            y += 8
                        }
                        x += 8
                    }
                    android.graphics.BitmapShader(tile, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        val paint = androidx.compose.ui.graphics.Paint()
                        paint.asFrameworkPaint().shader = grainShader
                        canvas.drawRect(0f, 0f, size.width, size.height, paint)
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
                val linenShader = remember {
                    val tile = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(tile)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        alpha = (0.035f * 255f).toInt()
                        strokeWidth = 0.75f
                    }
                    canvas.drawLine(0f, 0f, 0f, 32f, paint)
                    canvas.drawLine(0f, 0f, 32f, 0f, paint)
                    android.graphics.BitmapShader(tile, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        val paint = androidx.compose.ui.graphics.Paint()
                        paint.asFrameworkPaint().shader = linenShader
                        canvas.drawRect(0f, 0f, size.width, size.height, paint)
                    }
                }
            }
            BackgroundTexture.NONE -> {}
        }

        if (readingMode == ReadingMode.SCROLL) {
                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collectLatest { firstIndex ->
                                val binIdx = chapterCumulativeParaOffsets.binarySearch(firstIndex)
                                val currentChap = if (binIdx >= 0) binIdx else (-binIdx - 2).coerceIn(0, book.chapters.size - 1)
                                val paraIdx = (firstIndex - chapterCumulativeParaOffsets.getOrElse(currentChap) { 0 } - 1).coerceAtLeast(0)

                                currentVisibleChapterIdx = currentChap
                                currentVisibleParaIdx = paraIdx
                                val paraText = book.chapters.getOrNull(currentChap)?.paragraphs?.getOrNull(paraIdx) ?: ""
                                val snip = paraText.trim().take(50)
                                currentTopSnippet = snip
                                if (!isPinching) {
                                    anchorSnippet = snip
                                    anchorChapterIdx = currentChap
                                    anchorParaIdx = paraIdx
                                }

                                val newTitle = book.chapters.getOrNull(currentChap)?.title ?: "Chapter 1"
                                if (activeChapterTitle != newTitle) {
                                    activeChapterTitle = newTitle
                                }
                                if (speakingChapterIdx != currentChap) {
                                    speakingChapterIdx = currentChap
                                }
                                val overallProgress = ((firstIndex.toFloat() / totalParas) * 100).toInt().coerceIn(0, 100)
                                if (currentProgressPct != overallProgress) {
                                    currentProgressPct = overallProgress
                                }

                                // Debounce database write: only persist after scrolling pauses for 800ms
                                delay(800)
                                onPositionChange(currentChap, 0, firstIndex, overallProgress)
                            }
                    }

                    val scrollHorizontalPaddingStart = if (isLandscape) landscapeSidePaddingStart else horizontalPadding.dp
                    val scrollHorizontalPaddingEnd = if (isLandscape) landscapeSidePaddingEnd else horizontalPadding.dp

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = (76 + verticalPadding).dp,
                            bottom = 100.dp + progressBottomInset + verticalPadding.dp,
                            start = scrollHorizontalPaddingStart,
                            end = scrollHorizontalPaddingEnd
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(gestureDoubleTap, gestureSingleTap) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        executeGestureAction(gestureDoubleTap)
                                    },
                                    onTap = {
                                        if (isAutoScrolling) {
                                            isAutoScrolling = false
                                        } else if (showSelectionMenu) {
                                            showSelectionMenu = false
                                            selectedText = ""
                                        } else {
                                            executeGestureAction(gestureSingleTap)
                                        }
                                    }
                                )
                            }
                    ) {
                    book.chapters.forEachIndexed { chapIdx, chapter ->
                        val chapterBookmarks = bookmarksByChapter[chapter.title.trim().lowercase()] ?: emptyList()
                        item(key = "chap-header-$chapIdx", contentType = "chap_header") {
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
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Medium,
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

                                if (para.startsWith("[IMG:") && para.endsWith("]")) {
                                    // Inline illustration with safe image loading and placeholder
                                    val imgPath = para.removePrefix("[IMG:").removeSuffix("]")
                                    val bitmap = rememberBookImage(imgPath)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 22.dp)
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
                                    val isDropCap = chapIdx == 0 && pIdx == 0 && para.length > 40 && !para.startsWith("[IMG:")
                                    val onBgColor = MaterialTheme.colorScheme.onBackground
                                    val secColor = MaterialTheme.colorScheme.secondary
                                    val activeSearchQ = if (inBookSearchQuery.length >= 2) inBookSearchQuery else ""
                                    val isActiveMatch = inBookSearchResults.getOrNull(inBookCurrentMatchIndex)?.let {
                                        it.chapterIndex == chapIdx && it.paragraphIndex == pIdx
                                    } ?: false
                                    val hasFormatting = matchingBookmarks.isNotEmpty() || isDropCap || activeSearchQ.isNotEmpty()

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
                                                        speakingChapterIdx = chapIdx
                                                        speakingParaIdx = pIdx
                                                        showTtsDock = true
                                                        val chapterObj = book.chapters.getOrNull(chapIdx)
                                                        if (chapterObj != null) {
                                                            LuminaAudioService.startOrUpdate(
                                                                context = context,
                                                                bookId = book.id,
                                                                bookTitle = book.title,
                                                                chapterIndex = chapIdx,
                                                                chapterTitle = chapterObj.title,
                                                                paragraphIndex = pIdx,
                                                                paragraphs = chapterObj.paragraphs,
                                                                isEdgeTts = ttsEngine == "EDGE_NEURAL",
                                                                voice = ttsEdgeVoice,
                                                                speed = ttsSpeed
                                                            )
                                                        }
                                                    }
                                                } else Modifier
                                            )
                                    ) {
                                        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                                            if (hasFormatting) {
                                                val annotatedText = remember(
                                                    para, matchingBookmarks, isDropCap, fontSize, fontFamily, onBgColor,
                                                    activeSearchQ, isActiveMatch
                                                ) {
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
                                                        baseTextColor = onBgColor,
                                                        searchQuery = activeSearchQ,
                                                        isActiveSearchMatch = isActiveMatch
                                                    )
                                                }
                                                Text(
                                                    text = annotatedText,
                                                    lineHeight = (fontSize * lineHeightMultiplier).sp,
                                                    letterSpacing = letterSpacing.sp,
                                                    textAlign = contentTextAlign,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                Text(
                                                    text = para,
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
                        }
                        item(key = "bottom-empty-space", contentType = "bottom_space") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .pointerInput(gestureDoubleTap, gestureSingleTap) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                executeGestureAction(gestureDoubleTap)
                                            },
                                            onTap = {
                                                if (isAutoScrolling) {
                                                    isAutoScrolling = false
                                                } else if (showSelectionMenu) {
                                                    showSelectionMenu = false
                                                    selectedText = ""
                                                } else {
                                                    executeGestureAction(gestureSingleTap)
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
            } else {
                // ═════════════════════════════════════════════════════════════════════
                // PAGED / PAGED_SCROLL MODE: Swipe horizontal pager
                // ═════════════════════════════════════════════════════════════════════
                if (pages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LaunchedEffect(pagerState.currentPage) {
                        try {
                            activeReleaseSelectionAction?.invoke()
                        } catch (_: Throwable) {}
                        activeReleaseSelectionAction = null
                        showSelectionMenu = false
                        selectedText = ""
                        val currentPage = pagerState.currentPage
                        if (currentPage in pages.indices) {
                            val progress = if (pages.size > 1) {
                                ((currentPage.toFloat() / (pages.size - 1)) * 100).toInt().coerceIn(0, 100)
                            } else 0
                            if (currentProgressPct != progress) {
                                currentProgressPct = progress
                            }
                            val (currentPageChapTitle, pageContent) = pages[currentPage]
                            if (activeChapterTitle != currentPageChapTitle) {
                                activeChapterTitle = currentPageChapTitle
                            }
                            val chapIdx = book.chapters.indexOfFirst { it.title == currentPageChapTitle }.coerceAtLeast(0)
                            if (speakingChapterIdx != chapIdx) {
                                speakingChapterIdx = chapIdx
                            }

                            // Extract the top text snippet of the currently visible page to anchor zoom & landscape changes
                            val pageBody = if (pageContent.startsWith("CHAPTER_START:::") || pageContent.startsWith("TITLE:::")) {
                                pageContent.substringAfterLast(":::")
                            } else {
                                pageContent
                            }
                            val cleanSnippet = pageBody.trim().take(60)
                            if (!isPinching) {
                                if (cleanSnippet.isNotBlank()) {
                                    anchorSnippet = cleanSnippet
                                    currentTopSnippet = cleanSnippet
                                }
                                anchorChapterIdx = chapIdx
                                currentVisibleChapterIdx = chapIdx

                                val chap = book.chapters.getOrNull(chapIdx)
                                if (chap != null && cleanSnippet.isNotBlank()) {
                                    val foundPara = chap.paragraphs.indexOfFirst { it.contains(cleanSnippet.take(25)) }
                                    if (foundPara >= 0) {
                                        anchorParaIdx = foundPara
                                        currentVisibleParaIdx = foundPara
                                    }
                                }
                            }

                            delay(800)
                            onPositionChange(chapIdx, currentPage, 0, progress)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(gestureDoubleTap, gestureSingleTap) {
                                detectTapGestures(
                                    onDoubleTap = { executeGestureAction(gestureDoubleTap) },
                                    onTap = { executeGestureAction(gestureSingleTap) }
                                )
                            }
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            key = { pageIdx -> "paged_page_$pageIdx" },
                            beyondViewportPageCount = 1,
                            pageSpacing = (horizontalPadding * 1.5f).dp.coerceAtLeast(32.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (isLandscape) {
                                        if (isUiVisible) statusBarTopInset + 48.dp + (verticalPadding * 0.25f).dp
                                        else statusBarTopInset + 16.dp + (verticalPadding * 0.25f).dp
                                    } else {
                                        if (isUiVisible || readingMode == ReadingMode.PAGED) statusBarTopInset + 54.dp + (verticalPadding * 0.35f).dp
                                        else statusBarTopInset + 24.dp + (verticalPadding * 0.35f).dp
                                    },
                                    bottom = if (isLandscape) {
                                        if (isUiVisible) progressBottomInset + 54.dp + (verticalPadding * 0.25f).dp
                                        else progressBottomInset + 12.dp + (verticalPadding * 0.25f).dp
                                    } else {
                                        if (readingMode == ReadingMode.PAGED) progressBottomInset + 52.dp + (verticalPadding * 0.35f).dp
                                        else if (isUiVisible) progressBottomInset + 50.dp + (verticalPadding * 0.35f).dp
                                        else progressBottomInset + 18.dp + (verticalPadding * 0.35f).dp
                                    },
                                    start = effectiveStartPadding,
                                    end = effectiveEndPadding
                                )
                        ) { pageIdx ->
                            val (chapTitle, content) = pages[pageIdx]
                            val isChapterHeaderPage = content.startsWith("CHAPTER_START:::") || content.startsWith("TITLE:::")
                            val (cTitle, cSub, bodyText) = if (isChapterHeaderPage) {
                                val prefix = if (content.startsWith("CHAPTER_START:::")) "CHAPTER_START:::" else "TITLE:::"
                                val rawParts = content.removePrefix(prefix).split(":::")
                                Triple(
                                    rawParts.getOrNull(0)?.ifBlank { chapTitle } ?: chapTitle,
                                    rawParts.getOrNull(1) ?: "",
                                    rawParts.getOrNull(2) ?: ""
                                )
                            } else {
                                Triple("", "", "")
                            }

                            if (isChapterHeaderPage && bodyText.isBlank()) {
                                // Title-only page (e.g. part divider or section header without text)
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(gestureDoubleTap, gestureSingleTap) {
                                            detectTapGestures(
                                                onDoubleTap = { executeGestureAction(gestureDoubleTap) },
                                                onTap = { showControls = !showControls }
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
                                        .pointerInput(gestureDoubleTap, gestureSingleTap) {
                                            detectTapGestures(
                                                onDoubleTap = { executeGestureAction(gestureDoubleTap) },
                                                onTap = { showControls = !showControls }
                                            )
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

                                val annotatedContent = remember(pageText, matchingBookmarks, fontSize, fontFamily, onBgColor, isDropCap, activeSearchQ, isActiveMatch) {
                                    buildHighlightedAnnotatedString(
                                        text = pageText,
                                        matchingBookmarks = matchingBookmarks,
                                        onBookmarkClick = { bm ->
                                            selectedBookmarkForModal = bm
                                            showBookmarkDetailModal = true
                                        },
                                        isDropCap = isDropCap,
                                        baseFontFamily = fontFamily,
                                        baseFontSize = fontSize.sp,
                                        baseTextColor = onBgColor,
                                        searchQuery = activeSearchQ,
                                        isActiveSearchMatch = isActiveMatch
                                    )
                                }

                                var pagedTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                val pagedScrollModifier = if (readingMode == ReadingMode.PAGED_SCROLL) {
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
                                                onDoubleTap = { executeGestureAction(gestureDoubleTap) },
                                                onTap = {
                                                    if (isAutoScrolling) {
                                                        isAutoScrolling = false
                                                    } else if (showSelectionMenu) {
                                                        showSelectionMenu = false
                                                        selectedText = ""
                                                    } else {
                                                        executeGestureAction(gestureSingleTap)
                                                    }
                                                }
                                            )
                                        }
                                        .then(
                                            if (showTtsDock || isTtsSpeaking) {
                                                val pagedChapIdx = book.chapters.indexOfFirst { it.title == chapTitle }.coerceAtLeast(0)
                                                Modifier.clickable {
                                                    speakingChapterIdx = pagedChapIdx
                                                    speakingParaIdx = 0
                                                    showTtsDock = true
                                                    speakNextPara.value()
                                                }
                                            } else Modifier
                                        )
                                ) {
                                    if (isChapterHeaderPage) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp, bottom = 18.dp),
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
                                                fontWeight = FontWeight.Medium,
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

                                    SelectionContainer(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = annotatedContent,
                                            onTextLayout = { layoutResult ->
                                                pagedTextLayoutResult = layoutResult
                                                if (pageIdx == pagerState.currentPage) {
                                                    activePagedTextLayoutResult = layoutResult
                                                }
                                            },
                                            fontFamily = fontFamily,
                                            fontSize = fontSize.sp,
                                            lineHeight = (fontSize * lineHeightMultiplier).sp,
                                            letterSpacing = letterSpacing.sp,
                                            textAlign = contentTextAlign,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onGloballyPositioned { coords ->
                                                    if (pageIdx == pagerState.currentPage) {
                                                        activeTextBoundsInRoot = coords.boundsInRoot()
                                                    }
                                                }
                                        )
                                    }
                                    val tapSpacerModifier = if (readingMode == ReadingMode.PAGED_SCROLL) {
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 40.dp)
                                    } else {
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    }
                                    Spacer(
                                        modifier = tapSpacerModifier
                                            .pointerInput(gestureDoubleTap, gestureSingleTap) {
                                                detectTapGestures(
                                                    onDoubleTap = { executeGestureAction(gestureDoubleTap) },
                                                    onTap = {
                                                        if (isAutoScrolling) {
                                                            isAutoScrolling = false
                                                        } else if (showSelectionMenu) {
                                                            showSelectionMenu = false
                                                            selectedText = ""
                                                        } else {
                                                            executeGestureAction(gestureSingleTap)
                                                        }
                                                    }
                                                )
                                            }
                                    )
                                    if (readingMode == ReadingMode.PAGED_SCROLL) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                        }

                        // Page Navigation Tap Zones
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .align(Alignment.CenterStart)
                                .pointerInput(isAutoScrolling, gestureDoubleTap) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            executeGestureAction(gestureDoubleTap)
                                        },
                                        onTap = {
                                            if (isAutoScrolling) {
                                                isAutoScrolling = false
                                            } else if (pagerState.currentPage > 0) {
                                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                            }
                                        }
                                    )
                                }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .align(Alignment.CenterEnd)
                                .pointerInput(isAutoScrolling, gestureDoubleTap) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            executeGestureAction(gestureDoubleTap)
                                        },
                                        onTap = {
                                            if (isAutoScrolling) {
                                                isAutoScrolling = false
                                            } else if (pagerState.currentPage < pages.size - 1) {
                                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }

        // TOP HEADER BAR: Distraction-Free Header (Zero buttons, clean title & chapter)
        ReaderTopBar(
            isUiVisible = isUiVisible && !isAutoScrolling,
            readingMode = readingMode,
            voiceState = voiceState,
            showInBookSearchDialog = showInBookSearchDialog,
            bookTitle = book.title,
            activeChapterTitle = activeChapterTitle,
            assistantOrbStyle = assistantOrbStyle,
            disableAi = disableAi,
            onOpenToc = { showTocSheet = true },
            onOpenAssistant = { showAssistantChatSheet = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Highlight & Note Inspector / Editor Sheet
        if (showBookmarkDetailModal && selectedBookmarkForModal != null) {
            BookmarkDetailModal(
                bookmark = selectedBookmarkForModal!!,
                onDismiss = { showBookmarkDetailModal = false },
                onUpdateBookmark = { updated ->
                    selectedBookmarkForModal = updated
                    onUpdateBookmark(updated)
                },
                onDeleteBookmark = { id ->
                    onRemoveBookmark(id)
                    Toast.makeText(context, "Highlight removed", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // UNIFIED FLOATING BOTTOM DOCK: Progress Bar Extension + Navigation Bar
        ReaderBottomDock(
            isUiVisible = isUiVisible && !isAutoScrolling,
            readingMode = readingMode,
            voiceState = voiceState,
            progressBottomInset = progressBottomInset,
            showNavBarInReader = showNavBarInReader,
            currentProgressPct = currentProgressPct,
            currentReadTillPct = readTillMapState?.value?.get(book.id) ?: currentProgressPct,
            readTimeLeft = book.readTimeLeft,
            activeChapterTitle = activeChapterTitle,
            onToggleNavBar = onToggleNavBar,
            onForceSavePosition = {
                val currentChapIdx = speakingChapterIdx.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                val currentPageIdx = if (readingMode != ReadingMode.SCROLL) pagerState.currentPage else 0
                val currentScrollPos = if (readingMode == ReadingMode.SCROLL) listState.firstVisibleItemIndex else 0

                repository?.forceSetLastReadPosition(
                    book.id,
                    currentChapIdx,
                    currentPageIdx,
                    currentScrollPos,
                    currentProgressPct
                )
                onPositionChange(currentChapIdx, currentPageIdx, currentScrollPos, currentProgressPct)
                if (readingMode != ReadingMode.SCROLL) {
                    "Saved page ${currentPageIdx + 1} (${currentProgressPct}%)"
                } else {
                    "Saved paragraph ${currentScrollPos + 1} (${currentProgressPct}%)"
                }
            },
            onBackToLibrary = {
                stopAllAudio()
                onBackToLibrary()
            },
            onOpenToc = { showTocSheet = true },
            onOpenBookmarks = onOpenBookmarks,
            onOpenReadingSettings = onOpenAppearance,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Floating Assistant Orb (movable, smart inward arc, docked dot, renders on top of bottom docks)
        if (showFloatingAssistant && !isFullscreen && !isAutoScrolling && assistantOrbStyle != "TOP_BAR_BUTTON") {
            FloatingAssistantOrb(
                readingMode = readingMode,
                onToggleReadingMode = {
                    val nextMode = when (readingMode) {
                        ReadingMode.SCROLL -> ReadingMode.PAGED
                        ReadingMode.PAGED -> ReadingMode.PAGED_SCROLL
                        ReadingMode.PAGED_SCROLL -> ReadingMode.SCROLL
                    }
                    onModeChange(nextMode)
                },
                isTtsPlaying = isTtsSpeaking,
                onToggleTts = {
                    showTtsDock = true
                    if (isTtsSpeaking) {
                        stopAllAudio()
                    } else {
                        triggerSpeakNextPara()
                    }
                },
                onOpenSearch = { showInBookSearchDialog = true },
                onOpenCharacters = { showCharacterGuideSheet = true },
                onOpenToc = { showTocSheet = true },
                onOpenNote = onOpenBookmarks,
                onOpenSettings = onOpenAppearance,
                isDarkTheme = currentThemeVariant == ThemeVariant.DARK,
                onToggleThemeMode = {
                    val nextVariant = if (currentThemeVariant == ThemeVariant.DARK) ThemeVariant.LIGHT else ThemeVariant.DARK
                    onThemeVariantChange(nextVariant)
                },
                onStartVoiceListening = {
                    if (disableAi) {
                        Toast.makeText(context, "AI Assistant is disabled in Settings", Toast.LENGTH_SHORT).show()
                    } else {
                        assistantAutoStartVoice = true
                        showAssistantChatSheet = true
                    }
                },
                onDismissOrb = {
                    onToggleFloatingAssistant(false)
                    Toast.makeText(context, "Assistant dismissed. Re-enable anytime from Settings or triple-tap.", Toast.LENGTH_SHORT).show()
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
                onOpenAdvancedSettings = onOpenAdvancedSettings,
                onRetry = {
                    coroutineScope.launch {
                        voiceState = AssistantVoiceState.THINKING
                        val resp = assistantService.retryLastQuery()
                        if (resp != null) {
                            voiceResponse = resp.answerText
                            resp.executedAction?.let { handleAssistantAction(it) }
                        } else {
                            voiceResponse = "No query to retry."
                        }
                        voiceState = AssistantVoiceState.RESPONDING
                    }
                },
                isSpoilerShield = spoilerShield,
                onToggleSpoilerShield = {
                    repository?.setSpoilerShield(!spoilerShield)
                },
                orbSize = orbSize,
                orbMenuSize = orbMenuSize,
                orbEdgeSnap = orbEdgeSnap,
                orbColor = orbColor,
                orbOpacity = orbOpacity,
                savedX = if (isLandscape) orbLandscapeX else orbPortraitX,
                savedY = if (isLandscape) orbLandscapeY else orbPortraitY,
                onSavePosition = { x, y, land ->
                    repository?.saveOrbPosition(x, y, land)
                },
                modifier = Modifier.zIndex(150f)
            )
        }

        // Bottom offsets for floating docks & selection pill
        val dockHeight = if (showNavBarInReader) 94.dp else 44.dp
        val selectionMenuBottomPadding = if (isUiVisible) {
            progressBottomInset + 10.dp + dockHeight + 14.dp
        } else {
            progressBottomInset + 20.dp
        }

        // EXPANDABLE FLOATING TTS AUDIO PLAYER DOCK
        ReaderTtsDock(
            showTtsDock = showTtsDock && !isAutoScrolling,
            speakingParaIdx = speakingParaIdx,
            isTtsSpeaking = isTtsSpeaking,
            ttsSpeed = ttsSpeed,
            bottomPadding = selectionMenuBottomPadding,
            onClose = {
                showTtsDock = false
                stopAllAudio()
            },
            onPrevPara = {
                LuminaAudioService.prevParagraph(context)
            },
            onTogglePlayPause = {
                if (isTtsSpeaking) {
                    LuminaAudioService.togglePlayPause(context)
                } else {
                    triggerSpeakNextPara()
                }
            },
            onNextPara = {
                LuminaAudioService.nextParagraph(context)
            },
            onSpeedChange = { sp ->
                ttsSpeed = sp
                repository?.setTtsSpeed(sp)
                if (isTtsSpeaking) {
                    val chapter = book.chapters.getOrNull(speakingChapterIdx)
                    if (chapter != null) {
                        LuminaAudioService.startOrUpdate(
                            context = context,
                            bookId = book.id,
                            bookTitle = book.title,
                            chapterIndex = speakingChapterIdx,
                            chapterTitle = chapter.title,
                            paragraphIndex = speakingParaIdx,
                            paragraphs = chapter.paragraphs,
                            isEdgeTts = ttsEngine == "EDGE_NEURAL",
                            voice = ttsEdgeVoice,
                            speed = sp
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(185f)
        )

        // AUTO-SCROLL FLOATING INDICATOR PILL
        ReaderAutoScrollPill(
            isAutoScrolling = isAutoScrolling,
            currentSpeed = autoScrollSpeed,
            bottomPadding = if (isUiVisible && !isAutoScrolling) selectionMenuBottomPadding else progressBottomInset + 16.dp,
            onSpeedCycle = { repository?.cycleAutoScrollSpeed() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(185f)
        )

        val selectedParagraphText = remember(selectedText, selectedChapterTitle, activeChapterTitle, book.chapters) {
            if (selectedText.isBlank()) ""
            else {
                val targetTitle = selectedChapterTitle.ifBlank { activeChapterTitle }
                val chap = book.chapters.find { it.title.equals(targetTitle, ignoreCase = true) }
                    ?: book.chapters.getOrNull(speakingChapterIdx)
                val clean = selectedText.trim()
                chap?.paragraphs?.find { p ->
                    p.contains(clean, ignoreCase = true) || (clean.length > 20 && p.contains(clean.take(20), ignoreCase = true))
                } ?: ""
            }
        }

        // Selection Menu Pill (Rendered strictly on top of bottom dock, zIndex = 200f)
        ReaderSelectionMenu(
            showSelectionMenu = showSelectionMenu,
            selectedText = selectedText,
            paragraphText = selectedParagraphText,
            selectedChapterTitle = selectedChapterTitle,
            activeChapterTitle = activeChapterTitle,
            activePage = if (readingMode != ReadingMode.SCROLL) pagerState.currentPage + 1 else book.currentPage + 1,
            author = book.author,
            bookTitle = book.title,
            activeBookmark = activeBookmark,
            bottomPadding = selectionMenuBottomPadding,
            onAddBookmark = { text, color, page, note -> onAddBookmark(text, color, page, note) },
            onRemoveBookmark = { id -> onRemoveBookmark(id) },
            onOpenNoteModal = { mark ->
                selectedBookmarkForModal = mark
                showBookmarkDetailModal = true
            },
            onReadFromHere = { text ->
                val targetChapIdx = if (selectedChapterTitle.isNotBlank()) {
                    book.chapters.indexOfFirst { it.title == selectedChapterTitle }.takeIf { it >= 0 } ?: speakingChapterIdx
                } else {
                    speakingChapterIdx
                }
                speakingChapterIdx = targetChapIdx
                val currChap = book.chapters.getOrNull(targetChapIdx)
                val pIdx = currChap?.paragraphs?.indexOfFirst { it.contains(text, ignoreCase = true) } ?: -1
                if (pIdx >= 0) {
                    speakingParaIdx = pIdx
                }
                showTtsDock = true
                speakNextPara.value()
            },
            onLookupWord = { word -> onLookupWord(word) },
            onDismiss = {
                try {
                    activeReleaseSelectionAction?.invoke()
                } catch (_: Throwable) {}
                activeReleaseSelectionAction = null
                showSelectionMenu = false
                selectedText = ""
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(200f)
        )

        // Table of Contents Sheet
        if (showTocSheet) {
            TableOfContentsSheet(
                chapters = book.chapters,
                bookTitle = book.title,
                currentChapterIndex = book.chapters.indexOfFirst { it.title == activeChapterTitle }.coerceAtLeast(0),
                onSelectChapter = { idx ->
                    stopAllAudio()
                    activeChapterTitle = book.chapters.getOrNull(idx)?.title ?: "Chapter ${idx + 1}"
                    speakingChapterIdx = idx
                    speakingParaIdx = 0
                    if (readingMode == ReadingMode.SCROLL) {
                        var targetIdx = 0
                        for (c in 0 until idx) {
                            targetIdx += (book.chapters[c].paragraphs.size + 1)
                        }
                        coroutineScope.launch {
                            if (kotlin.math.abs(listState.firstVisibleItemIndex - targetIdx) > 3) {
                                listState.scrollToItem(targetIdx)
                            } else {
                                listState.animateScrollToItem(targetIdx)
                            }
                        }
                    } else {
                        val targetPage = pages.indexOfFirst { it.first == activeChapterTitle }.coerceAtLeast(0)
                        coroutineScope.launch {
                            if (kotlin.math.abs(pagerState.currentPage - targetPage) > 3) {
                                pagerState.scrollToPage(targetPage)
                            } else {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    }
                    showTocSheet = false
                },
                onDismiss = { showTocSheet = false }
            )
        }

        // AI Assistant Interactive Chat Sheet (Text Typing, Prompt Chips & Voice)
        AssistantChatSheet(
            isOpen = showAssistantChatSheet,
            onDismiss = {
                showAssistantChatSheet = false
                assistantAutoStartVoice = false
            },
            bookTitle = book.title,
            activeChapterTitle = activeChapterTitle,
            knownContext = book.chapters.firstOrNull { it.title == activeChapterTitle }?.paragraphs?.take(18)?.joinToString("\n") ?: "",
            apiKey = geminiApiKey,
            provider = aiProvider,
            baseUrl = aiBaseUrl,
            modelName = aiModel,
            spoilerShield = spoilerShield,
            disableStt = disableStt,
            disableAi = disableAi,
            autoStartVoice = assistantAutoStartVoice && autoStartMic,
            languageCode = repository?.getEffectiveLanguage() ?: book.language,
            assistantService = assistantService,
            onExecuteAction = { action -> handleAssistantAction(action) }
        )

        // AI Character Lore & Spoiler-Shielded Guide Sheet
        val charactersMap by (repository?.characters?.collectAsState(initial = emptyMap()) ?: remember { mutableStateOf(emptyMap()) })
        val bookCharacters = remember(charactersMap, book.id) {
            charactersMap[book.id] ?: emptyList()
        }

        LaunchedEffect(book.id) {
            repository?.loadCharacters(book.id)
        }

        if (showCharacterGuideSheet && repository != null) {
            CharacterGuideSheet(
                book = book,
                currentChapterIndex = if (isPagedReading) speakingChapterIdx else listState.firstVisibleItemIndex.let { fIdx ->
                    var accum = 0
                    var chapIdx = 0
                    for (c in book.chapters) {
                        accum += c.paragraphs.size + 1
                        if (fIdx < accum) break
                        chapIdx++
                    }
                    chapIdx.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                },
                isSpoilerShield = spoilerShield,
                geminiApiKey = geminiApiKey,
                aiProvider = aiProvider,
                aiModel = aiModel,
                customEndpoint = aiBaseUrl,
                repository = repository,
                onDismiss = { showCharacterGuideSheet = false }
            )
        }

        val jumpToMatch: (SceneMatch) -> Unit = { match ->
            val cIdx = if (match.chapterIndex in book.chapters.indices) {
                match.chapterIndex
            } else {
                book.chapters.indexOfFirst { it.title.equals(match.chapterTitle, ignoreCase = true) }
            }
            if (cIdx != -1) {
                speakingChapterIdx = cIdx
                speakingParaIdx = match.paragraphIndex
                activeChapterTitle = book.chapters[cIdx].title
                if (readingMode == ReadingMode.SCROLL) {
                    var itemIdx = 0
                    for (i in 0 until cIdx) {
                        itemIdx += (book.chapters[i].paragraphs.size + 1)
                    }
                    itemIdx += (match.paragraphIndex + 1)
                    coroutineScope.launch {
                        val focusOffsetPx = (screenHeightPx * 0.22f).roundToInt().coerceIn(120, 450)
                        listState.animateScrollToItem(itemIdx.coerceAtLeast(0), scrollOffset = -focusOffsetPx)
                    }
                } else {
                    val cleanSnippet = match.snippet.replace("...", "").trim().take(15)
                    val pageIdx = pages.indexOfFirst {
                        it.first.equals(match.chapterTitle, ignoreCase = true) &&
                        it.second.contains(cleanSnippet, ignoreCase = true)
                    }.let { if (it != -1) it else pages.indexOfFirst { p -> p.first.equals(match.chapterTitle, ignoreCase = true) } }

                    if (pageIdx != -1) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pageIdx) }
                    }
                }
            }
        }

        // In-Book Search Top Bar (Adobe-style one-liner with Plain vs Semantic toggle and Prev/Next navigation)
        InBookSearchDialog(
            showDialog = showInBookSearchDialog,
            book = book,
            repository = repository,
            onDismiss = { showInBookSearchDialog = false },
            onJumpToMatch = jumpToMatch,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Transient Font Size Pill (Disabled per user request: no toast during pinching)
        AnimatedVisibility(
            visible = false,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(300f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = transientFontBadge ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
}
