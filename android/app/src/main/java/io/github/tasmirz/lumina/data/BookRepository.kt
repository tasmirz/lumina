package io.github.tasmirz.lumina.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.github.tasmirz.lumina.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

import io.github.tasmirz.lumina.data.db.LuminaDatabaseHelper
import io.github.tasmirz.lumina.util.PageCache
import io.github.tasmirz.lumina.util.SimpleLruCache

class BookRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: BookRepository? = null

        fun getInstance(context: Context): BookRepository {
            return instance ?: synchronized(this) {
                instance ?: BookRepository(context.applicationContext).also { instance = it }
            }
        }

        operator fun invoke(context: Context): BookRepository = getInstance(context)

        fun resetInstanceForTesting() {
            instance = null
        }
    }

    val dbHelper = LuminaDatabaseHelper(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lumina_reader_prefs", Context.MODE_PRIVATE)

    private val repoScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var readingPositionSaveJob: kotlinx.coroutines.Job? = null

    private val chapterCache = SimpleLruCache<String, List<Chapter>>(8)

    private val _readingPosition = MutableStateFlow(
        ReadingPosition(
            bookId = prefs.getString("active_book_id", "") ?: "",
            chapterIndex = 0,
            pageIndex = 0,
            scrollPos = 0,
            progressPct = 0
        )
    )
    val readingPosition: StateFlow<ReadingPosition> = _readingPosition.asStateFlow()

    private val _readerSettings = MutableStateFlow(loadInitialSettingsFromPrefs())
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    private fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        _readerSettings.value = transform(_readerSettings.value)
    }

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _activeBookId = MutableStateFlow(prefs.getString("active_book_id", "") ?: "")
    val activeBookId: StateFlow<String> = _activeBookId.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _wishlistBooks = MutableStateFlow<List<WishlistBook>>(emptyList())
    val wishlistBooks: StateFlow<List<WishlistBook>> = _wishlistBooks.asStateFlow()

    private val _completedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val completedBookIds: StateFlow<Set<String>> = _completedBookIds.asStateFlow()

    private val _customThemes = MutableStateFlow<List<CustomThemeData>>(emptyList())
    val customThemes: StateFlow<List<CustomThemeData>> = _customThemes.asStateFlow()

    private val _isIndexingActive = MutableStateFlow(false)
    val isIndexingActive: StateFlow<Boolean> = _isIndexingActive.asStateFlow()

    private val _indexingProgress = MutableStateFlow("")
    val indexingProgress: StateFlow<String> = _indexingProgress.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(prefs.getString("preferred_language", "auto") ?: "auto")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    private val _horizontalPadding = MutableStateFlow(prefs.getInt("horizontal_padding", 20))
    val horizontalPadding: StateFlow<Int> = _horizontalPadding.asStateFlow()

    private val _verticalPadding = MutableStateFlow(prefs.getInt("vertical_padding", 16))
    val verticalPadding: StateFlow<Int> = _verticalPadding.asStateFlow()

    private val _assistantOrbStyle = MutableStateFlow(prefs.getString("assistant_orb_style", "EDGE_DOT") ?: "EDGE_DOT")
    val assistantOrbStyle: StateFlow<String> = _assistantOrbStyle.asStateFlow()

    private val _spoilerShield = MutableStateFlow(prefs.getBoolean("spoiler_shield", true))
    val spoilerShield: StateFlow<Boolean> = _spoilerShield.asStateFlow()

    private val _autoScrollSpeed = MutableStateFlow(prefs.getFloat("auto_scroll_speed", 1.0f))
    val autoScrollSpeed: StateFlow<Float> = _autoScrollSpeed.asStateFlow()

    private val _disableAi = MutableStateFlow(prefs.getBoolean("disable_ai", false))
    val disableAi: StateFlow<Boolean> = _disableAi.asStateFlow()

    private val _disableTts = MutableStateFlow(prefs.getBoolean("disable_tts", false))
    val disableTts: StateFlow<Boolean> = _disableTts.asStateFlow()

    private val _ttsEngine = MutableStateFlow(prefs.getString("tts_engine", "EDGE_NEURAL") ?: "EDGE_NEURAL")
    val ttsEngine: StateFlow<String> = _ttsEngine.asStateFlow()

    private val _ttsEdgeVoice = MutableStateFlow(prefs.getString("tts_edge_voice", "en-US-JennyNeural") ?: "en-US-JennyNeural")
    val ttsEdgeVoice: StateFlow<String> = _ttsEdgeVoice.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(prefs.getFloat("tts_speed", 1.0f))
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    private val _ttsPitch = MutableStateFlow(prefs.getFloat("tts_pitch", 1.0f))
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _disableStt = MutableStateFlow(prefs.getBoolean("disable_stt", false))
    val disableStt: StateFlow<Boolean> = _disableStt.asStateFlow()

    private val _autoStartMic = MutableStateFlow(prefs.getBoolean("auto_start_mic", true))
    val autoStartMic: StateFlow<Boolean> = _autoStartMic.asStateFlow()

    private val _enableFtsIndexing = MutableStateFlow(prefs.getBoolean("enable_fts_indexing", false))
    val enableFtsIndexing: StateFlow<Boolean> = _enableFtsIndexing.asStateFlow()

    // Preferences
    private val _fontSize = MutableStateFlow(prefs.getInt("font_size", 18))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    private val _readingMode = MutableStateFlow(
        try {
            ReadingMode.valueOf(prefs.getString("reading_mode", ReadingMode.SCROLL.name) ?: ReadingMode.SCROLL.name)
        } catch (_: Exception) { ReadingMode.SCROLL }
    )
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.WARM_PAPER.name) ?: ThemeMode.WARM_PAPER.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _typefaceMode = MutableStateFlow(
        TypefaceMode.valueOf(prefs.getString("typeface_mode", TypefaceMode.SERIF.name) ?: TypefaceMode.SERIF.name)
    )
    val typefaceMode: StateFlow<TypefaceMode> = _typefaceMode.asStateFlow()

    private val _lineHeightMultiplier = MutableStateFlow(prefs.getFloat("line_height", 1.68f))
    val lineHeightMultiplier: StateFlow<Float> = _lineHeightMultiplier.asStateFlow()

    private val _paragraphSpacingMultiplier = MutableStateFlow(prefs.getFloat("paragraph_spacing", 1.2f))
    val paragraphSpacingMultiplier: StateFlow<Float> = _paragraphSpacingMultiplier.asStateFlow()

    private val _showFloatingAssistant = MutableStateFlow(prefs.getBoolean("show_floating_assistant", true))
    val showFloatingAssistant: StateFlow<Boolean> = _showFloatingAssistant.asStateFlow()

    private val _orbSize = MutableStateFlow<OrbSize>(
        try {
            OrbSize.valueOf(prefs.getString("orb_size", OrbSize.NANO.name) ?: OrbSize.NANO.name)
        } catch (_: Exception) { OrbSize.NANO }
    )
    val orbSize: StateFlow<OrbSize> = _orbSize.asStateFlow()

    private val _orbMenuSize = MutableStateFlow<OrbMenuSize>(
        try {
            OrbMenuSize.valueOf(prefs.getString("orb_menu_size", OrbMenuSize.MEDIUM.name) ?: OrbMenuSize.MEDIUM.name)
        } catch (_: Exception) { OrbMenuSize.MEDIUM }
    )
    val orbMenuSize: StateFlow<OrbMenuSize> = _orbMenuSize.asStateFlow()

    private val _orbEdgeSnap = MutableStateFlow(prefs.getBoolean("orb_edge_snap", true))
    val orbEdgeSnap: StateFlow<Boolean> = _orbEdgeSnap.asStateFlow()

    // Distinct persistent dock coordinates for Portrait vs Landscape
    private val _orbPortraitX = MutableStateFlow(prefs.getFloat("orb_pos_x_portrait", -1f))
    val orbPortraitX: StateFlow<Float> = _orbPortraitX.asStateFlow()

    private val _orbPortraitY = MutableStateFlow(prefs.getFloat("orb_pos_y_portrait", -1f))
    val orbPortraitY: StateFlow<Float> = _orbPortraitY.asStateFlow()

    private val _orbLandscapeX = MutableStateFlow(prefs.getFloat("orb_pos_x_landscape", -1f))
    val orbLandscapeX: StateFlow<Float> = _orbLandscapeX.asStateFlow()

    private val _orbLandscapeY = MutableStateFlow(prefs.getFloat("orb_pos_y_landscape", -1f))
    val orbLandscapeY: StateFlow<Float> = _orbLandscapeY.asStateFlow()

    private val _orbColor = MutableStateFlow<OrbColor>(
        try {
            OrbColor.valueOf(prefs.getString("orb_color", OrbColor.THEME.name) ?: OrbColor.THEME.name)
        } catch (_: Exception) { OrbColor.THEME }
    )
    val orbColor: StateFlow<OrbColor> = _orbColor.asStateFlow()

    private val _orbOpacity = MutableStateFlow(prefs.getFloat("orb_opacity", 0.85f))
    val orbOpacity: StateFlow<Float> = _orbOpacity.asStateFlow()

    private val _themeFamily = MutableStateFlow(
        try {
            ThemeFamily.valueOf(prefs.getString("theme_family", ThemeFamily.PAPER.name) ?: ThemeFamily.PAPER.name)
        } catch (_: Exception) { ThemeFamily.PAPER }
    )
    val themeFamily: StateFlow<ThemeFamily> = _themeFamily.asStateFlow()

    private val _themeVariant = MutableStateFlow(
        try {
            ThemeVariant.valueOf(prefs.getString("theme_variant", ThemeVariant.LIGHT.name) ?: ThemeVariant.LIGHT.name)
        } catch (_: Exception) { ThemeVariant.LIGHT }
    )
    val themeVariant: StateFlow<ThemeVariant> = _themeVariant.asStateFlow()

    private val _backgroundTexture = MutableStateFlow(
        try {
            BackgroundTexture.valueOf(prefs.getString("background_texture", BackgroundTexture.NONE.name) ?: BackgroundTexture.NONE.name)
        } catch (_: Exception) { BackgroundTexture.NONE }
    )
    val backgroundTexture: StateFlow<BackgroundTexture> = _backgroundTexture.asStateFlow()

    private val _customBgUri = MutableStateFlow(prefs.getString("custom_bg_uri", "") ?: "")
    val customBgUri: StateFlow<String> = _customBgUri.asStateFlow()

    private val _orbActionItems = MutableStateFlow<Set<OrbActionItem>>(
        prefs.getStringSet("orb_action_items", null)?.mapNotNull { name ->
            try { OrbActionItem.valueOf(name) } catch (_: Exception) { null }
        }?.takeIf { it.size >= 8 }?.toSet() ?: OrbActionItem.entries.toSet()
    )
    val orbActionItems: StateFlow<Set<OrbActionItem>> = _orbActionItems.asStateFlow()

    private val _quickThemes = MutableStateFlow<Set<ThemeFamily>>(
        prefs.getStringSet("quick_themes", null)?.mapNotNull {
            try { ThemeFamily.valueOf(it) } catch (_: Exception) { null }
        }?.toSet() ?: ThemeFamily.entries.toSet()
    )
    val quickThemes: StateFlow<Set<ThemeFamily>> = _quickThemes.asStateFlow()

    private val _quickFonts = MutableStateFlow<Set<TypefaceMode>>(
        prefs.getStringSet("quick_fonts", null)?.mapNotNull {
            try { TypefaceMode.valueOf(it) } catch (_: Exception) { null }
        }?.toSet() ?: setOf(TypefaceMode.SERIF, TypefaceMode.SANS, TypefaceMode.GEORGIA)
    )
    val quickFonts: StateFlow<Set<TypefaceMode>> = _quickFonts.asStateFlow()

    private val _orbActionOrder = MutableStateFlow<List<OrbActionItem>>(
        prefs.getString("orb_action_order", null)?.split(",")?.mapNotNull { name ->
            try { OrbActionItem.valueOf(name) } catch (_: Exception) { null }
        }?.let { savedList ->
            val set = savedList.toSet()
            savedList + OrbActionItem.entries.filter { it !in set }
        } ?: OrbActionItem.entries.toList()
    )
    val orbActionOrder: StateFlow<List<OrbActionItem>> = _orbActionOrder.asStateFlow()

    private val _textAlignmentMode = MutableStateFlow(
        try {
            TextAlignmentMode.valueOf(prefs.getString("text_alignment_mode", TextAlignmentMode.JUSTIFY.name) ?: TextAlignmentMode.JUSTIFY.name)
        } catch (_: Exception) { TextAlignmentMode.JUSTIFY }
    )
    val textAlignmentMode: StateFlow<TextAlignmentMode> = _textAlignmentMode.asStateFlow()

    private val _letterSpacing = MutableStateFlow(prefs.getFloat("letter_spacing", 0.2f))
    val letterSpacing: StateFlow<Float> = _letterSpacing.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _aiProvider = MutableStateFlow(
        try {
            AiProvider.valueOf(prefs.getString("ai_provider", AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name)
        } catch (_: Exception) { AiProvider.GEMINI }
    )
    val aiProvider: StateFlow<AiProvider> = _aiProvider.asStateFlow()

    private val _aiBaseUrl = MutableStateFlow(prefs.getString("ai_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1")
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()

    private val _aiModel = MutableStateFlow(
        prefs.getString("ai_model", null).let { if (it.isNullOrBlank()) "gemini-3.1-flash-lite" else it }
    )
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _readTillMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val readTillMap: StateFlow<Map<String, Int>> = _readTillMap.asStateFlow()

    private val _customBgColor = MutableStateFlow(prefs.getLong("custom_bg_color", 0xFF1C1917L))
    val customBgColor: StateFlow<Long> = _customBgColor.asStateFlow()

    private val _customTextColor = MutableStateFlow(prefs.getLong("custom_text_color", 0xFFE7E5E4L))
    val customTextColor: StateFlow<Long> = _customTextColor.asStateFlow()

    private val _customAccentColor = MutableStateFlow(prefs.getLong("custom_accent_color", 0xFFD4AF37L))
    val customAccentColor: StateFlow<Long> = _customAccentColor.asStateFlow()

    private val _gestureDoubleTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_double_tap", GestureAction.TOGGLE_AUTOSCROLL.name) ?: GestureAction.TOGGLE_AUTOSCROLL.name)
        } catch (_: Exception) { GestureAction.TOGGLE_AUTOSCROLL }
    )
    val gestureDoubleTap: StateFlow<GestureAction> = _gestureDoubleTap.asStateFlow()

    private val _gestureTripleTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_triple_tap", GestureAction.SUMMON_ORB.name) ?: GestureAction.SUMMON_ORB.name)
        } catch (_: Exception) { GestureAction.SUMMON_ORB }
    )
    val gestureTripleTap: StateFlow<GestureAction> = _gestureTripleTap.asStateFlow()

    private val _gestureSingleTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_single_tap", GestureAction.TOGGLE_BARS.name) ?: GestureAction.TOGGLE_BARS.name)
        } catch (_: Exception) { GestureAction.TOGGLE_BARS }
    )
    val gestureSingleTap: StateFlow<GestureAction> = _gestureSingleTap.asStateFlow()

    private val _gestureTtsTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_tts_tap", GestureAction.TTS_READ_ALOUD.name) ?: GestureAction.TTS_READ_ALOUD.name)
        } catch (_: Exception) { GestureAction.TTS_READ_ALOUD }
    )
    val gestureTtsTap: StateFlow<GestureAction> = _gestureTtsTap.asStateFlow()

    private fun loadInitialSettingsFromPrefs(): ReaderSettings {
        return ReaderSettings(
            fontSize = prefs.getInt("font_size", 18),
            readingMode = try { ReadingMode.valueOf(prefs.getString("reading_mode", ReadingMode.SCROLL.name) ?: ReadingMode.SCROLL.name) } catch (_: Exception) { ReadingMode.SCROLL },
            themeMode = try { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.WARM_PAPER.name) ?: ThemeMode.WARM_PAPER.name) } catch (_: Exception) { ThemeMode.WARM_PAPER },
            themeFamily = try { ThemeFamily.valueOf(prefs.getString("theme_family", ThemeFamily.PAPER.name) ?: ThemeFamily.PAPER.name) } catch (_: Exception) { ThemeFamily.PAPER },
            themeVariant = try { ThemeVariant.valueOf(prefs.getString("theme_variant", ThemeVariant.LIGHT.name) ?: ThemeVariant.LIGHT.name) } catch (_: Exception) { ThemeVariant.LIGHT },
            typefaceMode = try { TypefaceMode.valueOf(prefs.getString("typeface_mode", TypefaceMode.SERIF.name) ?: TypefaceMode.SERIF.name) } catch (_: Exception) { TypefaceMode.SERIF },
            lineHeightMultiplier = prefs.getFloat("line_height", 1.68f),
            paragraphSpacingMultiplier = prefs.getFloat("paragraph_spacing", 1.2f),
            textAlignmentMode = try { TextAlignmentMode.valueOf(prefs.getString("text_alignment_mode", TextAlignmentMode.JUSTIFY.name) ?: TextAlignmentMode.JUSTIFY.name) } catch (_: Exception) { TextAlignmentMode.JUSTIFY },
            letterSpacing = prefs.getFloat("letter_spacing", 0.2f),
            horizontalPadding = prefs.getInt("horizontal_padding", 20),
            verticalPadding = prefs.getInt("vertical_padding", 16),
            showFloatingAssistant = prefs.getBoolean("show_floating_assistant", true),
            orbSize = try { OrbSize.valueOf(prefs.getString("orb_size", OrbSize.NANO.name) ?: OrbSize.NANO.name) } catch (_: Exception) { OrbSize.NANO },
            orbMenuSize = try { OrbMenuSize.valueOf(prefs.getString("orb_menu_size", OrbMenuSize.MEDIUM.name) ?: OrbMenuSize.MEDIUM.name) } catch (_: Exception) { OrbMenuSize.MEDIUM },
            orbEdgeSnap = prefs.getBoolean("orb_edge_snap", true),
            orbPortraitX = prefs.getFloat("orb_pos_x_portrait", -1f),
            orbPortraitY = prefs.getFloat("orb_pos_y_portrait", -1f),
            orbLandscapeX = prefs.getFloat("orb_pos_x_landscape", -1f),
            orbLandscapeY = prefs.getFloat("orb_pos_y_landscape", -1f),
            orbColor = try { OrbColor.valueOf(prefs.getString("orb_color", OrbColor.THEME.name) ?: OrbColor.THEME.name) } catch (_: Exception) { OrbColor.THEME },
            orbOpacity = prefs.getFloat("orb_opacity", 0.85f),
            backgroundTexture = try { BackgroundTexture.valueOf(prefs.getString("background_texture", BackgroundTexture.NONE.name) ?: BackgroundTexture.NONE.name) } catch (_: Exception) { BackgroundTexture.NONE },
            customBgUri = prefs.getString("custom_bg_uri", "") ?: "",
            customBgColor = prefs.getLong("custom_bg_color", 0xFF1C1917L),
            customTextColor = prefs.getLong("custom_text_color", 0xFFE7E5E4L),
            customAccentColor = prefs.getLong("custom_accent_color", 0xFFD4AF37L),
            orbActionItems = prefs.getStringSet("orb_action_items", null)?.mapNotNull { name ->
                try { OrbActionItem.valueOf(name) } catch (_: Exception) { null }
            }?.takeIf { it.size >= 8 }?.toSet() ?: OrbActionItem.entries.toSet(),
            quickThemes = prefs.getStringSet("quick_themes", null)?.mapNotNull {
                try { ThemeFamily.valueOf(it) } catch (_: Exception) { null }
            }?.toSet() ?: ThemeFamily.entries.toSet(),
            quickFonts = prefs.getStringSet("quick_fonts", null)?.mapNotNull {
                try { TypefaceMode.valueOf(it) } catch (_: Exception) { null }
            }?.toSet() ?: setOf(TypefaceMode.SERIF, TypefaceMode.SANS, TypefaceMode.GEORGIA),
            orbActionOrder = prefs.getString("orb_action_order", null)?.split(",")?.mapNotNull { name ->
                try { OrbActionItem.valueOf(name) } catch (_: Exception) { null }
            }?.let { savedList ->
                val set = savedList.toSet()
                savedList + OrbActionItem.entries.filter { it !in set }
            } ?: OrbActionItem.entries.toList(),
            autoScrollSpeed = prefs.getFloat("auto_scroll_speed", 1.0f),
            disableAi = prefs.getBoolean("disable_ai", false),
            disableTts = prefs.getBoolean("disable_tts", false),
            ttsEngine = prefs.getString("tts_engine", "EDGE_NEURAL") ?: "EDGE_NEURAL",
            ttsEdgeVoice = prefs.getString("tts_edge_voice", "en-US-JennyNeural") ?: "en-US-JennyNeural",
            ttsSpeed = prefs.getFloat("tts_speed", 1.0f),
            ttsPitch = prefs.getFloat("tts_pitch", 1.0f),
            disableStt = prefs.getBoolean("disable_stt", false),
            autoStartMic = prefs.getBoolean("auto_start_mic", true),
            enableFtsIndexing = prefs.getBoolean("enable_fts_indexing", false),
            spoilerShield = prefs.getBoolean("spoiler_shield", true),
            gestureDoubleTap = try { GestureAction.valueOf(prefs.getString("gesture_double_tap", GestureAction.TOGGLE_AUTOSCROLL.name) ?: GestureAction.TOGGLE_AUTOSCROLL.name) } catch (_: Exception) { GestureAction.TOGGLE_AUTOSCROLL },
            gestureTripleTap = try { GestureAction.valueOf(prefs.getString("gesture_triple_tap", GestureAction.SUMMON_ORB.name) ?: GestureAction.SUMMON_ORB.name) } catch (_: Exception) { GestureAction.SUMMON_ORB },
            gestureSingleTap = try { GestureAction.valueOf(prefs.getString("gesture_single_tap", GestureAction.TOGGLE_BARS.name) ?: GestureAction.TOGGLE_BARS.name) } catch (_: Exception) { GestureAction.TOGGLE_BARS },
            gestureTtsTap = try { GestureAction.valueOf(prefs.getString("gesture_tts_tap", GestureAction.TTS_READ_ALOUD.name) ?: GestureAction.TTS_READ_ALOUD.name) } catch (_: Exception) { GestureAction.TTS_READ_ALOUD },
            geminiApiKey = prefs.getString("gemini_api_key", "") ?: "",
            aiProvider = try { AiProvider.valueOf(prefs.getString("ai_provider", AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name) } catch (_: Exception) { AiProvider.GEMINI },
            aiBaseUrl = prefs.getString("ai_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            aiModel = prefs.getString("ai_model", null).let { if (it.isNullOrBlank()) "gemini-3.1-flash-lite" else it },
            assistantOrbStyle = prefs.getString("assistant_orb_style", "EDGE_DOT") ?: "EDGE_DOT",
            preferredLanguage = prefs.getString("preferred_language", "auto") ?: "auto"
        )
    }

    fun getChaptersForBook(bookId: String): List<Chapter> {
        val cached = chapterCache.get(bookId)
        if (cached != null) return cached
        val fromDb = dbHelper.getAllChaptersForBook(bookId)
        if (fromDb.isNotEmpty()) {
            chapterCache.put(bookId, fromDb)
        }
        return fromDb
    }

    fun getCachedChapters(bookId: String): List<Chapter>? {
        return chapterCache.get(bookId) ?: dbHelper.getCachedChapters(bookId)
    }

    fun prefetchChapters(bookId: String) {
        if (chapterCache.get(bookId) != null || dbHelper.getCachedChapters(bookId) != null) return
        repoScope.launch(Dispatchers.IO) {
            getChaptersForBook(bookId)
        }
    }

    fun getChapter(bookId: String, chapterIndex: Int): Chapter? {
        val cached = chapterCache.get(bookId)
        if (cached != null) {
            return cached.getOrNull(chapterIndex)
        }
        return dbHelper.getChapter(bookId, chapterIndex)
    }

    fun getChapterCount(bookId: String): Int {
        val cached = chapterCache.get(bookId)
        if (cached != null) return cached.size
        return dbHelper.getChapterCount(bookId)
    }

    fun precomputePagesProgressively(
        book: Book,
        fontSize: Int,
        isLandscape: Boolean,
        isStrictPaged: Boolean,
        activeChapterIndex: Int = 0,
        onActiveChapterReady: ((List<Pair<String, String>>) -> Unit)? = null,
        onAllPagesReady: ((List<Pair<String, String>>) -> Unit)? = null
    ) {
        repoScope.launch(Dispatchers.Default) {
            val chapters = if (book.chapters.isNotEmpty()) book.chapters else getChaptersForBook(book.id)
            if (chapters.isEmpty()) return@launch
            val allPages = PageCache.getOrComputeAsync(
                bookId = book.id,
                chapters = chapters,
                fontSize = fontSize,
                isLandscape = isLandscape,
                isStrictPaged = isStrictPaged,
                dbHelper = dbHelper,
                activeChapterIndex = activeChapterIndex,
                onActiveChapterReady = { activePages ->
                    onActiveChapterReady?.invoke(activePages)
                }
            )
            onAllPagesReady?.invoke(allPages)
        }
    }

    init {
        repoScope.launch(Dispatchers.IO) {
            val loadedBooks = loadAllBooks()
            val loadedBookmarks = loadPersistedBookmarks()
            val loadedWishlist = try { dbHelper.getAllWishlist() } catch (_: Exception) { emptyList() }
            val loadedCompleted = try { dbHelper.getAllCompletedBookIds() } catch (_: Exception) { emptySet() }
            val loadedThemes = try { dbHelper.getAllCustomThemes() } catch (_: Exception) { emptyList() }

            _books.value = loadedBooks
            _bookmarks.value = loadedBookmarks
            _wishlistBooks.value = loadedWishlist
            _completedBookIds.value = loadedCompleted
            _customThemes.value = loadedThemes

            try {
                val curActiveId = _activeBookId.value.ifBlank { loadedBooks.firstOrNull()?.id ?: "" }
                if (curActiveId.isNotBlank()) {
                    val chaps = getChaptersForBook(curActiveId)
                    if (chaps.isNotEmpty()) {
                        _books.value = _books.value.map {
                            if (it.id == curActiveId && it.chapters.isEmpty()) it.copy(chapters = chaps) else it
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                syncSettings()
            } catch (_: Exception) {}

            // Delay discovery and migration so initial frame composition, gestures, and UI responsiveness are completely fluid
            repoScope.launch(Dispatchers.IO) {
                delay(2500)
                try {
                    LuminaStorageManager.migrateLegacyFiles(context)
                } catch (_: Exception) {}
                try {
                    autoScanAndLoadPersistentEpubs()
                } catch (e: Exception) {
                    android.util.Log.w("BookRepository", "Startup autoScan error: ${e.message}")
                }
            }
        }
    }

    fun setGestureDoubleTap(action: GestureAction) {
        _gestureDoubleTap.value = action
        updateReaderSettings { it.copy(gestureDoubleTap = action) }
        prefs.edit().putString("gesture_double_tap", action.name).apply()
        persistSettingToDb("gesture_double_tap", action.name)
    }

    fun setGestureTripleTap(action: GestureAction) {
        _gestureTripleTap.value = action
        updateReaderSettings { it.copy(gestureTripleTap = action) }
        prefs.edit().putString("gesture_triple_tap", action.name).apply()
        persistSettingToDb("gesture_triple_tap", action.name)
    }

    fun setGestureSingleTap(action: GestureAction) {
        _gestureSingleTap.value = action
        updateReaderSettings { it.copy(gestureSingleTap = action) }
        prefs.edit().putString("gesture_single_tap", action.name).apply()
        persistSettingToDb("gesture_single_tap", action.name)
    }

    fun setGestureTtsTap(action: GestureAction) {
        _gestureTtsTap.value = action
        updateReaderSettings { it.copy(gestureTtsTap = action) }
        prefs.edit().putString("gesture_tts_tap", action.name).apply()
        persistSettingToDb("gesture_tts_tap", action.name)
    }

    fun setCustomThemeColors(bg: Long, text: Long, accent: Long) {
        _customBgColor.value = bg
        _customTextColor.value = text
        _customAccentColor.value = accent
        updateReaderSettings { it.copy(customBgColor = bg, customTextColor = text, customAccentColor = accent) }
        prefs.edit()
            .putLong("custom_bg_color", bg)
            .putLong("custom_text_color", text)
            .putLong("custom_accent_color", accent)
            .apply()
        persistSettingToDb("custom_bg_color", bg.toString())
        persistSettingToDb("custom_text_color", text.toString())
        persistSettingToDb("custom_accent_color", accent.toString())
    }

    fun setHorizontalPadding(padding: Int) {
        _horizontalPadding.value = padding
        updateReaderSettings { it.copy(horizontalPadding = padding) }
        prefs.edit().putInt("horizontal_padding", padding).apply()
        persistSettingToDb("horizontal_padding", padding.toString())
    }

    fun setVerticalPadding(padding: Int) {
        _verticalPadding.value = padding
        updateReaderSettings { it.copy(verticalPadding = padding) }
        prefs.edit().putInt("vertical_padding", padding).apply()
        persistSettingToDb("vertical_padding", padding.toString())
    }

    fun setAssistantOrbStyle(style: String) {
        _assistantOrbStyle.value = style
        updateReaderSettings { it.copy(assistantOrbStyle = style) }
        prefs.edit().putString("assistant_orb_style", style).apply()
        persistSettingToDb("assistant_orb_style", style)
    }

    fun setSpoilerShield(enabled: Boolean) {
        _spoilerShield.value = enabled
        updateReaderSettings { it.copy(spoilerShield = enabled) }
        prefs.edit().putBoolean("spoiler_shield", enabled).apply()
        persistSettingToDb("spoiler_shield", enabled.toString())
    }

    fun setAutoScrollSpeed(speed: Float) {
        _autoScrollSpeed.value = speed
        updateReaderSettings { it.copy(autoScrollSpeed = speed) }
        prefs.edit().putFloat("auto_scroll_speed", speed).apply()
        persistSettingToDb("auto_scroll_speed", speed.toString())
    }

    fun cycleAutoScrollSpeed(): Float {
        val current = _autoScrollSpeed.value
        val next = when {
            current < 0.9f -> 1.0f
            current < 1.4f -> 1.5f
            current < 1.9f -> 2.0f
            else -> 0.5f
        }
        setAutoScrollSpeed(next)
        return next
    }

    fun setTtsEngine(engine: String) {
        _ttsEngine.value = engine
        updateReaderSettings { it.copy(ttsEngine = engine) }
        prefs.edit().putString("tts_engine", engine).apply()
        persistSettingToDb("tts_engine", engine)
    }

    fun setTtsEdgeVoice(voice: String) {
        _ttsEdgeVoice.value = voice
        updateReaderSettings { it.copy(ttsEdgeVoice = voice) }
        prefs.edit().putString("tts_edge_voice", voice).apply()
        persistSettingToDb("tts_edge_voice", voice)
    }

    fun setTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
        updateReaderSettings { it.copy(ttsSpeed = speed) }
        prefs.edit().putFloat("tts_speed", speed).apply()
        persistSettingToDb("tts_speed", speed.toString())
    }

    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
        updateReaderSettings { it.copy(ttsPitch = pitch) }
        prefs.edit().putFloat("tts_pitch", pitch).apply()
        persistSettingToDb("tts_pitch", pitch.toString())
    }

    fun setDisableAi(disabled: Boolean) {
        _disableAi.value = disabled
        updateReaderSettings { it.copy(disableAi = disabled) }
        prefs.edit().putBoolean("disable_ai", disabled).apply()
        persistSettingToDb("disable_ai", disabled.toString())
    }

    fun setDisableTts(disabled: Boolean) {
        _disableTts.value = disabled
        updateReaderSettings { it.copy(disableTts = disabled) }
        prefs.edit().putBoolean("disable_tts", disabled).apply()
        persistSettingToDb("disable_tts", disabled.toString())
    }

    fun setAutoStartMic(enabled: Boolean) {
        _autoStartMic.value = enabled
        updateReaderSettings { it.copy(autoStartMic = enabled) }
        prefs.edit().putBoolean("auto_start_mic", enabled).apply()
        persistSettingToDb("auto_start_mic", enabled.toString())
    }

    fun exportUnifiedBackupJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings
        val settings = JSONObject()
        settings.put("font_size", _fontSize.value)
        settings.put("reading_mode", _readingMode.value.name)
        settings.put("theme_family", _themeFamily.value.name)
        settings.put("theme_variant", _themeVariant.value.name)
        settings.put("typeface_mode", _typefaceMode.value.name)
        settings.put("line_height", _lineHeightMultiplier.value.toDouble())
        settings.put("paragraph_spacing", _paragraphSpacingMultiplier.value.toDouble())
        settings.put("letter_spacing", _letterSpacing.value.toDouble())
        settings.put("text_alignment_mode", _textAlignmentMode.value.name)
        settings.put("horizontal_padding", _horizontalPadding.value)
        settings.put("vertical_padding", _verticalPadding.value)
        settings.put("assistant_orb_style", _assistantOrbStyle.value)
        settings.put("spoiler_shield", _spoilerShield.value)
        settings.put("auto_scroll_speed", _autoScrollSpeed.value.toDouble())
        settings.put("disable_ai", _disableAi.value)
        settings.put("disable_tts", _disableTts.value)
        settings.put("ai_provider", _aiProvider.value.name)
        settings.put("ai_model", _aiModel.value)
        settings.put("ai_base_url", _aiBaseUrl.value)
        settings.put("gemini_api_key", _geminiApiKey.value)
        settings.put("background_texture", _backgroundTexture.value.name)
        settings.put("custom_bg_uri", _customBgUri.value)
        settings.put("custom_bg_color", _customBgColor.value)
        settings.put("custom_text_color", _customTextColor.value)
        settings.put("custom_accent_color", _customAccentColor.value)
        root.put("settings", settings)

        // 2. Reading progress of books
        val booksProgressArray = JSONArray()
        _books.value.forEach { b ->
            val bObj = JSONObject()
            bObj.put("id", b.id)
            bObj.put("chapter", b.currentChapter)
            bObj.put("page", b.currentPage)
            bObj.put("scroll", b.scrollPos)
            bObj.put("progress", b.progress)
            booksProgressArray.put(bObj)
        }
        root.put("books_progress", booksProgressArray)

        // 3. Bookmarks / Highlights / Notes
        val bookmarksArray = JSONArray()
        _bookmarks.value.forEach { bm ->
            val bmObj = JSONObject()
            bmObj.put("id", bm.id)
            bmObj.put("bookTitle", bm.bookTitle)
            bmObj.put("chapter", bm.chapter)
            bmObj.put("quote", bm.quote)
            bmObj.put("color", bm.color.name)
            bmObj.put("note", bm.note)
            bmObj.put("timestamp", bm.timestamp)
            bookmarksArray.put(bmObj)
        }
        root.put("bookmarks", bookmarksArray)

        // 4. Wishlist
        val wishlistArray = JSONArray()
        _wishlistBooks.value.forEach { wb ->
            val wbObj = JSONObject()
            wbObj.put("id", wb.id)
            wbObj.put("title", wb.title)
            wbObj.put("author", wb.author)
            wbObj.put("note", wb.note)
            wbObj.put("addedAt", wb.addedAt)
            wishlistArray.put(wbObj)
        }
        root.put("wishlist", wishlistArray)

        // 5. Completed Books
        val completedArr = JSONArray()
        _completedBookIds.value.forEach { completedArr.put(it) }
        root.put("completed_book_ids", completedArr)

        // 6. Custom Themes
        val themesArray = JSONArray()
        _customThemes.value.forEach { th ->
            val thObj = JSONObject()
            thObj.put("id", th.id)
            thObj.put("name", th.name)
            thObj.put("bgColor", th.bgColor)
            thObj.put("textColor", th.textColor)
            thObj.put("accentColor", th.accentColor)
            themesArray.put(thObj)
        }
        root.put("custom_themes", themesArray)

        return root.toString(2)
    }

    fun importUnifiedBackupJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)

            // Restore settings
            if (root.has("settings")) {
                val s = root.getJSONObject("settings")
                if (s.has("font_size")) setFontSize(s.getInt("font_size"))
                if (s.has("reading_mode")) {
                    try { setReadingMode(ReadingMode.valueOf(s.getString("reading_mode"))) } catch (_: Exception) {}
                }
                if (s.has("theme_family")) {
                    try { setThemeFamily(ThemeFamily.valueOf(s.getString("theme_family"))) } catch (_: Exception) {}
                }
                if (s.has("theme_variant")) {
                    try { setThemeVariant(ThemeVariant.valueOf(s.getString("theme_variant"))) } catch (_: Exception) {}
                }
                if (s.has("typeface_mode")) {
                    try { setTypefaceMode(TypefaceMode.valueOf(s.getString("typeface_mode"))) } catch (_: Exception) {}
                }
                if (s.has("line_height")) setLineHeight(s.getDouble("line_height").toFloat())
                if (s.has("paragraph_spacing")) setParagraphSpacing(s.getDouble("paragraph_spacing").toFloat())
                if (s.has("letter_spacing")) setLetterSpacing(s.getDouble("letter_spacing").toFloat())
                if (s.has("text_alignment_mode")) {
                    try { setTextAlignmentMode(TextAlignmentMode.valueOf(s.getString("text_alignment_mode"))) } catch (_: Exception) {}
                }
                if (s.has("horizontal_padding")) setHorizontalPadding(s.getInt("horizontal_padding"))
                if (s.has("vertical_padding")) setVerticalPadding(s.getInt("vertical_padding"))
                if (s.has("assistant_orb_style")) setAssistantOrbStyle(s.getString("assistant_orb_style"))
                if (s.has("spoiler_shield")) setSpoilerShield(s.getBoolean("spoiler_shield"))
                if (s.has("auto_scroll_speed")) setAutoScrollSpeed(s.getDouble("auto_scroll_speed").toFloat())
                if (s.has("disable_ai")) setDisableAi(s.getBoolean("disable_ai"))
                if (s.has("disable_tts")) setDisableTts(s.getBoolean("disable_tts"))
                if (s.has("ai_provider")) {
                    try { setAiProvider(AiProvider.valueOf(s.getString("ai_provider"))) } catch (_: Exception) {}
                }
                if (s.has("ai_model")) setAiModel(s.getString("ai_model"))
                if (s.has("ai_base_url")) setAiBaseUrl(s.getString("ai_base_url"))
                if (s.has("gemini_api_key")) setGeminiApiKey(s.getString("gemini_api_key"))
                if (s.has("background_texture")) {
                    try { setBackgroundTexture(BackgroundTexture.valueOf(s.getString("background_texture"))) } catch (_: Exception) {}
                }
                if (s.has("custom_bg_uri")) setCustomBgUri(s.getString("custom_bg_uri"))
                if (s.has("custom_bg_color") && s.has("custom_text_color") && s.has("custom_accent_color")) {
                    setCustomThemeColors(s.getLong("custom_bg_color"), s.getLong("custom_text_color"), s.getLong("custom_accent_color"))
                }
            }

            // Restore Books progress
            if (root.has("books_progress")) {
                val bArr = root.getJSONArray("books_progress")
                for (i in 0 until bArr.length()) {
                    val bObj = bArr.getJSONObject(i)
                    val id = bObj.getString("id")
                    val ch = bObj.optInt("chapter", 0)
                    val pg = bObj.optInt("page", 0)
                    val sc = bObj.optInt("scroll", 0)
                    val pr = bObj.optInt("progress", 0)
                    updateReadingPosition(id, ch, pg, sc, pr)
                }
            }

            // Restore Bookmarks
            if (root.has("bookmarks")) {
                val bmArr = root.getJSONArray("bookmarks")
                val restoredBookmarks = mutableListOf<Bookmark>()
                for (i in 0 until bmArr.length()) {
                    val obj = bmArr.getJSONObject(i)
                    val bm = Bookmark(
                        id = obj.getLong("id"),
                        bookTitle = obj.getString("bookTitle"),
                        chapter = obj.getString("chapter"),
                        quote = obj.getString("quote"),
                        color = try { HighlightColor.valueOf(obj.getString("color")) } catch (_: Exception) { HighlightColor.GOLD },
                        note = obj.optString("note", ""),
                        timestamp = obj.optString("timestamp", "Imported")
                    )
                    dbHelper.insertBookmark(bm)
                    restoredBookmarks.add(bm)
                }
                _bookmarks.value = dbHelper.getAllBookmarks()
            }

            // Restore Wishlist
            if (root.has("wishlist")) {
                val wlArr = root.getJSONArray("wishlist")
                for (i in 0 until wlArr.length()) {
                    val obj = wlArr.getJSONObject(i)
                    val item = WishlistBook(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        author = obj.optString("author", ""),
                        note = obj.optString("note", ""),
                        addedAt = obj.optString("addedAt", "Imported")
                    )
                    dbHelper.insertWishlist(item)
                }
                _wishlistBooks.value = dbHelper.getAllWishlist()
            }

            // Restore Completed books
            if (root.has("completed_book_ids")) {
                val cArr = root.getJSONArray("completed_book_ids")
                val compSet = mutableSetOf<String>()
                for (i in 0 until cArr.length()) {
                    val id = cArr.getString(i)
                    compSet.add(id)
                    dbHelper.markBookCompleted(id, true)
                }
                _completedBookIds.value = compSet
            }

            // Restore Custom themes
            if (root.has("custom_themes")) {
                val thArr = root.getJSONArray("custom_themes")
                for (i in 0 until thArr.length()) {
                    val obj = thArr.getJSONObject(i)
                    val theme = CustomThemeData(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        bgColor = obj.getLong("bgColor"),
                        textColor = obj.getLong("textColor"),
                        accentColor = obj.getLong("accentColor")
                    )
                    dbHelper.saveCustomTheme(theme)
                }
                _customThemes.value = dbHelper.getAllCustomThemes()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun toggleBookCompleted(bookId: String) {
        val current = _completedBookIds.value.toMutableSet()
        val isNowCompleted = if (current.contains(bookId)) {
            current.remove(bookId)
            false
        } else {
            current.add(bookId)
            true
        }
        _completedBookIds.value = current
        dbHelper.markBookCompleted(bookId, isNowCompleted)
    }

    fun isBookCompleted(bookId: String): Boolean {
        return _completedBookIds.value.contains(bookId)
    }

    fun saveCustomTheme(name: String, bg: Long, text: Long, accent: Long) {
        val theme = CustomThemeData(
            name = name,
            bgColor = bg,
            textColor = text,
            accentColor = accent
        )
        dbHelper.saveCustomTheme(theme)
        _customThemes.value = dbHelper.getAllCustomThemes()
    }

    fun renameCustomTheme(themeId: String, newName: String) {
        val existing = _customThemes.value.find { it.id == themeId } ?: return
        val updated = existing.copy(name = newName)
        dbHelper.saveCustomTheme(updated)
        _customThemes.value = dbHelper.getAllCustomThemes()
    }

    fun applyCustomTheme(theme: CustomThemeData) {
        setCustomThemeColors(theme.bgColor, theme.textColor, theme.accentColor)
        setThemeFamily(ThemeFamily.CUSTOM)
    }

    fun createAndApplyCustomTheme(name: String, bg: Long, text: Long, accent: Long): CustomThemeData {
        val theme = CustomThemeData(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            bgColor = bg,
            textColor = text,
            accentColor = accent
        )
        dbHelper.saveCustomTheme(theme)
        _customThemes.value = dbHelper.getAllCustomThemes()
        applyCustomTheme(theme)
        return theme
    }

    fun updateAndApplyCustomTheme(name: String, bg: Long? = null, text: Long? = null, accent: Long? = null): CustomThemeData? {
        val existing = _customThemes.value.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
            ?: _customThemes.value.firstOrNull { it.name.lowercase().contains(name.trim().lowercase()) }
            ?: return null
        val updated = existing.copy(
            bgColor = bg ?: existing.bgColor,
            textColor = text ?: existing.textColor,
            accentColor = accent ?: existing.accentColor
        )
        dbHelper.saveCustomTheme(updated)
        _customThemes.value = dbHelper.getAllCustomThemes()
        applyCustomTheme(updated)
        return updated
    }

    fun deleteCustomTheme(themeId: String) {
        dbHelper.deleteCustomTheme(themeId)
        _customThemes.value = dbHelper.getAllCustomThemes()
    }

    fun searchScenes(bookId: String, query: String): List<SceneMatch> {
        val book = _books.value.find { it.id == bookId }
        if (book != null && !dbHelper.isBookFtsIndexed(bookId) && book.chapters.isNotEmpty()) {
            dbHelper.indexEntireBook(bookId, book.chapters)
        }
        return dbHelper.searchScenes(bookId, query)
    }

    fun searchAllBooks(query: String): List<SceneMatch> {
        return dbHelper.searchAllBooks(query)
    }

    fun setDisableStt(disabled: Boolean) {
        _disableStt.value = disabled
        updateReaderSettings { it.copy(disableStt = disabled) }
        prefs.edit().putBoolean("disable_stt", disabled).apply()
        persistSettingToDb("disable_stt", disabled.toString())
    }

    fun setEnableFtsIndexing(enabled: Boolean) {
        _enableFtsIndexing.value = enabled
        updateReaderSettings { it.copy(enableFtsIndexing = enabled) }
        prefs.edit().putBoolean("enable_fts_indexing", enabled).apply()
        persistSettingToDb("enable_fts_indexing", enabled.toString())
    }

    fun clearFtsIndex() {
        dbHelper.clearFtsIndex()
    }

    fun getFtsIndexCount(): Int {
        return dbHelper.getFtsIndexCount()
    }

    fun getImageCacheSizeBytes(): Long {
        return try {
            fun dirSize(dir: File): Long {
                var size = 0L
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) dirSize(file) else file.length()
                }
                return size
            }
            var total = dirSize(context.cacheDir)
            val coversDir = File(context.filesDir, "covers")
            if (coversDir.exists()) total += dirSize(coversDir)
            val booksDir = File(context.filesDir, "books")
            if (booksDir.exists()) total += dirSize(booksDir)
            total
        } catch (_: Exception) {
            0L
        }
    }

    fun clearImageCache() {
        try {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            val coversDir = File(context.filesDir, "covers")
            if (coversDir.exists()) {
                coversDir.deleteRecursively()
                coversDir.mkdirs()
            }
        } catch (_: Exception) {}
    }

    fun getDatabaseSizeBytes(): Long {
        return try {
            val dbFile = context.getDatabasePath(LuminaDatabaseHelper.DATABASE_NAME)
            var size = 0L
            if (dbFile.exists()) size += dbFile.length()
            val wal = File(dbFile.path + "-wal")
            if (wal.exists()) size += wal.length()
            val shm = File(dbFile.path + "-shm")
            if (shm.exists()) size += shm.length()
            size
        } catch (_: Exception) {
            0L
        }
    }

    fun resetReadingProgress() {
        _books.value = _books.value.map { b ->
            prefs.edit()
                .putInt("${b.id}_chapter", 0)
                .putInt("${b.id}_page", 0)
                .putInt("${b.id}_scroll", 0)
                .apply()
            b.copy(progress = 0, currentChapter = 0, currentPage = 0, scrollPos = 0)
        }
    }

    fun indexBookIfNeeded(book: Book) {
        if (!_enableFtsIndexing.value) return
        if (book.chapters.isEmpty()) return
        repoScope.launch(Dispatchers.IO) {
            if (dbHelper.isBookFtsIndexed(book.id)) return@launch
            try {
                _isIndexingActive.value = true
                _indexingProgress.value = "Indexing ${book.title}..."
                val count = dbHelper.indexEntireBook(book.id, book.chapters)
                _indexingProgress.value = "Indexed $count paragraphs"
            } catch (_: Exception) {
                _indexingProgress.value = "Indexing failed"
            } finally {
                _isIndexingActive.value = false
            }
        }
    }

    fun indexEntireBookNow(book: Book, onComplete: ((Int) -> Unit)? = null) {
        repoScope.launch(Dispatchers.IO) {
            _isIndexingActive.value = true
            _indexingProgress.value = "Indexing ${book.title}..."
            try {
                val count = dbHelper.indexEntireBook(book.id, book.chapters)
                _indexingProgress.value = "Indexed $count paragraphs"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(count)
                }
            } catch (_: Exception) {
            } finally {
                _isIndexingActive.value = false
            }
        }
    }

    fun setPreferredLanguage(langCode: String) {
        _preferredLanguage.value = langCode
        updateReaderSettings { it.copy(preferredLanguage = langCode) }
        prefs.edit().putString("preferred_language", langCode).apply()
        persistSettingToDb("preferred_language", langCode)
    }

    fun getEffectiveLanguage(): String {
        val pref = _preferredLanguage.value
        if (pref.isNotBlank() && pref != "auto") return pref
        return getActiveBook()?.language?.takeIf { it.isNotBlank() } ?: "en"
    }

    fun setReadingProgressPercent(bookId: String, percent: Int) {
        val book = _books.value.find { it.id == bookId } ?: return
        val totalChapters = book.chapters.size
        if (totalChapters == 0) return
        val clampedPercent = percent.coerceIn(0, 100)
        val targetChapter = ((clampedPercent.toFloat() / 100f) * (totalChapters - 1)).toInt().coerceIn(0, totalChapters - 1)
        updateReadingPosition(
            bookId = bookId,
            chapterIdx = targetChapter,
            pageIdx = 0,
            scrollPos = 0,
            progressPct = clampedPercent
        )
    }

    fun getReadTillPercent(bookId: String): Int {
        val inMemory = _readTillMap.value[bookId]
        if (inMemory != null) return inMemory
        val persisted = prefs.getInt("${bookId}_read_till", -1)
        if (persisted != -1) {
            _readTillMap.value = _readTillMap.value + (bookId to persisted)
            return persisted
        }
        val bookProg = _books.value.find { it.id == bookId }?.progress ?: 0
        _readTillMap.value = _readTillMap.value + (bookId to bookProg)
        return bookProg
    }

    fun forceSetReadTillPercent(bookId: String, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _readTillMap.value = _readTillMap.value + (bookId to clamped)
        prefs.edit().putInt("${bookId}_read_till", clamped).apply()
        persistSettingToDb("read_till_${bookId}", clamped.toString())
    }

    fun forceSetLastReadPosition(bookId: String, chapterIdx: Int, pageIdx: Int, scrollPos: Int, progressPct: Int) {
        val clamped = progressPct.coerceIn(0, 100)
        _readTillMap.value = _readTillMap.value + (bookId to clamped)
        prefs.edit().putInt("${bookId}_read_till", clamped).apply()
        persistSettingToDb("read_till_${bookId}", clamped.toString())

        val updated = _books.value.map {
            if (it.id == bookId) {
                it.copy(
                    currentChapter = chapterIdx,
                    currentPage = pageIdx,
                    scrollPos = scrollPos,
                    progress = clamped,
                    lastRead = "Just now"
                )
            } else it
        }
        _books.value = updated

        prefs.edit()
            .putInt("${bookId}_chapter", chapterIdx)
            .putInt("${bookId}_page", pageIdx)
            .putInt("${bookId}_scroll", scrollPos)
            .putInt("${bookId}_progress", clamped)
            .apply()

        repoScope.launch {
            try {
                val book = _books.value.find { it.id == bookId }
                if (book != null) {
                    dbHelper.updateReadingProgress(bookId, book.title, book.author, clamped, "${100 - clamped}m left")
                    dbHelper.updateBookProgress(bookId, chapterIdx, pageIdx, scrollPos, clamped)
                }
            } catch (_: Exception) {}
        }
    }

    fun setAiProvider(provider: AiProvider) {
        _aiProvider.value = provider
        updateReaderSettings { it.copy(aiProvider = provider) }
        prefs.edit().putString("ai_provider", provider.name).apply()
        persistSettingToDb("ai_provider", provider.name)
    }

    fun setAiBaseUrl(url: String) {
        _aiBaseUrl.value = url
        updateReaderSettings { it.copy(aiBaseUrl = url) }
        prefs.edit().putString("ai_base_url", url).apply()
        persistSettingToDb("ai_base_url", url)
    }

    fun setAiModel(model: String) {
        _aiModel.value = model
        updateReaderSettings { it.copy(aiModel = model) }
        prefs.edit().putString("ai_model", model).apply()
        persistSettingToDb("ai_model", model)
    }

    fun toggleQuickTheme(family: ThemeFamily) {
        val current = _quickThemes.value.toMutableSet()
        if (current.contains(family)) {
            if (current.size > 1) current.remove(family)
        } else {
            current.add(family)
        }
        _quickThemes.value = current
        updateReaderSettings { it.copy(quickThemes = current) }
        prefs.edit().putStringSet("quick_themes", current.map { it.name }.toSet()).apply()
        persistSettingToDb("quick_themes", current.joinToString(",") { it.name })
    }

    fun toggleQuickFont(font: TypefaceMode) {
        val current = _quickFonts.value.toMutableSet()
        if (current.contains(font)) {
            if (current.size > 1) current.remove(font)
        } else {
            current.add(font)
        }
        _quickFonts.value = current
        updateReaderSettings { it.copy(quickFonts = current) }
        prefs.edit().putStringSet("quick_fonts", current.map { it.name }.toSet()).apply()
        persistSettingToDb("quick_fonts", current.joinToString(",") { it.name })
    }

    fun reorderOrbAction(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val list = _orbActionOrder.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _orbActionOrder.value = list
            updateReaderSettings { it.copy(orbActionOrder = list) }
            prefs.edit().putString("orb_action_order", list.joinToString(",") { it.name }).apply()
            persistSettingToDb("orb_action_order", list.joinToString(",") { it.name })
        }
    }

    fun getActiveBook(): Book? {
        val id = _activeBookId.value
        val book = _books.value.find { it.id == id } ?: _books.value.firstOrNull() ?: return null
        if (book.chapters.isNotEmpty()) return book
        val chaps = getChaptersForBook(book.id)
        return if (chaps.isNotEmpty()) book.copy(chapters = chaps) else book
    }

    fun setActiveBook(bookId: String) {
        _activeBookId.value = bookId
        prefs.edit().putString("active_book_id", bookId).apply()
        
        // Update last read timestamp
        val updated = _books.value.map {
            if (it.id == bookId) it.copy(lastRead = "Just now") else it
        }
        _books.value = updated
        repoScope.launch(Dispatchers.IO) {
            val chaps = getChaptersForBook(bookId)
            if (chaps.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _books.value = _books.value.map {
                        if (it.id == bookId && it.chapters.isEmpty()) it.copy(chapters = chaps) else it
                    }
                }
            }
        }
    }

    fun updateReadingPosition(bookId: String, chapterIdx: Int, pageIdx: Int, scrollPos: Int, progressPct: Int) {
        val currentPos = _readingPosition.value
        if (currentPos.bookId == bookId &&
            currentPos.chapterIndex == chapterIdx &&
            currentPos.pageIndex == pageIdx &&
            currentPos.scrollPos == scrollPos &&
            currentPos.progressPct == progressPct
        ) {
            return
        }

        _readingPosition.value = ReadingPosition(bookId, chapterIdx, pageIdx, scrollPos, progressPct)

        // Persist per book in prefs immediately
        prefs.edit()
            .putInt("${bookId}_chapter", chapterIdx)
            .putInt("${bookId}_page", pageIdx)
            .putInt("${bookId}_scroll", scrollPos)
            .putInt("${bookId}_progress", progressPct)
            .apply()

        // Incremental advance: furthest place scrolled/read is read till
        val currentReadTill = getReadTillPercent(bookId)
        if (progressPct > currentReadTill) {
            val clamped = progressPct.coerceIn(0, 100)
            _readTillMap.value = _readTillMap.value + (bookId to clamped)
            prefs.edit().putInt("${bookId}_read_till", clamped).apply()
            persistSettingToDb("read_till_${bookId}", clamped.toString())
        }

        // Debounce updating _books.value and SQLite reading progress updates to prevent database lock contention & UI recomposition storms
        readingPositionSaveJob?.cancel()
        readingPositionSaveJob = repoScope.launch {
            kotlinx.coroutines.delay(800)
            try {
                val updated = _books.value.map {
                    if (it.id == bookId) {
                        it.copy(
                            currentChapter = chapterIdx,
                            currentPage = pageIdx,
                            scrollPos = scrollPos,
                            progress = progressPct,
                            lastRead = "Just now"
                        )
                    } else it
                }
                _books.value = updated

                val book = updated.find { it.id == bookId }
                if (book != null) {
                    dbHelper.updateReadingProgress(bookId, book.title, book.author, progressPct, "${100 - progressPct}m left")
                    dbHelper.updateBookProgress(bookId, chapterIdx, pageIdx, scrollPos, progressPct)
                }
            } catch (_: Exception) {}
        }
    }

    fun updateCharacterCheckpoint(bookId: String, checkpointChapter: Int, checkpointPage: Int) {
        val updated = _books.value.map {
            if (it.id == bookId) {
                it.copy(
                    characterCheckpointChapter = checkpointChapter,
                    characterCheckpointPage = checkpointPage
                )
            } else it
        }
        _books.value = updated
        dbHelper.updateCharacterCheckpoint(bookId, checkpointChapter, checkpointPage)
    }

    fun setFontSize(size: Int) {
        _fontSize.value = size
        updateReaderSettings { it.copy(fontSize = size) }
        prefs.edit().putInt("font_size", size).apply()
        persistSettingToDb("font_size", size.toString())
    }

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        updateReaderSettings { it.copy(readingMode = mode) }
        prefs.edit().putString("reading_mode", mode.name).apply()
        persistSettingToDb("reading_mode", mode.name)
    }

    fun setThemeMode(theme: ThemeMode) {
        _themeMode.value = theme
        updateReaderSettings { it.copy(themeMode = theme) }
        prefs.edit().putString("theme_mode", theme.name).apply()
        persistSettingToDb("theme_mode", theme.name)
        when (theme) {
            ThemeMode.WARM_PAPER -> {
                setThemeFamily(ThemeFamily.PAPER)
                setThemeVariant(ThemeVariant.LIGHT)
            }
            ThemeMode.PURE_WHITE -> {
                setThemeFamily(ThemeFamily.MODERN)
                setThemeVariant(ThemeVariant.LIGHT)
            }
            ThemeMode.NIGHT -> {
                setThemeFamily(ThemeFamily.MODERN)
                setThemeVariant(ThemeVariant.DARK)
            }
        }
    }

    fun setThemeFamily(family: ThemeFamily) {
        _themeFamily.value = family
        updateReaderSettings { it.copy(themeFamily = family) }
        prefs.edit().putString("theme_family", family.name).apply()
        persistSettingToDb("theme_family", family.name)
    }

    fun setThemeVariant(variant: ThemeVariant) {
        _themeVariant.value = variant
        updateReaderSettings { it.copy(themeVariant = variant) }
        prefs.edit().putString("theme_variant", variant.name).apply()
        persistSettingToDb("theme_variant", variant.name)
    }

    fun setBackgroundTexture(texture: BackgroundTexture) {
        _backgroundTexture.value = texture
        updateReaderSettings { it.copy(backgroundTexture = texture) }
        prefs.edit().putString("background_texture", texture.name).apply()
        persistSettingToDb("background_texture", texture.name)
    }

    fun setCustomBgUri(uri: String) {
        _customBgUri.value = uri
        updateReaderSettings { it.copy(customBgUri = uri) }
        prefs.edit().putString("custom_bg_uri", uri).apply()
        persistSettingToDb("custom_bg_uri", uri)
    }

    fun setOrbActionItems(items: Set<OrbActionItem>) {
        _orbActionItems.value = items
        updateReaderSettings { it.copy(orbActionItems = items) }
        prefs.edit().putStringSet("orb_action_items", items.map { it.name }.toSet()).apply()
        persistSettingToDb("orb_action_items", items.joinToString(",") { it.name })
    }

    fun toggleOrbActionItem(item: OrbActionItem) {
        val current = _orbActionItems.value.toMutableSet()
        if (current.contains(item)) {
            current.remove(item)
        } else {
            current.add(item)
        }
        setOrbActionItems(current)
    }

    fun setTextAlignmentMode(alignment: TextAlignmentMode) {
        _textAlignmentMode.value = alignment
        updateReaderSettings { it.copy(textAlignmentMode = alignment) }
        prefs.edit().putString("text_alignment_mode", alignment.name).apply()
        persistSettingToDb("text_alignment_mode", alignment.name)
    }

    fun setLetterSpacing(spacing: Float) {
        _letterSpacing.value = spacing
        updateReaderSettings { it.copy(letterSpacing = spacing) }
        prefs.edit().putFloat("letter_spacing", spacing).apply()
        persistSettingToDb("letter_spacing", spacing.toString())
    }

    fun setTypefaceMode(typeface: TypefaceMode) {
        _typefaceMode.value = typeface
        updateReaderSettings { it.copy(typefaceMode = typeface) }
        prefs.edit().putString("typeface_mode", typeface.name).apply()
        persistSettingToDb("typeface_mode", typeface.name)
    }

    fun setLineHeight(multiplier: Float) {
        _lineHeightMultiplier.value = multiplier
        updateReaderSettings { it.copy(lineHeightMultiplier = multiplier) }
        prefs.edit().putFloat("line_height", multiplier).apply()
        persistSettingToDb("line_height", multiplier.toString())
    }

    fun setParagraphSpacing(multiplier: Float) {
        _paragraphSpacingMultiplier.value = multiplier
        updateReaderSettings { it.copy(paragraphSpacingMultiplier = multiplier) }
        prefs.edit().putFloat("paragraph_spacing", multiplier).apply()
        persistSettingToDb("paragraph_spacing", multiplier.toString())
    }

    fun setShowFloatingAssistant(show: Boolean) {
        _showFloatingAssistant.value = show
        updateReaderSettings { it.copy(showFloatingAssistant = show) }
        prefs.edit().putBoolean("show_floating_assistant", show).apply()
        persistSettingToDb("show_floating_assistant", show.toString())
    }

    fun setOrbSize(size: OrbSize) {
        _orbSize.value = size
        updateReaderSettings { it.copy(orbSize = size) }
        prefs.edit().putString("orb_size", size.name).apply()
        persistSettingToDb("orb_size", size.name)
    }

    fun setOrbMenuSize(size: OrbMenuSize) {
        _orbMenuSize.value = size
        updateReaderSettings { it.copy(orbMenuSize = size) }
        prefs.edit().putString("orb_menu_size", size.name).apply()
        persistSettingToDb("orb_menu_size", size.name)
    }

    fun setOrbEdgeSnap(snap: Boolean) {
        _orbEdgeSnap.value = snap
        updateReaderSettings { it.copy(orbEdgeSnap = snap) }
        prefs.edit().putBoolean("orb_edge_snap", snap).apply()
        persistSettingToDb("orb_edge_snap", snap.toString())
    }

    fun saveOrbPosition(x: Float, y: Float, isLandscape: Boolean) {
        if (isLandscape) {
            _orbLandscapeX.value = x
            _orbLandscapeY.value = y
            updateReaderSettings { it.copy(orbLandscapeX = x, orbLandscapeY = y) }
            prefs.edit().putFloat("orb_pos_x_landscape", x).putFloat("orb_pos_y_landscape", y).apply()
            persistSettingToDb("orb_pos_x_landscape", x.toString())
            persistSettingToDb("orb_pos_y_landscape", y.toString())
        } else {
            _orbPortraitX.value = x
            _orbPortraitY.value = y
            updateReaderSettings { it.copy(orbPortraitX = x, orbPortraitY = y) }
            prefs.edit().putFloat("orb_pos_x_portrait", x).putFloat("orb_pos_y_portrait", y).apply()
            persistSettingToDb("orb_pos_x_portrait", x.toString())
            persistSettingToDb("orb_pos_y_portrait", y.toString())
        }
    }

    fun setOrbColor(color: OrbColor) {
        _orbColor.value = color
        updateReaderSettings { it.copy(orbColor = color) }
        prefs.edit().putString("orb_color", color.name).apply()
        persistSettingToDb("orb_color", color.name)
    }

    fun setOrbOpacity(opacity: Float) {
        _orbOpacity.value = opacity
        updateReaderSettings { it.copy(orbOpacity = opacity) }
        prefs.edit().putFloat("orb_opacity", opacity).apply()
        persistSettingToDb("orb_opacity", opacity.toString())
    }

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        updateReaderSettings { it.copy(geminiApiKey = key) }
        prefs.edit().putString("gemini_api_key", key).apply()
        persistSettingToDb("gemini_api_key", key)
    }

    fun setLastTab(tab: String) {
        prefs.edit().putString("last_screen_tab", tab).apply()
    }

    fun getLastTab(): String {
        return prefs.getString("last_screen_tab", "LIBRARY") ?: "LIBRARY"
    }

    fun addBookmark(
        quote: String,
        color: HighlightColor = HighlightColor.GOLD,
        note: String = "",
        chapterTitle: String? = null,
        pageNumber: Int = 0
    ) {
        val book = getActiveBook() ?: return
        val chapter = chapterTitle?.ifBlank { null } ?: book.chapters.getOrNull(book.currentChapter)?.title ?: "Chapter"
        val actualPage = if (pageNumber > 0) pageNumber else (book.currentPage + 1)
        val mark = Bookmark(
            bookTitle = book.title,
            chapter = chapter,
            quote = quote,
            color = color,
            note = note.trim(),
            timestamp = "Just now",
            pageNumber = actualPage
        )
        val updated = listOf(mark) + _bookmarks.value
        _bookmarks.value = updated
        dbHelper.insertBookmark(mark)
        saveBookmarks(updated)
    }

    fun updateBookmark(bookmark: Bookmark) {
        val updated = _bookmarks.value.map { if (it.id == bookmark.id) bookmark else it }
        _bookmarks.value = updated
        dbHelper.insertBookmark(bookmark)
        saveBookmarks(updated)
    }

    fun removeBookmark(id: Long) {
        val updated = _bookmarks.value.filterNot { it.id == id }
        _bookmarks.value = updated
        dbHelper.deleteBookmark(id)
        saveBookmarks(updated)
    }

    // --- Wishlist Management ---

    fun addToWishlist(title: String, author: String = "", note: String = "") {
        val item = WishlistBook(
            title = title.trim(),
            author = author.trim(),
            note = note.trim(),
            addedAt = "Today"
        )
        dbHelper.insertWishlist(item)
        _wishlistBooks.value = dbHelper.getAllWishlist()
    }

    fun removeFromWishlist(id: String) {
        dbHelper.deleteWishlist(id)
        _wishlistBooks.value = dbHelper.getAllWishlist()
    }

    // --- Book Characters Management ---

    private val _characters = MutableStateFlow<Map<String, List<BookCharacter>>>(emptyMap())
    val characters: StateFlow<Map<String, List<BookCharacter>>> = _characters.asStateFlow()

    fun getCharactersForBook(bookId: String): List<BookCharacter> {
        val cached = _characters.value[bookId]
        if (cached != null) return cached
        dbHelper.deduplicateCharacters(bookId)
        val list = dbHelper.getCharacters(bookId)
        _characters.value = _characters.value + (bookId to list)
        return list
    }

    fun loadCharacters(bookId: String) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.deduplicateCharacters(bookId)
            val list = dbHelper.getCharacters(bookId)
            _characters.value = _characters.value + (bookId to list)
        }
    }

    fun saveCharacter(character: BookCharacter) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.insertCharacter(character)
            val list = dbHelper.getCharacters(character.bookId)
            _characters.value = _characters.value + (character.bookId to list)
        }
    }

    fun saveCharacters(bookId: String, newCharacters: List<BookCharacter>) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.saveCharacters(bookId, newCharacters)
            dbHelper.deduplicateCharacters(bookId)
            val list = dbHelper.getCharacters(bookId)
            _characters.value = _characters.value + (bookId to list)
        }
    }

    fun deleteCharacter(id: Long, bookId: String) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.deleteCharacter(id)
            val list = dbHelper.getCharacters(bookId)
            _characters.value = _characters.value + (bookId to list)
        }
    }

    fun clearCharacters(bookId: String) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.clearCharacters(bookId)
            _characters.value = _characters.value + (bookId to emptyList())
        }
    }

    // --- Book Lore Management ---

    private val _lore = MutableStateFlow<Map<String, List<BookLore>>>(emptyMap())
    val lore: StateFlow<Map<String, List<BookLore>>> = _lore.asStateFlow()

    fun getLoreForBook(bookId: String): List<BookLore> {
        val cached = _lore.value[bookId]
        if (cached != null) return cached
        dbHelper.deduplicateLore(bookId)
        val list = dbHelper.getLore(bookId)
        _lore.value = _lore.value + (bookId to list)
        return list
    }

    fun loadLore(bookId: String) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.deduplicateLore(bookId)
            val list = dbHelper.getLore(bookId)
            _lore.value = _lore.value + (bookId to list)
        }
    }

    fun saveLore(loreItem: BookLore) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.insertLore(loreItem)
            val list = dbHelper.getLore(loreItem.bookId)
            _lore.value = _lore.value + (loreItem.bookId to list)
        }
    }

    fun saveLoreList(bookId: String, newLoreList: List<BookLore>) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.saveLoreList(bookId, newLoreList)
            dbHelper.deduplicateLore(bookId)
            val list = dbHelper.getLore(bookId)
            _lore.value = _lore.value + (bookId to list)
        }
    }

    fun deleteLore(id: Long, bookId: String) {
        repoScope.launch(Dispatchers.IO) {
            dbHelper.deleteLore(id)
            val list = dbHelper.getLore(bookId)
            _lore.value = _lore.value + (bookId to list)
        }
    }

    fun clearLore(bookId: String) {
        repoScope.launch {
            dbHelper.clearLore(bookId)
            _lore.value = _lore.value + (bookId to emptyList())
        }
    }

    // --- SQLite Content Sharing ---

    fun shareHighlights(context: Context, bookTitle: String? = null) {
        val text = dbHelper.formatHighlightsForShare(bookTitle)
        shareContent(context, text, "Reading Highlights - Lumina")
    }

    fun shareReadingList(context: Context) {
        val text = dbHelper.formatReadingListForShare()
        shareContent(context, text, "My Lumina Reading List")
    }

    fun shareContent(context: Context, text: String, title: String) {
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            putExtra(android.content.Intent.EXTRA_TITLE, title)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, title).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }

    fun addBook(book: Book) {
        // Undelete if previously deleted
        val currentDeleted = prefs.getStringSet("deleted_book_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentDeleted.contains(book.id)) {
            currentDeleted.remove(book.id)
            prefs.edit().putStringSet("deleted_book_ids", currentDeleted).apply()
        }

        val updated = listOf(book) + _books.value.filterNot { it.id == book.id }
        _books.value = updated
        if (book.chapters.isNotEmpty()) {
            chapterCache.put(book.id, book.chapters)
        }
        setActiveBook(book.id)
        dbHelper.insertOrUpdateBook(book, book.filePath, book.isDownloaded, book.downloadUrl, book.fileSize)
    }

    suspend fun importEpubFromUri(uri: Uri): Book? = withContext(Dispatchers.IO) {
        try {
            var fileName: String? = null
            if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            fileName = cursor.getString(index)
                        }
                    }
                }
            }
            if (fileName.isNullOrBlank()) {
                fileName = uri.path?.substringAfterLast('/')?.substringAfterLast(':') ?: uri.lastPathSegment
            }
            val cleanName = if (!fileName.isNullOrBlank() && fileName.endsWith(".epub", ignoreCase = true)) {
                fileName
            } else {
                "${fileName ?: "Imported"}.epub"
            }
            val epubDir = LuminaStorageManager.getPersistentEpubDirectory(context)
            val destFile = File(epubDir, "${System.currentTimeMillis()}_$cleanName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            destFile.inputStream().use { stream ->
                val parsedBook = EpubParser.parseEpub(stream, cleanName, context)
                val bookToSave = parsedBook.copy(
                    filePath = destFile.absolutePath,
                    fileSize = destFile.length(),
                    isDownloaded = false
                )
                addBook(bookToSave)
                bookToSave
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun removeBook(bookId: String) {
        chapterCache.remove(bookId)
        PageCache.invalidate(bookId, dbHelper)
        val currentDeleted = prefs.getStringSet("deleted_book_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentDeleted.add(bookId)
        prefs.edit().putStringSet("deleted_book_ids", currentDeleted).apply()

        val updated = _books.value.filterNot { it.id == bookId }
        _books.value = updated
        dbHelper.deleteBook(bookId)

        try {
            val imgDir = File(context.filesDir, "books/$bookId")
            if (imgDir.exists()) {
                imgDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val epubFile = File(context.filesDir, "epubs/${bookId}.epub")
            if (epubFile.exists()) {
                epubFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If active book was removed, switch to another book or empty
        if (_activeBookId.value == bookId) {
            val fallback = updated.firstOrNull()
            if (fallback != null) {
                setActiveBook(fallback.id)
            } else {
                _activeBookId.value = ""
                prefs.edit().remove("active_book_id").apply()
            }
        }
    }

    private fun loadAllBooks(): List<Book> {
        try {
            dbHelper.purgeDemoBooks()
        } catch (_: Exception) {}

        try {
            val legacyFile = File(context.filesDir, "custom_books.json")
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
        } catch (_: Exception) {}

        val fromDb = try { dbHelper.getAllBooks() } catch (_: Exception) { emptyList() }
        val searchDirs = LuminaStorageManager.getAllSearchDirectories(context)

        val resolvedBooks = fromDb.map { b ->
            if (!b.filePath.isNullOrBlank() && File(b.filePath).exists()) {
                b
            } else {
                // Attempt recovery: check if a file with matching name or ID exists in persistent storage
                val originalFileName = b.filePath.substringAfterLast('/')
                var recoveredFile: File? = null
                for (dir in searchDirs) {
                    if (originalFileName.isNotBlank()) {
                        val candidate = File(dir, originalFileName)
                        if (candidate.exists() && candidate.length() > 0L) {
                            recoveredFile = candidate
                            break
                        }
                    }
                    val idCandidate = File(dir, "${b.id}.epub")
                    if (idCandidate.exists() && idCandidate.length() > 0L) {
                        recoveredFile = idCandidate
                        break
                    }
                }
                if (recoveredFile != null) {
                    val updated = b.copy(filePath = recoveredFile.absolutePath, fileSize = recoveredFile.length())
                    try {
                        dbHelper.updateBookFilePath(updated.id, recoveredFile.absolutePath, recoveredFile.length())
                    } catch (_: Exception) {}
                    updated
                } else {
                    b
                }
            }
        }

        return resolvedBooks.filterNot { b ->
            b.id in setOf("book-kafka", "book-alice", "book-artofwar", "1", "2", "3", "demo-kafka", "demo-alice", "demo-artofwar") ||
            ((b.filePath.isNullOrBlank() || !File(b.filePath).exists()) && !b.isDownloaded)
        }
    }

    suspend fun autoScanAndLoadPersistentEpubs() = withContext(Dispatchers.IO) {
        try {
            val scannedFiles = LuminaStorageManager.scanEpubFiles(context)
            if (scannedFiles.isEmpty()) return@withContext

            val currentBooks = _books.value
            val existingPaths = currentBooks.mapNotNull { it.filePath.takeIf { p -> p.isNotBlank() } }
                .map { try { File(it).canonicalPath } catch (_: Exception) { it } }.toSet()
            val existingTitles = currentBooks.map { it.title.trim().lowercase() }.toSet()
            val deletedIds = prefs.getStringSet("deleted_book_ids", emptySet()) ?: emptySet()

            // Pre-index SQLite books once to avoid querying SQLite inside the loop
            val dbBooks = try { dbHelper.getAllBooks() } catch (_: Exception) { emptyList() }
            val dbPathMap = dbBooks.filter { it.filePath.isNotBlank() }.associateBy {
                try { File(it.filePath).canonicalPath } catch (_: Exception) { it.filePath }
            }
            val dbFileNameMap = dbBooks.filter { it.filePath.isNotBlank() }.associateBy {
                it.filePath.substringAfterLast('/')
            }

            val newlyDiscoveredBooks = mutableListOf<Book>()
            val updatedBooks = mutableListOf<Book>()

            for (epubFile in scannedFiles) {
                val canonical = try { epubFile.canonicalPath } catch (_: Exception) { epubFile.absolutePath }
                if (canonical in existingPaths) continue

                // Check if already in SQLite db via fast in-memory map lookup
                val dbMatch = dbPathMap[canonical] ?: dbFileNameMap[epubFile.name]
                if (dbMatch != null) {
                    if (dbMatch.filePath != canonical) {
                        dbHelper.updateBookFilePath(dbMatch.id, canonical, epubFile.length())
                        val updated = dbMatch.copy(filePath = canonical, fileSize = epubFile.length())
                        updatedBooks.add(updated)
                    }
                    continue
                }

                // If user previously deleted this book explicitly by ID, do not re-import unless filename changed
                if (epubFile.nameWithoutExtension in deletedIds) continue

                // Parse and stage new book without triggering activeBook changes or recomposition storms
                try {
                    epubFile.inputStream().use { stream ->
                        val parsed = EpubParser.parseEpub(stream, epubFile.name, context)
                        if (parsed.title.trim().lowercase() in existingTitles) {
                            return@use
                        }
                        val bookToSave = parsed.copy(
                            filePath = epubFile.absolutePath,
                            fileSize = epubFile.length(),
                            isDownloaded = false
                        )
                        // Save directly to SQLite and cache chapters
                        dbHelper.insertOrUpdateBook(bookToSave, bookToSave.filePath, bookToSave.isDownloaded, bookToSave.downloadUrl, bookToSave.fileSize)
                        if (bookToSave.chapters.isNotEmpty()) {
                            chapterCache.put(bookToSave.id, bookToSave.chapters)
                        }
                        newlyDiscoveredBooks.add(bookToSave)
                        android.util.Log.i("BookRepository", "Auto-loaded EPUB from persistent storage: ${epubFile.name} as \"${bookToSave.title}\"")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("BookRepository", "Failed to auto-load EPUB ${epubFile.name}: ${e.message}")
                }
            }

            // Single atomic state update to _books.value at the end of the scan
            if (newlyDiscoveredBooks.isNotEmpty() || updatedBooks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    var merged = _books.value
                    if (updatedBooks.isNotEmpty()) {
                        val updatedMap = updatedBooks.associateBy { it.id }
                        merged = merged.map { updatedMap[it.id] ?: it }
                    }
                    if (newlyDiscoveredBooks.isNotEmpty()) {
                        val existingIds = merged.map { it.id }.toSet()
                        val toAppend = newlyDiscoveredBooks.filterNot { it.id in existingIds }
                        if (toAppend.isNotEmpty()) {
                            merged = merged + toAppend
                        }
                    }
                    _books.value = merged
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "Error in autoScanAndLoadPersistentEpubs: ${e.message}")
        }
    }

    fun scanAndSyncPersistentEpubs() {
        repoScope.launch(Dispatchers.IO) {
            autoScanAndLoadPersistentEpubs()
        }
    }


    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        try {
            val array = JSONArray()
            bookmarks.forEach { bm ->
                val obj = JSONObject()
                obj.put("id", bm.id)
                obj.put("bookTitle", bm.bookTitle)
                obj.put("chapter", bm.chapter)
                obj.put("quote", bm.quote)
                obj.put("color", bm.color.name)
                obj.put("note", bm.note)
                obj.put("timestamp", bm.timestamp)
                array.put(obj)
            }
            File(context.filesDir, "bookmarks.json").writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPersistedBookmarks(): List<Bookmark> {
        val fromDb = try { dbHelper.getAllBookmarks() } catch (_: Exception) { emptyList() }
        if (fromDb.isNotEmpty()) return fromDb

        val file = File(context.filesDir, "custom_bookmarks.json")
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(file.readText())
            val list = mutableListOf<Bookmark>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val bm = Bookmark(
                    id = obj.getLong("id"),
                    bookTitle = obj.getString("bookTitle"),
                    chapter = obj.getString("chapter"),
                    quote = obj.getString("quote"),
                    color = try { HighlightColor.valueOf(obj.getString("color")) } catch (_: Exception) { HighlightColor.GOLD },
                    note = obj.optString("note", ""),
                    timestamp = obj.optString("timestamp", "Just now")
                )
                list.add(bm)
                dbHelper.insertBookmark(bm)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistSettingToDb(key: String, value: String) {
        try {
            dbHelper.setSetting(key, value)
        } catch (_: Exception) {}
    }

    private fun syncSettings() {
        val dbSettings = dbHelper.getAllSettings()
        if (dbSettings.isNotEmpty()) {
            var s = _readerSettings.value
            dbSettings["font_size"]?.toIntOrNull()?.let { s = s.copy(fontSize = it) }
            dbSettings["reading_mode"]?.let {
                try { s = s.copy(readingMode = ReadingMode.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["theme_mode"]?.let {
                try { s = s.copy(themeMode = ThemeMode.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["typeface_mode"]?.let {
                try { s = s.copy(typefaceMode = TypefaceMode.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["line_height"]?.toFloatOrNull()?.let { s = s.copy(lineHeightMultiplier = it) }
            dbSettings["paragraph_spacing"]?.toFloatOrNull()?.let { s = s.copy(paragraphSpacingMultiplier = it) }
            dbSettings["letter_spacing"]?.toFloatOrNull()?.let { s = s.copy(letterSpacing = it) }
            dbSettings["show_floating_assistant"]?.toBooleanStrictOrNull()?.let { s = s.copy(showFloatingAssistant = it) }
            dbSettings["orb_size"]?.let {
                try { s = s.copy(orbSize = OrbSize.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["orb_menu_size"]?.let {
                try { s = s.copy(orbMenuSize = OrbMenuSize.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["orb_edge_snap"]?.toBooleanStrictOrNull()?.let { s = s.copy(orbEdgeSnap = it) }
            dbSettings["orb_color"]?.let {
                try { s = s.copy(orbColor = OrbColor.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["orb_opacity"]?.toFloatOrNull()?.let { s = s.copy(orbOpacity = it) }
            dbSettings["orb_pos_x_portrait"]?.toFloatOrNull()?.let { s = s.copy(orbPortraitX = it) }
            dbSettings["orb_pos_y_portrait"]?.toFloatOrNull()?.let { s = s.copy(orbPortraitY = it) }
            dbSettings["orb_pos_x_landscape"]?.toFloatOrNull()?.let { s = s.copy(orbLandscapeX = it) }
            dbSettings["orb_pos_y_landscape"]?.toFloatOrNull()?.let { s = s.copy(orbLandscapeY = it) }
            dbSettings["theme_family"]?.let {
                try { s = s.copy(themeFamily = ThemeFamily.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["theme_variant"]?.let {
                try { s = s.copy(themeVariant = ThemeVariant.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["background_texture"]?.let {
                try { s = s.copy(backgroundTexture = BackgroundTexture.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["custom_bg_uri"]?.let { s = s.copy(customBgUri = it) }
            dbSettings["text_alignment_mode"]?.let {
                try { s = s.copy(textAlignmentMode = TextAlignmentMode.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["horizontal_padding"]?.toIntOrNull()?.let { s = s.copy(horizontalPadding = it) }
            dbSettings["vertical_padding"]?.toIntOrNull()?.let { s = s.copy(verticalPadding = it) }
            dbSettings["assistant_orb_style"]?.let { s = s.copy(assistantOrbStyle = it) }
            dbSettings["spoiler_shield"]?.toBooleanStrictOrNull()?.let { s = s.copy(spoilerShield = it) }
            dbSettings["auto_scroll_speed"]?.toFloatOrNull()?.let { s = s.copy(autoScrollSpeed = it) }
            dbSettings["disable_ai"]?.toBooleanStrictOrNull()?.let { s = s.copy(disableAi = it) }
            dbSettings["disable_tts"]?.toBooleanStrictOrNull()?.let { s = s.copy(disableTts = it) }
            dbSettings["tts_engine"]?.let { s = s.copy(ttsEngine = it) }
            dbSettings["tts_edge_voice"]?.let { s = s.copy(ttsEdgeVoice = it) }
            dbSettings["tts_speed"]?.toFloatOrNull()?.let { s = s.copy(ttsSpeed = it) }
            dbSettings["tts_pitch"]?.toFloatOrNull()?.let { s = s.copy(ttsPitch = it) }
            dbSettings["disable_stt"]?.toBooleanStrictOrNull()?.let { s = s.copy(disableStt = it) }
            dbSettings["auto_start_mic"]?.toBooleanStrictOrNull()?.let { s = s.copy(autoStartMic = it) }
            dbSettings["enable_fts_indexing"]?.toBooleanStrictOrNull()?.let { s = s.copy(enableFtsIndexing = it) }
            dbSettings["gesture_double_tap"]?.let {
                try { s = s.copy(gestureDoubleTap = GestureAction.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["gesture_triple_tap"]?.let {
                try { s = s.copy(gestureTripleTap = GestureAction.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["gesture_single_tap"]?.let {
                try { s = s.copy(gestureSingleTap = GestureAction.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["gesture_tts_tap"]?.let {
                try { s = s.copy(gestureTtsTap = GestureAction.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["custom_bg_color"]?.toLongOrNull()?.let { s = s.copy(customBgColor = it) }
            dbSettings["custom_text_color"]?.toLongOrNull()?.let { s = s.copy(customTextColor = it) }
            dbSettings["custom_accent_color"]?.toLongOrNull()?.let { s = s.copy(customAccentColor = it) }
            dbSettings["orb_action_items"]?.split(",")?.mapNotNull { name ->
                try { OrbActionItem.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.toSet()?.let { s = s.copy(orbActionItems = it) }
            dbSettings["orb_action_order"]?.split(",")?.mapNotNull { name ->
                try { OrbActionItem.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.let { s = s.copy(orbActionOrder = it) }
            dbSettings["quick_themes"]?.split(",")?.mapNotNull { name ->
                try { ThemeFamily.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.toSet()?.let { s = s.copy(quickThemes = it) }
            dbSettings["quick_fonts"]?.split(",")?.mapNotNull { name ->
                try { TypefaceMode.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.toSet()?.let { s = s.copy(quickFonts = it) }
            dbSettings["ai_provider"]?.let {
                try { s = s.copy(aiProvider = AiProvider.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["ai_model"]?.let { if (it.isNotBlank()) s = s.copy(aiModel = it) }
            dbSettings["ai_base_url"]?.let { if (it.isNotBlank()) s = s.copy(aiBaseUrl = it) }
            dbSettings["gemini_api_key"]?.let { dbKey ->
                if (dbKey.isNotBlank()) {
                    s = s.copy(geminiApiKey = dbKey)
                    prefs.edit().putString("gemini_api_key", dbKey).apply()
                } else {
                    val prefsKey = prefs.getString("gemini_api_key", "") ?: ""
                    if (prefsKey.isNotBlank()) {
                        s = s.copy(geminiApiKey = prefsKey)
                        persistSettingToDb("gemini_api_key", prefsKey)
                    }
                }
            } ?: run {
                val prefsKey = prefs.getString("gemini_api_key", "") ?: ""
                if (prefsKey.isNotBlank()) {
                    s = s.copy(geminiApiKey = prefsKey)
                    persistSettingToDb("gemini_api_key", prefsKey)
                }
            }

            // Atomic update to readerSettings Flow
            _readerSettings.value = s

            // Also keep individual StateFlows in sync
            _fontSize.value = s.fontSize
            _readingMode.value = s.readingMode
            _themeMode.value = s.themeMode
            _typefaceMode.value = s.typefaceMode
            _lineHeightMultiplier.value = s.lineHeightMultiplier
            _paragraphSpacingMultiplier.value = s.paragraphSpacingMultiplier
            _letterSpacing.value = s.letterSpacing
            _showFloatingAssistant.value = s.showFloatingAssistant
            _orbSize.value = s.orbSize
            _orbMenuSize.value = s.orbMenuSize
            _orbEdgeSnap.value = s.orbEdgeSnap
            _orbColor.value = s.orbColor
            _orbOpacity.value = s.orbOpacity
            _orbPortraitX.value = s.orbPortraitX
            _orbPortraitY.value = s.orbPortraitY
            _orbLandscapeX.value = s.orbLandscapeX
            _orbLandscapeY.value = s.orbLandscapeY
            _themeFamily.value = s.themeFamily
            _themeVariant.value = s.themeVariant
            _backgroundTexture.value = s.backgroundTexture
            _customBgUri.value = s.customBgUri
            _textAlignmentMode.value = s.textAlignmentMode
            _horizontalPadding.value = s.horizontalPadding
            _verticalPadding.value = s.verticalPadding
            _assistantOrbStyle.value = s.assistantOrbStyle
            _spoilerShield.value = s.spoilerShield
            _autoScrollSpeed.value = s.autoScrollSpeed
            _disableAi.value = s.disableAi
            _disableTts.value = s.disableTts
            _ttsEngine.value = s.ttsEngine
            _ttsEdgeVoice.value = s.ttsEdgeVoice
            _ttsSpeed.value = s.ttsSpeed
            _ttsPitch.value = s.ttsPitch
            _disableStt.value = s.disableStt
            _autoStartMic.value = s.autoStartMic
            _enableFtsIndexing.value = s.enableFtsIndexing
            _gestureDoubleTap.value = s.gestureDoubleTap
            _gestureTripleTap.value = s.gestureTripleTap
            _gestureSingleTap.value = s.gestureSingleTap
            _gestureTtsTap.value = s.gestureTtsTap
            _customBgColor.value = s.customBgColor
            _customTextColor.value = s.customTextColor
            _customAccentColor.value = s.customAccentColor
            _orbActionItems.value = s.orbActionItems
            _orbActionOrder.value = s.orbActionOrder
            _quickThemes.value = s.quickThemes
            _quickFonts.value = s.quickFonts
            _aiProvider.value = s.aiProvider
            _aiModel.value = s.aiModel
            _aiBaseUrl.value = s.aiBaseUrl
            _geminiApiKey.value = s.geminiApiKey
        } else {
            // Initial seed into SQLite in a single transaction
            val s = _readerSettings.value
            val initialMap = mapOf(
                "font_size" to s.fontSize.toString(),
                "reading_mode" to s.readingMode.name,
                "theme_mode" to s.themeMode.name,
                "typeface_mode" to s.typefaceMode.name,
                "line_height" to s.lineHeightMultiplier.toString(),
                "paragraph_spacing" to s.paragraphSpacingMultiplier.toString(),
                "letter_spacing" to s.letterSpacing.toString(),
                "show_floating_assistant" to s.showFloatingAssistant.toString(),
                "orb_size" to s.orbSize.name,
                "orb_menu_size" to s.orbMenuSize.name,
                "orb_edge_snap" to s.orbEdgeSnap.toString(),
                "orb_color" to s.orbColor.name,
                "orb_opacity" to s.orbOpacity.toString(),
                "orb_pos_x_portrait" to s.orbPortraitX.toString(),
                "orb_pos_y_portrait" to s.orbPortraitY.toString(),
                "orb_pos_x_landscape" to s.orbLandscapeX.toString(),
                "orb_pos_y_landscape" to s.orbLandscapeY.toString(),
                "theme_family" to s.themeFamily.name,
                "theme_variant" to s.themeVariant.name,
                "background_texture" to s.backgroundTexture.name,
                "custom_bg_uri" to s.customBgUri,
                "text_alignment_mode" to s.textAlignmentMode.name,
                "horizontal_padding" to s.horizontalPadding.toString(),
                "vertical_padding" to s.verticalPadding.toString(),
                "assistant_orb_style" to s.assistantOrbStyle,
                "spoiler_shield" to s.spoilerShield.toString(),
                "auto_scroll_speed" to s.autoScrollSpeed.toString(),
                "disable_ai" to s.disableAi.toString(),
                "disable_tts" to s.disableTts.toString(),
                "tts_engine" to s.ttsEngine,
                "tts_edge_voice" to s.ttsEdgeVoice,
                "tts_speed" to s.ttsSpeed.toString(),
                "tts_pitch" to s.ttsPitch.toString(),
                "disable_stt" to s.disableStt.toString(),
                "auto_start_mic" to s.autoStartMic.toString(),
                "enable_fts_indexing" to s.enableFtsIndexing.toString(),
                "gesture_double_tap" to s.gestureDoubleTap.name,
                "gesture_triple_tap" to s.gestureTripleTap.name,
                "gesture_single_tap" to s.gestureSingleTap.name,
                "gesture_tts_tap" to s.gestureTtsTap.name,
                "custom_bg_color" to s.customBgColor.toString(),
                "custom_text_color" to s.customTextColor.toString(),
                "custom_accent_color" to s.customAccentColor.toString(),
                "orb_action_items" to s.orbActionItems.joinToString(",") { it.name },
                "orb_action_order" to s.orbActionOrder.joinToString(",") { it.name },
                "quick_themes" to s.quickThemes.joinToString(",") { it.name },
                "quick_fonts" to s.quickFonts.joinToString(",") { it.name },
                "ai_provider" to s.aiProvider.name,
                "ai_model" to s.aiModel,
                "ai_base_url" to s.aiBaseUrl,
                "gemini_api_key" to s.geminiApiKey
            )
            dbHelper.setSettings(initialMap)
        }
    }
}
