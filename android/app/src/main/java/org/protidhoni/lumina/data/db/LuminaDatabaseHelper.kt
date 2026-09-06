package org.protidhoni.lumina.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.protidhoni.lumina.model.Bookmark
import org.protidhoni.lumina.model.HighlightColor
import org.protidhoni.lumina.model.WishlistBook

class LuminaDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "lumina_reader.db"
        const val DATABASE_VERSION = 2

        // Bookmarks table
        const val TABLE_BOOKMARKS = "bookmarks"
        const val COL_BOOKMARK_ID = "id"
        const val COL_BOOK_TITLE = "book_title"
        const val COL_CHAPTER = "chapter"
        const val COL_QUOTE = "quote"
        const val COL_COLOR = "color"
        const val COL_BOOKMARK_NOTE = "note"
        const val COL_TIMESTAMP = "timestamp"

        // Wishlist table
        const val TABLE_WISHLIST = "wishlist"
        const val COL_WISHLIST_ID = "id"
        const val COL_WISHLIST_TITLE = "title"
        const val COL_WISHLIST_AUTHOR = "author"
        const val COL_WISHLIST_NOTE = "note"
        const val COL_WISHLIST_ADDED_AT = "added_at"

        // Reading List table
        const val TABLE_READING_LIST = "reading_list"
        const val COL_RL_BOOK_ID = "book_id"
        const val COL_RL_TITLE = "title"
        const val COL_RL_AUTHOR = "author"
        const val COL_RL_PROGRESS = "progress"
        const val COL_RL_TIME_LEFT = "read_time_left"
        const val COL_RL_LAST_READ = "last_read"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_BOOKMARKS (
                $COL_BOOKMARK_ID INTEGER PRIMARY KEY,
                $COL_BOOK_TITLE TEXT NOT NULL,
                $COL_CHAPTER TEXT NOT NULL,
                $COL_QUOTE TEXT NOT NULL,
                $COL_COLOR TEXT NOT NULL,
                $COL_BOOKMARK_NOTE TEXT DEFAULT '',
                $COL_TIMESTAMP TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_WISHLIST (
                $COL_WISHLIST_ID TEXT PRIMARY KEY,
                $COL_WISHLIST_TITLE TEXT NOT NULL,
                $COL_WISHLIST_AUTHOR TEXT,
                $COL_WISHLIST_NOTE TEXT,
                $COL_WISHLIST_ADDED_AT TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_READING_LIST (
                $COL_RL_BOOK_ID TEXT PRIMARY KEY,
                $COL_RL_TITLE TEXT NOT NULL,
                $COL_RL_AUTHOR TEXT,
                $COL_RL_PROGRESS INTEGER NOT NULL DEFAULT 0,
                $COL_RL_TIME_LEFT TEXT,
                $COL_RL_LAST_READ TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKMARKS ADD COLUMN $COL_BOOKMARK_NOTE TEXT DEFAULT ''")
            } catch (_: Exception) {}
        }
    }

    // --- Bookmarks CRUD ---

    fun insertBookmark(bookmark: Bookmark) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOKMARK_ID, bookmark.id)
            put(COL_BOOK_TITLE, bookmark.bookTitle)
            put(COL_CHAPTER, bookmark.chapter)
            put(COL_QUOTE, bookmark.quote)
            put(COL_COLOR, bookmark.color.name)
            put(COL_BOOKMARK_NOTE, bookmark.note)
            put(COL_TIMESTAMP, bookmark.timestamp)
        }
        db.insertWithOnConflict(TABLE_BOOKMARKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteBookmark(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_BOOKMARKS, "$COL_BOOKMARK_ID = ?", arrayOf(id.toString()))
    }

    fun getAllBookmarks(): List<Bookmark> {
        val list = mutableListOf<Bookmark>()
        val db = readableDatabase
        val cursor = db.query(TABLE_BOOKMARKS, null, null, null, null, null, "$COL_BOOKMARK_ID DESC")
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(COL_BOOKMARK_ID)
            val titleCol = it.getColumnIndexOrThrow(COL_BOOK_TITLE)
            val chapCol = it.getColumnIndexOrThrow(COL_CHAPTER)
            val quoteCol = it.getColumnIndexOrThrow(COL_QUOTE)
            val colorCol = it.getColumnIndexOrThrow(COL_COLOR)
            val noteCol = it.getColumnIndex(COL_BOOKMARK_NOTE)
            val timeCol = it.getColumnIndexOrThrow(COL_TIMESTAMP)

            while (it.moveToNext()) {
                val colorStr = it.getString(colorCol)
                val color = try { HighlightColor.valueOf(colorStr) } catch (_: Exception) { HighlightColor.GOLD }
                val note = if (noteCol >= 0) it.getString(noteCol) ?: "" else ""
                list.add(
                    Bookmark(
                        id = it.getLong(idCol),
                        bookTitle = it.getString(titleCol),
                        chapter = it.getString(chapCol),
                        quote = it.getString(quoteCol),
                        color = color,
                        note = note,
                        timestamp = it.getString(timeCol)
                    )
                )
            }
        }
        return list
    }

    // --- Wishlist CRUD ---

    fun insertWishlist(item: WishlistBook) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_WISHLIST_ID, item.id)
            put(COL_WISHLIST_TITLE, item.title)
            put(COL_WISHLIST_AUTHOR, item.author)
            put(COL_WISHLIST_NOTE, item.note)
            put(COL_WISHLIST_ADDED_AT, item.addedAt)
        }
        db.insertWithOnConflict(TABLE_WISHLIST, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWishlist(id: String) {
        val db = writableDatabase
        db.delete(TABLE_WISHLIST, "$COL_WISHLIST_ID = ?", arrayOf(id))
    }

    fun getAllWishlist(): List<WishlistBook> {
        val list = mutableListOf<WishlistBook>()
        val db = readableDatabase
        val cursor = db.query(TABLE_WISHLIST, null, null, null, null, null, "$COL_WISHLIST_ADDED_AT DESC")
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(COL_WISHLIST_ID)
            val titleCol = it.getColumnIndexOrThrow(COL_WISHLIST_TITLE)
            val authorCol = it.getColumnIndexOrThrow(COL_WISHLIST_AUTHOR)
            val noteCol = it.getColumnIndexOrThrow(COL_WISHLIST_NOTE)
            val addedCol = it.getColumnIndexOrThrow(COL_WISHLIST_ADDED_AT)

            while (it.moveToNext()) {
                list.add(
                    WishlistBook(
                        id = it.getString(idCol),
                        title = it.getString(titleCol),
                        author = it.getString(authorCol) ?: "",
                        note = it.getString(noteCol) ?: "",
                        addedAt = it.getString(addedCol) ?: "Recently"
                    )
                )
            }
        }
        return list
    }

    // --- Reading Progress CRUD ---

    fun updateReadingProgress(bookId: String, title: String, author: String, progress: Int, readTimeLeft: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_RL_BOOK_ID, bookId)
            put(COL_RL_TITLE, title)
            put(COL_RL_AUTHOR, author)
            put(COL_RL_PROGRESS, progress)
            put(COL_RL_TIME_LEFT, readTimeLeft)
            put(COL_RL_LAST_READ, "Today")
        }
        db.insertWithOnConflict(TABLE_READING_LIST, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- Share Formatting Helpers ---

    fun formatHighlightsForShare(bookTitleFilter: String? = null): String {
        val all = getAllBookmarks()
        val filtered = if (!bookTitleFilter.isNullOrBlank()) {
            all.filter { it.bookTitle.equals(bookTitleFilter, ignoreCase = true) }
        } else {
            all
        }

        if (filtered.isEmpty()) {
            return "No highlights saved yet in Lumina Reader."
        }

        val sb = StringBuilder()
        val title = bookTitleFilter ?: "Lumina Reading Highlights"
        sb.appendLine("📖 $title")
        sb.appendLine("======================================")
        sb.appendLine()

        filtered.groupBy { it.bookTitle }.forEach { (book, marks) ->
            if (bookTitleFilter == null) {
                sb.appendLine("## $book")
            }
            marks.forEach { mark ->
                sb.appendLine("“${mark.quote}”")
                if (mark.note.isNotBlank()) {
                    sb.appendLine("Note: ${mark.note}")
                }
                sb.appendLine("— ${mark.chapter} (${mark.color.displayName} highlight • ${mark.timestamp})")
                sb.appendLine()
            }
        }
        sb.appendLine("Shared from Lumina Reader")
        return sb.toString().trim()
    }

    fun formatReadingListForShare(): String {
        val db = readableDatabase
        val cursor = db.query(TABLE_READING_LIST, null, null, null, null, null, "$COL_RL_PROGRESS DESC")
        val sb = StringBuilder()
        sb.appendLine("📚 My Lumina Reading List")
        sb.appendLine("======================================")
        sb.appendLine()

        var hasBooks = false
        cursor.use {
            val titleCol = it.getColumnIndexOrThrow(COL_RL_TITLE)
            val authorCol = it.getColumnIndexOrThrow(COL_RL_AUTHOR)
            val progCol = it.getColumnIndexOrThrow(COL_RL_PROGRESS)
            val timeCol = it.getColumnIndexOrThrow(COL_RL_TIME_LEFT)

            while (it.moveToNext()) {
                hasBooks = true
                val title = it.getString(titleCol)
                val author = it.getString(authorCol)
                val prog = it.getInt(progCol)
                val time = it.getString(timeCol)
                sb.appendLine("• $title by $author — $prog% completed ($time)")
            }
        }

        val wishlist = getAllWishlist()
        if (wishlist.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("✨ Want to Read (Wishlist):")
            wishlist.forEach {
                sb.appendLine("• ${it.title}${if (it.author.isNotBlank()) " by ${it.author}" else ""}${if (it.note.isNotBlank()) " [Note: ${it.note}]" else ""}")
            }
        }

        if (!hasBooks && wishlist.isEmpty()) {
            return "My Lumina Reading List is currently empty."
        }

        sb.appendLine()
        sb.appendLine("Shared from Lumina Reader")
        return sb.toString().trim()
    }
}
