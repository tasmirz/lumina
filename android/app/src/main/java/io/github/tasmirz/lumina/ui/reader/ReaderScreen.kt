package io.github.tasmirz.lumina.ui.reader

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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.ui.input.pointer.PointerInputChange
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
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.util.Locale

enum class InBookSearchMode {
    PLAIN,
    SEMANTIC
}

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
    baseTextColor: Color,
    searchQuery: String = "",
    isActiveSearchMatch: Boolean = false
): AnnotatedString {
    // Fast path: cached lookup for standard paragraphs without highlights, drop caps, or search query
    if (!isDropCap && matchingBookmarks.isEmpty() && searchQuery.isBlank()) {
        val cacheKey = "${text.hashCode()}_${baseFontSize.value}_${baseFontFamily.hashCode()}_${baseTextColor.value}"
        val cached = AnnotatedTextCache.get(cacheKey)
        if (cached != null) return cached
        val res = buildAnnotatedString {
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
        AnnotatedTextCache.put(cacheKey, res)
        return res
    }

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

        // In-book search matches highlighting
        if (searchQuery.isNotBlank() && text.contains(searchQuery, ignoreCase = true)) {
            val q = searchQuery.trim()
            if (q.isNotEmpty()) {
                var searchIndex = 0
                while (searchIndex < text.length) {
                    val idx = text.indexOf(q, searchIndex, ignoreCase = true)
                    if (idx == -1) break
                    val end = (idx + q.length).coerceAtMost(text.length)
                    val matchBg = if (isActiveSearchMatch) Color(0xBBF59E0B) else Color(0x55FFC107)
                    addStyle(
                        SpanStyle(
                            background = matchBg,
                            fontWeight = if (isActiveSearchMatch) FontWeight.Bold else FontWeight.Normal
                        ),
                        start = idx,
                        end = end
                    )
                    searchIndex = end
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
    onAddBookmark: (String, HighlightColor, Int) -> Unit = { _, _, _ -> },
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
    var isScrubbingProgress by remember { mutableStateOf(false) }
    var scrubProgressPct by remember { mutableFloatStateOf(book.progress.toFloat()) }
    var currentProgressPct by rememberSaveable(book.id) { mutableIntStateOf(book.progress) }

    // TTS Reader states
    var isTtsSpeaking by remember { mutableStateOf(false) }
    var showTtsDock by rememberSaveable { mutableStateOf(false) }
    var ttsSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var speakingChapterIdx by rememberSaveable { mutableIntStateOf(book.currentChapter) }
    var speakingParaIdx by rememberSaveable { mutableIntStateOf(0) }

    // Auto-scroll state
    var isAutoScrolling by remember { mutableStateOf(false) }

    // Tap tracking for double-tap (autoscroll) and triple-tap (summon orb)
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }

    // Repository states
    val disableAiState = repository?.disableAi?.collectAsState(initial = false)
    val disableAi = disableAiState?.value ?: false

    val disableTtsState = repository?.disableTts?.collectAsState(initial = false)
    val disableTts = disableTtsState?.value ?: false

    val disableSttState = repository?.disableStt?.collectAsState(initial = false)
    val disableStt = disableSttState?.value ?: false

    var showAssistantChatSheet by rememberSaveable { mutableStateOf(false) }
    var assistantAutoStartVoice by rememberSaveable { mutableStateOf(false) }

    // TextToSpeech Engine
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    val isTtsInitialized = remember { mutableStateOf(false) }

    // Floating Voice Assistant State & Service
    val assistantService = remember { AssistantService(context) }
    var voiceState by remember { mutableStateOf(AssistantVoiceState.IDLE) }
    var voiceQuery by remember { mutableStateOf("") }
    var voiceResponse by remember { mutableStateOf("") }

    val speakNextPara = rememberUpdatedState {
        if (disableTts) {
            Toast.makeText(context, "TTS Audio Engine is disabled in Settings", Toast.LENGTH_SHORT).show()
            isTtsSpeaking = false
            return@rememberUpdatedState
        }
        val tts = ttsRef.value
        if (tts != null && isTtsInitialized.value) {
            tts.setSpeechRate(ttsSpeed)
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

    DisposableEffect(context, disableTts) {
        if (disableTts) {
            ttsRef.value?.stop()
            ttsRef.value?.shutdown()
            ttsRef.value = null
            isTtsInitialized.value = false
            onDispose {}
        } else {
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
            }
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
                android.util.Log.d("LuminaToolbar", "hide() called. suppress=$suppressToolbarHide, selectedText='$selectedText', elapsed=${System.currentTimeMillis() - lastSelectionTimestamp}")
                if (System.currentTimeMillis() - lastSelectionTimestamp < 500L) return
                if (!suppressToolbarHide && selectedText.isBlank()) {
                    showSelectionMenu = false
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

    // Prepared pages for Paged Mode with smart character/sentence budgeting so text never overflows
    // Only computed when in PAGED or PAGED_SCROLL mode to eliminate overhead in Continuous Scroll mode
    val pages = remember(book.id, book.chapters.size, fontSize, readingMode, isLandscape) {
        if (isPagedReading) {
            PageCache.getOrCompute(book.id, book.chapters, fontSize, isLandscape, isStrictPaged)
        } else {
            emptyList()
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = book.scrollPos)
    val pagerState = rememberPagerState(
        initialPage = book.currentPage.coerceIn(0, maxOf(pages.size - 1, 0)),
        pageCount = { pages.size }
    )

    fun executeGestureAction(action: GestureAction, chapIdx: Int = speakingChapterIdx, pIdx: Int = speakingParaIdx) {
        when (action) {
            GestureAction.TOGGLE_AUTOSCROLL -> {
                isAutoScrolling = !isAutoScrolling
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
                speakNextPara.value()
            }
            GestureAction.IN_BOOK_SEARCH -> {
                showInBookSearchDialog = true
            }
            GestureAction.PREVIOUS_CHAPTER -> {
                val prev = (chapIdx - 1).coerceAtLeast(0)
                speakingChapterIdx = prev
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
                val next = (chapIdx + 1).coerceAtMost(book.chapters.size - 1)
                speakingChapterIdx = next
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
                onAddBookmark(preview, HighlightColor.GOLD, activePage)
                Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
            }
            GestureAction.NONE -> {}
        }
    }

    // Auto-scroll loop for hands-free reading in Scroll Mode
    LaunchedEffect(isAutoScrolling, autoScrollSpeed) {
        if (isAutoScrolling && readingMode == ReadingMode.SCROLL) {
            while (isAutoScrolling) {
                listState.scrollBy(2f)
                val delayMs = (25L / autoScrollSpeed.coerceIn(0.5f, 3.0f)).toLong().coerceAtLeast(8L)
                kotlinx.coroutines.delay(delayMs)
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
                        speakNextPara.value()
                    }
                    "stop", "pause" -> {
                        ttsRef.value?.stop()
                        isTtsSpeaking = false
                    }
                    "next" -> {
                        speakingParaIdx++
                        speakNextPara.value()
                    }
                    "prev", "previous" -> {
                        speakingParaIdx = (speakingParaIdx - 1).coerceAtLeast(0)
                        speakNextPara.value()
                    }
                }
            }
            is AssistantAction.ToggleAutoScroll -> {
                if (readingMode == ReadingMode.SCROLL) {
                    isAutoScrolling = action.enable
                    Toast.makeText(context, if (action.enable) "Auto-scroll started" else "Auto-scroll stopped", Toast.LENGTH_SHORT).show()
                }
            }
            is AssistantAction.NextChapter -> {
                val next = (speakingChapterIdx + 1).coerceAtMost(book.chapters.size - 1)
                speakingChapterIdx = next
                speakingParaIdx = 0
                val chapTitle = book.chapters.getOrNull(next)?.title ?: "Chapter ${next + 1}"
                activeChapterTitle = chapTitle
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
                coroutineScope.launch {
                    var idx = 0
                    for (i in 0 until prev) {
                        idx += (book.chapters[i].paragraphs.size + 1)
                    }
                    listState.animateScrollToItem(idx)
                }
            }
            is AssistantAction.NavigateChapter -> {
                val target = action.targetIndex.coerceIn(0, book.chapters.size - 1)
                speakingChapterIdx = target
                speakingParaIdx = 0
                val chapTitle = book.chapters.getOrNull(target)?.title ?: "Chapter ${target + 1}"
                activeChapterTitle = chapTitle
                coroutineScope.launch {
                    var idx = 0
                    for (i in 0 until target) {
                        idx += (book.chapters[i].paragraphs.size + 1)
                    }
                    listState.animateScrollToItem(idx)
                }
            }
            is AssistantAction.ToggleTts -> {
                if (action.play) {
                    showTtsDock = true
                    speakNextPara.value()
                } else {
                    ttsRef.value?.stop()
                    isTtsSpeaking = false
                }
            }
            is AssistantAction.AddNote -> {
                val activePage = if (readingMode != ReadingMode.SCROLL) pagerState.currentPage + 1 else book.currentPage + 1
                onAddBookmark(action.noteContent, HighlightColor.GOLD, activePage)
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
                .pointerInput(fontSize) {
                    awaitEachGesture {
                        var cumulativeZoom = 1.0f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val p0 = event.changes[0].position
                                val p1 = event.changes[1].position
                                val prevP0 = event.changes[0].previousPosition
                                val prevP1 = event.changes[1].previousPosition
                                val currentDist = (p0 - p1).getDistance()
                                val prevDist = (prevP0 - prevP1).getDistance()
                                if (prevDist > 0f) {
                                    val scale = currentDist / prevDist
                                    cumulativeZoom *= scale
                                    if (cumulativeZoom > 1.15f) {
                                        val newSize = (fontSize + 1).coerceAtMost(36)
                                        if (newSize != fontSize) {
                                            onFontSizeChange(newSize)
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        cumulativeZoom = 1.0f
                                    } else if (cumulativeZoom < 0.85f) {
                                        val newSize = (fontSize - 1).coerceAtLeast(12)
                                        if (newSize != fontSize) {
                                            onFontSizeChange(newSize)
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        cumulativeZoom = 1.0f
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
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

        val progressBottomInset = max(
            max(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                WindowInsets.mandatorySystemGestures.asPaddingValues().calculateBottomPadding()
            ),
            16.dp
        )
        val statusBarTopInset = max(
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            24.dp
        )

        if (readingMode == ReadingMode.SCROLL) {
                    // Continuous Vertical Scroll Mode across all chapters
                    val totalParas = remember(book.id, book.chapters.size) { maxOf(book.chapters.sumOf { it.paragraphs.size }, 1) }

                    // Precompute cumulative chapter paragraph start offsets for instant O(log N) lookup
                    val chapterCumulativeParaOffsets = remember(book.id, book.chapters.size) {
                        val offsets = IntArray(book.chapters.size)
                        var accum = 0
                        for (i in book.chapters.indices) {
                            offsets[i] = accum
                            accum += (book.chapters[i].paragraphs.size + 1)
                        }
                        offsets
                    }

                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collectLatest { firstIndex ->
                                val binIdx = chapterCumulativeParaOffsets.binarySearch(firstIndex)
                                val currentChap = if (binIdx >= 0) binIdx else (-binIdx - 2).coerceIn(0, book.chapters.size - 1)

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
                            modifier = Modifier.fillMaxSize()
                        ) {
                        book.chapters.forEachIndexed { chapIdx, chapter ->
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
                                val chapterBookmarks = remember(bookmarksByChapter, chapter.title) {
                                    bookmarksByChapter[chapter.title.trim().lowercase()] ?: emptyList()
                                }
                                val matchingBookmarks = remember(para, chapterBookmarks) {
                                    if (chapterBookmarks.isEmpty()) emptyList()
                                    else chapterBookmarks.filter { b ->
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
                                    // Text Paragraph — Highlights applied strictly to text spans, no block background
                                    val isDropCap = chapIdx == 0 && pIdx == 0 && para.length > 40 && !para.startsWith("[IMG:")
                                    val onBgColor = MaterialTheme.colorScheme.onBackground
                                    val secColor = MaterialTheme.colorScheme.secondary
                                    val currentMatch = inBookSearchResults.getOrNull(inBookCurrentMatchIndex)
                                    val isActiveMatch = showInBookSearchDialog && inBookSearchQuery.isNotBlank() &&
                                        currentMatch != null && currentMatch.chapterIndex == chapIdx && currentMatch.paragraphIndex == pIdx
                                    val activeSearchQ = if (showInBookSearchDialog) inBookSearchQuery.trim() else ""

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

                                    val textLayoutRef = remember { AtomicReference<TextLayoutResult?>(null) }

                                    val paraBottomSpacing = (fontSize * 0.85f * paragraphSpacingMultiplier).dp.coerceIn(8.dp, 42.dp)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isBeingSpoken) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else Color.Transparent)
                                            .padding(top = 2.dp, bottom = paraBottomSpacing)
                                            .pointerInput(para, matchingBookmarks) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        // Consume long press so finger lift is not treated as a tap
                                                    },
                                                    onDoubleTap = {
                                                        executeGestureAction(gestureDoubleTap, chapIdx, pIdx)
                                                    },
                                                    onTap = { offset ->
                                                         if (showSelectionMenu) {
                                                             try {
                                                                 activeReleaseSelectionAction?.invoke()
                                                             } catch (_: Throwable) {}
                                                             activeReleaseSelectionAction = null
                                                             showSelectionMenu = false
                                                             selectedText = ""
                                                             return@detectTapGestures
                                                         }

                                                         // 1. Any tap immediately stops auto-scrolling
                                                         if (isAutoScrolling) {
                                                             isAutoScrolling = false
                                                             return@detectTapGestures
                                                         }

                                                         // 2. If TTS dock is open or speaking, tapping a paragraph executes configured TTS tap gesture
                                                         if (showTtsDock || isTtsSpeaking) {
                                                             executeGestureAction(gestureTtsTap, chapIdx, pIdx)
                                                             return@detectTapGestures
                                                         }

                                                         val layout = textLayoutRef.get()
                                                         var hitBookmark: Bookmark? = null
                                                         if (layout != null && matchingBookmarks.isNotEmpty()) {
                                                             val charOffset = layout.getOffsetForPosition(offset)
                                                             hitBookmark = matchingBookmarks.firstOrNull { bm ->
                                                                 val quote = bm.quote.trim()
                                                                 if (quote.isEmpty()) return@firstOrNull false
                                                                 var sIdx = 0
                                                                 while (sIdx < para.length) {
                                                                     val s = para.indexOf(quote, sIdx, ignoreCase = true)
                                                                     if (s == -1) break
                                                                     val e = (s + quote.length).coerceAtMost(para.length)
                                                                     if (charOffset in s until e) return@firstOrNull true
                                                                     sIdx = e
                                                                 }
                                                                 false
                                                             }
                                                         }

                                                         // Multi-tap detection for double-tap and triple-tap
                                                         val now = System.currentTimeMillis()
                                                         if (now - lastTapTime < 350) {
                                                             tapCount++
                                                         } else {
                                                             tapCount = 1
                                                         }
                                                         lastTapTime = now

                                                         if (tapCount == 3) {
                                                             tapCount = 0
                                                             executeGestureAction(gestureTripleTap, chapIdx, pIdx)
                                                         } else if (tapCount == 1) {
                                                             if (hitBookmark != null) {
                                                                 selectedBookmarkForModal = hitBookmark
                                                                 showBookmarkDetailModal = true
                                                             } else {
                                                                 executeGestureAction(gestureSingleTap, chapIdx, pIdx)
                                                             }
                                                         }
                                                    }
                                                )
                                            }
                                    ) {
                                        SelectionContainer(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = annotatedText,
                                                onTextLayout = { textLayoutRef.set(it) },
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
                            val currentPageChapTitle = pages.getOrNull(currentPage)?.first ?: "Chapter 1"
                            if (activeChapterTitle != currentPageChapTitle) {
                                activeChapterTitle = currentPageChapTitle
                            }
                            val chapIdx = book.chapters.indexOfFirst { it.title == currentPageChapTitle }.coerceAtLeast(0)
                            if (speakingChapterIdx != chapIdx) {
                                speakingChapterIdx = chapIdx
                            }
                            delay(800)
                            onPositionChange(chapIdx, currentPage, 0, progress)
                        }
                    }

                    val pagedBottomContentPadding = if (isUiVisible) {
                        val dockHeight = if (showNavBarInReader) 94.dp else 44.dp
                        progressBottomInset + dockHeight + 20.dp
                    } else {
                        progressBottomInset + 28.dp
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            key = { pageIdx -> "paged_page_$pageIdx" },
                            beyondViewportPageCount = 1,
                            pageSpacing = (horizontalPadding * 1.5f).dp.coerceAtLeast(32.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (isUiVisible) {
                                        statusBarTopInset + 50.dp + (verticalPadding * 0.4f).dp
                                    } else {
                                        statusBarTopInset + 10.dp + (verticalPadding * 0.4f).dp
                                    },
                                    bottom = if (readingMode == ReadingMode.PAGED) {
                                        progressBottomInset + 56.dp + (verticalPadding * 0.4f).dp
                                    } else if (isUiVisible) {
                                        progressBottomInset + 48.dp + (verticalPadding * 0.4f).dp
                                    } else {
                                        progressBottomInset + 20.dp + (verticalPadding * 0.4f).dp
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
                                        .then(pagedScrollModifier)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .pointerInput(pageText, matchingBookmarks) {
                                                val pagedChapIdx = book.chapters.indexOfFirst { it.title == chapTitle }.coerceAtLeast(0)
                                                detectTapGestures(
                                                    onLongPress = {
                                                        // Consume long press so finger lift is not treated as a tap
                                                    },
                                                    onDoubleTap = {
                                                        executeGestureAction(gestureDoubleTap, pagedChapIdx, 0)
                                                    },
                                                    onTap = { offset ->
                                                    if (System.currentTimeMillis() - lastSelectionTimestamp < 500L) {
                                                        return@detectTapGestures
                                                    }
                                                    // 1. If TTS dock is open or speaking, tapping starts reading from this chapter/paragraph
                                                    if (showTtsDock || isTtsSpeaking) {
                                                        val chap = book.chapters.getOrNull(pagedChapIdx)
                                                        val layout = pagedTextLayoutResult
                                                        var targetPIdx = 0
                                                        if (layout != null && chap != null && pageText.isNotEmpty()) {
                                                            val charOffset = layout.getOffsetForPosition(offset).coerceIn(0, pageText.length)
                                                            val snippet = pageText.substring(
                                                                (charOffset - 25).coerceAtLeast(0),
                                                                (charOffset + 35).coerceAtMost(pageText.length)
                                                            ).trim()
                                                            val matchIdx = chap.paragraphs.indexOfFirst { it.contains(snippet, ignoreCase = true) }
                                                            if (matchIdx >= 0) {
                                                                targetPIdx = matchIdx
                                                            }
                                                        }
                                                        speakingChapterIdx = pagedChapIdx
                                                        speakingParaIdx = targetPIdx
                                                        showTtsDock = true
                                                        speakNextPara.value()
                                                        return@detectTapGestures
                                                    }

                                                    val layout = pagedTextLayoutResult
                                                    var hitBookmark: Bookmark? = null
                                                    if (layout != null && matchingBookmarks.isNotEmpty()) {
                                                        val charOffset = layout.getOffsetForPosition(offset)
                                                        hitBookmark = matchingBookmarks.firstOrNull { bm ->
                                                            val quote = bm.quote.trim()
                                                            if (quote.isEmpty()) return@firstOrNull false
                                                            var sIdx = 0
                                                            while (sIdx < pageText.length) {
                                                                val s = pageText.indexOf(quote, sIdx, ignoreCase = true)
                                                                if (s == -1) break
                                                                val e = (s + quote.length).coerceAtMost(pageText.length)
                                                                if (charOffset in s until e) return@firstOrNull true
                                                                sIdx = e
                                                            }
                                                            false
                                                        }
                                                    }

                                                    // Multi-tap detection for triple-tap (summon orb)
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastTapTime < 350) {
                                                        tapCount++
                                                    } else {
                                                        tapCount = 1
                                                    }
                                                    lastTapTime = now

                                                    if (tapCount == 3) {
                                                        tapCount = 0
                                                        onToggleFloatingAssistant(true)
                                                        Toast.makeText(context, "Assistant Orb summoned", Toast.LENGTH_SHORT).show()
                                                    } else if (tapCount == 1) {
                                                        android.util.Log.d("LuminaToolbar", "paged onTap fired! showSelectionMenu=$showSelectionMenu, selectedText='$selectedText'")
                                                        if (hitBookmark != null) {
                                                            selectedBookmarkForModal = hitBookmark
                                                            showBookmarkDetailModal = true
                                                        } else {
                                                            if (showSelectionMenu) {
                                                              try {
                                                                  activeReleaseSelectionAction?.invoke()
                                                              } catch (_: Throwable) {}
                                                              activeReleaseSelectionAction = null
                                                              showSelectionMenu = false
                                                              selectedText = ""
                                                          } else {
                                                                showControls = !showControls
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
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

        // TOP HEADER BAR: Distraction-Free Header (Zero buttons, clean title & chapter)
        AnimatedVisibility(
            visible = isUiVisible && voiceState == AssistantVoiceState.IDLE && !showInBookSearchDialog,
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
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
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

                        if (assistantOrbStyle == "TOP_BAR_BUTTON") {
                            IconButton(
                                onClick = {
                                    if (disableAi) {
                                        Toast.makeText(context, "AI Assistant is disabled in Settings", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showAssistantChatSheet = true
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Assistant",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { /* Consume taps to prevent passing through to reader */ }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Progress Bar Extension: Pure Display (not movable, no actions), tapping toggles bottom nav dock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleNavBar() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${currentProgressPct}%",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        // Pure Display Progress Rail - Non-movable, no actions
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Background rail
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            )
                            // Filled progress bar
                            val currentFraction = (currentProgressPct.toFloat() / 100f).coerceIn(0.01f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(currentFraction)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))
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

        // Floating Assistant Orb (movable, smart inward arc, docked dot, renders on top of bottom docks)
        if (showFloatingAssistant && !isFullscreen && assistantOrbStyle != "TOP_BAR_BUTTON") {
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
                        ttsRef.value?.stop()
                        isTtsSpeaking = false
                    } else {
                        speakNextPara.value()
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
        AnimatedVisibility(
            visible = showTtsDock,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(185f)
                .padding(bottom = selectionMenuBottomPadding + 6.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Audio Reader",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "• Para ${speakingParaIdx + 1}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                showTtsDock = false
                                ttsRef.value?.stop()
                                isTtsSpeaking = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Player",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous paragraph
                        IconButton(
                            onClick = {
                                speakingParaIdx = (speakingParaIdx - 1).coerceAtLeast(0)
                                speakNextPara.value()
                            }
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Paragraph")
                        }

                        // Play/Pause button
                        FilledIconButton(
                            onClick = {
                                if (isTtsSpeaking) {
                                    ttsRef.value?.stop()
                                    isTtsSpeaking = false
                                } else {
                                    speakNextPara.value()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isTtsSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isTtsSpeaking) "Pause" else "Play"
                            )
                        }

                        // Next paragraph
                        IconButton(
                            onClick = {
                                speakingParaIdx++
                                speakNextPara.value()
                            }
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next Paragraph")
                        }

                        // Speed selector dropdown
                        var showSpeedMenu by remember { mutableStateOf(false) }
                        val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        Box {
                            AssistChip(
                                onClick = { showSpeedMenu = true },
                                label = { Text("${ttsSpeed}x", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                modifier = Modifier.height(28.dp)
                            )
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                speedOptions.forEach { sp ->
                                    DropdownMenuItem(
                                        text = { Text("${sp}x") },
                                        onClick = {
                                            ttsSpeed = sp
                                            ttsRef.value?.setSpeechRate(sp)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // AUTO-SCROLL FLOATING INDICATOR PILL
        AnimatedVisibility(
            visible = isAutoScrolling,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(185f)
                .padding(bottom = selectionMenuBottomPadding + 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                shadowElevation = 6.dp,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Auto-Scrolling",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "• Tap anywhere to pause",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { isAutoScrolling = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause Auto-scroll", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }


        // Selection Menu Pill (Rendered strictly on top of bottom dock, zIndex = 200f)
        AnimatedVisibility(
            visible = showSelectionMenu,
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
                    val activePage = if (readingMode != ReadingMode.SCROLL) pagerState.currentPage + 1 else book.currentPage + 1
                    val dismissSelection = {
                        try {
                            activeReleaseSelectionAction?.invoke()
                        } catch (_: Throwable) {}
                        activeReleaseSelectionAction = null
                        showSelectionMenu = false
                        selectedText = ""
                    }
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
                                    onAddBookmark(selectedText, HighlightColor.GOLD, activePage)
                                    dismissSelection()
                                    Toast.makeText(context, "Added Gold highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5B7B7))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.ROSE, activePage)
                                    dismissSelection()
                                    Toast.makeText(context, "Added Rose highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB2C2B2))
                                .clickable {
                                    onAddBookmark(selectedText, HighlightColor.SAGE, activePage)
                                    dismissSelection()
                                    Toast.makeText(context, "Added Sage highlight", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Read from here: starts TTS from the selected paragraph
                        IconButton(
                            onClick = {
                                val targetChapIdx = if (selectedChapterTitle.isNotBlank()) {
                                    book.chapters.indexOfFirst { it.title == selectedChapterTitle }.takeIf { it >= 0 } ?: speakingChapterIdx
                                } else {
                                    speakingChapterIdx
                                }
                                speakingChapterIdx = targetChapIdx
                                val currChap = book.chapters.getOrNull(targetChapIdx)
                                val pIdx = currChap?.paragraphs?.indexOfFirst { it.contains(selectedText, ignoreCase = true) } ?: -1
                                if (pIdx >= 0) {
                                    speakingParaIdx = pIdx
                                }
                                showTtsDock = true
                                speakNextPara.value()
                                dismissSelection()
                                Toast.makeText(context, "Reading aloud from selection", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Read from here", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Note button: directly inspect or attach note
                        IconButton(
                            onClick = {
                                val existing = activeBookmark
                                if (existing != null) {
                                    selectedBookmarkForModal = existing
                                } else {
                                    val mark = Bookmark(
                                        bookTitle = book.title,
                                        chapter = selectedChapterTitle.ifBlank { activeChapterTitle },
                                        quote = selectedText,
                                        color = HighlightColor.GOLD,
                                        timestamp = "Just now",
                                        pageNumber = activePage
                                    )
                                    onAddBookmark(selectedText, HighlightColor.GOLD, activePage)
                                    selectedBookmarkForModal = mark
                                }
                                showBookmarkDetailModal = true
                                dismissSelection()
                            }
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = "Add Note / Inspect", tint = MaterialTheme.colorScheme.secondary)
                        }

                        if (activeBookmark != null) {
                            IconButton(
                                onClick = {
                                    onRemoveBookmark(activeBookmark.id)
                                    dismissSelection()
                                    Toast.makeText(context, "Removed highlight", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Highlight", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        IconButton(
                            onClick = {
                                val firstWord = selectedText.trim().split("\\s+".toRegex()).firstOrNull()?.replace("[^a-zA-Z]".toRegex(), "") ?: selectedText
                                onLookupWord(firstWord.ifBlank { selectedText.trim() })
                                dismissSelection()
                            }
                        ) {
                            Icon(Icons.Default.Spellcheck, contentDescription = "Word Meaning", tint = MaterialTheme.colorScheme.primary)
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
                                dismissSelection()
                                Toast.makeText(context, "Copied with citation reference!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.FormatQuote, contentDescription = "Cite Quote")
                        }

                        IconButton(onClick = { dismissSelection() }) {
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

        // AI Assistant Interactive Chat Sheet (Text Typing, Prompt Chips & Voice)
        AssistantChatSheet(
            isOpen = showAssistantChatSheet,
            onDismiss = {
                showAssistantChatSheet = false
                assistantAutoStartVoice = false
            },
            bookTitle = book.title,
            activeChapterTitle = activeChapterTitle,
            knownContext = book.chapters.firstOrNull { it.title == activeChapterTitle }?.paragraphs?.take(10)?.joinToString("\n") ?: "",
            apiKey = geminiApiKey,
            provider = aiProvider,
            baseUrl = aiBaseUrl,
            modelName = aiModel,
            spoilerShield = spoilerShield,
            disableStt = disableStt,
            disableAi = disableAi,
            autoStartVoice = assistantAutoStartVoice,
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

        // In-Book Search Top Bar (Adobe-style one-liner with Plain vs Semantic toggle and Prev/Next navigation)
        if (showInBookSearchDialog) {
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
                            (it.second.contains(inBookSearchQuery.trim(), ignoreCase = true) || it.second.contains(cleanSnippet, ignoreCase = true))
                        }.let { if (it != -1) it else pages.indexOfFirst { p -> p.first.equals(match.chapterTitle, ignoreCase = true) } }

                        if (pageIdx != -1) {
                            coroutineScope.launch { pagerState.animateScrollToPage(pageIdx) }
                        }
                    }
                }
            }

            LaunchedEffect(inBookSearchQuery, inBookSearchMode) {
                val q = inBookSearchQuery.trim()
                if (q.length < 2) {
                    inBookSearchResults = emptyList()
                    inBookCurrentMatchIndex = 0
                    isSearchingInBook = false
                    return@LaunchedEffect
                }
                isSearchingInBook = true
                delay(if (inBookSearchMode == InBookSearchMode.PLAIN) 250 else 300)
                val results = withContext(Dispatchers.IO) {
                    if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                        val semanticStopwords = setOf(
                            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
                            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
                            "by", "can", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for",
                            "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
                            "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "just",
                            "me", "more", "most", "my", "myself", "no", "nor", "not", "now", "of", "off", "on", "once",
                            "only", "or", "other", "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should",
                            "so", "some", "such", "than", "that", "the", "their", "theirs", "them", "themselves", "then",
                            "there", "these", "they", "this", "those", "through", "to", "too", "under", "until", "up",
                            "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why",
                            "with", "would", "you", "your", "yours", "yourself", "yourselves", "find", "scene", "jump",
                            "show", "tell", "book", "chapter"
                        )
                        val ftsResults = repository?.searchScenes(book.id, q) ?: emptyList()
                        val semanticResults = mutableListOf<SceneMatch>()
                        val seenKeys = mutableSetOf<String>()

                        ftsResults.forEach {
                            seenKeys.add("${it.chapterIndex}-${it.paragraphIndex}")
                            semanticResults.add(it)
                        }

                        val cleanTokens = q.lowercase().split(Regex("\\W+"))
                            .map { it.trim() }
                            .filter { it.length >= 2 && !semanticStopwords.contains(it) }
                        val searchTokens = if (cleanTokens.isNotEmpty()) cleanTokens else listOf(q.lowercase().trim())

                        data class ScoredMatch(val match: SceneMatch, val score: Int)
                        val scoredList = mutableListOf<ScoredMatch>()

                        for ((cIdx, chap) in book.chapters.withIndex()) {
                            val chapTitleLower = chap.title.lowercase()
                            val chapTitleMatches = searchTokens.count { chapTitleLower.contains(it) }

                            for ((pIdx, para) in chap.paragraphs.withIndex()) {
                                val key = "$cIdx-$pIdx"
                                if (seenKeys.contains(key) || para.startsWith("[IMG:") || para.isBlank()) continue

                                val paraLower = para.lowercase()
                                var matchedTokensCount = 0
                                var totalTokenOccurrences = 0
                                var firstMatchPos = -1

                                for (token in searchTokens) {
                                    val idx = paraLower.indexOf(token)
                                    if (idx != -1) {
                                        matchedTokensCount++
                                        if (firstMatchPos == -1 || idx < firstMatchPos) {
                                            firstMatchPos = idx
                                        }
                                        val isWordBoundary = (idx == 0 || !paraLower[idx - 1].isLetterOrDigit())
                                        if (isWordBoundary) totalTokenOccurrences += 2 else totalTokenOccurrences += 1
                                    } else if (token.length >= 4) {
                                        val stem = token.take(token.length - 2)
                                        val stemIdx = paraLower.indexOf(stem)
                                        if (stemIdx != -1) {
                                            matchedTokensCount++
                                            if (firstMatchPos == -1 || stemIdx < firstMatchPos) {
                                                firstMatchPos = stemIdx
                                            }
                                            totalTokenOccurrences += 1
                                        }
                                    }
                                }

                                if (matchedTokensCount > 0) {
                                    var score = matchedTokensCount * 40 + totalTokenOccurrences * 10 + chapTitleMatches * 25
                                    if (matchedTokensCount == searchTokens.size) {
                                        score += 100
                                    }

                                    val start = maxOf(0, firstMatchPos - 35)
                                    val end = minOf(para.length, start + 110)
                                    val snippet = (if (start > 0) "..." else "") +
                                            para.substring(start, end).trim() +
                                            (if (end < para.length) "..." else "")

                                    scoredList.add(ScoredMatch(SceneMatch(book.id, cIdx, chap.title, pIdx, snippet), score))
                                }
                            }
                        }

                        scoredList.sortByDescending { it.score }
                        for (item in scoredList) {
                            if (semanticResults.size >= 35) break
                            val key = "${item.match.chapterIndex}-${item.match.paragraphIndex}"
                            if (seenKeys.add(key)) {
                                semanticResults.add(item.match)
                            }
                        }
                        semanticResults
                    } else {
                        val list = mutableListOf<SceneMatch>()
                        for ((cIdx, chap) in book.chapters.withIndex()) {
                            for ((pIdx, para) in chap.paragraphs.withIndex()) {
                                if (para.contains(q, ignoreCase = true) && !para.startsWith("[IMG:")) {
                                    val start = maxOf(0, para.indexOf(q, ignoreCase = true) - 25)
                                    val end = minOf(para.length, start + 90)
                                    val snippet = (if (start > 0) "..." else "") + para.substring(start, end).trim() + (if (end < para.length) "..." else "")
                                    list.add(SceneMatch(book.id, cIdx, chap.title, pIdx, snippet))
                                    if (list.size >= 100) break
                                }
                            }
                            if (list.size >= 100) break
                        }
                        list
                    }
                }
                inBookSearchResults = results
                inBookCurrentMatchIndex = 0
                isSearchingInBook = false
                if (results.isNotEmpty()) {
                    jumpToMatch(results[0])
                }
            }

            AnimatedVisibility(
                visible = showInBookSearchDialog,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // One-liner Search Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Mode Toggle Button (Top-left)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        inBookSearchMode = if (inBookSearchMode == InBookSearchMode.PLAIN) {
                                            InBookSearchMode.SEMANTIC
                                        } else {
                                            InBookSearchMode.PLAIN
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                                            Icons.Default.AutoAwesome
                                        } else {
                                            Icons.Default.FindInPage
                                        },
                                        contentDescription = if (inBookSearchMode == InBookSearchMode.SEMANTIC) "Semantic Search (AI)" else "Plain Text Search",
                                        tint = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (inBookSearchMode == InBookSearchMode.SEMANTIC) "AI" else "Text",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // 2. Search Text Input
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inBookSearchQuery.isEmpty()) {
                                    Text(
                                        text = if (inBookSearchMode == InBookSearchMode.PLAIN) "Find in book..." else "Search scenes & themes...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = inBookSearchQuery,
                                    onValueChange = { inBookSearchQuery = it },
                                    singleLine = true,
                                    maxLines = 1,
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = if (inBookSearchMode == InBookSearchMode.PLAIN) ImeAction.Next else ImeAction.Search
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            if (inBookSearchResults.isNotEmpty()) {
                                                inBookCurrentMatchIndex = (inBookCurrentMatchIndex + 1) % inBookSearchResults.size
                                                jumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                            }
                                        },
                                        onSearch = {
                                            if (inBookSearchResults.isNotEmpty()) {
                                                jumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                            }
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 3. Clear button
                            if (inBookSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { inBookSearchQuery = "" },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // 4. Regular / Plain Search controls (Adobe-style match counter & Prev / Next arrows)
                            if (inBookSearchMode == InBookSearchMode.PLAIN) {
                                if (inBookSearchQuery.trim().length >= 2) {
                                    Text(
                                        text = if (inBookSearchResults.isEmpty()) {
                                            if (isSearchingInBook) "..." else "0/0"
                                        } else {
                                            "${(inBookCurrentMatchIndex + 1).coerceAtMost(inBookSearchResults.size)}/${inBookSearchResults.size}"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (inBookSearchResults.isNotEmpty()) {
                                            inBookCurrentMatchIndex = if (inBookCurrentMatchIndex <= 0) inBookSearchResults.size - 1 else inBookCurrentMatchIndex - 1
                                            jumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                        }
                                    },
                                    enabled = inBookSearchResults.isNotEmpty(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Previous Match",
                                        tint = if (inBookSearchResults.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (inBookSearchResults.isNotEmpty()) {
                                            inBookCurrentMatchIndex = (inBookCurrentMatchIndex + 1) % inBookSearchResults.size
                                            jumpToMatch(inBookSearchResults[inBookCurrentMatchIndex])
                                        }
                                    },
                                    enabled = inBookSearchResults.isNotEmpty(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Next Match",
                                        tint = if (inBookSearchResults.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // 5. Close button (X)
                            IconButton(
                                onClick = {
                                    showInBookSearchDialog = false
                                    inBookSearchQuery = ""
                                    inBookSearchResults = emptyList()
                                    inBookCurrentMatchIndex = 0
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 6. Semantic Search Results List (Only displayed when SEMANTIC is toggled)
                        if (inBookSearchMode == InBookSearchMode.SEMANTIC) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            if (isSearchingInBook) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (inBookSearchQuery.trim().length >= 2 && !isSearchingInBook) {
                                    Text(
                                        text = "${inBookSearchResults.size} scenes found",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                if (inBookSearchResults.isEmpty() && inBookSearchQuery.trim().length >= 2 && !isSearchingInBook) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No matching scenes or concepts found",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(inBookSearchResults) { match ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        jumpToMatch(match)
                                                        Toast.makeText(context, "Navigated to ${match.chapterTitle}", Toast.LENGTH_SHORT).show()
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                ),
                                                border = BorderStroke(
                                                    0.6.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = match.chapterTitle,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = match.snippet,
                                                        fontSize = 11.sp,
                                                        lineHeight = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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
