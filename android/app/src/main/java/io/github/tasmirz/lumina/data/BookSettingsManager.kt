package io.github.tasmirz.lumina.data

import android.content.SharedPreferences
import io.github.tasmirz.lumina.model.*

/**
 * Handles initialization, synchronization, and database persistence of reader settings.
 */
object BookSettingsManager {

    fun loadInitialSettingsFromPrefs(prefs: SharedPreferences): ReaderSettings {
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
            pagedSafeLinesToRemove = prefs.getInt("paged_safe_lines_to_remove", 0),
            verticallyCenterPages = prefs.getBoolean("vertically_center_pages", false),
            fillPageToBottom = prefs.getBoolean("fill_page_to_bottom", true),
            adaptiveVerticalSpacing = prefs.getBoolean("adaptive_vertical_spacing", false),
            goldenRatioChapterOpeners = prefs.getBoolean("golden_ratio_chapter_openers", false),
            pagedSwipeThreshold = prefs.getFloat("paged_swipe_threshold", 0.08f),
            autoSaveDwellSeconds = prefs.getInt("auto_save_dwell_seconds", 5).coerceIn(1, 60),
            smartConsecutiveAutoSave = prefs.getBoolean("smart_consecutive_auto_save", true),
            autoSaveConsecutivePages = prefs.getInt("auto_save_consecutive_pages", 3).coerceIn(2, 10),
            allowTableSplitting = prefs.getBoolean("allow_table_splitting", true),
            showStartupLoadingScreen = prefs.getBoolean("show_startup_loading_screen", false),
            uncachedBookOpenMode = try { UncachedBookOpenMode.valueOf(prefs.getString("uncached_book_open_mode", UncachedBookOpenMode.INSTANT_ACTIVE_FIRST.name) ?: UncachedBookOpenMode.INSTANT_ACTIVE_FIRST.name) } catch (_: Exception) { UncachedBookOpenMode.INSTANT_ACTIVE_FIRST },
            inlineImageWidthPercent = prefs.getInt("inline_image_width_percent", 100),
            showFloatingAssistant = prefs.getBoolean("show_floating_assistant", true),
            orbSize = try { OrbSize.valueOf(prefs.getString("orb_size", OrbSize.DEFAULT.name) ?: OrbSize.DEFAULT.name) } catch (_: Exception) { OrbSize.DEFAULT },
            orbMenuSize = try { OrbMenuSize.valueOf(prefs.getString("orb_menu_size", OrbMenuSize.MEDIUM.name) ?: OrbMenuSize.MEDIUM.name) } catch (_: Exception) { OrbMenuSize.MEDIUM },
            orbEdgeSnap = prefs.getBoolean("orb_edge_snap", true),
            orbPortraitX = prefs.getFloat("orb_pos_x_portrait", -1f),
            orbPortraitY = prefs.getFloat("orb_pos_y_portrait", -1f),
            orbLandscapeX = prefs.getFloat("orb_pos_x_landscape", -1f),
            orbLandscapeY = prefs.getFloat("orb_pos_y_landscape", -1f),
            orbColor = try { OrbColor.valueOf(prefs.getString("orb_color", OrbColor.THEME.name) ?: OrbColor.THEME.name) } catch (_: Exception) { OrbColor.THEME },
            customOrbColor = prefs.getLong("custom_orb_color", 0xFF4F46E5L),
            orbOpacity = prefs.getFloat("orb_opacity", 0.85f),
            backgroundTexture = try { BackgroundTexture.valueOf(prefs.getString("background_texture", BackgroundTexture.NONE.name) ?: BackgroundTexture.NONE.name) } catch (_: Exception) { BackgroundTexture.NONE },
            selectedCustomTextureId = prefs.getString("selected_custom_texture_id", "") ?: "",
            dynamicRollingTexture = prefs.getBoolean("dynamic_rolling_texture", true),
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
            localOnlyMode = prefs.getBoolean("local_only_mode", false),
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
            preferredLanguage = prefs.getString("preferred_language", "auto") ?: "auto",
            openLibraryApiKey = prefs.getString("open_library_api_key", "") ?: "",
            imageFilterMode = try { ImageFilterMode.valueOf(prefs.getString("image_filter_mode", ImageFilterMode.ORIGINAL.name) ?: ImageFilterMode.ORIGINAL.name) } catch (_: Exception) { ImageFilterMode.ORIGINAL },
            invertScreen = prefs.getBoolean("invert_screen", false),
            pagedBottomClearanceDp = prefs.getInt("paged_bottom_clearance_dp", 28).coerceIn(0, 100),
            landscapeHideHeader = prefs.getBoolean("landscape_hide_header", true),
            landscapeHideProgress = prefs.getBoolean("landscape_hide_progress", true)
        )
    }

    fun persistSettingToDb(repository: BookRepository, key: String, value: String) {
        try {
            repository.dbHelper.setSetting(key, value)
        } catch (_: Exception) {}
    }

    fun syncSettings(repository: BookRepository) {
        val dbHelper = repository.dbHelper
        val prefs = repository.prefs
        val dbSettings = dbHelper.getAllSettings()

        if (dbSettings.isNotEmpty()) {
            var s = repository._readerSettings.value
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
            dbSettings["selected_custom_texture_id"]?.let { s = s.copy(selectedCustomTextureId = it) }
            dbSettings["dynamic_rolling_texture"]?.toBooleanStrictOrNull()?.let { s = s.copy(dynamicRollingTexture = it) }
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
                        persistSettingToDb(repository, "gemini_api_key", prefsKey)
                    }
                }
            } ?: run {
                val prefsKey = prefs.getString("gemini_api_key", "") ?: ""
                if (prefsKey.isNotBlank()) {
                    s = s.copy(geminiApiKey = prefsKey)
                    persistSettingToDb(repository, "gemini_api_key", prefsKey)
                }
            }
            dbSettings["open_library_api_key"]?.let { dbKey ->
                if (dbKey.isNotBlank()) {
                    s = s.copy(openLibraryApiKey = dbKey)
                    prefs.edit().putString("open_library_api_key", dbKey).apply()
                } else {
                    val prefsKey = prefs.getString("open_library_api_key", "") ?: ""
                    if (prefsKey.isNotBlank()) {
                        s = s.copy(openLibraryApiKey = prefsKey)
                        persistSettingToDb(repository, "open_library_api_key", prefsKey)
                    }
                }
            } ?: run {
                val prefsKey = prefs.getString("open_library_api_key", "") ?: ""
                if (prefsKey.isNotBlank()) {
                    s = s.copy(openLibraryApiKey = prefsKey)
                    persistSettingToDb(repository, "open_library_api_key", prefsKey)
                }
            }
            dbSettings["image_filter_mode"]?.let {
                try { s = s.copy(imageFilterMode = ImageFilterMode.valueOf(it)) } catch (_: Exception) {}
            }
            dbSettings["invert_screen"]?.let {
                s = s.copy(invertScreen = it.toBoolean())
            }
            dbSettings["paged_bottom_clearance_dp"]?.toIntOrNull()?.let {
                s = s.copy(pagedBottomClearanceDp = it.coerceIn(0, 100))
            }
            dbSettings["landscape_hide_header"]?.let {
                s = s.copy(landscapeHideHeader = it.toBoolean())
            }
            dbSettings["landscape_hide_progress"]?.let {
                s = s.copy(landscapeHideProgress = it.toBoolean())
            }

            // Atomic update to readerSettings Flow
            repository._readerSettings.value = s

            // Also keep individual StateFlows in sync
            repository._fontSize.value = s.fontSize
            repository._readingMode.value = s.readingMode
            repository._themeMode.value = s.themeMode
            repository._typefaceMode.value = s.typefaceMode
            repository._lineHeightMultiplier.value = s.lineHeightMultiplier
            repository._paragraphSpacingMultiplier.value = s.paragraphSpacingMultiplier
            repository._letterSpacing.value = s.letterSpacing
            repository._showFloatingAssistant.value = s.showFloatingAssistant
            repository._orbSize.value = s.orbSize
            repository._orbMenuSize.value = s.orbMenuSize
            repository._orbEdgeSnap.value = s.orbEdgeSnap
            repository._orbColor.value = s.orbColor
            repository._orbOpacity.value = s.orbOpacity
            repository._orbPortraitX.value = s.orbPortraitX
            repository._orbPortraitY.value = s.orbPortraitY
            repository._orbLandscapeX.value = s.orbLandscapeX
            repository._orbLandscapeY.value = s.orbLandscapeY
            repository._themeFamily.value = s.themeFamily
            repository._themeVariant.value = s.themeVariant
            repository._backgroundTexture.value = s.backgroundTexture
            repository._selectedCustomTextureId.value = s.selectedCustomTextureId
            repository._dynamicRollingTexture.value = s.dynamicRollingTexture
            repository._customBgUri.value = s.customBgUri
            repository._textAlignmentMode.value = s.textAlignmentMode
            repository._horizontalPadding.value = s.horizontalPadding
            repository._verticalPadding.value = s.verticalPadding
            repository._assistantOrbStyle.value = s.assistantOrbStyle
            repository._spoilerShield.value = s.spoilerShield
            repository._autoScrollSpeed.value = s.autoScrollSpeed
            repository._disableAi.value = s.disableAi
            repository._disableTts.value = s.disableTts
            repository._ttsEngine.value = s.ttsEngine
            repository._ttsEdgeVoice.value = s.ttsEdgeVoice
            repository._ttsSpeed.value = s.ttsSpeed
            repository._ttsPitch.value = s.ttsPitch
            repository._disableStt.value = s.disableStt
            repository._autoStartMic.value = s.autoStartMic
            repository._enableFtsIndexing.value = s.enableFtsIndexing
            repository._gestureDoubleTap.value = s.gestureDoubleTap
            repository._gestureTripleTap.value = s.gestureTripleTap
            repository._gestureSingleTap.value = s.gestureSingleTap
            repository._gestureTtsTap.value = s.gestureTtsTap
            repository._customBgColor.value = s.customBgColor
            repository._customTextColor.value = s.customTextColor
            repository._customAccentColor.value = s.customAccentColor
            repository._orbActionItems.value = s.orbActionItems
            repository._orbActionOrder.value = s.orbActionOrder
            repository._quickThemes.value = s.quickThemes
            repository._quickFonts.value = s.quickFonts
            repository._aiProvider.value = s.aiProvider
            repository._aiModel.value = s.aiModel
            repository._aiBaseUrl.value = s.aiBaseUrl
            repository._geminiApiKey.value = s.geminiApiKey
            repository._openLibraryApiKey.value = s.openLibraryApiKey
            OnlineEpubService.openLibraryApiKey = s.openLibraryApiKey
            repository._imageFilterMode.value = s.imageFilterMode
            repository._invertScreen.value = s.invertScreen
            repository._pagedBottomClearanceDp.value = s.pagedBottomClearanceDp
            repository._landscapeHideHeader.value = s.landscapeHideHeader
            repository._landscapeHideProgress.value = s.landscapeHideProgress
        } else {
            // Initial seed into SQLite in a single transaction
            val s = repository._readerSettings.value
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
                "selected_custom_texture_id" to s.selectedCustomTextureId,
                "dynamic_rolling_texture" to s.dynamicRollingTexture.toString(),
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
                "gemini_api_key" to s.geminiApiKey,
                "open_library_api_key" to s.openLibraryApiKey,
                "image_filter_mode" to s.imageFilterMode.name,
                "invert_screen" to s.invertScreen.toString(),
                "paged_bottom_clearance_dp" to s.pagedBottomClearanceDp.toString(),
                "landscape_hide_header" to s.landscapeHideHeader.toString(),
                "landscape_hide_progress" to s.landscapeHideProgress.toString()
            )
            dbHelper.setSettings(initialMap)
        }
    }
}
