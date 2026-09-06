package org.protidhoni.lumina.data

import android.content.Context
import android.content.SharedPreferences
import org.protidhoni.lumina.model.Book
import org.protidhoni.lumina.model.Bookmark
import org.protidhoni.lumina.model.Chapter
import org.protidhoni.lumina.model.HighlightColor
import org.protidhoni.lumina.model.ReadingMode
import org.protidhoni.lumina.model.ThemeMode
import org.protidhoni.lumina.model.TypefaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class BookRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lumina_reader_prefs", Context.MODE_PRIVATE)

    private val _books = MutableStateFlow<List<Book>>(loadAllBooks())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _activeBookId = MutableStateFlow(prefs.getString("active_book_id", "book-kafka") ?: "book-kafka")
    val activeBookId: StateFlow<String> = _activeBookId.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(loadPersistedBookmarks())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

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
    }

    fun setTypefaceMode(typeface: TypefaceMode) {
        _typefaceMode.value = typeface
        prefs.edit().putString("typeface_mode", typeface.name).apply()
    }

    fun setLineHeight(multiplier: Float) {
        _lineHeightMultiplier.value = multiplier
        prefs.edit().putFloat("line_height", multiplier).apply()
    }

    fun setLastTab(tab: String) {
        prefs.edit().putString("last_screen_tab", tab).apply()
    }

    fun getLastTab(): String {
        return prefs.getString("last_screen_tab", "LIBRARY") ?: "LIBRARY"
    }

    fun addBookmark(quote: String, color: HighlightColor = HighlightColor.GOLD) {
        val book = getActiveBook()
        val chapter = book.chapters.getOrNull(book.currentChapter)?.title ?: "Chapter"
        val mark = Bookmark(
            bookTitle = book.title,
            chapter = chapter,
            quote = quote,
            color = color,
            timestamp = "Just now"
        )
        val updated = listOf(mark) + _bookmarks.value
        _bookmarks.value = updated
        saveBookmarks(updated)
    }

    fun removeBookmark(id: Long) {
        val updated = _bookmarks.value.filterNot { it.id == id }
        _bookmarks.value = updated
        saveBookmarks(updated)
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
                val cleanedChapters = chapters.map { chap ->
                    chap.copy(
                        paragraphs = chap.paragraphs.map { p ->
                            if (p.startsWith("[IMG:") && p.endsWith("]")) p
                            else p.replace("\\s+".toRegex(), " ").trim()
                        }.filter { it.isNotBlank() }
                    )
                }
                list.add(
                    Book(
                        id = id,
                        title = bookTitle,
                        author = bookAuthor,
                        coverUrl = obj.optString("coverUrl", "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80"),
                        lastRead = obj.optString("lastRead", "Just added"),
                        progress = prefs.getInt("${id}_progress", obj.optInt("progress", 0)),
                        readTimeLeft = obj.optString("readTimeLeft", "10m left"),
                        currentChapter = prefs.getInt("${id}_chapter", obj.optInt("currentChapter", 0)),
                        currentPage = prefs.getInt("${id}_page", obj.optInt("currentPage", 0)),
                        scrollPos = prefs.getInt("${id}_scroll", obj.optInt("scrollPos", 0)),
                        chapters = cleanedChapters
                    )
                )
            }
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
                obj.put("timestamp", bm.timestamp)
                array.put(obj)
            }
            File(context.filesDir, "custom_bookmarks.json").writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPersistedBookmarks(): List<Bookmark> {
        val file = File(context.filesDir, "custom_bookmarks.json")
        if (!file.exists()) return loadInitialBookmarks()
        return try {
            val array = JSONArray(file.readText())
            val list = mutableListOf<Bookmark>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Bookmark(
                        id = obj.getLong("id"),
                        bookTitle = obj.getString("bookTitle"),
                        chapter = obj.getString("chapter"),
                        quote = obj.getString("quote"),
                        color = try { HighlightColor.valueOf(obj.getString("color")) } catch (_: Exception) { HighlightColor.GOLD },
                        timestamp = obj.optString("timestamp", "Just now")
                    )
                )
            }
            if (list.isEmpty()) loadInitialBookmarks() else list
        } catch (e: Exception) {
            loadInitialBookmarks()
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
                coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80",
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
                coverUrl = "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=400&q=80",
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
                coverUrl = "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?auto=format&fit=crop&w=400&q=80",
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
