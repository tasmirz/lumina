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

class BookRepository private constructor(internal val context: Context) {

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

    internal val prefs: SharedPreferences =
        context.getSharedPreferences("lumina_reader_prefs", Context.MODE_PRIVATE)

    internal val repoScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    internal var readingPositionSaveJob: kotlinx.coroutines.Job? = null

    internal val chapterCache = SimpleLruCache<String, List<Chapter>>(8)

    internal val _readingPosition = MutableStateFlow(
        ReadingPosition(
            bookId = prefs.getString("active_book_id", "") ?: "",
            chapterIndex = 0,
            pageIndex = 0,
            scrollPos = 0,
            progressPct = 0
        )
    )
    val readingPosition: StateFlow<ReadingPosition> = _readingPosition.asStateFlow()

    internal val _readerSettings = MutableStateFlow(loadInitialSettingsFromPrefs())
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    internal fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        _readerSettings.value = transform(_readerSettings.value)
    }

    internal val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    internal val _activeBookId = MutableStateFlow(prefs.getString("active_book_id", "") ?: "")
    val activeBookId: StateFlow<String> = _activeBookId.asStateFlow()

    internal val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    internal val _wishlistBooks = MutableStateFlow<List<WishlistBook>>(emptyList())
    val wishlistBooks: StateFlow<List<WishlistBook>> = _wishlistBooks.asStateFlow()

    internal val _completedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val completedBookIds: StateFlow<Set<String>> = _completedBookIds.asStateFlow()

    internal val _customThemes = MutableStateFlow<List<CustomThemeData>>(emptyList())
    val customThemes: StateFlow<List<CustomThemeData>> = _customThemes.asStateFlow()

    internal val _customTextures = MutableStateFlow<List<CustomTextureData>>(emptyList())
    val customTextures: StateFlow<List<CustomTextureData>> = _customTextures.asStateFlow()

    internal val _selectedCustomTextureId = MutableStateFlow(prefs.getString("selected_custom_texture_id", "") ?: "")
    val selectedCustomTextureId: StateFlow<String> = _selectedCustomTextureId.asStateFlow()

    internal val _isIndexingActive = MutableStateFlow(false)
    val isIndexingActive: StateFlow<Boolean> = _isIndexingActive.asStateFlow()

    internal val _indexingProgress = MutableStateFlow("")
    val indexingProgress: StateFlow<String> = _indexingProgress.asStateFlow()

    internal val _activeBackgroundTask = MutableStateFlow<String?>(null)
    val activeBackgroundTask: StateFlow<String?> = _activeBackgroundTask.asStateFlow()

    internal val _preferredLanguage = MutableStateFlow(prefs.getString("preferred_language", "auto") ?: "auto")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    internal val _horizontalPadding = MutableStateFlow(prefs.getInt("horizontal_padding", 20))
    val horizontalPadding: StateFlow<Int> = _horizontalPadding.asStateFlow()

    internal val _verticalPadding = MutableStateFlow(prefs.getInt("vertical_padding", 16))
    val verticalPadding: StateFlow<Int> = _verticalPadding.asStateFlow()

    internal val _pagedSafeLinesToRemove = MutableStateFlow(prefs.getInt("paged_safe_lines_to_remove", 0))
    val pagedSafeLinesToRemove: StateFlow<Int> = _pagedSafeLinesToRemove.asStateFlow()

    internal val _verticallyCenterPages = MutableStateFlow(prefs.getBoolean("vertically_center_pages", false))
    val verticallyCenterPages: StateFlow<Boolean> = _verticallyCenterPages.asStateFlow()

    internal val _fillPageToBottom = MutableStateFlow(prefs.getBoolean("fill_page_to_bottom", true))
    val fillPageToBottom: StateFlow<Boolean> = _fillPageToBottom.asStateFlow()

    internal val _adaptiveVerticalSpacing = MutableStateFlow(prefs.getBoolean("adaptive_vertical_spacing", false))
    val adaptiveVerticalSpacing: StateFlow<Boolean> = _adaptiveVerticalSpacing.asStateFlow()

    internal val _goldenRatioChapterOpeners = MutableStateFlow(prefs.getBoolean("golden_ratio_chapter_openers", false))
    val goldenRatioChapterOpeners: StateFlow<Boolean> = _goldenRatioChapterOpeners.asStateFlow()

    internal val _pagedSwipeThreshold = MutableStateFlow(prefs.getFloat("paged_swipe_threshold", 0.08f))
    val pagedSwipeThreshold: StateFlow<Float> = _pagedSwipeThreshold.asStateFlow()

    internal val _autoSaveDwellSeconds = MutableStateFlow(prefs.getInt("auto_save_dwell_seconds", 5).coerceIn(1, 60))
    val autoSaveDwellSeconds: StateFlow<Int> = _autoSaveDwellSeconds.asStateFlow()

    internal val _smartConsecutiveAutoSave = MutableStateFlow(prefs.getBoolean("smart_consecutive_auto_save", true))
    val smartConsecutiveAutoSave: StateFlow<Boolean> = _smartConsecutiveAutoSave.asStateFlow()

    internal val _autoSaveConsecutivePages = MutableStateFlow(prefs.getInt("auto_save_consecutive_pages", 3).coerceIn(2, 10))
    val autoSaveConsecutivePages: StateFlow<Int> = _autoSaveConsecutivePages.asStateFlow()

    internal val _allowTableSplitting = MutableStateFlow(prefs.getBoolean("allow_table_splitting", true))
    val allowTableSplitting: StateFlow<Boolean> = _allowTableSplitting.asStateFlow()

    internal val _showStartupLoadingScreen = MutableStateFlow(prefs.getBoolean("show_startup_loading_screen", false))
    val showStartupLoadingScreen: StateFlow<Boolean> = _showStartupLoadingScreen.asStateFlow()

    internal val _uncachedBookOpenMode = MutableStateFlow(
        try {
            UncachedBookOpenMode.valueOf(prefs.getString("uncached_book_open_mode", UncachedBookOpenMode.INSTANT_ACTIVE_FIRST.name) ?: UncachedBookOpenMode.INSTANT_ACTIVE_FIRST.name)
        } catch (_: Exception) {
            UncachedBookOpenMode.INSTANT_ACTIVE_FIRST
        }
    )
    val uncachedBookOpenMode: StateFlow<UncachedBookOpenMode> = _uncachedBookOpenMode.asStateFlow()

    internal val _inlineImageWidthPercent = MutableStateFlow(prefs.getInt("inline_image_width_percent", 100))
    val inlineImageWidthPercent: StateFlow<Int> = _inlineImageWidthPercent.asStateFlow()

    internal val _isStartupInitialized = MutableStateFlow(false)
    val isStartupInitialized: StateFlow<Boolean> = _isStartupInitialized.asStateFlow()

    internal val _startupStatusMessage = MutableStateFlow("Initializing Lumina library…")
    val startupStatusMessage: StateFlow<String> = _startupStatusMessage.asStateFlow()

    internal val _assistantOrbStyle = MutableStateFlow(prefs.getString("assistant_orb_style", "EDGE_DOT") ?: "EDGE_DOT")
    val assistantOrbStyle: StateFlow<String> = _assistantOrbStyle.asStateFlow()

    internal val _spoilerShield = MutableStateFlow(prefs.getBoolean("spoiler_shield", true))
    val spoilerShield: StateFlow<Boolean> = _spoilerShield.asStateFlow()

    internal val _autoScrollSpeed = MutableStateFlow(prefs.getFloat("auto_scroll_speed", 1.0f))
    val autoScrollSpeed: StateFlow<Float> = _autoScrollSpeed.asStateFlow()

    internal val _localOnlyMode = MutableStateFlow(prefs.getBoolean("local_only_mode", false))
    val localOnlyMode: StateFlow<Boolean> = _localOnlyMode.asStateFlow()

    internal val _disableAi = MutableStateFlow(prefs.getBoolean("disable_ai", false))
    val disableAi: StateFlow<Boolean> = _disableAi.asStateFlow()

    internal val _disableTts = MutableStateFlow(prefs.getBoolean("disable_tts", false))
    val disableTts: StateFlow<Boolean> = _disableTts.asStateFlow()

    internal val _ttsEngine = MutableStateFlow(prefs.getString("tts_engine", "EDGE_NEURAL") ?: "EDGE_NEURAL")
    val ttsEngine: StateFlow<String> = _ttsEngine.asStateFlow()

    internal val _ttsEdgeVoice = MutableStateFlow(prefs.getString("tts_edge_voice", "en-US-JennyNeural") ?: "en-US-JennyNeural")
    val ttsEdgeVoice: StateFlow<String> = _ttsEdgeVoice.asStateFlow()

    internal val _ttsSpeed = MutableStateFlow(prefs.getFloat("tts_speed", 1.0f))
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    internal val _ttsPitch = MutableStateFlow(prefs.getFloat("tts_pitch", 1.0f))
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    internal val _disableStt = MutableStateFlow(prefs.getBoolean("disable_stt", false))
    val disableStt: StateFlow<Boolean> = _disableStt.asStateFlow()

    internal val _autoStartMic = MutableStateFlow(prefs.getBoolean("auto_start_mic", true))
    val autoStartMic: StateFlow<Boolean> = _autoStartMic.asStateFlow()

    internal val _enableFtsIndexing = MutableStateFlow(prefs.getBoolean("enable_fts_indexing", false))
    val enableFtsIndexing: StateFlow<Boolean> = _enableFtsIndexing.asStateFlow()

    // Preferences
    internal val _fontSize = MutableStateFlow(prefs.getInt("font_size", 18))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    internal val _readingMode = MutableStateFlow(
        try {
            ReadingMode.valueOf(prefs.getString("reading_mode", ReadingMode.SCROLL.name) ?: ReadingMode.SCROLL.name)
        } catch (_: Exception) { ReadingMode.SCROLL }
    )
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    internal val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.WARM_PAPER.name) ?: ThemeMode.WARM_PAPER.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    internal val _typefaceMode = MutableStateFlow(
        TypefaceMode.valueOf(prefs.getString("typeface_mode", TypefaceMode.SERIF.name) ?: TypefaceMode.SERIF.name)
    )
    val typefaceMode: StateFlow<TypefaceMode> = _typefaceMode.asStateFlow()

    internal val _lineHeightMultiplier = MutableStateFlow(prefs.getFloat("line_height", 1.68f))
    val lineHeightMultiplier: StateFlow<Float> = _lineHeightMultiplier.asStateFlow()

    internal val _paragraphSpacingMultiplier = MutableStateFlow(prefs.getFloat("paragraph_spacing", 1.2f))
    val paragraphSpacingMultiplier: StateFlow<Float> = _paragraphSpacingMultiplier.asStateFlow()

    internal val _showFloatingAssistant = MutableStateFlow(prefs.getBoolean("show_floating_assistant", true))
    val showFloatingAssistant: StateFlow<Boolean> = _showFloatingAssistant.asStateFlow()

    internal val _orbSize = MutableStateFlow<OrbSize>(
        try {
            OrbSize.valueOf(prefs.getString("orb_size", OrbSize.NANO.name) ?: OrbSize.NANO.name)
        } catch (_: Exception) { OrbSize.NANO }
    )
    val orbSize: StateFlow<OrbSize> = _orbSize.asStateFlow()

    internal val _orbMenuSize = MutableStateFlow<OrbMenuSize>(
        try {
            OrbMenuSize.valueOf(prefs.getString("orb_menu_size", OrbMenuSize.MEDIUM.name) ?: OrbMenuSize.MEDIUM.name)
        } catch (_: Exception) { OrbMenuSize.MEDIUM }
    )
    val orbMenuSize: StateFlow<OrbMenuSize> = _orbMenuSize.asStateFlow()

    internal val _orbEdgeSnap = MutableStateFlow(prefs.getBoolean("orb_edge_snap", true))
    val orbEdgeSnap: StateFlow<Boolean> = _orbEdgeSnap.asStateFlow()

    // Distinct persistent dock coordinates for Portrait vs Landscape
    internal val _orbPortraitX = MutableStateFlow(prefs.getFloat("orb_pos_x_portrait", -1f))
    val orbPortraitX: StateFlow<Float> = _orbPortraitX.asStateFlow()

    internal val _orbPortraitY = MutableStateFlow(prefs.getFloat("orb_pos_y_portrait", -1f))
    val orbPortraitY: StateFlow<Float> = _orbPortraitY.asStateFlow()

    internal val _orbLandscapeX = MutableStateFlow(prefs.getFloat("orb_pos_x_landscape", -1f))
    val orbLandscapeX: StateFlow<Float> = _orbLandscapeX.asStateFlow()

    internal val _orbLandscapeY = MutableStateFlow(prefs.getFloat("orb_pos_y_landscape", -1f))
    val orbLandscapeY: StateFlow<Float> = _orbLandscapeY.asStateFlow()

    internal val _orbColor = MutableStateFlow<OrbColor>(
        try {
            OrbColor.valueOf(prefs.getString("orb_color", OrbColor.THEME.name) ?: OrbColor.THEME.name)
        } catch (_: Exception) { OrbColor.THEME }
    )
    val orbColor: StateFlow<OrbColor> = _orbColor.asStateFlow()

    internal val _customOrbColor = MutableStateFlow(prefs.getLong("custom_orb_color", 0xFF4F46E5L))
    val customOrbColor: StateFlow<Long> = _customOrbColor.asStateFlow()

    internal val _orbOpacity = MutableStateFlow(prefs.getFloat("orb_opacity", 0.85f))
    val orbOpacity: StateFlow<Float> = _orbOpacity.asStateFlow()

    internal val _themeFamily = MutableStateFlow(
        try {
            ThemeFamily.valueOf(prefs.getString("theme_family", ThemeFamily.PAPER.name) ?: ThemeFamily.PAPER.name)
        } catch (_: Exception) { ThemeFamily.PAPER }
    )
    val themeFamily: StateFlow<ThemeFamily> = _themeFamily.asStateFlow()

    internal val _themeVariant = MutableStateFlow(
        try {
            ThemeVariant.valueOf(prefs.getString("theme_variant", ThemeVariant.LIGHT.name) ?: ThemeVariant.LIGHT.name)
        } catch (_: Exception) { ThemeVariant.LIGHT }
    )
    val themeVariant: StateFlow<ThemeVariant> = _themeVariant.asStateFlow()

    internal val _backgroundTexture = MutableStateFlow(
        try {
            BackgroundTexture.valueOf(prefs.getString("background_texture", BackgroundTexture.NONE.name) ?: BackgroundTexture.NONE.name)
        } catch (_: Exception) { BackgroundTexture.NONE }
    )
    val backgroundTexture: StateFlow<BackgroundTexture> = _backgroundTexture.asStateFlow()

    internal val _dynamicRollingTexture = MutableStateFlow(prefs.getBoolean("dynamic_rolling_texture", true))
    val dynamicRollingTexture: StateFlow<Boolean> = _dynamicRollingTexture.asStateFlow()

    internal val _customBgUri = MutableStateFlow(prefs.getString("custom_bg_uri", "") ?: "")
    val customBgUri: StateFlow<String> = _customBgUri.asStateFlow()

    internal val _orbActionItems = MutableStateFlow<Set<OrbActionItem>>(
        prefs.getStringSet("orb_action_items", null)?.mapNotNull { name ->
            try { OrbActionItem.valueOf(name) } catch (_: Exception) { null }
        }?.takeIf { it.size >= 8 }?.toSet() ?: OrbActionItem.entries.toSet()
    )
    val orbActionItems: StateFlow<Set<OrbActionItem>> = _orbActionItems.asStateFlow()

    internal val _quickThemes = MutableStateFlow<Set<ThemeFamily>>(
        prefs.getStringSet("quick_themes", null)?.mapNotNull {
            try { ThemeFamily.valueOf(it) } catch (_: Exception) { null }
        }?.toSet() ?: ThemeFamily.entries.toSet()
    )
    val quickThemes: StateFlow<Set<ThemeFamily>> = _quickThemes.asStateFlow()

    internal val _quickFonts = MutableStateFlow<Set<TypefaceMode>>(
        prefs.getStringSet("quick_fonts", null)?.mapNotNull {
            try { TypefaceMode.valueOf(it) } catch (_: Exception) { null }
        }?.toSet() ?: setOf(TypefaceMode.SERIF, TypefaceMode.SANS, TypefaceMode.GEORGIA)
    )
    val quickFonts: StateFlow<Set<TypefaceMode>> = _quickFonts.asStateFlow()

    internal val _orbActionOrder = MutableStateFlow<List<OrbActionItem>>(
        prefs.getString("orb_action_order", null)?.split(",")?.mapNotNull { name ->
            try { OrbActionItem.valueOf(name) } catch (_: Exception) { null }
        }?.let { savedList ->
            val set = savedList.toSet()
            savedList + OrbActionItem.entries.filter { it !in set }
        } ?: OrbActionItem.entries.toList()
    )
    val orbActionOrder: StateFlow<List<OrbActionItem>> = _orbActionOrder.asStateFlow()

    internal val _textAlignmentMode = MutableStateFlow(
        try {
            TextAlignmentMode.valueOf(prefs.getString("text_alignment_mode", TextAlignmentMode.JUSTIFY.name) ?: TextAlignmentMode.JUSTIFY.name)
        } catch (_: Exception) { TextAlignmentMode.JUSTIFY }
    )
    val textAlignmentMode: StateFlow<TextAlignmentMode> = _textAlignmentMode.asStateFlow()

    internal val _letterSpacing = MutableStateFlow(prefs.getFloat("letter_spacing", 0.2f))
    val letterSpacing: StateFlow<Float> = _letterSpacing.asStateFlow()

    internal val _geminiApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    internal val _openLibraryApiKey = MutableStateFlow(prefs.getString("open_library_api_key", "") ?: "").also {
        OnlineEpubService.openLibraryApiKey = it.value
    }
    val openLibraryApiKey: StateFlow<String> = _openLibraryApiKey.asStateFlow()

    internal val _customEndpoints = MutableStateFlow<List<CustomCatalogEndpoint>>(emptyList())
    val customEndpoints: StateFlow<List<CustomCatalogEndpoint>> = _customEndpoints.asStateFlow()

    internal val _aiProvider = MutableStateFlow(
        try {
            AiProvider.valueOf(prefs.getString("ai_provider", AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name)
        } catch (_: Exception) { AiProvider.GEMINI }
    )
    val aiProvider: StateFlow<AiProvider> = _aiProvider.asStateFlow()

    internal val _aiBaseUrl = MutableStateFlow(prefs.getString("ai_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1")
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()

    internal val _aiModel = MutableStateFlow(
        prefs.getString("ai_model", null).let { if (it.isNullOrBlank()) "gemini-3.1-flash-lite" else it }
    )
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    internal val _readTillMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val readTillMap: StateFlow<Map<String, Int>> = _readTillMap.asStateFlow()

    internal val _customBgColor = MutableStateFlow(prefs.getLong("custom_bg_color", 0xFF1C1917L))
    val customBgColor: StateFlow<Long> = _customBgColor.asStateFlow()

    internal val _customTextColor = MutableStateFlow(prefs.getLong("custom_text_color", 0xFFE7E5E4L))
    val customTextColor: StateFlow<Long> = _customTextColor.asStateFlow()

    internal val _customAccentColor = MutableStateFlow(prefs.getLong("custom_accent_color", 0xFFD4AF37L))
    val customAccentColor: StateFlow<Long> = _customAccentColor.asStateFlow()

    internal val _gestureDoubleTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_double_tap", GestureAction.TOGGLE_AUTOSCROLL.name) ?: GestureAction.TOGGLE_AUTOSCROLL.name)
        } catch (_: Exception) { GestureAction.TOGGLE_AUTOSCROLL }
    )
    val gestureDoubleTap: StateFlow<GestureAction> = _gestureDoubleTap.asStateFlow()

    internal val _gestureTripleTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_triple_tap", GestureAction.SUMMON_ORB.name) ?: GestureAction.SUMMON_ORB.name)
        } catch (_: Exception) { GestureAction.SUMMON_ORB }
    )
    val gestureTripleTap: StateFlow<GestureAction> = _gestureTripleTap.asStateFlow()

    internal val _gestureSingleTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_single_tap", GestureAction.TOGGLE_BARS.name) ?: GestureAction.TOGGLE_BARS.name)
        } catch (_: Exception) { GestureAction.TOGGLE_BARS }
    )
    val gestureSingleTap: StateFlow<GestureAction> = _gestureSingleTap.asStateFlow()

    internal val _gestureTtsTap = MutableStateFlow(
        try {
            GestureAction.valueOf(prefs.getString("gesture_tts_tap", GestureAction.TTS_READ_ALOUD.name) ?: GestureAction.TTS_READ_ALOUD.name)
        } catch (_: Exception) { GestureAction.TTS_READ_ALOUD }
    )
    val gestureTtsTap: StateFlow<GestureAction> = _gestureTtsTap.asStateFlow()

    internal val _imageFilterMode = MutableStateFlow(
        try {
            ImageFilterMode.valueOf(prefs.getString("image_filter_mode", ImageFilterMode.ORIGINAL.name) ?: ImageFilterMode.ORIGINAL.name)
        } catch (_: Exception) { ImageFilterMode.ORIGINAL }
    )
    val imageFilterMode: StateFlow<ImageFilterMode> = _imageFilterMode.asStateFlow()

    internal val _invertScreen = MutableStateFlow(prefs.getBoolean("invert_screen", false))
    val invertScreen: StateFlow<Boolean> = _invertScreen.asStateFlow()

    internal val _pagedBottomClearanceDp = MutableStateFlow(prefs.getInt("paged_bottom_clearance_dp", 28).coerceIn(0, 100))
    val pagedBottomClearanceDp: StateFlow<Int> = _pagedBottomClearanceDp.asStateFlow()

    fun setImageFilterMode(mode: ImageFilterMode) {
        _imageFilterMode.value = mode
        updateReaderSettings { it.copy(imageFilterMode = mode) }
        prefs.edit().putString("image_filter_mode", mode.name).apply()
        persistSettingToDb("image_filter_mode", mode.name)
    }

    fun setInvertScreen(enabled: Boolean) {
        _invertScreen.value = enabled
        updateReaderSettings { it.copy(invertScreen = enabled) }
        prefs.edit().putBoolean("invert_screen", enabled).apply()
        persistSettingToDb("invert_screen", enabled.toString())
    }

    fun setPagedBottomClearanceDp(dp: Int) {
        val clamped = dp.coerceIn(0, 100)
        _pagedBottomClearanceDp.value = clamped
        updateReaderSettings { it.copy(pagedBottomClearanceDp = clamped) }
        prefs.edit().putInt("paged_bottom_clearance_dp", clamped).apply()
        persistSettingToDb("paged_bottom_clearance_dp", clamped.toString())
    }

    private fun loadInitialSettingsFromPrefs(): ReaderSettings =
        BookSettingsManager.loadInitialSettingsFromPrefs(prefs)

    fun getChaptersForBook(bookId: String): List<Chapter> {
        val cached = chapterCache.get(bookId)
        if (cached != null && cached.isNotEmpty()) return cached
        val fromDb = dbHelper.getAllChaptersForBook(bookId)
        if (fromDb.isNotEmpty()) {
            chapterCache.put(bookId, fromDb)
            return fromDb
        }
        // Lazy on-demand chapter parsing for books added via fast metadata-only discovery
        val book = _books.value.find { it.id == bookId } ?: dbHelper.getAllBooks().find { it.id == bookId }
        if (book != null && !book.filePath.isNullOrBlank()) {
            val file = File(book.filePath)
            if (file.exists() && file.length() > 0L) {
                try {
                    file.inputStream().use { stream ->
                        val parsed = EpubParser.parseEpub(stream, file.name, context, bookId)
                        if (parsed.chapters.isNotEmpty()) {
                            dbHelper.saveChaptersForBook(bookId, parsed.chapters)
                            chapterCache.put(bookId, parsed.chapters)
                            return parsed.chapters
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("BookRepository", "Failed lazy parsing chapters for ${book.title}: ${e.message}")
                }
            }
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
        screenWidthDp: Int = 0,
        screenHeightDp: Int = 0,
        onActiveChapterReady: ((List<Pair<String, String>>) -> Unit)? = null,
        onAllPagesReady: ((List<Pair<String, String>>) -> Unit)? = null
    ) {
        repoScope.launch(Dispatchers.Default) {
            val chapters = if (book.chapters.isNotEmpty()) book.chapters else getChaptersForBook(book.id)
            if (chapters.isEmpty()) return@launch
            val config = context.resources?.configuration
            val sw = if (screenWidthDp > 0) screenWidthDp else (config?.screenWidthDp ?: 0)
            val sh = if (screenHeightDp > 0) screenHeightDp else (config?.screenHeightDp ?: 0)
            val allPages = PageCache.getOrComputeAsync(
                bookId = book.id,
                chapters = chapters,
                fontSize = fontSize,
                isLandscape = isLandscape,
                isStrictPaged = isStrictPaged,
                screenWidthDp = sw,
                screenHeightDp = sh,
                fillPageToBottom = _fillPageToBottom.value,
                allowTableSplitting = _allowTableSplitting.value,
                dbHelper = dbHelper,
                context = context,
                activeChapterIndex = activeChapterIndex,
                onActiveChapterReady = { activePages ->
                    onActiveChapterReady?.invoke(activePages)
                }
            )
            onAllPagesReady?.invoke(allPages)
        }
    }

    init {
        PageCache.init(context)
        repoScope.launch(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            _startupStatusMessage.value = "Reading library database…"
            var loadedBooks = loadAllBooks()
            // If books are empty (fresh install / reinstall), restore from persistent backup on SD card / home storage
            if (loadedBooks.isEmpty()) {
                val backupFile = LuminaStorageManager.getPersistentBackupFile(context)
                if (backupFile.exists() && backupFile.length() > 0L) {
                    _startupStatusMessage.value = "Restoring library from backup…"
                    val restored = dbHelper.restoreStateFromPersistentFile(backupFile)
                    if (restored) {
                        loadedBooks = loadAllBooks()
                    }
                }
            }

            _startupStatusMessage.value = "Loading bookmarks & themes…"
            val loadedBookmarks = loadPersistedBookmarks()
            val loadedWishlist = try { dbHelper.getAllWishlist() } catch (_: Exception) { emptyList() }
            val loadedCompleted = try { dbHelper.getAllCompletedBookIds() } catch (_: Exception) { emptySet() }
            val loadedThemes = try { dbHelper.getAllCustomThemes() } catch (_: Exception) { emptyList() }
            val loadedTextures = try { dbHelper.getAllCustomTextures() } catch (_: Exception) { emptyList() }
            val loadedEndpoints = try { dbHelper.getAllCustomEndpoints() } catch (_: Exception) { emptyList() }

            val currentParserRev = 37
            val lastParserRev = prefs.getInt("epub_parser_rev", 0)
            if (lastParserRev < currentParserRev) {
                _startupStatusMessage.value = "Updating book chapters to latest rendering format…"
                for (b in loadedBooks) {
                    if (b.filePath.isNotBlank() && File(b.filePath).exists()) {
                        try {
                            reparseBook(b.id)
                        } catch (e: Exception) {
                            io.github.tasmirz.lumina.util.LuminaLog.w("BookRepository", "Failed reparsing ${b.title}", e)
                        }
                    }
                }
                prefs.edit().putInt("epub_parser_rev", currentParserRev).apply()
                loadedBooks = loadAllBooks()
            }

            _books.value = loadedBooks
            _bookmarks.value = loadedBookmarks
            _wishlistBooks.value = loadedWishlist
            _completedBookIds.value = loadedCompleted
            _customThemes.value = loadedThemes
            _customTextures.value = loadedTextures
            _customEndpoints.value = loadedEndpoints
            OnlineEpubService.customEndpoints = loadedEndpoints
            io.github.tasmirz.lumina.util.LuminaLog.perf("BookRepository.init", System.currentTimeMillis() - startMs, "Loaded ${loadedBooks.size} books, ${loadedBookmarks.size} bookmarks, ${loadedTextures.size} custom textures, ${loadedEndpoints.size} custom endpoints")

            // Pre-warm active book chapters & page cache in memory so Reader opens instantly without disk stall
            val activeId = prefs.getString("active_book_id", "") ?: ""
            val active = loadedBooks.find { it.id == activeId } ?: loadedBooks.firstOrNull()
            if (active != null) {
                _startupStatusMessage.value = "Warming up chapters for \"${active.title}\"…"
                val chaps = dbHelper.getChapterMetadataForBook(active.id).ifEmpty { getChaptersForBook(active.id) }
                if (chaps.isNotEmpty() && _readingMode.value == ReadingMode.PAGED) {
                    val config = context.resources?.configuration
                    val sw = config?.screenWidthDp ?: 0
                    val sh = config?.screenHeightDp ?: 0
                    val isLand = config?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    PageCache.getOrComputeAsync(
                        bookId = active.id,
                        chapters = chaps,
                        fontSize = _fontSize.value,
                        isLandscape = isLand,
                        isStrictPaged = true,
                        screenWidthDp = sw,
                        screenHeightDp = sh,
                        horizontalPaddingDp = _horizontalPadding.value,
                        verticalPaddingDp = _verticalPadding.value,
                        lineHeightMultiplier = _lineHeightMultiplier.value,
                        paragraphSpacingMultiplier = _paragraphSpacingMultiplier.value,
                        safeLinesToRemove = _pagedSafeLinesToRemove.value,
                        fillPageToBottom = _fillPageToBottom.value,
                        allowTableSplitting = _allowTableSplitting.value,
                        dbHelper = dbHelper,
                        context = context
                    )
                }
            }

            try {
                syncSettings()
            } catch (_: Exception) {}

            _startupStatusMessage.value = "Ready"
            _isStartupInitialized.value = true

            // Background database maintenance: checkpoint WAL passively if oversized without blocking UI
            launch(Dispatchers.IO) {
                delay(3000)
                dbHelper.compactDatabaseIfNeeded()
            }

            // Only if library is completely empty (e.g. fresh install with no backup),
            // do a one-time initial scan of persistent directories
            if (loadedBooks.isEmpty()) {
                try {
                    LuminaStorageManager.migrateLegacyFiles(context)
                    autoScanAndLoadPersistentEpubs()
                } catch (e: Exception) {
                    io.github.tasmirz.lumina.util.LuminaLog.w("BookRepository", "Initial autoScan error", e)
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

    fun setInlineImageWidthPercent(pct: Int) {
        val clamped = pct.coerceIn(40, 100)
        _inlineImageWidthPercent.value = clamped
        updateReaderSettings { it.copy(inlineImageWidthPercent = clamped) }
        prefs.edit().putInt("inline_image_width_percent", clamped).apply()
        persistSettingToDb("inline_image_width_percent", clamped.toString())
    }

    fun updatePagedSafeLinesToRemove(lines: Int) {
        val clamped = lines.coerceIn(0, 10)
        _pagedSafeLinesToRemove.value = clamped
        updateReaderSettings { it.copy(pagedSafeLinesToRemove = clamped) }
        prefs.edit().putInt("paged_safe_lines_to_remove", clamped).apply()
        persistSettingToDb("paged_safe_lines_to_remove", clamped.toString())
    }

    fun setPagedSafeLinesToRemove(lines: Int) = updatePagedSafeLinesToRemove(lines)

    fun setVerticallyCenterPages(enabled: Boolean) {
        _verticallyCenterPages.value = enabled
        updateReaderSettings { it.copy(verticallyCenterPages = enabled) }
        prefs.edit().putBoolean("vertically_center_pages", enabled).apply()
        persistSettingToDb("vertically_center_pages", enabled.toString())
    }

    fun setFillPageToBottom(enabled: Boolean) {
        _fillPageToBottom.value = enabled
        updateReaderSettings { it.copy(fillPageToBottom = enabled) }
        prefs.edit().putBoolean("fill_page_to_bottom", enabled).apply()
        persistSettingToDb("fill_page_to_bottom", enabled.toString())
        PageCache.clear()
    }

    fun setAdaptiveVerticalSpacing(enabled: Boolean) {
        _adaptiveVerticalSpacing.value = enabled
        updateReaderSettings { it.copy(adaptiveVerticalSpacing = enabled) }
        prefs.edit().putBoolean("adaptive_vertical_spacing", enabled).apply()
        persistSettingToDb("adaptive_vertical_spacing", enabled.toString())
    }

    fun setGoldenRatioChapterOpeners(enabled: Boolean) {
        _goldenRatioChapterOpeners.value = enabled
        updateReaderSettings { it.copy(goldenRatioChapterOpeners = enabled) }
        prefs.edit().putBoolean("golden_ratio_chapter_openers", enabled).apply()
        persistSettingToDb("golden_ratio_chapter_openers", enabled.toString())
    }

    fun setPagedSwipeThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.03f, 0.60f)
        _pagedSwipeThreshold.value = clamped
        updateReaderSettings { it.copy(pagedSwipeThreshold = clamped) }
        prefs.edit().putFloat("paged_swipe_threshold", clamped).apply()
        persistSettingToDb("paged_swipe_threshold", clamped.toString())
    }

    fun setAutoSaveDwellSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 60)
        _autoSaveDwellSeconds.value = clamped
        updateReaderSettings { it.copy(autoSaveDwellSeconds = clamped) }
        prefs.edit().putInt("auto_save_dwell_seconds", clamped).apply()
        persistSettingToDb("auto_save_dwell_seconds", clamped.toString())
    }

    fun setSmartConsecutiveAutoSave(enabled: Boolean) {
        _smartConsecutiveAutoSave.value = enabled
        updateReaderSettings { it.copy(smartConsecutiveAutoSave = enabled) }
        prefs.edit().putBoolean("smart_consecutive_auto_save", enabled).apply()
        persistSettingToDb("smart_consecutive_auto_save", enabled.toString())
    }

    fun setAutoSaveConsecutivePages(pages: Int) {
        val clamped = pages.coerceIn(2, 10)
        _autoSaveConsecutivePages.value = clamped
        updateReaderSettings { it.copy(autoSaveConsecutivePages = clamped) }
        prefs.edit().putInt("auto_save_consecutive_pages", clamped).apply()
        persistSettingToDb("auto_save_consecutive_pages", clamped.toString())
    }

    fun setAllowTableSplitting(enabled: Boolean) {
        _allowTableSplitting.value = enabled
        updateReaderSettings { it.copy(allowTableSplitting = enabled) }
        prefs.edit().putBoolean("allow_table_splitting", enabled).apply()
        persistSettingToDb("allow_table_splitting", enabled.toString())
    }

    fun setShowStartupLoadingScreen(enabled: Boolean) {
        _showStartupLoadingScreen.value = enabled
        updateReaderSettings { it.copy(showStartupLoadingScreen = enabled) }
        prefs.edit().putBoolean("show_startup_loading_screen", enabled).apply()
        persistSettingToDb("show_startup_loading_screen", enabled.toString())
    }

    fun setUncachedBookOpenMode(mode: UncachedBookOpenMode) {
        _uncachedBookOpenMode.value = mode
        updateReaderSettings { it.copy(uncachedBookOpenMode = mode) }
        prefs.edit().putString("uncached_book_open_mode", mode.name).apply()
        persistSettingToDb("uncached_book_open_mode", mode.name)
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

    fun setLocalOnlyMode(enabled: Boolean) {
        _localOnlyMode.value = enabled
        if (enabled) {
            _disableAi.value = true
            _ttsEngine.value = "SYSTEM"
            prefs.edit()
                .putBoolean("local_only_mode", true)
                .putBoolean("disable_ai", true)
                .putString("tts_engine", "SYSTEM")
                .apply()
            updateReaderSettings { it.copy(localOnlyMode = true, disableAi = true, ttsEngine = "SYSTEM") }
            persistSettingToDb("local_only_mode", "true")
            persistSettingToDb("disable_ai", "true")
            persistSettingToDb("tts_engine", "SYSTEM")
        } else {
            prefs.edit().putBoolean("local_only_mode", false).apply()
            updateReaderSettings { it.copy(localOnlyMode = false) }
            persistSettingToDb("local_only_mode", "false")
        }
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

    fun exportUnifiedBackupJson(): String = BookBackupManager.exportUnifiedBackupJson(this)

    fun importUnifiedBackupJson(jsonString: String): Boolean = BookBackupManager.importUnifiedBackupJson(this, jsonString)

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

    fun saveCustomTheme(name: String, bg: Long, text: Long, accent: Long) =
        BookThemeTextureManager.saveCustomTheme(this, name, bg, text, accent)

    fun renameCustomTheme(themeId: String, newName: String) =
        BookThemeTextureManager.renameCustomTheme(this, themeId, newName)

    fun applyCustomTheme(theme: CustomThemeData) =
        BookThemeTextureManager.applyCustomTheme(this, theme)

    fun createAndApplyCustomTheme(name: String, bg: Long, text: Long, accent: Long): CustomThemeData =
        BookThemeTextureManager.createAndApplyCustomTheme(this, name, bg, text, accent)

    fun updateAndApplyCustomTheme(name: String, bg: Long? = null, text: Long? = null, accent: Long? = null): CustomThemeData? =
        BookThemeTextureManager.updateAndApplyCustomTheme(this, name, bg, text, accent)

    fun deleteCustomTheme(themeId: String) =
        BookThemeTextureManager.deleteCustomTheme(this, themeId)

    fun importCustomTexture(name: String, sourceUri: Uri, isTiled: Boolean = true, opacity: Float = 0.5f): CustomTextureData? =
        BookThemeTextureManager.importCustomTexture(this, name, sourceUri, isTiled, opacity)

    fun selectCustomTexture(textureId: String) =
        BookThemeTextureManager.selectCustomTexture(this, textureId)

    fun renameCustomTexture(textureId: String, newName: String) =
        BookThemeTextureManager.renameCustomTexture(this, textureId, newName)

    fun deleteCustomTexture(textureId: String) =
        BookThemeTextureManager.deleteCustomTexture(this, textureId)


    fun searchScenes(bookId: String, query: String): List<SceneMatch> =
        BookSearchManager.searchScenes(this, bookId, query)

    fun searchAllBooks(query: String): List<SceneMatch> =
        BookSearchManager.searchAllBooks(this, query)


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

    fun clearFtsIndex() = BookSearchManager.clearFtsIndex(this)

    fun getFtsIndexCount(): Int = BookSearchManager.getFtsIndexCount(this)

    fun getImageCacheSizeBytes(): Long = BookSearchManager.getImageCacheSizeBytes(this)

    fun clearImageCache() = BookSearchManager.clearImageCache(this)

    fun getDatabaseSizeBytes(): Long = BookSearchManager.getDatabaseSizeBytes(this)


    fun resetReadingProgress() =
        BookPositionManager.resetReadingProgress(this)

    fun indexBookIfNeeded(book: Book) = BookSearchManager.indexBookIfNeeded(this, book)

    fun indexEntireBookNow(book: Book, onComplete: ((Int) -> Unit)? = null) =
        BookSearchManager.indexEntireBookNow(this, book, onComplete)


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

    fun setReadingProgressPercent(bookId: String, percent: Int) =
        BookPositionManager.setReadingProgressPercent(this, bookId, percent)

    fun getReadTillPercent(bookId: String): Int =
        BookPositionManager.getReadTillPercent(this, bookId)

    fun getVerifiedPage(bookId: String): Int =
        BookPositionManager.getVerifiedPage(this, bookId)

    fun setVerifiedPage(bookId: String, page: Int) =
        BookPositionManager.setVerifiedPage(this, bookId, page)

    fun forceSetReadTillPercent(bookId: String, percent: Int) =
        BookPositionManager.forceSetReadTillPercent(this, bookId, percent)

    fun forceSetLastReadPosition(bookId: String, chapterIdx: Int, pageIdx: Int, scrollPos: Int, progressPct: Int) =
        BookPositionManager.forceSetLastReadPosition(this, bookId, chapterIdx, pageIdx, scrollPos, progressPct)

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
        val cached = getCachedChapters(book.id)
        if (cached != null && cached.isNotEmpty()) {
            return book.copy(chapters = cached)
        }
        return book
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

    fun updateReadingPosition(bookId: String, chapterIdx: Int, pageIdx: Int, scrollPos: Int, progressPct: Int) =
        BookPositionManager.updateReadingPosition(this, bookId, chapterIdx, pageIdx, scrollPos, progressPct)

    fun updateCharacterCheckpoint(bookId: String, checkpointChapter: Int, checkpointPage: Int) =
        BookPositionManager.updateCharacterCheckpoint(this, bookId, checkpointChapter, checkpointPage)

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

    fun setDynamicRollingTexture(enabled: Boolean) {
        _dynamicRollingTexture.value = enabled
        updateReaderSettings { it.copy(dynamicRollingTexture = enabled) }
        prefs.edit().putBoolean("dynamic_rolling_texture", enabled).apply()
        persistSettingToDb("dynamic_rolling_texture", enabled.toString())
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

    fun setCustomOrbColor(color: Long) {
        _customOrbColor.value = color
        _orbColor.value = OrbColor.CUSTOM
        updateReaderSettings { it.copy(customOrbColor = color, orbColor = OrbColor.CUSTOM) }
        prefs.edit().putLong("custom_orb_color", color).putString("orb_color", OrbColor.CUSTOM.name).apply()
        persistSettingToDb("custom_orb_color", color.toString())
        persistSettingToDb("orb_color", OrbColor.CUSTOM.name)
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

    fun setOpenLibraryApiKey(key: String) {
        _openLibraryApiKey.value = key
        OnlineEpubService.openLibraryApiKey = key
        updateReaderSettings { it.copy(openLibraryApiKey = key) }
        prefs.edit().putString("open_library_api_key", key).apply()
        persistSettingToDb("open_library_api_key", key)
    }

    // --- Custom Catalog Endpoints ---

    fun addCustomEndpoint(endpoint: CustomCatalogEndpoint) =
        BookBookmarkWishlistManager.addCustomEndpoint(this, endpoint)

    fun updateCustomEndpoint(endpoint: CustomCatalogEndpoint) =
        BookBookmarkWishlistManager.updateCustomEndpoint(this, endpoint)

    fun deleteCustomEndpoint(endpointId: String) =
        BookBookmarkWishlistManager.deleteCustomEndpoint(this, endpointId)

    fun toggleCustomEndpoint(endpointId: String, isEnabled: Boolean) =
        BookBookmarkWishlistManager.toggleCustomEndpoint(this, endpointId, isEnabled)

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
        pageNumber: Int = 0,
        isHighlight: Boolean = false,
        isLastRead: Boolean = false
    ) = BookBookmarkWishlistManager.addBookmark(this, quote, color, note, chapterTitle, pageNumber, isHighlight, isLastRead)

    fun updateLastReadBookmark(
        bookId: String,
        chapterTitle: String,
        pageNumber: Int,
        paragraphSnippet: String
    ) = BookBookmarkWishlistManager.updateLastReadBookmark(this, bookId, chapterTitle, pageNumber, paragraphSnippet)

    fun updateBookmark(bookmark: Bookmark) =
        BookBookmarkWishlistManager.updateBookmark(this, bookmark)

    fun removeBookmark(id: Long) =
        BookBookmarkWishlistManager.removeBookmark(this, id)

    // --- Wishlist Management ---

    fun addToWishlist(title: String, author: String = "", note: String = "") =
        BookBookmarkWishlistManager.addToWishlist(this, title, author, note)

    fun removeFromWishlist(id: String) =
        BookBookmarkWishlistManager.removeFromWishlist(this, id)

    // --- Book Characters Management ---

    internal val _characters = MutableStateFlow<Map<String, List<BookCharacter>>>(emptyMap())
    val characters: StateFlow<Map<String, List<BookCharacter>>> = _characters.asStateFlow()

    fun getCharactersForBook(bookId: String): List<BookCharacter> =
        BookCharacterLoreManager.getCharactersForBook(this, bookId)

    fun loadCharacters(bookId: String) =
        BookCharacterLoreManager.loadCharacters(this, bookId)

    fun saveCharacter(character: BookCharacter) =
        BookCharacterLoreManager.saveCharacter(this, character)

    fun saveCharacters(bookId: String, newCharacters: List<BookCharacter>) =
        BookCharacterLoreManager.saveCharacters(this, bookId, newCharacters)

    fun deleteCharacter(id: Long, bookId: String) =
        BookCharacterLoreManager.deleteCharacter(this, id, bookId)

    fun clearCharacters(bookId: String) =
        BookCharacterLoreManager.clearCharacters(this, bookId)

    // --- Book Lore Management ---

    internal val _lore = MutableStateFlow<Map<String, List<BookLore>>>(emptyMap())
    val lore: StateFlow<Map<String, List<BookLore>>> = _lore.asStateFlow()

    fun getLoreForBook(bookId: String): List<BookLore> =
        BookCharacterLoreManager.getLoreForBook(this, bookId)

    fun loadLore(bookId: String) =
        BookCharacterLoreManager.loadLore(this, bookId)

    fun saveLore(loreItem: BookLore) =
        BookCharacterLoreManager.saveLore(this, loreItem)

    fun saveLoreList(bookId: String, newLoreList: List<BookLore>) =
        BookCharacterLoreManager.saveLoreList(this, bookId, newLoreList)

    fun deleteLore(id: Long, bookId: String) =
        BookCharacterLoreManager.deleteLore(this, id, bookId)

    fun clearLore(bookId: String) =
        BookCharacterLoreManager.clearLore(this, bookId)

    // --- SQLite Content Sharing ---

    fun shareHighlights(context: Context, bookTitle: String? = null) =
        BookCharacterLoreManager.shareHighlights(this, context, bookTitle)

    fun shareReadingList(context: Context) =
        BookCharacterLoreManager.shareReadingList(this, context)

    fun shareContent(context: Context, text: String, title: String) =
        BookCharacterLoreManager.shareContent(context, text, title)


    fun isDuplicateBook(b1: Book, b2: Book): Boolean =
        BookImportManager.isDuplicateBook(b1, b2)

    fun addBook(book: Book) =
        BookImportManager.addBook(this, book)

    suspend fun importEpubFromUri(uri: Uri): Book? =
        BookImportManager.importEpubFromUri(this, uri)

    fun reparseBook(bookId: String): Book? =
        BookImportManager.reparseBook(this, bookId)

    fun removeBook(bookId: String) =
        BookImportManager.removeBook(this, bookId)

    internal fun reconcileMissingBookPaths(books: List<Book>) =
        BookImportManager.reconcileMissingBookPaths(this, books)

    internal fun loadAllBooks(): List<Book> =
        BookImportManager.loadAllBooks(this)

    suspend fun autoScanAndLoadPersistentEpubs() =
        BookImportManager.autoScanAndLoadPersistentEpubs(this)

    suspend fun refreshLibrary() =
        BookImportManager.refreshLibrary(this)

    fun scanAndSyncPersistentEpubs() =
        BookImportManager.scanAndSyncPersistentEpubs(this)


    internal fun saveBookmarks(bookmarks: List<Bookmark>) =
        BookBookmarkWishlistManager.saveBookmarks(context, bookmarks)

    internal fun loadPersistedBookmarks(): List<Bookmark> =
        BookBookmarkWishlistManager.loadPersistedBookmarks(context, dbHelper)

    internal fun persistSettingToDb(key: String, value: String) =
        BookSettingsManager.persistSettingToDb(this, key, value)

    internal fun syncSettings() =
        BookSettingsManager.syncSettings(this)
}
