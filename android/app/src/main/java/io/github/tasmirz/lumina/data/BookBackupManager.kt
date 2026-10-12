package io.github.tasmirz.lumina.data

import io.github.tasmirz.lumina.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles unified JSON backup export and import for user settings, reading progress,
 * bookmarks, wishlist, completed books, and custom themes.
 */
object BookBackupManager {

    fun exportUnifiedBackupJson(repository: BookRepository): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings
        val settings = JSONObject()
        settings.put("font_size", repository._fontSize.value)
        settings.put("reading_mode", repository._readingMode.value.name)
        settings.put("theme_family", repository._themeFamily.value.name)
        settings.put("theme_variant", repository._themeVariant.value.name)
        settings.put("typeface_mode", repository._typefaceMode.value.name)
        settings.put("line_height", repository._lineHeightMultiplier.value.toDouble())
        settings.put("paragraph_spacing", repository._paragraphSpacingMultiplier.value.toDouble())
        settings.put("letter_spacing", repository._letterSpacing.value.toDouble())
        settings.put("text_alignment_mode", repository._textAlignmentMode.value.name)
        settings.put("horizontal_padding", repository._horizontalPadding.value)
        settings.put("vertical_padding", repository._verticalPadding.value)
        settings.put("paged_safe_lines_to_remove", repository._pagedSafeLinesToRemove.value)
        settings.put("vertically_center_pages", repository._verticallyCenterPages.value)
        settings.put("fill_page_to_bottom", repository._fillPageToBottom.value)
        settings.put("adaptive_vertical_spacing", repository._adaptiveVerticalSpacing.value)
        settings.put("golden_ratio_chapter_openers", repository._goldenRatioChapterOpeners.value)
        settings.put("paged_swipe_threshold", repository._pagedSwipeThreshold.value.toDouble())
        settings.put("auto_save_dwell_seconds", repository._autoSaveDwellSeconds.value)
        settings.put("smart_consecutive_auto_save", repository._smartConsecutiveAutoSave.value)
        settings.put("auto_save_consecutive_pages", repository._autoSaveConsecutivePages.value)
        settings.put("show_startup_loading_screen", repository._showStartupLoadingScreen.value)
        settings.put("uncached_book_open_mode", repository._uncachedBookOpenMode.value.name)
        settings.put("assistant_orb_style", repository._assistantOrbStyle.value)
        settings.put("spoiler_shield", repository._spoilerShield.value)
        settings.put("auto_scroll_speed", repository._autoScrollSpeed.value.toDouble())
        settings.put("disable_ai", repository._disableAi.value)
        settings.put("disable_tts", repository._disableTts.value)
        settings.put("ai_provider", repository._aiProvider.value.name)
        settings.put("ai_model", repository._aiModel.value)
        settings.put("ai_base_url", repository._aiBaseUrl.value)
        settings.put("gemini_api_key", repository._geminiApiKey.value)
        settings.put("open_library_api_key", repository._openLibraryApiKey.value)
        settings.put("background_texture", repository._backgroundTexture.value.name)
        settings.put("selected_custom_texture_id", repository._selectedCustomTextureId.value)
        settings.put("dynamic_rolling_texture", repository._dynamicRollingTexture.value)
        settings.put("custom_bg_uri", repository._customBgUri.value)
        settings.put("custom_bg_color", repository._customBgColor.value)
        settings.put("custom_text_color", repository._customTextColor.value)
        settings.put("custom_accent_color", repository._customAccentColor.value)
        settings.put("orb_color", repository._orbColor.value.name)
        settings.put("custom_orb_color", repository._customOrbColor.value)
        settings.put("image_filter_mode", repository.imageFilterMode.value.name)
        settings.put("invert_screen", repository.invertScreen.value)
        settings.put("paged_bottom_clearance_dp", repository.pagedBottomClearanceDp.value)

        val delIds = repository.prefs.getStringSet("deleted_book_ids", emptySet()) ?: emptySet()
        val delTitles = repository.prefs.getStringSet("deleted_book_titles", emptySet()) ?: emptySet()
        val delPaths = repository.prefs.getStringSet("deleted_book_paths", emptySet()) ?: emptySet()
        val delIdsArr = JSONArray()
        delIds.forEach { delIdsArr.put(it) }
        settings.put("deleted_book_ids", delIdsArr)
        val delTitlesArr = JSONArray()
        delTitles.forEach { delTitlesArr.put(it) }
        settings.put("deleted_book_titles", delTitlesArr)
        val delPathsArr = JSONArray()
        delPaths.forEach { delPathsArr.put(it) }
        settings.put("deleted_book_paths", delPathsArr)
        root.put("settings", settings)

        // 2. Reading progress of books
        val booksProgressArray = JSONArray()
        repository._books.value.forEach { b ->
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
        repository._bookmarks.value.forEach { bm ->
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
        repository._wishlistBooks.value.forEach { wb ->
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
        repository._completedBookIds.value.forEach { completedArr.put(it) }
        root.put("completed_book_ids", completedArr)

        // 6. Custom Themes
        val themesArray = JSONArray()
        repository._customThemes.value.forEach { th ->
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

    fun importUnifiedBackupJson(repository: BookRepository, jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)

            // Restore settings
            if (root.has("settings")) {
                val s = root.getJSONObject("settings")
                if (s.has("font_size")) repository.setFontSize(s.getInt("font_size"))
                if (s.has("reading_mode")) {
                    try { repository.setReadingMode(ReadingMode.valueOf(s.getString("reading_mode"))) } catch (_: Exception) {}
                }
                if (s.has("theme_family")) {
                    try { repository.setThemeFamily(ThemeFamily.valueOf(s.getString("theme_family"))) } catch (_: Exception) {}
                }
                if (s.has("theme_variant")) {
                    try { repository.setThemeVariant(ThemeVariant.valueOf(s.getString("theme_variant"))) } catch (_: Exception) {}
                }
                if (s.has("typeface_mode")) {
                    try { repository.setTypefaceMode(TypefaceMode.valueOf(s.getString("typeface_mode"))) } catch (_: Exception) {}
                }
                if (s.has("line_height")) repository.setLineHeight(s.getDouble("line_height").toFloat())
                if (s.has("paragraph_spacing")) repository.setParagraphSpacing(s.getDouble("paragraph_spacing").toFloat())
                if (s.has("letter_spacing")) repository.setLetterSpacing(s.getDouble("letter_spacing").toFloat())
                if (s.has("text_alignment_mode")) {
                    try { repository.setTextAlignmentMode(TextAlignmentMode.valueOf(s.getString("text_alignment_mode"))) } catch (_: Exception) {}
                }
                if (s.has("horizontal_padding")) repository.setHorizontalPadding(s.getInt("horizontal_padding"))
                if (s.has("vertical_padding")) repository.setVerticalPadding(s.getInt("vertical_padding"))
                if (s.has("paged_safe_lines_to_remove")) repository.setPagedSafeLinesToRemove(s.getInt("paged_safe_lines_to_remove"))
                if (s.has("vertically_center_pages")) repository.setVerticallyCenterPages(s.getBoolean("vertically_center_pages"))
                if (s.has("fill_page_to_bottom")) repository.setFillPageToBottom(s.getBoolean("fill_page_to_bottom"))
                if (s.has("adaptive_vertical_spacing")) repository.setAdaptiveVerticalSpacing(s.getBoolean("adaptive_vertical_spacing"))
                if (s.has("golden_ratio_chapter_openers")) repository.setGoldenRatioChapterOpeners(s.getBoolean("golden_ratio_chapter_openers"))
                if (s.has("paged_swipe_threshold")) repository.setPagedSwipeThreshold(s.getDouble("paged_swipe_threshold").toFloat())
                if (s.has("auto_save_dwell_seconds")) repository.setAutoSaveDwellSeconds(s.getInt("auto_save_dwell_seconds"))
                if (s.has("smart_consecutive_auto_save")) repository.setSmartConsecutiveAutoSave(s.getBoolean("smart_consecutive_auto_save"))
                if (s.has("auto_save_consecutive_pages")) repository.setAutoSaveConsecutivePages(s.getInt("auto_save_consecutive_pages"))
                if (s.has("show_startup_loading_screen")) repository.setShowStartupLoadingScreen(s.getBoolean("show_startup_loading_screen"))
                if (s.has("uncached_book_open_mode")) {
                    try { repository.setUncachedBookOpenMode(UncachedBookOpenMode.valueOf(s.getString("uncached_book_open_mode"))) } catch (_: Exception) {}
                }
                if (s.has("assistant_orb_style")) repository.setAssistantOrbStyle(s.getString("assistant_orb_style"))
                if (s.has("spoiler_shield")) repository.setSpoilerShield(s.getBoolean("spoiler_shield"))
                if (s.has("auto_scroll_speed")) repository.setAutoScrollSpeed(s.getDouble("auto_scroll_speed").toFloat())
                if (s.has("disable_ai")) repository.setDisableAi(s.getBoolean("disable_ai"))
                if (s.has("disable_tts")) repository.setDisableTts(s.getBoolean("disable_tts"))
                if (s.has("ai_provider")) {
                    try { repository.setAiProvider(AiProvider.valueOf(s.getString("ai_provider"))) } catch (_: Exception) {}
                }
                if (s.has("ai_model")) repository.setAiModel(s.getString("ai_model"))
                if (s.has("ai_base_url")) repository.setAiBaseUrl(s.getString("ai_base_url"))
                if (s.has("gemini_api_key")) repository.setGeminiApiKey(s.getString("gemini_api_key"))
                if (s.has("open_library_api_key")) repository.setOpenLibraryApiKey(s.getString("open_library_api_key"))
                if (s.has("background_texture")) {
                    try { repository.setBackgroundTexture(BackgroundTexture.valueOf(s.getString("background_texture"))) } catch (_: Exception) {}
                }
                if (s.has("selected_custom_texture_id")) {
                    val texId = s.getString("selected_custom_texture_id")
                    repository._selectedCustomTextureId.value = texId
                    repository.prefs.edit().putString("selected_custom_texture_id", texId).apply()
                    repository.persistSettingToDb("selected_custom_texture_id", texId)
                }
                if (s.has("custom_bg_uri")) repository.setCustomBgUri(s.getString("custom_bg_uri"))
                if (s.has("custom_bg_color") && s.has("custom_text_color") && s.has("custom_accent_color")) {
                    repository.setCustomThemeColors(s.getLong("custom_bg_color"), s.getLong("custom_text_color"), s.getLong("custom_accent_color"))
                }
                if (s.has("custom_orb_color")) {
                    repository.setCustomOrbColor(s.getLong("custom_orb_color"))
                }
                if (s.has("orb_color")) {
                    try { repository.setOrbColor(OrbColor.valueOf(s.getString("orb_color"))) } catch (_: Exception) {}
                }
                if (s.has("image_filter_mode")) {
                    try { repository.setImageFilterMode(ImageFilterMode.valueOf(s.getString("image_filter_mode"))) } catch (_: Exception) {}
                }
                if (s.has("invert_screen")) {
                    repository.setInvertScreen(s.getBoolean("invert_screen"))
                }
                if (s.has("paged_bottom_clearance_dp")) {
                    repository.setPagedBottomClearanceDp(s.getInt("paged_bottom_clearance_dp"))
                }
                if (s.has("deleted_book_ids")) {
                    val arr = s.getJSONArray("deleted_book_ids")
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    val current = repository.prefs.getStringSet("deleted_book_ids", emptySet()) ?: emptySet()
                    repository.prefs.edit().putStringSet("deleted_book_ids", current + set).apply()
                    repository.persistSettingToDb("deleted_book_ids", (current + set).joinToString(","))
                }
                if (s.has("deleted_book_titles")) {
                    val arr = s.getJSONArray("deleted_book_titles")
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    val current = repository.prefs.getStringSet("deleted_book_titles", emptySet()) ?: emptySet()
                    repository.prefs.edit().putStringSet("deleted_book_titles", current + set).apply()
                    repository.persistSettingToDb("deleted_book_titles", (current + set).joinToString(","))
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
                    repository.updateReadingPosition(id, ch, pg, sc, pr)
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
                    repository.dbHelper.insertBookmark(bm)
                    restoredBookmarks.add(bm)
                }
                repository._bookmarks.value = repository.dbHelper.getAllBookmarks()
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
                    repository.dbHelper.insertWishlist(item)
                }
                repository._wishlistBooks.value = repository.dbHelper.getAllWishlist()
            }

            // Restore Completed books
            if (root.has("completed_book_ids")) {
                val cArr = root.getJSONArray("completed_book_ids")
                val compSet = mutableSetOf<String>()
                for (i in 0 until cArr.length()) {
                    val id = cArr.getString(i)
                    compSet.add(id)
                    repository.dbHelper.markBookCompleted(id, true)
                }
                repository._completedBookIds.value = compSet
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
                    repository.dbHelper.saveCustomTheme(theme)
                }
                repository._customThemes.value = repository.dbHelper.getAllCustomThemes()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
