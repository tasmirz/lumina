package org.protidhoni.lumina.data

import android.content.Context
import android.content.SharedPreferences
import org.protidhoni.lumina.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

import org.protidhoni.lumina.data.db.LuminaDatabaseHelper

class BookRepository(private val context: Context) {

    val dbHelper = LuminaDatabaseHelper(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lumina_reader_prefs", Context.MODE_PRIVATE)

    private val _books = MutableStateFlow<List<Book>>(loadAllBooks())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _activeBookId = MutableStateFlow(prefs.getString("active_book_id", "book-kafka") ?: "book-kafka")
    val activeBookId: StateFlow<String> = _activeBookId.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(loadPersistedBookmarks())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _wishlistBooks = MutableStateFlow<List<WishlistBook>>(dbHelper.getAllWishlist())
    val wishlistBooks: StateFlow<List<WishlistBook>> = _wishlistBooks.asStateFlow()

    // Preferences
    private val _fontSize = MutableStateFlow(prefs.getInt("font_size", 18))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    private val _readingMode = MutableStateFlow(
        ReadingMode.valueOf(prefs.getString("reading_mode", ReadingMode.SCROLL.name) ?: ReadingMode.SCROLL.name)
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

    private val _showFloatingAssistant = MutableStateFlow(prefs.getBoolean("show_floating_assistant", true))
    val showFloatingAssistant: StateFlow<Boolean> = _showFloatingAssistant.asStateFlow()

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
        }?.toSet() ?: setOf(
            OrbActionItem.READING_MODE,
            OrbActionItem.TTS,
            OrbActionItem.NOTE,
            OrbActionItem.TOC,
            OrbActionItem.SETTINGS
        )
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

    private val _customBgColor = MutableStateFlow(prefs.getLong("custom_bg_color", 0xFF1C1917L))
    val customBgColor: StateFlow<Long> = _customBgColor.asStateFlow()

    private val _customTextColor = MutableStateFlow(prefs.getLong("custom_text_color", 0xFFE7E5E4L))
    val customTextColor: StateFlow<Long> = _customTextColor.asStateFlow()

    private val _customAccentColor = MutableStateFlow(prefs.getLong("custom_accent_color", 0xFFD4AF37L))
    val customAccentColor: StateFlow<Long> = _customAccentColor.asStateFlow()

    fun setCustomThemeColors(bg: Long, text: Long, accent: Long) {
        _customBgColor.value = bg
        _customTextColor.value = text
        _customAccentColor.value = accent
        prefs.edit()
            .putLong("custom_bg_color", bg)
            .putLong("custom_text_color", text)
            .putLong("custom_accent_color", accent)
            .apply()
    }

    fun setAiProvider(provider: AiProvider) {
        _aiProvider.value = provider
        prefs.edit().putString("ai_provider", provider.name).apply()
    }

    fun setAiBaseUrl(url: String) {
        _aiBaseUrl.value = url
        prefs.edit().putString("ai_base_url", url).apply()
    }

    fun setAiModel(model: String) {
        _aiModel.value = model
        prefs.edit().putString("ai_model", model).apply()
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
    }

    fun reorderOrbAction(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val list = _orbActionOrder.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _orbActionOrder.value = list
            prefs.edit().putString("orb_action_order", list.joinToString(",") { it.name }).apply()
        }
    }

    fun getActiveBook(): Book {
        val id = _activeBookId.value
        return _books.value.find { it.id == id } ?: _books.value.first()
    }

    fun setActiveBook(bookId: String) {
        _activeBookId.value = bookId
        prefs.edit().putString("active_book_id", bookId).apply()
        
        // Update last read timestamp
        val updated = _books.value.map {
            if (it.id == bookId) it.copy(lastRead = "Just now") else it
        }
        _books.value = updated
    }

    fun updateReadingPosition(bookId: String, chapterIdx: Int, pageIdx: Int, scrollPos: Int, progressPct: Int) {
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

        // Sync with SQLite reading progress
        val book = _books.value.find { it.id == bookId }
        if (book != null) {
            dbHelper.updateReadingProgress(bookId, book.title, book.author, progressPct, "${100 - progressPct}m left")
        }
    }

    fun setFontSize(size: Int) {
        _fontSize.value = size
        prefs.edit().putInt("font_size", size).apply()
    }

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        prefs.edit().putString("reading_mode", mode.name).apply()
    }

    fun setThemeMode(theme: ThemeMode) {
        _themeMode.value = theme
        prefs.edit().putString("theme_mode", theme.name).apply()
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
    }

    fun setThemeVariant(variant: ThemeVariant) {
        _themeVariant.value = variant
        prefs.edit().putString("theme_variant", variant.name).apply()
    }

    fun setBackgroundTexture(texture: BackgroundTexture) {
        _backgroundTexture.value = texture
        prefs.edit().putString("background_texture", texture.name).apply()
    }

    fun setCustomBgUri(uri: String) {
        _customBgUri.value = uri
        prefs.edit().putString("custom_bg_uri", uri).apply()
    }

    fun setOrbActionItems(items: Set<OrbActionItem>) {
        _orbActionItems.value = items
        prefs.edit().putStringSet("orb_action_items", items.map { it.name }.toSet()).apply()
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
    }

    fun setLetterSpacing(spacing: Float) {
        _letterSpacing.value = spacing
        prefs.edit().putFloat("letter_spacing", spacing).apply()
    }

    fun setTypefaceMode(typeface: TypefaceMode) {
        _typefaceMode.value = typeface
        prefs.edit().putString("typeface_mode", typeface.name).apply()
    }

    fun setLineHeight(multiplier: Float) {
        _lineHeightMultiplier.value = multiplier
        prefs.edit().putFloat("line_height", multiplier).apply()
    }

    fun setShowFloatingAssistant(show: Boolean) {
        _showFloatingAssistant.value = show
        prefs.edit().putBoolean("show_floating_assistant", show).apply()
    }

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun setLastTab(tab: String) {
        prefs.edit().putString("last_screen_tab", tab).apply()
    }

    fun getLastTab(): String {
        return prefs.getString("last_screen_tab", "LIBRARY") ?: "LIBRARY"
    }

    fun addBookmark(quote: String, color: HighlightColor = HighlightColor.GOLD, note: String = "") {
        val book = getActiveBook()
        val chapter = book.chapters.getOrNull(book.currentChapter)?.title ?: "Chapter"
        val mark = Bookmark(
            bookTitle = book.title,
            chapter = chapter,
            quote = quote,
            color = color,
            note = note.trim(),
            timestamp = "Just now"
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

    private val defaultBookIds = setOf("book-kafka", "book-alice", "book-artofwar")

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
        saveCustomBooks(updated.filter { it.id !in defaultBookIds })
    }

    fun removeBook(bookId: String) {
        val currentDeleted = prefs.getStringSet("deleted_book_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentDeleted.add(bookId)
        prefs.edit().putStringSet("deleted_book_ids", currentDeleted).apply()

        val updated = _books.value.filterNot { it.id == bookId }
        _books.value = updated
        saveCustomBooks(updated.filter { it.id !in defaultBookIds })

        try {
            val imgDir = File(context.filesDir, "books/$bookId")
            if (imgDir.exists()) {
                imgDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If active book was removed, switch to another book
        if (_activeBookId.value == bookId) {
            val fallback = updated.firstOrNull()
            if (fallback != null) {
                setActiveBook(fallback.id)
            }
        }
    }

    private fun loadAllBooks(): List<Book> {
        val deleted = prefs.getStringSet("deleted_book_ids", emptySet()) ?: emptySet()
        val defaultBooks = loadInitialBooks().filterNot { it.id in deleted }
        val customBooks = loadCustomBooks().filterNot { it.id in deleted }
        return customBooks + defaultBooks
    }

    private fun saveCustomBooks(books: List<Book>) {
        try {
            val array = JSONArray()
            books.forEach { book ->
                val obj = JSONObject()
                obj.put("id", book.id)
                obj.put("title", book.title)
                obj.put("author", book.author)
                obj.put("coverUrl", book.coverUrl)
                obj.put("lastRead", book.lastRead)
                obj.put("progress", book.progress)
                obj.put("readTimeLeft", book.readTimeLeft)
                obj.put("currentChapter", book.currentChapter)
                obj.put("currentPage", book.currentPage)
                obj.put("scrollPos", book.scrollPos)

                val chaptersArray = JSONArray()
                book.chapters.forEach { chap ->
                    val chapObj = JSONObject()
                    chapObj.put("title", chap.title)
                    chapObj.put("subtitle", chap.subtitle)
                    chapObj.put("readTime", chap.readTime)
                    val parasArray = JSONArray()
                    chap.paragraphs.forEach { parasArray.put(it) }
                    chapObj.put("paragraphs", parasArray)
                    chaptersArray.put(chapObj)
                }
                obj.put("chapters", chaptersArray)
                array.put(obj)
            }
            File(context.filesDir, "custom_books.json").writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCustomBooks(): List<Book> {
        val file = File(context.filesDir, "custom_books.json")
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            val list = mutableListOf<Book>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val chaptersArray = obj.optJSONArray("chapters") ?: JSONArray()
                val chapters = mutableListOf<Chapter>()
                for (j in 0 until chaptersArray.length()) {
                    val chapObj = chaptersArray.getJSONObject(j)
                    val parasArray = chapObj.optJSONArray("paragraphs") ?: JSONArray()
                    val paras = mutableListOf<String>()
                    for (k in 0 until parasArray.length()) {
                        paras.add(parasArray.getString(k))
                    }
                    chapters.add(
                        Chapter(
                            title = chapObj.optString("title", "Chapter ${j + 1}"),
                            subtitle = chapObj.optString("subtitle", ""),
                            readTime = chapObj.optString("readTime", "10 mins"),
                            paragraphs = paras
                        )
                    )
                }
                var bookTitle = obj.getString("title")
                var bookAuthor = obj.getString("author")
                if (bookTitle.startsWith("Document:", ignoreCase = true)) {
                    val sample = chapters.take(3).flatMap { it.paragraphs }.joinToString(" ")
                    if (sample.contains("Nineteen Eighty-Four", ignoreCase = true) || sample.contains("1984")) {
                        bookTitle = "Nineteen Eighty-Four"
                        bookAuthor = "George Orwell"
                    }
                }
                var currentPart = ""
                val cleanedChapters = mutableListOf<Chapter>()
                for (chap in chapters) {
                    val cleanTitle = EpubParser.deduplicateRepeatedHeading(chap.title)
                    val rawParas = chap.paragraphs.map { p ->
                        if (p.startsWith("[IMG:") && p.endsWith("]")) p
                        else EpubParser.deduplicateRepeatedHeading(p.replace("\\s+".toRegex(), " ").trim())
                    }.filter { it.isNotBlank() }

                    // 1. Skip TOC chapter
                    if (cleanTitle.contains("Table of Contents", ignoreCase = true) ||
                        cleanTitle.contains("toc", ignoreCase = true)) {
                        continue
                    }
                    if (rawParas.size > 8) {
                        val headingCount = rawParas.count { p ->
                            p.startsWith("Chapter", ignoreCase = true) || p.startsWith("Part", ignoreCase = true)
                        }
                        if (headingCount > rawParas.size * 0.5) {
                            continue
                        }
                    }

                    // 2. Skip cover or empty part divider page
                    if (rawParas.size <= 1) {
                        val singleText = rawParas.firstOrNull() ?: ""
                        val singleTrimmed = singleText.trim()
                        if (singleTrimmed.length in 1..40 && "^(?i)(part|book|volume|section)\\s+\\w+".toRegex().matches(singleTrimmed)) {
                            currentPart = EpubParser.deduplicateRepeatedHeading(singleTrimmed)
                            continue
                        }
                        if (singleTrimmed.length in 1..30 && (singleTrimmed.contains("cover", ignoreCase = true) || singleTrimmed.equals(bookTitle, ignoreCase = true))) {
                            continue
                        }
                    }

                    // 3. Strip leading heading paragraphs
                    val dedupParas = rawParas.toMutableList()
                    while (dedupParas.isNotEmpty() && EpubParser.isHeadingOnly(dedupParas[0], cleanTitle, bookTitle, currentPart)) {
                        dedupParas.removeAt(0)
                    }

                    if (dedupParas.isEmpty()) continue

                    val finalSubtitle = if (currentPart.isNotBlank()) currentPart else chap.subtitle.ifBlank { "Section ${cleanedChapters.size + 1}" }

                    cleanedChapters.add(
                        chap.copy(
                            title = cleanTitle,
                            subtitle = finalSubtitle,
                            paragraphs = dedupParas
                        )
                    )
                }

                val rawCover = obj.optString("coverUrl", "")
                val finalCover = if (rawCover.isBlank() || rawCover.contains("unsplash")) {
                    if (bookTitle.contains("Nineteen Eighty-Four", ignoreCase = true) || bookTitle.contains("1984")) {
                        "res://cover_1984"
                    } else {
                        "res://cover_kafka"
                    }
                } else rawCover

                val maxChapter = maxOf(0, cleanedChapters.size - 1)
                val savedChapter = prefs.getInt("${id}_chapter", obj.optInt("currentChapter", 0)).coerceIn(0, maxChapter)

                list.add(
                    Book(
                        id = id,
                        title = bookTitle,
                        author = bookAuthor,
                        coverUrl = finalCover,
                        lastRead = obj.optString("lastRead", "Just added"),
                        progress = prefs.getInt("${id}_progress", obj.optInt("progress", 0)),
                        readTimeLeft = obj.optString("readTimeLeft", "10m left"),
                        currentChapter = savedChapter,
                        currentPage = prefs.getInt("${id}_page", obj.optInt("currentPage", 0)),
                        scrollPos = prefs.getInt("${id}_scroll", obj.optInt("scrollPos", 0)),
                        chapters = if (cleanedChapters.isNotEmpty()) cleanedChapters else chapters
                    )
                )
            }
            // Save back cleaned books if any were fixed
            saveCustomBooks(list)
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
            val initial = loadInitialBookmarks()
            initial.forEach { dbHelper.insertBookmark(it) }
            return initial
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
            if (list.isEmpty()) {
                val initial = loadInitialBookmarks()
                initial.forEach { dbHelper.insertBookmark(it) }
                initial
            } else list
        } catch (e: Exception) {
            val initial = loadInitialBookmarks()
            initial.forEach { dbHelper.insertBookmark(it) }
            initial
        }
    }

    private fun loadInitialBookmarks(): List<Bookmark> {
        return listOf(
            Bookmark(
                id = 1L,
                bookTitle = "The Metamorphosis",
                chapter = "Chapter I",
                quote = "One morning, when Gregor Samsa woke from troubled dreams, he found himself transformed in his bed into a horrible vermin.",
                color = HighlightColor.GOLD,
                timestamp = "1 hour ago"
            )
        )
    }

    private fun loadInitialBooks(): List<Book> {
        return listOf(
            Book(
                id = "book-kafka",
                title = "The Metamorphosis",
                author = "Franz Kafka",
                coverUrl = "res://cover_kafka",
                lastRead = "2m ago",
                progress = 35,
                readTimeLeft = "16m left",
                currentChapter = prefs.getInt("book-kafka_chapter", 0),
                currentPage = prefs.getInt("book-kafka_page", 0),
                scrollPos = prefs.getInt("book-kafka_scroll", 0),
                chapters = listOf(
                    Chapter(
                        title = "Chapter I",
                        subtitle = "The Transformation",
                        readTime = "18 mins",
                        paragraphs = listOf(
                            "One morning, when Gregor Samsa woke from troubled dreams, he found himself transformed in his bed into a horrible vermin. He lay on his armour-like back, and if he lifted his head a little he could see his brown belly, slightly domed and divided by arches into stiff sections.",
                            "The bedding was hardly able to cover it and seemed ready to slide off any moment. His many legs, pitifully thin compared with the size of the rest of him, waved about helplessly as he looked.",
                            "\"What's happened to me?\" he thought. It wasn't a dream. His room, a proper human room although a little too small, lay peacefully between its four familiar walls. A collection of textile samples lay spread out on the table—Samsa was a travelling salesman—and above it there hung a picture that he had recently cut out of an illustrated magazine and housed in a nice, gilded frame.",
                            "Gregor then turned to look out the window at the dull weather. Drops of rain could be heard hitting the pane, which made him feel quite sad. \"How about if I sleep a little bit longer and forget all this nonsense\", he thought, but that was something he was unable to do because he was used to sleeping on his right, and in his present state couldn't get into that position.",
                            "\"O God,\" he thought, \"what a grueling job I've picked! Day in, day out—on the road. The upset of doing business is much worse than the actual business in the home office, and, besides, I've got the torture of traveling, worrying about changing trains, eating miserable food at all hours, constantly seeing new faces, no relationships that last or get more intimate. To the devil with it all!\"",
                            "He felt a slight itching up on his belly; pushed himself slowly up on his back towards the headboard so that he could lift his head better; found where the itch was, and saw that it was covered with lots of little white spots which he didn't know what to make of."
                        )
                    ),
                    Chapter(
                        title = "Chapter II",
                        subtitle = "Family & Isolation",
                        readTime = "24 mins",
                        paragraphs = listOf(
                            "It was not until it was getting dark that evening that Gregor awoke from his deep and coma-like sleep. He would have woken soon anyway, he felt, even without any disturbance, as he was sufficiently rested, but it seemed to him that he had been awoken by the sound of hurried steps and a door being softly shut.",
                            "The light of the electric street lamps in the room lay pale here and there on the ceiling and on the upper parts of the furniture, but below, around Gregor, it was dark. He pushed himself over to the door slowly and clumsily, feeling his way with his antennae, which he now began to appreciate.",
                            "His left side seemed one big, unpleasantly tautening scar, and he had to limp along awkwardly on his two rows of legs. One of his little legs, in the course of the morning's events, had suffered serious injury—it was almost a miracle that only one had been injured—and dragged along lifelessly behind him."
                        )
                    )
                )
            ),
            Book(
                id = "book-alice",
                title = "Alice in Wonderland",
                author = "Lewis Carroll",
                coverUrl = "res://cover_alice",
                lastRead = "Yesterday",
                progress = 12,
                readTimeLeft = "42m left",
                currentChapter = prefs.getInt("book-alice_chapter", 0),
                currentPage = prefs.getInt("book-alice_page", 0),
                scrollPos = prefs.getInt("book-alice_scroll", 0),
                chapters = listOf(
                    Chapter(
                        title = "Chapter I",
                        subtitle = "Down the Rabbit-Hole",
                        readTime = "15 mins",
                        paragraphs = listOf(
                            "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, \"and what is the use of a book,\" thought Alice \"without pictures or conversation?\"",
                            "So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her."
                        )
                    )
                )
            ),
            Book(
                id = "book-artofwar",
                title = "The Art of War",
                author = "Sun Tzu",
                coverUrl = "res://cover_gatsby",
                lastRead = "3 days ago",
                progress = 60,
                readTimeLeft = "25m left",
                currentChapter = prefs.getInt("book-artofwar_chapter", 0),
                currentPage = prefs.getInt("book-artofwar_page", 0),
                scrollPos = prefs.getInt("book-artofwar_scroll", 0),
                chapters = listOf(
                    Chapter(
                        title = "Chapter I",
                        subtitle = "Laying Plans",
                        readTime = "12 mins",
                        paragraphs = listOf(
                            "Sun Tzu said: The art of war is of vital importance to the State. It is a matter of life and death, a road either to safety or to ruin. Hence it is a subject of inquiry which can on no account be neglected.",
                            "All warfare is based on deception. Hence, when able to attack, we must seem unable; when using our forces, we must seem inactive; when we are near, we must make the enemy believe we are far away."
                        )
                    )
                )
            )
        )
    }
}
