package io.github.tasmirz.lumina.data

import android.content.Context
import android.content.SharedPreferences
import io.github.tasmirz.lumina.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

import io.github.tasmirz.lumina.data.db.LuminaDatabaseHelper

class BookRepository(private val context: Context) {

    val dbHelper = LuminaDatabaseHelper(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lumina_reader_prefs", Context.MODE_PRIVATE)

    private val repoScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var readingPositionSaveJob: kotlinx.coroutines.Job? = null

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

    private val _disableStt = MutableStateFlow(prefs.getBoolean("disable_stt", false))
    val disableStt: StateFlow<Boolean> = _disableStt.asStateFlow()

    private val _enableFtsIndexing = MutableStateFlow(prefs.getBoolean("enable_fts_indexing", true))
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
                syncSettings()
            } catch (_: Exception) {}

            // Pre-index active book if needed
            val activeId = _activeBookId.value
            val active = loadedBooks.find { it.id == activeId } ?: loadedBooks.firstOrNull()
            if (active != null) {
                indexBookIfNeeded(active)
            }
        }
    }

    fun setGestureDoubleTap(action: GestureAction) {
        _gestureDoubleTap.value = action
        prefs.edit().putString("gesture_double_tap", action.name).apply()
        persistSettingToDb("gesture_double_tap", action.name)
    }

    fun setGestureTripleTap(action: GestureAction) {
        _gestureTripleTap.value = action
        prefs.edit().putString("gesture_triple_tap", action.name).apply()
        persistSettingToDb("gesture_triple_tap", action.name)
    }

    fun setGestureSingleTap(action: GestureAction) {
        _gestureSingleTap.value = action
        prefs.edit().putString("gesture_single_tap", action.name).apply()
        persistSettingToDb("gesture_single_tap", action.name)
    }

    fun setGestureTtsTap(action: GestureAction) {
        _gestureTtsTap.value = action
        prefs.edit().putString("gesture_tts_tap", action.name).apply()
        persistSettingToDb("gesture_tts_tap", action.name)
    }

    fun setCustomThemeColors(bg: Long, text: Long, accent: Long) {
        _customBgColor.value = bg
        _customTextColor.value = text
        _customAccentColor.value = accent
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
        prefs.edit().putInt("horizontal_padding", padding).apply()
        persistSettingToDb("horizontal_padding", padding.toString())
    }

    fun setVerticalPadding(padding: Int) {
        _verticalPadding.value = padding
        prefs.edit().putInt("vertical_padding", padding).apply()
        persistSettingToDb("vertical_padding", padding.toString())
    }

    fun setAssistantOrbStyle(style: String) {
        _assistantOrbStyle.value = style
        prefs.edit().putString("assistant_orb_style", style).apply()
        persistSettingToDb("assistant_orb_style", style)
    }

    fun setSpoilerShield(enabled: Boolean) {
        _spoilerShield.value = enabled
        prefs.edit().putBoolean("spoiler_shield", enabled).apply()
        persistSettingToDb("spoiler_shield", enabled.toString())
    }

    fun setAutoScrollSpeed(speed: Float) {
        _autoScrollSpeed.value = speed
        prefs.edit().putFloat("auto_scroll_speed", speed).apply()
        persistSettingToDb("auto_scroll_speed", speed.toString())
    }

    fun setDisableAi(disabled: Boolean) {
        _disableAi.value = disabled
        prefs.edit().putBoolean("disable_ai", disabled).apply()
        persistSettingToDb("disable_ai", disabled.toString())
    }

    fun setDisableTts(disabled: Boolean) {
        _disableTts.value = disabled
        prefs.edit().putBoolean("disable_tts", disabled).apply()
        persistSettingToDb("disable_tts", disabled.toString())
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
        prefs.edit().putBoolean("disable_stt", disabled).apply()
        persistSettingToDb("disable_stt", disabled.toString())
    }

    fun setEnableFtsIndexing(enabled: Boolean) {
        _enableFtsIndexing.value = enabled
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
        _books.value.forEach { b ->
            b.progress = 0
            b.currentChapter = 0
            b.currentPage = 0
            b.scrollPos = 0
            prefs.edit()
                .putInt("${b.id}_chapter", 0)
                .putInt("${b.id}_page", 0)
                .putInt("${b.id}_scroll", 0)
                .apply()
        }
        _books.value = _books.value.toList()
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
        prefs.edit().putString("ai_provider", provider.name).apply()
        persistSettingToDb("ai_provider", provider.name)
    }

    fun setAiBaseUrl(url: String) {
        _aiBaseUrl.value = url
        prefs.edit().putString("ai_base_url", url).apply()
        persistSettingToDb("ai_base_url", url)
    }

    fun setAiModel(model: String) {
        _aiModel.value = model
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
            prefs.edit().putString("orb_action_order", list.joinToString(",") { it.name }).apply()
            persistSettingToDb("orb_action_order", list.joinToString(",") { it.name })
        }
    }

    fun getActiveBook(): Book? {
        val id = _activeBookId.value
        return _books.value.find { it.id == id } ?: _books.value.firstOrNull()
    }

    fun setActiveBook(bookId: String) {
        _activeBookId.value = bookId
        prefs.edit().putString("active_book_id", bookId).apply()
        
        // Update last read timestamp
        val updated = _books.value.map {
            if (it.id == bookId) it.copy(lastRead = "Just now") else it
        }
        _books.value = updated
        _books.value.find { it.id == bookId }?.let { indexBookIfNeeded(it) }
    }

    fun updateReadingPosition(bookId: String, chapterIdx: Int, pageIdx: Int, scrollPos: Int, progressPct: Int) {
        val currentBook = _books.value.find { it.id == bookId }
        if (currentBook != null &&
            currentBook.currentChapter == chapterIdx &&
            currentBook.currentPage == pageIdx &&
            currentBook.scrollPos == scrollPos &&
            currentBook.progress == progressPct
        ) {
            return
        }

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

        // Persist per book in prefs
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

        // Debounce SQLite reading progress updates to prevent database lock contention
        readingPositionSaveJob?.cancel()
        readingPositionSaveJob = repoScope.launch {
            kotlinx.coroutines.delay(800)
            try {
                val book = _books.value.find { it.id == bookId }
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
        prefs.edit().putInt("font_size", size).apply()
        persistSettingToDb("font_size", size.toString())
    }

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        prefs.edit().putString("reading_mode", mode.name).apply()
        persistSettingToDb("reading_mode", mode.name)
    }

    fun setThemeMode(theme: ThemeMode) {
        _themeMode.value = theme
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
        prefs.edit().putString("theme_family", family.name).apply()
        persistSettingToDb("theme_family", family.name)
    }

    fun setThemeVariant(variant: ThemeVariant) {
        _themeVariant.value = variant
        prefs.edit().putString("theme_variant", variant.name).apply()
        persistSettingToDb("theme_variant", variant.name)
    }

    fun setBackgroundTexture(texture: BackgroundTexture) {
        _backgroundTexture.value = texture
        prefs.edit().putString("background_texture", texture.name).apply()
        persistSettingToDb("background_texture", texture.name)
    }

    fun setCustomBgUri(uri: String) {
        _customBgUri.value = uri
        prefs.edit().putString("custom_bg_uri", uri).apply()
        persistSettingToDb("custom_bg_uri", uri)
    }

    fun setOrbActionItems(items: Set<OrbActionItem>) {
        _orbActionItems.value = items
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
        prefs.edit().putString("text_alignment_mode", alignment.name).apply()
        persistSettingToDb("text_alignment_mode", alignment.name)
    }

    fun setLetterSpacing(spacing: Float) {
        _letterSpacing.value = spacing
        prefs.edit().putFloat("letter_spacing", spacing).apply()
        persistSettingToDb("letter_spacing", spacing.toString())
    }

    fun setTypefaceMode(typeface: TypefaceMode) {
        _typefaceMode.value = typeface
        prefs.edit().putString("typeface_mode", typeface.name).apply()
        persistSettingToDb("typeface_mode", typeface.name)
    }

    fun setLineHeight(multiplier: Float) {
        _lineHeightMultiplier.value = multiplier
        prefs.edit().putFloat("line_height", multiplier).apply()
        persistSettingToDb("line_height", multiplier.toString())
    }

    fun setParagraphSpacing(multiplier: Float) {
        _paragraphSpacingMultiplier.value = multiplier
        prefs.edit().putFloat("paragraph_spacing", multiplier).apply()
        persistSettingToDb("paragraph_spacing", multiplier.toString())
    }

    fun setShowFloatingAssistant(show: Boolean) {
        _showFloatingAssistant.value = show
        prefs.edit().putBoolean("show_floating_assistant", show).apply()
        persistSettingToDb("show_floating_assistant", show.toString())
    }

    fun setOrbSize(size: OrbSize) {
        _orbSize.value = size
        prefs.edit().putString("orb_size", size.name).apply()
        persistSettingToDb("orb_size", size.name)
    }

    fun setOrbMenuSize(size: OrbMenuSize) {
        _orbMenuSize.value = size
        prefs.edit().putString("orb_menu_size", size.name).apply()
        persistSettingToDb("orb_menu_size", size.name)
    }

    fun setOrbEdgeSnap(snap: Boolean) {
        _orbEdgeSnap.value = snap
        prefs.edit().putBoolean("orb_edge_snap", snap).apply()
        persistSettingToDb("orb_edge_snap", snap.toString())
    }

    fun saveOrbPosition(x: Float, y: Float, isLandscape: Boolean) {
        if (isLandscape) {
            _orbLandscapeX.value = x
            _orbLandscapeY.value = y
            prefs.edit().putFloat("orb_pos_x_landscape", x).putFloat("orb_pos_y_landscape", y).apply()
            persistSettingToDb("orb_pos_x_landscape", x.toString())
            persistSettingToDb("orb_pos_y_landscape", y.toString())
        } else {
            _orbPortraitX.value = x
            _orbPortraitY.value = y
            prefs.edit().putFloat("orb_pos_x_portrait", x).putFloat("orb_pos_y_portrait", y).apply()
            persistSettingToDb("orb_pos_x_portrait", x.toString())
            persistSettingToDb("orb_pos_y_portrait", y.toString())
        }
    }

    fun setOrbColor(color: OrbColor) {
        _orbColor.value = color
        prefs.edit().putString("orb_color", color.name).apply()
        persistSettingToDb("orb_color", color.name)
    }

    fun setOrbOpacity(opacity: Float) {
        _orbOpacity.value = opacity
        prefs.edit().putFloat("orb_opacity", opacity).apply()
        persistSettingToDb("orb_opacity", opacity.toString())
    }

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key
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
        val list = dbHelper.getCharacters(bookId)
        _characters.value = _characters.value + (bookId to list)
        return list
    }

    fun loadCharacters(bookId: String) {
        repoScope.launch {
            val list = dbHelper.getCharacters(bookId)
            _characters.value = _characters.value + (bookId to list)
        }
    }

    fun saveCharacter(character: BookCharacter) {
        repoScope.launch {
            dbHelper.insertCharacter(character)
            val list = dbHelper.getCharacters(character.bookId)
            _characters.value = _characters.value + (character.bookId to list)
        }
    }

    fun deleteCharacter(id: Long, bookId: String) {
        repoScope.launch {
            dbHelper.deleteCharacter(id)
            val list = dbHelper.getCharacters(bookId)
            _characters.value = _characters.value + (bookId to list)
        }
    }

    fun clearCharacters(bookId: String) {
        repoScope.launch {
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
        val list = dbHelper.getLore(bookId)
        _lore.value = _lore.value + (bookId to list)
        return list
    }

    fun loadLore(bookId: String) {
        repoScope.launch {
            val list = dbHelper.getLore(bookId)
            _lore.value = _lore.value + (bookId to list)
        }
    }

    fun saveLore(loreItem: BookLore) {
        repoScope.launch {
            dbHelper.insertLore(loreItem)
            val list = dbHelper.getLore(loreItem.bookId)
            _lore.value = _lore.value + (loreItem.bookId to list)
        }
    }

    fun deleteLore(id: Long, bookId: String) {
        repoScope.launch {
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
        setActiveBook(book.id)
        dbHelper.insertOrUpdateBook(book, book.filePath, book.isDownloaded, book.downloadUrl, book.fileSize)
    }

    fun removeBook(bookId: String) {
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
        return fromDb.filterNot { b ->
            b.id in setOf("book-kafka", "book-alice", "book-artofwar", "1", "2", "3", "demo-kafka", "demo-alice", "demo-artofwar") ||
            ((b.filePath.isNullOrBlank() || !File(b.filePath).exists()) && !b.isDownloaded)
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
            dbSettings["font_size"]?.toIntOrNull()?.let { _fontSize.value = it }
            dbSettings["reading_mode"]?.let {
                try { _readingMode.value = ReadingMode.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["theme_mode"]?.let {
                try { _themeMode.value = ThemeMode.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["typeface_mode"]?.let {
                try { _typefaceMode.value = TypefaceMode.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["line_height"]?.toFloatOrNull()?.let { _lineHeightMultiplier.value = it }
            dbSettings["paragraph_spacing"]?.toFloatOrNull()?.let { _paragraphSpacingMultiplier.value = it }
            dbSettings["letter_spacing"]?.toFloatOrNull()?.let { _letterSpacing.value = it }
            dbSettings["show_floating_assistant"]?.toBooleanStrictOrNull()?.let { _showFloatingAssistant.value = it }
            dbSettings["orb_size"]?.let {
                try { _orbSize.value = OrbSize.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["orb_menu_size"]?.let {
                try { _orbMenuSize.value = OrbMenuSize.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["orb_edge_snap"]?.toBooleanStrictOrNull()?.let { _orbEdgeSnap.value = it }
            dbSettings["orb_color"]?.let {
                try { _orbColor.value = OrbColor.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["orb_opacity"]?.toFloatOrNull()?.let { _orbOpacity.value = it }
            dbSettings["orb_pos_x_portrait"]?.toFloatOrNull()?.let { _orbPortraitX.value = it }
            dbSettings["orb_pos_y_portrait"]?.toFloatOrNull()?.let { _orbPortraitY.value = it }
            dbSettings["orb_pos_x_landscape"]?.toFloatOrNull()?.let { _orbLandscapeX.value = it }
            dbSettings["orb_pos_y_landscape"]?.toFloatOrNull()?.let { _orbLandscapeY.value = it }
            dbSettings["theme_family"]?.let {
                try { _themeFamily.value = ThemeFamily.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["theme_variant"]?.let {
                try { _themeVariant.value = ThemeVariant.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["background_texture"]?.let {
                try { _backgroundTexture.value = BackgroundTexture.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["custom_bg_uri"]?.let { _customBgUri.value = it }
            dbSettings["text_alignment_mode"]?.let {
                try { _textAlignmentMode.value = TextAlignmentMode.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["horizontal_padding"]?.toIntOrNull()?.let { _horizontalPadding.value = it }
            dbSettings["vertical_padding"]?.toIntOrNull()?.let { _verticalPadding.value = it }
            dbSettings["assistant_orb_style"]?.let { _assistantOrbStyle.value = it }
            dbSettings["spoiler_shield"]?.toBooleanStrictOrNull()?.let { _spoilerShield.value = it }
            dbSettings["auto_scroll_speed"]?.toFloatOrNull()?.let { _autoScrollSpeed.value = it }
            dbSettings["disable_ai"]?.toBooleanStrictOrNull()?.let { _disableAi.value = it }
            dbSettings["disable_tts"]?.toBooleanStrictOrNull()?.let { _disableTts.value = it }
            dbSettings["disable_stt"]?.toBooleanStrictOrNull()?.let { _disableStt.value = it }
            dbSettings["enable_fts_indexing"]?.toBooleanStrictOrNull()?.let { _enableFtsIndexing.value = it }
            dbSettings["gesture_double_tap"]?.let {
                try { _gestureDoubleTap.value = GestureAction.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["gesture_triple_tap"]?.let {
                try { _gestureTripleTap.value = GestureAction.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["gesture_single_tap"]?.let {
                try { _gestureSingleTap.value = GestureAction.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["gesture_tts_tap"]?.let {
                try { _gestureTtsTap.value = GestureAction.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["custom_bg_color"]?.toLongOrNull()?.let { _customBgColor.value = it }
            dbSettings["custom_text_color"]?.toLongOrNull()?.let { _customTextColor.value = it }
            dbSettings["custom_accent_color"]?.toLongOrNull()?.let { _customAccentColor.value = it }
            dbSettings["orb_action_items"]?.split(",")?.mapNotNull { name ->
                try { OrbActionItem.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.toSet()?.let { _orbActionItems.value = it }
            dbSettings["orb_action_order"]?.split(",")?.mapNotNull { name ->
                try { OrbActionItem.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.let { _orbActionOrder.value = it }
            dbSettings["quick_themes"]?.split(",")?.mapNotNull { name ->
                try { ThemeFamily.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.toSet()?.let { _quickThemes.value = it }
            dbSettings["quick_fonts"]?.split(",")?.mapNotNull { name ->
                try { TypefaceMode.valueOf(name.trim()) } catch (_: Exception) { null }
            }?.takeIf { it.isNotEmpty() }?.toSet()?.let { _quickFonts.value = it }
            dbSettings["ai_provider"]?.let {
                try { _aiProvider.value = AiProvider.valueOf(it) } catch (_: Exception) {}
            }
            dbSettings["ai_model"]?.let { if (it.isNotBlank()) _aiModel.value = it }
            dbSettings["ai_base_url"]?.let { if (it.isNotBlank()) _aiBaseUrl.value = it }
            dbSettings["gemini_api_key"]?.let { dbKey ->
                if (dbKey.isNotBlank()) {
                    _geminiApiKey.value = dbKey
                    prefs.edit().putString("gemini_api_key", dbKey).apply()
                } else {
                    val prefsKey = prefs.getString("gemini_api_key", "") ?: ""
                    if (prefsKey.isNotBlank()) {
                        _geminiApiKey.value = prefsKey
                        persistSettingToDb("gemini_api_key", prefsKey)
                    }
                }
            } ?: run {
                val prefsKey = prefs.getString("gemini_api_key", "") ?: ""
                if (prefsKey.isNotBlank()) {
                    _geminiApiKey.value = prefsKey
                    persistSettingToDb("gemini_api_key", prefsKey)
                }
            }
        }

        // Sync current state into SQLite
        persistSettingToDb("font_size", _fontSize.value.toString())
        persistSettingToDb("reading_mode", _readingMode.value.name)
        persistSettingToDb("theme_mode", _themeMode.value.name)
        persistSettingToDb("typeface_mode", _typefaceMode.value.name)
        persistSettingToDb("line_height", _lineHeightMultiplier.value.toString())
        persistSettingToDb("paragraph_spacing", _paragraphSpacingMultiplier.value.toString())
        persistSettingToDb("letter_spacing", _letterSpacing.value.toString())
        persistSettingToDb("show_floating_assistant", _showFloatingAssistant.value.toString())
        persistSettingToDb("orb_size", _orbSize.value.name)
        persistSettingToDb("orb_menu_size", _orbMenuSize.value.name)
        persistSettingToDb("orb_edge_snap", _orbEdgeSnap.value.toString())
        persistSettingToDb("orb_color", _orbColor.value.name)
        persistSettingToDb("orb_opacity", _orbOpacity.value.toString())
        persistSettingToDb("orb_pos_x_portrait", _orbPortraitX.value.toString())
        persistSettingToDb("orb_pos_y_portrait", _orbPortraitY.value.toString())
        persistSettingToDb("orb_pos_x_landscape", _orbLandscapeX.value.toString())
        persistSettingToDb("orb_pos_y_landscape", _orbLandscapeY.value.toString())
        persistSettingToDb("theme_family", _themeFamily.value.name)
        persistSettingToDb("theme_variant", _themeVariant.value.name)
        persistSettingToDb("background_texture", _backgroundTexture.value.name)
        persistSettingToDb("custom_bg_uri", _customBgUri.value)
        persistSettingToDb("text_alignment_mode", _textAlignmentMode.value.name)
        persistSettingToDb("horizontal_padding", _horizontalPadding.value.toString())
        persistSettingToDb("vertical_padding", _verticalPadding.value.toString())
        persistSettingToDb("assistant_orb_style", _assistantOrbStyle.value)
        persistSettingToDb("spoiler_shield", _spoilerShield.value.toString())
        persistSettingToDb("auto_scroll_speed", _autoScrollSpeed.value.toString())
        persistSettingToDb("disable_ai", _disableAi.value.toString())
        persistSettingToDb("disable_tts", _disableTts.value.toString())
        persistSettingToDb("disable_stt", _disableStt.value.toString())
        persistSettingToDb("enable_fts_indexing", _enableFtsIndexing.value.toString())
        persistSettingToDb("gesture_double_tap", _gestureDoubleTap.value.name)
        persistSettingToDb("gesture_triple_tap", _gestureTripleTap.value.name)
        persistSettingToDb("gesture_single_tap", _gestureSingleTap.value.name)
        persistSettingToDb("gesture_tts_tap", _gestureTtsTap.value.name)
        persistSettingToDb("custom_bg_color", _customBgColor.value.toString())
        persistSettingToDb("custom_text_color", _customTextColor.value.toString())
        persistSettingToDb("custom_accent_color", _customAccentColor.value.toString())
        persistSettingToDb("orb_action_items", _orbActionItems.value.joinToString(",") { it.name })
        persistSettingToDb("orb_action_order", _orbActionOrder.value.joinToString(",") { it.name })
        persistSettingToDb("quick_themes", _quickThemes.value.joinToString(",") { it.name })
        persistSettingToDb("quick_fonts", _quickFonts.value.joinToString(",") { it.name })
        persistSettingToDb("ai_provider", _aiProvider.value.name)
        persistSettingToDb("ai_model", _aiModel.value)
        persistSettingToDb("ai_base_url", _aiBaseUrl.value)
        persistSettingToDb("gemini_api_key", _geminiApiKey.value)
    }
}
