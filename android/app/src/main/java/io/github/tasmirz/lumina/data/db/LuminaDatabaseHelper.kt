package io.github.tasmirz.lumina.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.BookCharacter
import io.github.tasmirz.lumina.model.BookLore
import io.github.tasmirz.lumina.model.Bookmark
import io.github.tasmirz.lumina.model.Chapter
import io.github.tasmirz.lumina.model.CustomCatalogEndpoint
import io.github.tasmirz.lumina.model.CustomTextureData
import io.github.tasmirz.lumina.model.CustomThemeData
import io.github.tasmirz.lumina.model.HighlightColor
import io.github.tasmirz.lumina.model.SceneMatch
import io.github.tasmirz.lumina.model.WishlistBook
import org.json.JSONArray
import org.json.JSONObject

class LuminaDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "lumina_reader.db"
        const val DATABASE_VERSION = 15

        // Custom Textures table
        const val TABLE_CUSTOM_TEXTURES = "custom_textures"
        const val COL_TEX_ID = "id"
        const val COL_TEX_NAME = "name"
        const val COL_TEX_IMAGE_PATH = "image_path"
        const val COL_TEX_IS_TILED = "is_tiled"
        const val COL_TEX_OPACITY = "opacity"
        const val COL_TEX_CREATED_AT = "created_at"

        // Custom Catalog Endpoints table
        const val TABLE_CUSTOM_ENDPOINTS = "custom_endpoints"
        const val COL_EP_ID = "id"
        const val COL_EP_NAME = "name"
        const val COL_EP_GALLERY_URL = "gallery_url"
        const val COL_EP_SEARCH_URL = "search_url"
        const val COL_EP_API_KEY = "api_key"
        const val COL_EP_AUTH_HEADER = "auth_header"
        const val COL_EP_IS_ENABLED = "is_enabled"

        // Normalized Chapters table (stores chunked chapter content per book)
        const val TABLE_CHAPTERS = "book_chapters"
        const val COL_CHAP_ID = "id"
        const val COL_CHAP_BOOK_ID = "book_id"
        const val COL_CHAP_INDEX = "chapter_index"
        const val COL_CHAP_TITLE = "title"
        const val COL_CHAP_SUBTITLE = "subtitle"
        const val COL_CHAP_READ_TIME = "read_time"
        const val COL_CHAP_PARAS_JSON = "paragraphs_json"

        // Page Cache table (stores precomputed layout pagination sets)
        const val TABLE_PAGE_CACHE = "page_cache"
        const val COL_PC_KEY = "cache_key"
        const val COL_PC_BOOK_ID = "book_id"
        const val COL_PC_CHAP_INDEX = "chapter_index"
        const val COL_PC_PAGES_JSON = "pages_json"
        const val COL_PC_CREATED_AT = "created_at"

        // Characters table
        const val TABLE_CHARACTERS = "book_characters"
        const val COL_CHAR_ID = "id"
        const val COL_CHAR_BOOK_ID = "book_id"
        const val COL_CHAR_NAME = "name"
        const val COL_CHAR_ROLE = "role"
        const val COL_CHAR_FIRST_SEEN = "first_appearance"
        const val COL_CHAR_SUMMARY = "summary"
        const val COL_CHAR_EVENTS = "key_events"
        const val COL_CHAR_IS_SPOILER = "is_spoiler"
        const val COL_CHAR_CREATED_AT = "created_at"

        // Lore table
        const val TABLE_LORE = "book_lore"
        const val COL_LORE_ID = "id"
        const val COL_LORE_BOOK_ID = "book_id"
        const val COL_LORE_TITLE = "title"
        const val COL_LORE_CATEGORY = "category"
        const val COL_LORE_FIRST_SEEN = "first_appearance"
        const val COL_LORE_DESCRIPTION = "description"
        const val COL_LORE_KEY_FACTS = "key_facts"
        const val COL_LORE_IS_SPOILER = "is_spoiler"
        const val COL_LORE_CREATED_AT = "created_at"

        // App Settings table (persists user settings directly into SQLite)
        const val TABLE_SETTINGS = "app_settings"
        const val COL_SETTING_KEY = "setting_key"
        const val COL_SETTING_VALUE = "setting_value"

        // Books table (persists imported & downloaded books metadata, local file path & chapters)
        const val TABLE_BOOKS = "books"
        const val COL_BOOK_ID = "id"
        const val COL_BOOK_TITLE_MAIN = "title"
        const val COL_BOOK_AUTHOR = "author"
        const val COL_BOOK_COVER = "cover_url"
        const val COL_BOOK_FILE_PATH = "file_path"
        const val COL_BOOK_LAST_READ = "last_read"
        const val COL_BOOK_PROGRESS = "progress"
        const val COL_BOOK_TIME_LEFT = "read_time_left"
        const val COL_BOOK_CURRENT_CHAPTER = "current_chapter"
        const val COL_BOOK_CURRENT_PAGE = "current_page"
        const val COL_BOOK_SCROLL_POS = "scroll_pos"
        const val COL_BOOK_CHAPTERS_JSON = "chapters_json"
        const val COL_BOOK_IS_DOWNLOADED = "is_downloaded"
        const val COL_BOOK_DOWNLOAD_URL = "download_url"
        const val COL_BOOK_FILE_SIZE = "file_size"
        const val COL_BOOK_ADDED_AT = "added_at"
        const val COL_BOOK_CHAR_CHECKPOINT_CHAPTER = "char_checkpoint_chapter"
        const val COL_BOOK_CHAR_CHECKPOINT_PAGE = "char_checkpoint_page"
        const val COL_BOOK_LANGUAGE = "language"

        // Bookmarks table
        const val TABLE_BOOKMARKS = "bookmarks"
        const val COL_BOOKMARK_ID = "id"
        const val COL_BOOK_TITLE = "book_title"
        const val COL_CHAPTER = "chapter"
        const val COL_QUOTE = "quote"
        const val COL_COLOR = "color"
        const val COL_BOOKMARK_NOTE = "note"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_BOOKMARK_PAGE = "page_number"
        const val COL_IS_HIGHLIGHT = "is_highlight"
        const val COL_IS_LAST_READ = "is_last_read"

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

        // FTS5 Virtual Table for full-text search
        const val TABLE_BOOK_FTS = "book_fts"
        const val COL_FTS_BOOK_ID = "book_id"
        const val COL_FTS_CHAPTER_INDEX = "chapter_index"
        const val COL_FTS_CHAPTER_TITLE = "chapter_title"
        const val COL_FTS_PARAGRAPH_INDEX = "paragraph_index"
        const val COL_FTS_CONTENT = "content"

        // Indexed Books table (tracks which books have had FTS5 indexing completed)
        const val TABLE_INDEXED_BOOKS = "indexed_books"
        const val COL_IB_BOOK_ID = "book_id"
        const val COL_IB_INDEXED_AT = "indexed_at"
        const val COL_IB_PARAGRAPHS_COUNT = "paragraphs_count"

        // Completed Books table
        const val TABLE_COMPLETED_BOOKS = "completed_books"
        const val COL_CB_BOOK_ID = "book_id"
        const val COL_CB_COMPLETED_AT = "completed_at"

        // Custom Themes table
        const val TABLE_CUSTOM_THEMES = "custom_themes"
        const val COL_CT_ID = "id"
        const val COL_CT_NAME = "name"
        const val COL_CT_BG = "bg_color"
        const val COL_CT_TEXT = "text_color"
        const val COL_CT_ACCENT = "accent_color"
        const val COL_CT_CREATED_AT = "created_at"

        // In-memory cache for parsed Chapter objects to avoid expensive repeated JSON deserialization
        private val chaptersCache = java.util.concurrent.ConcurrentHashMap<String, List<Chapter>>()
    }

    private fun ensureBookFtsTableExists(db: SQLiteDatabase) {
        try {
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_BOOK_FTS USING fts5(
                    $COL_FTS_BOOK_ID UNINDEXED,
                    $COL_FTS_CHAPTER_INDEX UNINDEXED,
                    $COL_FTS_CHAPTER_TITLE UNINDEXED,
                    $COL_FTS_PARAGRAPH_INDEX UNINDEXED,
                    $COL_FTS_CONTENT
                )
            """.trimIndent())
        } catch (_: Exception) {
            try {
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_BOOK_FTS USING fts4(
                        $COL_FTS_BOOK_ID,
                        $COL_FTS_CHAPTER_INDEX,
                        $COL_FTS_CHAPTER_TITLE,
                        $COL_FTS_PARAGRAPH_INDEX,
                        $COL_FTS_CONTENT
                    )
                """.trimIndent())
            } catch (_: Exception) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS $TABLE_BOOK_FTS (
                            $COL_FTS_BOOK_ID TEXT,
                            $COL_FTS_CHAPTER_INDEX INTEGER,
                            $COL_FTS_CHAPTER_TITLE TEXT,
                            $COL_FTS_PARAGRAPH_INDEX INTEGER,
                            $COL_FTS_CONTENT TEXT
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_fts_id ON $TABLE_BOOK_FTS($COL_FTS_BOOK_ID)")
                } catch (_: Exception) {}
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
        } catch (_: Exception) {}
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_BOOKS (
                $COL_BOOK_ID TEXT PRIMARY KEY,
                $COL_BOOK_TITLE_MAIN TEXT NOT NULL,
                $COL_BOOK_AUTHOR TEXT NOT NULL,
                $COL_BOOK_COVER TEXT,
                $COL_BOOK_FILE_PATH TEXT,
                $COL_BOOK_LAST_READ TEXT,
                $COL_BOOK_PROGRESS INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_TIME_LEFT TEXT,
                $COL_BOOK_CURRENT_CHAPTER INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_CURRENT_PAGE INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_SCROLL_POS INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_CHAPTERS_JSON TEXT NOT NULL,
                $COL_BOOK_IS_DOWNLOADED INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_DOWNLOAD_URL TEXT,
                $COL_BOOK_FILE_SIZE INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_ADDED_AT INTEGER NOT NULL,
                $COL_BOOK_CHAR_CHECKPOINT_CHAPTER INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_CHAR_CHECKPOINT_PAGE INTEGER NOT NULL DEFAULT 0,
                $COL_BOOK_LANGUAGE TEXT NOT NULL DEFAULT 'en'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_BOOKMARKS (
                $COL_BOOKMARK_ID INTEGER PRIMARY KEY,
                $COL_BOOK_TITLE TEXT NOT NULL,
                $COL_CHAPTER TEXT NOT NULL,
                $COL_QUOTE TEXT NOT NULL,
                $COL_COLOR TEXT NOT NULL,
                $COL_BOOKMARK_NOTE TEXT DEFAULT '',
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_BOOKMARK_PAGE INTEGER DEFAULT 0,
                $COL_IS_HIGHLIGHT INTEGER DEFAULT 0,
                $COL_IS_LAST_READ INTEGER DEFAULT 0
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

        try {
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_BOOK_FTS USING fts5(
                    $COL_FTS_BOOK_ID UNINDEXED,
                    $COL_FTS_CHAPTER_INDEX UNINDEXED,
                    $COL_FTS_CHAPTER_TITLE UNINDEXED,
                    $COL_FTS_PARAGRAPH_INDEX UNINDEXED,
                    $COL_FTS_CONTENT
                )
            """.trimIndent())
        } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_COMPLETED_BOOKS (
                $COL_CB_BOOK_ID TEXT PRIMARY KEY,
                $COL_CB_COMPLETED_AT TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_THEMES (
                $COL_CT_ID TEXT PRIMARY KEY,
                $COL_CT_NAME TEXT NOT NULL,
                $COL_CT_BG INTEGER NOT NULL,
                $COL_CT_TEXT INTEGER NOT NULL,
                $COL_CT_ACCENT INTEGER NOT NULL,
                $COL_CT_CREATED_AT TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_TEXTURES (
                $COL_TEX_ID TEXT PRIMARY KEY,
                $COL_TEX_NAME TEXT NOT NULL,
                $COL_TEX_IMAGE_PATH TEXT NOT NULL,
                $COL_TEX_IS_TILED INTEGER NOT NULL DEFAULT 1,
                $COL_TEX_OPACITY REAL NOT NULL DEFAULT 0.5,
                $COL_TEX_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                $COL_SETTING_KEY TEXT PRIMARY KEY,
                $COL_SETTING_VALUE TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CHARACTERS (
                $COL_CHAR_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CHAR_BOOK_ID TEXT NOT NULL,
                $COL_CHAR_NAME TEXT NOT NULL,
                $COL_CHAR_ROLE TEXT NOT NULL,
                $COL_CHAR_FIRST_SEEN TEXT DEFAULT '',
                $COL_CHAR_SUMMARY TEXT NOT NULL,
                $COL_CHAR_EVENTS TEXT DEFAULT '',
                $COL_CHAR_IS_SPOILER INTEGER NOT NULL DEFAULT 0,
                $COL_CHAR_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_LORE (
                $COL_LORE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LORE_BOOK_ID TEXT NOT NULL,
                $COL_LORE_TITLE TEXT NOT NULL,
                $COL_LORE_CATEGORY TEXT NOT NULL,
                $COL_LORE_FIRST_SEEN TEXT DEFAULT '',
                $COL_LORE_DESCRIPTION TEXT NOT NULL,
                $COL_LORE_KEY_FACTS TEXT DEFAULT '',
                $COL_LORE_IS_SPOILER INTEGER NOT NULL DEFAULT 0,
                $COL_LORE_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_INDEXED_BOOKS (
                $COL_IB_BOOK_ID TEXT PRIMARY KEY,
                $COL_IB_INDEXED_AT INTEGER NOT NULL,
                $COL_IB_PARAGRAPHS_COUNT INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CHAPTERS (
                $COL_CHAP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CHAP_BOOK_ID TEXT NOT NULL,
                $COL_CHAP_INDEX INTEGER NOT NULL,
                $COL_CHAP_TITLE TEXT NOT NULL,
                $COL_CHAP_SUBTITLE TEXT DEFAULT '',
                $COL_CHAP_READ_TIME TEXT DEFAULT '15 mins',
                $COL_CHAP_PARAS_JSON TEXT NOT NULL
            )
        """.trimIndent())
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_book_chapters ON $TABLE_CHAPTERS ($COL_CHAP_BOOK_ID, $COL_CHAP_INDEX)")
        } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_PAGE_CACHE (
                $COL_PC_KEY TEXT NOT NULL,
                $COL_PC_CHAP_INDEX INTEGER NOT NULL,
                $COL_PC_BOOK_ID TEXT NOT NULL,
                $COL_PC_PAGES_JSON TEXT NOT NULL,
                $COL_PC_CREATED_AT INTEGER NOT NULL,
                PRIMARY KEY ($COL_PC_KEY, $COL_PC_CHAP_INDEX)
            )
        """.trimIndent())
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_page_cache_book ON $TABLE_PAGE_CACHE ($COL_PC_BOOK_ID)")
        } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_ENDPOINTS (
                $COL_EP_ID TEXT PRIMARY KEY,
                $COL_EP_NAME TEXT NOT NULL,
                $COL_EP_GALLERY_URL TEXT DEFAULT '',
                $COL_EP_SEARCH_URL TEXT DEFAULT '',
                $COL_EP_API_KEY TEXT DEFAULT '',
                $COL_EP_AUTH_HEADER TEXT DEFAULT 'Authorization',
                $COL_EP_IS_ENABLED INTEGER DEFAULT 1
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKMARKS ADD COLUMN $COL_BOOKMARK_NOTE TEXT DEFAULT ''")
            } catch (_: Exception) {}
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_BOOK_FTS USING fts5(
                        $COL_FTS_BOOK_ID UNINDEXED,
                        $COL_FTS_CHAPTER_INDEX UNINDEXED,
                        $COL_FTS_CHAPTER_TITLE UNINDEXED,
                        $COL_FTS_PARAGRAPH_INDEX UNINDEXED,
                        $COL_FTS_CONTENT
                    )
                """.trimIndent())
            } catch (_: Exception) {}

            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_COMPLETED_BOOKS (
                        $COL_CB_BOOK_ID TEXT PRIMARY KEY,
                        $COL_CB_COMPLETED_AT TEXT NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}

            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_THEMES (
                        $COL_CT_ID TEXT PRIMARY KEY,
                        $COL_CT_NAME TEXT NOT NULL,
                        $COL_CT_BG INTEGER NOT NULL,
                        $COL_CT_TEXT INTEGER NOT NULL,
                        $COL_CT_ACCENT INTEGER NOT NULL,
                        $COL_CT_CREATED_AT TEXT NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_BOOKS (
                        $COL_BOOK_ID TEXT PRIMARY KEY,
                        $COL_BOOK_TITLE_MAIN TEXT NOT NULL,
                        $COL_BOOK_AUTHOR TEXT NOT NULL,
                        $COL_BOOK_COVER TEXT,
                        $COL_BOOK_FILE_PATH TEXT,
                        $COL_BOOK_LAST_READ TEXT,
                        $COL_BOOK_PROGRESS INTEGER NOT NULL DEFAULT 0,
                        $COL_BOOK_TIME_LEFT TEXT,
                        $COL_BOOK_CURRENT_CHAPTER INTEGER NOT NULL DEFAULT 0,
                        $COL_BOOK_CURRENT_PAGE INTEGER NOT NULL DEFAULT 0,
                        $COL_BOOK_SCROLL_POS INTEGER NOT NULL DEFAULT 0,
                        $COL_BOOK_CHAPTERS_JSON TEXT NOT NULL,
                        $COL_BOOK_IS_DOWNLOADED INTEGER NOT NULL DEFAULT 0,
                        $COL_BOOK_DOWNLOAD_URL TEXT,
                        $COL_BOOK_FILE_SIZE INTEGER NOT NULL DEFAULT 0,
                        $COL_BOOK_ADDED_AT INTEGER NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                        $COL_SETTING_KEY TEXT PRIMARY KEY,
                        $COL_SETTING_VALUE TEXT NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 6) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_CHARACTERS (
                        $COL_CHAR_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_CHAR_BOOK_ID TEXT NOT NULL,
                        $COL_CHAR_NAME TEXT NOT NULL,
                        $COL_CHAR_ROLE TEXT NOT NULL,
                        $COL_CHAR_FIRST_SEEN TEXT DEFAULT '',
                        $COL_CHAR_SUMMARY TEXT NOT NULL,
                        $COL_CHAR_EVENTS TEXT DEFAULT '',
                        $COL_CHAR_IS_SPOILER INTEGER NOT NULL DEFAULT 0,
                        $COL_CHAR_CREATED_AT INTEGER NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKMARKS ADD COLUMN $COL_BOOKMARK_PAGE INTEGER DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COL_BOOK_CHAR_CHECKPOINT_CHAPTER INTEGER DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COL_BOOK_CHAR_CHECKPOINT_PAGE INTEGER DEFAULT 0")
            } catch (_: Exception) {}
        }
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKS ADD COLUMN $COL_BOOK_LANGUAGE TEXT DEFAULT 'en'")
            } catch (_: Exception) {}
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_INDEXED_BOOKS (
                        $COL_IB_BOOK_ID TEXT PRIMARY KEY,
                        $COL_IB_INDEXED_AT INTEGER NOT NULL,
                        $COL_IB_PARAGRAPHS_COUNT INTEGER NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 9) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_LORE (
                        $COL_LORE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_LORE_BOOK_ID TEXT NOT NULL,
                        $COL_LORE_TITLE TEXT NOT NULL,
                        $COL_LORE_CATEGORY TEXT NOT NULL,
                        $COL_LORE_FIRST_SEEN TEXT DEFAULT '',
                        $COL_LORE_DESCRIPTION TEXT NOT NULL,
                        $COL_LORE_KEY_FACTS TEXT DEFAULT '',
                        $COL_LORE_IS_SPOILER INTEGER NOT NULL DEFAULT 0,
                        $COL_LORE_CREATED_AT INTEGER NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 10) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_CHAPTERS (
                        $COL_CHAP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_CHAP_BOOK_ID TEXT NOT NULL,
                        $COL_CHAP_INDEX INTEGER NOT NULL,
                        $COL_CHAP_TITLE TEXT NOT NULL,
                        $COL_CHAP_SUBTITLE TEXT DEFAULT '',
                        $COL_CHAP_READ_TIME TEXT DEFAULT '15 mins',
                        $COL_CHAP_PARAS_JSON TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_book_chapters ON $TABLE_CHAPTERS ($COL_CHAP_BOOK_ID, $COL_CHAP_INDEX)")
            } catch (_: Exception) {}

            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_PAGE_CACHE (
                        $COL_PC_KEY TEXT PRIMARY KEY,
                        $COL_PC_BOOK_ID TEXT NOT NULL,
                        $COL_PC_CHAP_INDEX INTEGER NOT NULL,
                        $COL_PC_PAGES_JSON TEXT NOT NULL,
                        $COL_PC_CREATED_AT INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_page_cache_book ON $TABLE_PAGE_CACHE ($COL_PC_BOOK_ID)")
            } catch (_: Exception) {}

            // Migrate legacy chapters stored in books.chapters_json to book_chapters
            try {
                val cursor = db.rawQuery("SELECT $COL_BOOK_ID, $COL_BOOK_CHAPTERS_JSON FROM $TABLE_BOOKS WHERE $COL_BOOK_CHAPTERS_JSON IS NOT NULL AND $COL_BOOK_CHAPTERS_JSON != ''", null)
                cursor.use {
                    while (it.moveToNext()) {
                        val bookId = it.getString(0)
                        val json = it.getString(1)
                        if (!json.isNullOrBlank()) {
                            val chaps = deserializeChapters(json)
                            for (idx in chaps.indices) {
                                val chap = chaps[idx]
                                val cv = ContentValues().apply {
                                    put(COL_CHAP_BOOK_ID, bookId)
                                    put(COL_CHAP_INDEX, idx)
                                    put(COL_CHAP_TITLE, chap.title)
                                    put(COL_CHAP_SUBTITLE, chap.subtitle)
                                    put(COL_CHAP_READ_TIME, chap.readTime)
                                    put(COL_CHAP_PARAS_JSON, serializeParagraphs(chap.paragraphs))
                                }
                                db.insertWithOnConflict(TABLE_CHAPTERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                            }
                        }
                    }
                }
                // Clear out large chapters JSON from books table to reclaim storage and memory
                db.execSQL("UPDATE $TABLE_BOOKS SET $COL_BOOK_CHAPTERS_JSON = ''")
            } catch (_: Exception) {}
        }
        if (oldVersion < 11) {
            try {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_PAGE_CACHE")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_PAGE_CACHE (
                        $COL_PC_KEY TEXT NOT NULL,
                        $COL_PC_CHAP_INDEX INTEGER NOT NULL,
                        $COL_PC_BOOK_ID TEXT NOT NULL,
                        $COL_PC_PAGES_JSON TEXT NOT NULL,
                        $COL_PC_CREATED_AT INTEGER NOT NULL,
                        PRIMARY KEY ($COL_PC_KEY, $COL_PC_CHAP_INDEX)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_page_cache_book ON $TABLE_PAGE_CACHE ($COL_PC_BOOK_ID)")
            } catch (_: Exception) {}
        }
        if (oldVersion < 12) {
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKMARKS ADD COLUMN $COL_IS_HIGHLIGHT INTEGER DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_BOOKMARKS ADD COLUMN $COL_IS_LAST_READ INTEGER DEFAULT 0")
            } catch (_: Exception) {}
        }
        if (oldVersion < 13) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_ENDPOINTS (
                        $COL_EP_ID TEXT PRIMARY KEY,
                        $COL_EP_NAME TEXT NOT NULL,
                        $COL_EP_GALLERY_URL TEXT DEFAULT '',
                        $COL_EP_SEARCH_URL TEXT DEFAULT '',
                        $COL_EP_API_KEY TEXT DEFAULT '',
                        $COL_EP_AUTH_HEADER TEXT DEFAULT 'Authorization',
                        $COL_EP_IS_ENABLED INTEGER DEFAULT 1
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 14) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_TEXTURES (
                        $COL_TEX_ID TEXT PRIMARY KEY,
                        $COL_TEX_NAME TEXT NOT NULL,
                        $COL_TEX_IMAGE_PATH TEXT NOT NULL,
                        $COL_TEX_IS_TILED INTEGER NOT NULL DEFAULT 1,
                        $COL_TEX_OPACITY REAL NOT NULL DEFAULT 0.5,
                        $COL_TEX_CREATED_AT INTEGER NOT NULL
                    )
                """.trimIndent())
            } catch (_: Exception) {}
        }
        if (oldVersion < 15) {
            try {
                // Purge stale accumulated page cache entries to reclaim tens of megabytes of storage
                db.execSQL("DELETE FROM $TABLE_PAGE_CACHE")
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
            put(COL_BOOKMARK_PAGE, bookmark.pageNumber)
            put(COL_IS_HIGHLIGHT, if (bookmark.isHighlight) 1 else 0)
            put(COL_IS_LAST_READ, if (bookmark.isLastRead) 1 else 0)
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
            val pageCol = it.getColumnIndex(COL_BOOKMARK_PAGE)
            val isHighlightCol = it.getColumnIndex(COL_IS_HIGHLIGHT)
            val isLastReadCol = it.getColumnIndex(COL_IS_LAST_READ)

            while (it.moveToNext()) {
                val colorStr = it.getString(colorCol)
                val color = try { HighlightColor.valueOf(colorStr) } catch (_: Exception) { HighlightColor.GOLD }
                val note = if (noteCol >= 0) it.getString(noteCol) ?: "" else ""
                val pageNumber = if (pageCol >= 0) it.getInt(pageCol) else 0
                val isHighlight = if (isHighlightCol >= 0) it.getInt(isHighlightCol) == 1 else false
                val isLastRead = if (isLastReadCol >= 0) it.getInt(isLastReadCol) == 1 else false
                list.add(
                    Bookmark(
                        id = it.getLong(idCol),
                        bookTitle = it.getString(titleCol),
                        chapter = it.getString(chapCol),
                        quote = it.getString(quoteCol),
                        color = color,
                        note = note,
                        timestamp = it.getString(timeCol),
                        pageNumber = pageNumber,
                        isHighlight = isHighlight,
                        isLastRead = isLastRead
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

    // --- Book FTS5 Scene Search ---

    fun indexEntireBook(bookId: String, chapters: List<io.github.tasmirz.lumina.model.Chapter>): Int {
        val db = writableDatabase
        ensureBookFtsTableExists(db)
        var totalParas = 0
        try {
            db.delete(TABLE_BOOK_FTS, "$COL_FTS_BOOK_ID = ?", arrayOf(bookId))
            for ((cIdx, chap) in chapters.withIndex()) {
                db.beginTransaction()
                try {
                    for ((pIdx, paragraph) in chap.paragraphs.withIndex()) {
                        if (paragraph.isBlank()) continue
                        val values = ContentValues().apply {
                            put(COL_FTS_BOOK_ID, bookId)
                            put(COL_FTS_CHAPTER_INDEX, cIdx)
                            put(COL_FTS_CHAPTER_TITLE, chap.title)
                            put(COL_FTS_PARAGRAPH_INDEX, pIdx)
                            put(COL_FTS_CONTENT, paragraph)
                        }
                        db.insert(TABLE_BOOK_FTS, null, values)
                        totalParas++
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            val ibValues = ContentValues().apply {
                put(COL_IB_BOOK_ID, bookId)
                put(COL_IB_INDEXED_AT, System.currentTimeMillis())
                put(COL_IB_PARAGRAPHS_COUNT, totalParas)
            }
            db.insertWithOnConflict(TABLE_INDEXED_BOOKS, null, ibValues, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {
        }
        return totalParas
    }

    fun isBookFtsIndexed(bookId: String): Boolean {
        val db = readableDatabase
        return try {
            val cursor = db.rawQuery("SELECT 1 FROM $TABLE_INDEXED_BOOKS WHERE $COL_IB_BOOK_ID = ? LIMIT 1", arrayOf(bookId))
            cursor.use { it.moveToFirst() }
        } catch (_: Exception) {
            isBookIndexed(bookId)
        }
    }

    fun indexChapterParagraphs(bookId: String, chapterIndex: Int, chapterTitle: String, paragraphs: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((pIdx, paragraph) in paragraphs.withIndex()) {
                if (paragraph.isBlank()) continue
                val values = ContentValues().apply {
                    put(COL_FTS_BOOK_ID, bookId)
                    put(COL_FTS_CHAPTER_INDEX, chapterIndex)
                    put(COL_FTS_CHAPTER_TITLE, chapterTitle)
                    put(COL_FTS_PARAGRAPH_INDEX, pIdx)
                    put(COL_FTS_CONTENT, paragraph)
                }
                db.insert(TABLE_BOOK_FTS, null, values)
            }
            db.setTransactionSuccessful()
        } catch (_: Exception) {
        } finally {
            db.endTransaction()
        }
    }

    fun isBookIndexed(bookId: String): Boolean {
        val db = readableDatabase
        return try {
            val cursor = db.rawQuery("SELECT 1 FROM $TABLE_BOOK_FTS WHERE $COL_FTS_BOOK_ID = ? LIMIT 1", arrayOf(bookId))
            cursor.use { it.moveToFirst() }
        } catch (_: Exception) {
            false
        }
    }

    private val SEMANTIC_STOPWORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
        "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
        "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
        "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
        "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself",
        "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
        "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
        "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there",
        "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to",
        "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
        "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom",
        "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your",
        "yours", "yourself", "yourselves", "scene", "find", "jump", "show", "tell"
    )

    fun searchScenes(bookId: String, query: String, maxResults: Int = 10): List<SceneMatch> {
        val results = mutableListOf<SceneMatch>()
        val seen = mutableSetOf<String>()
        val db = readableDatabase
        val rawClean = query.replace("\"", "").replace("'", "").trim()
        if (rawClean.isBlank()) return results

        val tokens = rawClean.split("\\W+".toRegex())
            .map { it.lowercase().trim() }
            .filter { it.length >= 2 && !SEMANTIC_STOPWORDS.contains(it) }

        val searchTokens = if (tokens.isNotEmpty()) tokens else listOf(rawClean.lowercase())

        fun executeFts(ftsExpression: String) {
            if (results.size >= maxResults) return
            try {
                val cursor = db.rawQuery(
                    "SELECT $COL_FTS_CHAPTER_INDEX, $COL_FTS_CHAPTER_TITLE, $COL_FTS_PARAGRAPH_INDEX, $COL_FTS_CONTENT FROM $TABLE_BOOK_FTS WHERE $TABLE_BOOK_FTS MATCH ? AND $COL_FTS_BOOK_ID = ? LIMIT ?",
                    arrayOf(ftsExpression, bookId, (maxResults * 2).toString())
                )
                cursor.use {
                    val cIdx = it.getColumnIndexOrThrow(COL_FTS_CHAPTER_INDEX)
                    val cTitle = it.getColumnIndexOrThrow(COL_FTS_CHAPTER_TITLE)
                    val pIdx = it.getColumnIndexOrThrow(COL_FTS_PARAGRAPH_INDEX)
                    val pContent = it.getColumnIndexOrThrow(COL_FTS_CONTENT)
                    while (it.moveToNext() && results.size < maxResults) {
                        val key = "${it.getInt(cIdx)}-${it.getInt(pIdx)}"
                        if (seen.add(key)) {
                            results.add(
                                SceneMatch(
                                    bookId = bookId,
                                    chapterIndex = it.getInt(cIdx),
                                    chapterTitle = it.getString(cTitle),
                                    paragraphIndex = it.getInt(pIdx),
                                    snippet = it.getString(pContent)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 1. Exact phrase if enclosed or short
        if (rawClean.split("\\s+".toRegex()).size <= 2) {
            executeFts("\"$rawClean\"*")
        }

        // 2. AND conjunction of semantic non-stopword tokens (e.g. "oliver*" AND "fagin*")
        if (results.size < maxResults && searchTokens.size > 1) {
            val andQuery = searchTokens.joinToString(" AND ") { "$it*" }
            executeFts(andQuery)
        }

        // 3. OR conjunction with rank sorting
        if (results.size < maxResults) {
            val orQuery = searchTokens.joinToString(" OR ") { "$it*" }
            executeFts(orQuery)
        }

        // Sort results by semantic token match density
        return results.sortedByDescending { match ->
            val textLower = match.snippet.lowercase()
            searchTokens.count { token -> textLower.contains(token) }
        }
    }

    fun searchAllBooks(query: String, maxResults: Int = 40): List<SceneMatch> {
        val results = mutableListOf<SceneMatch>()
        val seen = mutableSetOf<String>()
        val db = readableDatabase
        val rawClean = query.replace("\"", "").replace("'", "").trim()
        if (rawClean.isBlank()) return results

        val tokens = rawClean.split("\\W+".toRegex())
            .map { it.lowercase().trim() }
            .filter { it.length >= 2 && !SEMANTIC_STOPWORDS.contains(it) }

        val searchTokens = if (tokens.isNotEmpty()) tokens else listOf(rawClean.lowercase())

        fun executeFts(ftsExpression: String) {
            if (results.size >= maxResults) return
            try {
                val cursor = db.rawQuery(
                    "SELECT $COL_FTS_BOOK_ID, $COL_FTS_CHAPTER_INDEX, $COL_FTS_CHAPTER_TITLE, $COL_FTS_PARAGRAPH_INDEX, $COL_FTS_CONTENT FROM $TABLE_BOOK_FTS WHERE $TABLE_BOOK_FTS MATCH ? LIMIT ?",
                    arrayOf(ftsExpression, (maxResults * 2).toString())
                )
                cursor.use {
                    val bId = it.getColumnIndexOrThrow(COL_FTS_BOOK_ID)
                    val cIdx = it.getColumnIndexOrThrow(COL_FTS_CHAPTER_INDEX)
                    val cTitle = it.getColumnIndexOrThrow(COL_FTS_CHAPTER_TITLE)
                    val pIdx = it.getColumnIndexOrThrow(COL_FTS_PARAGRAPH_INDEX)
                    val pContent = it.getColumnIndexOrThrow(COL_FTS_CONTENT)
                    while (it.moveToNext() && results.size < maxResults) {
                        val key = "${it.getString(bId)}-${it.getInt(cIdx)}-${it.getInt(pIdx)}"
                        if (seen.add(key)) {
                            results.add(
                                SceneMatch(
                                    bookId = it.getString(bId),
                                    chapterIndex = it.getInt(cIdx),
                                    chapterTitle = it.getString(cTitle),
                                    paragraphIndex = it.getInt(pIdx),
                                    snippet = it.getString(pContent)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (rawClean.split("\\s+".toRegex()).size <= 2) {
            executeFts("\"$rawClean\"*")
        }

        if (results.size < maxResults && searchTokens.size > 1) {
            executeFts(searchTokens.joinToString(" AND ") { "$it*" })
        }

        if (results.size < maxResults) {
            executeFts(searchTokens.joinToString(" OR ") { "$it*" })
        }

        return results.sortedByDescending { match ->
            val textLower = match.snippet.lowercase()
            searchTokens.count { token -> textLower.contains(token) }
        }
    }

    fun clearFtsIndex() {
        val db = writableDatabase
        try {
            db.execSQL("DELETE FROM $TABLE_BOOK_FTS")
            db.execSQL("DELETE FROM $TABLE_INDEXED_BOOKS")
            db.execSQL("VACUUM")
        } catch (_: Exception) {}
    }

    fun getFtsIndexCount(): Int {
        val db = readableDatabase
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_BOOK_FTS", null)
            cursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (_: Exception) { 0 }
    }

    // --- Completed Books ---

    fun markBookCompleted(bookId: String, completed: Boolean) {
        val db = writableDatabase
        if (completed) {
            val values = ContentValues().apply {
                put(COL_CB_BOOK_ID, bookId)
                put(COL_CB_COMPLETED_AT, System.currentTimeMillis().toString())
            }
            db.insertWithOnConflict(TABLE_COMPLETED_BOOKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.delete(TABLE_COMPLETED_BOOKS, "$COL_CB_BOOK_ID = ?", arrayOf(bookId))
        }
    }

    fun isBookCompleted(bookId: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(TABLE_COMPLETED_BOOKS, null, "$COL_CB_BOOK_ID = ?", arrayOf(bookId), null, null, null)
        return cursor.use { it.moveToFirst() }
    }

    fun getAllCompletedBookIds(): Set<String> {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        val cursor = db.query(TABLE_COMPLETED_BOOKS, arrayOf(COL_CB_BOOK_ID), null, null, null, null, null)
        cursor.use {
            val col = it.getColumnIndexOrThrow(COL_CB_BOOK_ID)
            while (it.moveToNext()) {
                set.add(it.getString(col))
            }
        }
        return set
    }

    // --- Custom Themes CRUD ---

    fun saveCustomTheme(theme: CustomThemeData) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CT_ID, theme.id)
            put(COL_CT_NAME, theme.name)
            put(COL_CT_BG, theme.bgColor)
            put(COL_CT_TEXT, theme.textColor)
            put(COL_CT_ACCENT, theme.accentColor)
            put(COL_CT_CREATED_AT, System.currentTimeMillis().toString())
        }
        db.insertWithOnConflict(TABLE_CUSTOM_THEMES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllCustomThemes(): List<CustomThemeData> {
        val list = mutableListOf<CustomThemeData>()
        val db = readableDatabase
        try {
            val cursor = db.query(TABLE_CUSTOM_THEMES, null, null, null, null, null, "$COL_CT_CREATED_AT DESC")
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(COL_CT_ID)
                val nameCol = it.getColumnIndexOrThrow(COL_CT_NAME)
                val bgCol = it.getColumnIndexOrThrow(COL_CT_BG)
                val textCol = it.getColumnIndexOrThrow(COL_CT_TEXT)
                val accentCol = it.getColumnIndexOrThrow(COL_CT_ACCENT)
                while (it.moveToNext()) {
                    list.add(
                        CustomThemeData(
                            id = it.getString(idCol),
                            name = it.getString(nameCol),
                            bgColor = it.getLong(bgCol),
                            textColor = it.getLong(textCol),
                            accentColor = it.getLong(accentCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteCustomTheme(themeId: String) {
        val db = writableDatabase
        db.delete(TABLE_CUSTOM_THEMES, "$COL_CT_ID = ?", arrayOf(themeId))
    }

    // --- Custom Textures CRUD ---

    fun saveCustomTexture(texture: CustomTextureData) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TEX_ID, texture.id)
            put(COL_TEX_NAME, texture.name)
            put(COL_TEX_IMAGE_PATH, texture.imagePath)
            put(COL_TEX_IS_TILED, if (texture.isTiled) 1 else 0)
            put(COL_TEX_OPACITY, texture.opacity)
            put(COL_TEX_CREATED_AT, texture.createdAt)
        }
        db.insertWithOnConflict(TABLE_CUSTOM_TEXTURES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllCustomTextures(): List<CustomTextureData> {
        val list = mutableListOf<CustomTextureData>()
        val db = readableDatabase
        try {
            val cursor = db.query(TABLE_CUSTOM_TEXTURES, null, null, null, null, null, "$COL_TEX_CREATED_AT DESC")
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(COL_TEX_ID)
                val nameCol = it.getColumnIndexOrThrow(COL_TEX_NAME)
                val pathCol = it.getColumnIndexOrThrow(COL_TEX_IMAGE_PATH)
                val tiledCol = it.getColumnIndexOrThrow(COL_TEX_IS_TILED)
                val opacityCol = it.getColumnIndexOrThrow(COL_TEX_OPACITY)
                val createdCol = it.getColumnIndexOrThrow(COL_TEX_CREATED_AT)
                while (it.moveToNext()) {
                    list.add(
                        CustomTextureData(
                            id = it.getString(idCol),
                            name = it.getString(nameCol),
                            imagePath = it.getString(pathCol),
                            isTiled = it.getInt(tiledCol) == 1,
                            opacity = it.getFloat(opacityCol),
                            createdAt = it.getLong(createdCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteCustomTexture(textureId: String) {
        val db = writableDatabase
        db.delete(TABLE_CUSTOM_TEXTURES, "$COL_TEX_ID = ?", arrayOf(textureId))
    }

    fun updateCustomTextureName(textureId: String, newName: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TEX_NAME, newName)
        }
        db.update(TABLE_CUSTOM_TEXTURES, values, "$COL_TEX_ID = ?", arrayOf(textureId))
    }

    // --- Custom Catalog Endpoints CRUD ---

    fun insertCustomEndpoint(endpoint: CustomCatalogEndpoint) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_EP_ID, endpoint.id)
            put(COL_EP_NAME, endpoint.name)
            put(COL_EP_GALLERY_URL, endpoint.galleryUrl)
            put(COL_EP_SEARCH_URL, endpoint.searchUrl)
            put(COL_EP_API_KEY, endpoint.apiKey)
            put(COL_EP_AUTH_HEADER, endpoint.authHeader)
            put(COL_EP_IS_ENABLED, if (endpoint.isEnabled) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_CUSTOM_ENDPOINTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllCustomEndpoints(): List<CustomCatalogEndpoint> {
        val list = mutableListOf<CustomCatalogEndpoint>()
        val db = readableDatabase
        try {
            val cursor = db.query(TABLE_CUSTOM_ENDPOINTS, null, null, null, null, null, "$COL_EP_NAME ASC")
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(COL_EP_ID)
                val nameCol = it.getColumnIndexOrThrow(COL_EP_NAME)
                val galCol = it.getColumnIndexOrThrow(COL_EP_GALLERY_URL)
                val searchCol = it.getColumnIndexOrThrow(COL_EP_SEARCH_URL)
                val keyCol = it.getColumnIndexOrThrow(COL_EP_API_KEY)
                val authCol = it.getColumnIndexOrThrow(COL_EP_AUTH_HEADER)
                val enabledCol = it.getColumnIndexOrThrow(COL_EP_IS_ENABLED)
                while (it.moveToNext()) {
                    list.add(
                        CustomCatalogEndpoint(
                            id = it.getString(idCol),
                            name = it.getString(nameCol),
                            galleryUrl = it.getString(galCol) ?: "",
                            searchUrl = it.getString(searchCol) ?: "",
                            apiKey = it.getString(keyCol) ?: "",
                            authHeader = it.getString(authCol) ?: "Authorization",
                            isEnabled = it.getInt(enabledCol) == 1
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteCustomEndpoint(endpointId: String) {
        val db = writableDatabase
        db.delete(TABLE_CUSTOM_ENDPOINTS, "$COL_EP_ID = ?", arrayOf(endpointId))
    }

    // --- Books Table CRUD (Permanent SQLite Storage for Local & Downloaded Books) ---

    fun deleteDuplicateBooks(title: String, author: String, filePath: String) {
        val db = writableDatabase
        try {
            val duplicateIds = mutableListOf<String>()
            val cursor = db.query(
                TABLE_BOOKS,
                arrayOf(COL_BOOK_ID, COL_BOOK_TITLE_MAIN, COL_BOOK_AUTHOR, COL_BOOK_FILE_PATH),
                null, null, null, null, null
            )
            cursor.use {
                val idIdx = it.getColumnIndexOrThrow(COL_BOOK_ID)
                val titleIdx = it.getColumnIndexOrThrow(COL_BOOK_TITLE_MAIN)
                val authorIdx = it.getColumnIndexOrThrow(COL_BOOK_AUTHOR)
                val fileIdx = it.getColumnIndexOrThrow(COL_BOOK_FILE_PATH)
                val targetNormTitle = title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                val targetNormAuthor = author.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

                while (it.moveToNext()) {
                    val rowId = it.getString(idIdx)
                    val rowTitle = it.getString(titleIdx) ?: ""
                    val rowAuthor = it.getString(authorIdx) ?: ""
                    val rowPath = it.getString(fileIdx) ?: ""
                    val rowNormTitle = rowTitle.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                    val rowNormAuthor = rowAuthor.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

                    val matchPath = filePath.isNotBlank() && rowPath == filePath
                    val matchTitle = targetNormTitle.isNotBlank() && rowNormTitle == targetNormTitle &&
                        (targetNormAuthor.isBlank() || rowNormAuthor.isBlank() || targetNormAuthor == rowNormAuthor || targetNormAuthor == "unknown" || rowNormAuthor == "unknown")

                    if (matchPath || matchTitle) {
                        duplicateIds.add(rowId)
                    }
                }
            }
            for (dupId in duplicateIds) {
                deleteBook(dupId)
            }
        } catch (_: Exception) {}
    }

    fun insertOrUpdateBook(
        book: Book,
        filePath: String = book.filePath,
        isDownloaded: Boolean = book.isDownloaded,
        downloadUrl: String = book.downloadUrl,
        fileSize: Long = book.fileSize
    ) {
        val db = writableDatabase
        // Deduplicate against existing books with same title+author or same filePath but different ID
        try {
            val cursor = db.query(
                TABLE_BOOKS,
                arrayOf(COL_BOOK_ID, COL_BOOK_TITLE_MAIN, COL_BOOK_AUTHOR, COL_BOOK_FILE_PATH),
                null, null, null, null, null
            )
            val conflictIds = mutableListOf<String>()
            cursor.use {
                val idIdx = it.getColumnIndexOrThrow(COL_BOOK_ID)
                val titleIdx = it.getColumnIndexOrThrow(COL_BOOK_TITLE_MAIN)
                val authorIdx = it.getColumnIndexOrThrow(COL_BOOK_AUTHOR)
                val fileIdx = it.getColumnIndexOrThrow(COL_BOOK_FILE_PATH)
                val targetNormTitle = book.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                val targetNormAuthor = book.author.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

                while (it.moveToNext()) {
                    val rowId = it.getString(idIdx)
                    if (rowId == book.id) continue
                    val rowTitle = it.getString(titleIdx) ?: ""
                    val rowAuthor = it.getString(authorIdx) ?: ""
                    val rowPath = it.getString(fileIdx) ?: ""
                    val rowNormTitle = rowTitle.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                    val rowNormAuthor = rowAuthor.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

                    val matchPath = filePath.isNotBlank() && rowPath.isNotBlank() && rowPath == filePath
                    val matchTitle = targetNormTitle.isNotBlank() && rowNormTitle == targetNormTitle &&
                        (targetNormAuthor.isBlank() || rowNormAuthor.isBlank() || targetNormAuthor == rowNormAuthor || targetNormAuthor == "unknown" || rowNormAuthor == "unknown")

                    if (matchPath || matchTitle) {
                        conflictIds.add(rowId)
                    }
                }
            }
            for (conflictId in conflictIds) {
                deleteBook(conflictId)
            }
        } catch (_: Exception) {}

        // Normalized chapters are saved in TABLE_CHAPTERS via saveChaptersForBook.
        // We do not bloat TABLE_BOOKS with monolithic multi-megabyte JSON blobs.
        val chaptersJson = ""
        val values = ContentValues().apply {
            put(COL_BOOK_ID, book.id)
            put(COL_BOOK_TITLE_MAIN, book.title)
            put(COL_BOOK_AUTHOR, book.author)
            put(COL_BOOK_COVER, book.coverUrl)
            put(COL_BOOK_FILE_PATH, filePath)
            put(COL_BOOK_LAST_READ, book.lastRead)
            put(COL_BOOK_PROGRESS, book.progress)
            put(COL_BOOK_TIME_LEFT, book.readTimeLeft)
            put(COL_BOOK_CURRENT_CHAPTER, book.currentChapter)
            put(COL_BOOK_CURRENT_PAGE, book.currentPage)
            put(COL_BOOK_SCROLL_POS, book.scrollPos)
            put(COL_BOOK_CHAPTERS_JSON, chaptersJson)
            put(COL_BOOK_IS_DOWNLOADED, if (isDownloaded) 1 else 0)
            put(COL_BOOK_DOWNLOAD_URL, downloadUrl)
            put(COL_BOOK_FILE_SIZE, fileSize)
            put(COL_BOOK_ADDED_AT, System.currentTimeMillis())
            put(COL_BOOK_CHAR_CHECKPOINT_CHAPTER, book.characterCheckpointChapter)
            put(COL_BOOK_CHAR_CHECKPOINT_PAGE, book.characterCheckpointPage)
            put(COL_BOOK_LANGUAGE, book.language)
        }
        db.insertWithOnConflict(TABLE_BOOKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        if (book.chapters.isNotEmpty()) {
            saveChaptersForBook(book.id, book.chapters)
        }
    }

    fun updateCharacterCheckpoint(bookId: String, checkpointChapter: Int, checkpointPage: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOK_CHAR_CHECKPOINT_CHAPTER, checkpointChapter)
            put(COL_BOOK_CHAR_CHECKPOINT_PAGE, checkpointPage)
        }
        db.update(TABLE_BOOKS, values, "$COL_BOOK_ID = ?", arrayOf(bookId))
    }

    fun updateBookFilePath(bookId: String, newPath: String, fileSize: Long = 0L) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOK_FILE_PATH, newPath)
            if (fileSize > 0L) {
                put(COL_BOOK_FILE_SIZE, fileSize)
            }
        }
        db.update(TABLE_BOOKS, values, "$COL_BOOK_ID = ?", arrayOf(bookId))
    }

    fun updateBookProgress(
        bookId: String,
        currentChapter: Int,
        currentPage: Int,
        scrollPos: Int,
        progress: Int,
        lastRead: String = "Just now",
        readTimeLeft: String = ""
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_BOOK_CURRENT_CHAPTER, currentChapter)
            put(COL_BOOK_CURRENT_PAGE, currentPage)
            put(COL_BOOK_SCROLL_POS, scrollPos)
            put(COL_BOOK_PROGRESS, progress)
            put(COL_BOOK_LAST_READ, lastRead)
            if (readTimeLeft.isNotBlank()) {
                put(COL_BOOK_TIME_LEFT, readTimeLeft)
            }
        }
        db.update(TABLE_BOOKS, values, "$COL_BOOK_ID = ?", arrayOf(bookId))
    }

    fun deleteBook(bookId: String) {
        val db = writableDatabase
        chaptersCache.remove(bookId)
        db.delete(TABLE_BOOKS, "$COL_BOOK_ID = ?", arrayOf(bookId))
        db.delete(TABLE_CHAPTERS, "$COL_CHAP_BOOK_ID = ?", arrayOf(bookId))
        db.delete(TABLE_PAGE_CACHE, "$COL_PC_BOOK_ID = ?", arrayOf(bookId))
        db.delete(TABLE_READING_LIST, "$COL_RL_BOOK_ID = ?", arrayOf(bookId))
        db.delete(TABLE_COMPLETED_BOOKS, "$COL_CB_BOOK_ID = ?", arrayOf(bookId))
        try {
            db.delete(TABLE_BOOK_FTS, "$COL_FTS_BOOK_ID = ?", arrayOf(bookId))
            db.delete(TABLE_INDEXED_BOOKS, "$COL_IB_BOOK_ID = ?", arrayOf(bookId))
        } catch (_: Exception) {}
    }

    fun getAllBooks(): List<Book> {
        val list = mutableListOf<Book>()
        val db = readableDatabase
        try {
            // High-performance metadata-only projection: zero JSON deserialization on startup!
            val cursor = db.query(
                TABLE_BOOKS,
                arrayOf(
                    COL_BOOK_ID,
                    COL_BOOK_TITLE_MAIN,
                    COL_BOOK_AUTHOR,
                    COL_BOOK_COVER,
                    COL_BOOK_FILE_PATH,
                    COL_BOOK_LAST_READ,
                    COL_BOOK_PROGRESS,
                    COL_BOOK_TIME_LEFT,
                    COL_BOOK_CURRENT_CHAPTER,
                    COL_BOOK_CURRENT_PAGE,
                    COL_BOOK_SCROLL_POS,
                    COL_BOOK_IS_DOWNLOADED,
                    COL_BOOK_DOWNLOAD_URL,
                    COL_BOOK_FILE_SIZE,
                    COL_BOOK_CHAR_CHECKPOINT_CHAPTER,
                    COL_BOOK_CHAR_CHECKPOINT_PAGE,
                    COL_BOOK_LANGUAGE
                ),
                null, null, null, null, "$COL_BOOK_ADDED_AT DESC"
            )
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(COL_BOOK_ID)
                val titleCol = it.getColumnIndexOrThrow(COL_BOOK_TITLE_MAIN)
                val authorCol = it.getColumnIndexOrThrow(COL_BOOK_AUTHOR)
                val coverCol = it.getColumnIndexOrThrow(COL_BOOK_COVER)
                val fileCol = it.getColumnIndexOrThrow(COL_BOOK_FILE_PATH)
                val lastReadCol = it.getColumnIndexOrThrow(COL_BOOK_LAST_READ)
                val progCol = it.getColumnIndexOrThrow(COL_BOOK_PROGRESS)
                val timeLeftCol = it.getColumnIndexOrThrow(COL_BOOK_TIME_LEFT)
                val chapCol = it.getColumnIndexOrThrow(COL_BOOK_CURRENT_CHAPTER)
                val pageCol = it.getColumnIndexOrThrow(COL_BOOK_CURRENT_PAGE)
                val scrollCol = it.getColumnIndexOrThrow(COL_BOOK_SCROLL_POS)
                val dlCol = it.getColumnIndexOrThrow(COL_BOOK_IS_DOWNLOADED)
                val dlUrlCol = it.getColumnIndexOrThrow(COL_BOOK_DOWNLOAD_URL)
                val sizeCol = it.getColumnIndexOrThrow(COL_BOOK_FILE_SIZE)
                val charChapCol = it.getColumnIndex(COL_BOOK_CHAR_CHECKPOINT_CHAPTER)
                val charPageCol = it.getColumnIndex(COL_BOOK_CHAR_CHECKPOINT_PAGE)
                val langCol = it.getColumnIndex(COL_BOOK_LANGUAGE)

                while (it.moveToNext()) {
                    val bookId = it.getString(idCol)
                    val chapters = chaptersCache[bookId] ?: emptyList()
                    list.add(
                        Book(
                            id = bookId,
                            title = it.getString(titleCol),
                            author = it.getString(authorCol) ?: "",
                            coverUrl = it.getString(coverCol) ?: "",
                            lastRead = it.getString(lastReadCol) ?: "Just now",
                            progress = it.getInt(progCol),
                            readTimeLeft = it.getString(timeLeftCol) ?: "10m left",
                            currentChapter = it.getInt(chapCol),
                            currentPage = it.getInt(pageCol),
                            scrollPos = it.getInt(scrollCol),
                            chapters = chapters,
                            filePath = it.getString(fileCol) ?: "",
                            isDownloaded = it.getInt(dlCol) == 1,
                            downloadUrl = it.getString(dlUrlCol) ?: "",
                            fileSize = it.getLong(sizeCol),
                            characterCheckpointChapter = if (charChapCol >= 0) it.getInt(charChapCol) else 0,
                            characterCheckpointPage = if (charPageCol >= 0) it.getInt(charPageCol) else 0,
                            language = if (langCol >= 0) it.getString(langCol) ?: "en" else "en"
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun getBookById(bookId: String): Book? {
        val db = readableDatabase
        try {
            val cursor = db.query(TABLE_BOOKS, null, "$COL_BOOK_ID = ?", arrayOf(bookId), null, null, null)
            cursor.use {
                if (it.moveToFirst()) {
                    val idCol = it.getColumnIndexOrThrow(COL_BOOK_ID)
                    val titleCol = it.getColumnIndexOrThrow(COL_BOOK_TITLE_MAIN)
                    val authorCol = it.getColumnIndexOrThrow(COL_BOOK_AUTHOR)
                    val coverCol = it.getColumnIndexOrThrow(COL_BOOK_COVER)
                    val fileCol = it.getColumnIndexOrThrow(COL_BOOK_FILE_PATH)
                    val lastReadCol = it.getColumnIndexOrThrow(COL_BOOK_LAST_READ)
                    val progCol = it.getColumnIndexOrThrow(COL_BOOK_PROGRESS)
                    val timeLeftCol = it.getColumnIndexOrThrow(COL_BOOK_TIME_LEFT)
                    val chapCol = it.getColumnIndexOrThrow(COL_BOOK_CURRENT_CHAPTER)
                    val pageCol = it.getColumnIndexOrThrow(COL_BOOK_CURRENT_PAGE)
                    val scrollCol = it.getColumnIndexOrThrow(COL_BOOK_SCROLL_POS)
                    val dlCol = it.getColumnIndexOrThrow(COL_BOOK_IS_DOWNLOADED)
                    val dlUrlCol = it.getColumnIndexOrThrow(COL_BOOK_DOWNLOAD_URL)
                    val sizeCol = it.getColumnIndexOrThrow(COL_BOOK_FILE_SIZE)
                    val charChapCol = it.getColumnIndex(COL_BOOK_CHAR_CHECKPOINT_CHAPTER)
                    val charPageCol = it.getColumnIndex(COL_BOOK_CHAR_CHECKPOINT_PAGE)
                    val langCol = it.getColumnIndex(COL_BOOK_LANGUAGE)

                    val chapters = getAllChaptersForBook(bookId)
                    return Book(
                        id = it.getString(idCol),
                        title = it.getString(titleCol),
                        author = it.getString(authorCol) ?: "",
                        coverUrl = it.getString(coverCol) ?: "",
                        lastRead = it.getString(lastReadCol) ?: "Just now",
                        progress = it.getInt(progCol),
                        readTimeLeft = it.getString(timeLeftCol) ?: "10m left",
                        currentChapter = it.getInt(chapCol),
                        currentPage = it.getInt(pageCol),
                        scrollPos = it.getInt(scrollCol),
                        chapters = chapters,
                        filePath = it.getString(fileCol) ?: "",
                        isDownloaded = it.getInt(dlCol) == 1,
                        downloadUrl = it.getString(dlUrlCol) ?: "",
                        fileSize = it.getLong(sizeCol),
                        characterCheckpointChapter = if (charChapCol >= 0) it.getInt(charChapCol) else 0,
                        characterCheckpointPage = if (charPageCol >= 0) it.getInt(charPageCol) else 0,
                        language = if (langCol >= 0) it.getString(langCol) ?: "en" else "en"
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // --- Normalized Chunked Chapter Storage ---

    fun getAllChaptersForBook(bookId: String): List<Chapter> {
        val inMem = chaptersCache[bookId]
        if (inMem != null && inMem.isNotEmpty()) return inMem

        val list = mutableListOf<Chapter>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_CHAPTERS,
                arrayOf(COL_CHAP_INDEX, COL_CHAP_TITLE, COL_CHAP_SUBTITLE, COL_CHAP_READ_TIME, COL_CHAP_PARAS_JSON),
                "$COL_CHAP_BOOK_ID = ?",
                arrayOf(bookId),
                null, null,
                "$COL_CHAP_INDEX ASC"
            )
            cursor.use {
                val titleCol = it.getColumnIndexOrThrow(COL_CHAP_TITLE)
                val subtitleCol = it.getColumnIndexOrThrow(COL_CHAP_SUBTITLE)
                val readTimeCol = it.getColumnIndexOrThrow(COL_CHAP_READ_TIME)
                val parasCol = it.getColumnIndexOrThrow(COL_CHAP_PARAS_JSON)
                while (it.moveToNext()) {
                    list.add(
                        Chapter(
                            title = it.getString(titleCol),
                            subtitle = it.getString(subtitleCol) ?: "",
                            readTime = it.getString(readTimeCol) ?: "15 mins",
                            paragraphs = deserializeParagraphs(it.getString(parasCol))
                        )
                    )
                }
            }
            // Backward compatibility fallback for books stored in legacy format
            if (list.isEmpty()) {
                val legacyCursor = db.query(
                    TABLE_BOOKS,
                    arrayOf(COL_BOOK_CHAPTERS_JSON),
                    "$COL_BOOK_ID = ?",
                    arrayOf(bookId),
                    null, null, null
                )
                legacyCursor.use {
                    if (it.moveToFirst()) {
                        val chapsJson = it.getString(0)
                        if (!chapsJson.isNullOrBlank()) {
                            val parsed = deserializeChapters(chapsJson)
                            if (parsed.isNotEmpty()) {
                                saveChaptersForBook(bookId, parsed)
                                return parsed
                            }
                        }
                    }
                }
            }
            if (list.isNotEmpty()) {
                chaptersCache[bookId] = list
            }
        } catch (_: Exception) {}
        return list
    }

    fun getCachedChapters(bookId: String): List<Chapter>? {
        return chaptersCache[bookId]
    }

    fun getChapter(bookId: String, chapterIndex: Int): Chapter? {
        val inMem = chaptersCache[bookId]?.getOrNull(chapterIndex)
        if (inMem != null) return inMem

        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_CHAPTERS,
                arrayOf(COL_CHAP_TITLE, COL_CHAP_SUBTITLE, COL_CHAP_READ_TIME, COL_CHAP_PARAS_JSON),
                "$COL_CHAP_BOOK_ID = ? AND $COL_CHAP_INDEX = ?",
                arrayOf(bookId, chapterIndex.toString()),
                null, null, null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return Chapter(
                        title = it.getString(0),
                        subtitle = it.getString(1) ?: "",
                        readTime = it.getString(2) ?: "15 mins",
                        paragraphs = deserializeParagraphs(it.getString(3))
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getChapters(bookId: String, startIndex: Int, count: Int): List<Chapter> {
        val inMem = chaptersCache[bookId]
        if (inMem != null && inMem.isNotEmpty()) {
            val endIndex = (startIndex + count).coerceAtMost(inMem.size)
            if (startIndex in inMem.indices && startIndex < endIndex) {
                return inMem.subList(startIndex, endIndex)
            }
        }

        val list = mutableListOf<Chapter>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_CHAPTERS,
                arrayOf(COL_CHAP_INDEX, COL_CHAP_TITLE, COL_CHAP_SUBTITLE, COL_CHAP_READ_TIME, COL_CHAP_PARAS_JSON),
                "$COL_CHAP_BOOK_ID = ? AND $COL_CHAP_INDEX >= ? AND $COL_CHAP_INDEX < ?",
                arrayOf(bookId, startIndex.toString(), (startIndex + count).toString()),
                null, null,
                "$COL_CHAP_INDEX ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        Chapter(
                            title = it.getString(1),
                            subtitle = it.getString(2) ?: "",
                            readTime = it.getString(3) ?: "15 mins",
                            paragraphs = deserializeParagraphs(it.getString(4))
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun getChapterCount(bookId: String): Int {
        val inMem = chaptersCache[bookId]?.size
        if (inMem != null && inMem > 0) return inMem

        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CHAPTERS WHERE $COL_CHAP_BOOK_ID = ?", arrayOf(bookId))
            cursor.use {
                if (it.moveToFirst()) {
                    val count = it.getInt(0)
                    if (count > 0) return count
                }
            }
        } catch (_: Exception) {}
        return 0
    }

    fun saveChaptersForBook(bookId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_CHAPTERS, "$COL_CHAP_BOOK_ID = ?", arrayOf(bookId))
            for (idx in chapters.indices) {
                val chap = chapters[idx]
                val values = ContentValues().apply {
                    put(COL_CHAP_BOOK_ID, bookId)
                    put(COL_CHAP_INDEX, idx)
                    put(COL_CHAP_TITLE, chap.title)
                    put(COL_CHAP_SUBTITLE, chap.subtitle)
                    put(COL_CHAP_READ_TIME, chap.readTime)
                    put(COL_CHAP_PARAS_JSON, serializeParagraphs(chap.paragraphs))
                }
                db.insertWithOnConflict(TABLE_CHAPTERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            chaptersCache[bookId] = chapters
        } catch (_: Exception) {
        } finally {
            db.endTransaction()
        }
    }

    // --- Precomputed Page Cache Persistence ---

    fun getPageCache(cacheKey: String): List<Pair<String, String>>? {
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_PAGE_CACHE,
                arrayOf(COL_PC_PAGES_JSON),
                "$COL_PC_KEY = ?",
                arrayOf(cacheKey),
                null, null,
                "$COL_PC_CHAP_INDEX ASC"
            )
            cursor.use {
                val list = mutableListOf<Pair<String, String>>()
                while (it.moveToNext()) {
                    val json = it.getString(0)
                    list.addAll(deserializePages(json))
                }
                if (list.isNotEmpty()) return list
            }
        } catch (_: Exception) {}
        return null
    }

    fun savePageCache(cacheKey: String, bookId: String, chapterIndex: Int, pages: List<Pair<String, String>>) {
        if (pages.isEmpty()) return
        try {
            val db = writableDatabase
            // Chunk pages into 25-page slices (~30-50KB JSON) so individual rows never exceed Android's 2MB CursorWindow limit
            val chunks = pages.chunked(25)
            db.beginTransaction()
            try {
                // Prune any existing cache for this book to keep SQLite lean and prevent multi-megabyte bloat
                db.delete(TABLE_PAGE_CACHE, "$COL_PC_BOOK_ID = ?", arrayOf(bookId))
                for ((idx, chunk) in chunks.withIndex()) {
                    val values = ContentValues().apply {
                        put(COL_PC_KEY, cacheKey)
                        put(COL_PC_BOOK_ID, bookId)
                        put(COL_PC_CHAP_INDEX, idx)
                        put(COL_PC_PAGES_JSON, serializePages(chunk))
                        put(COL_PC_CREATED_AT, System.currentTimeMillis())
                    }
                    db.insertWithOnConflict(TABLE_PAGE_CACHE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {}
    }

    /**
     * Compacts SQLite database if file size has grown excessively due to transient cache churn.
     * Must be invoked on a background IO dispatcher.
     */
    fun compactDatabaseIfNeeded() {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val walFile = java.io.File(dbFile.parentFile, "$DATABASE_NAME-wal")
            if ((dbFile.exists() && dbFile.length() > 6 * 1024 * 1024L) || (walFile.exists() && walFile.length() > 4 * 1024 * 1024L)) {
                val db = writableDatabase
                try {
                    val c1 = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                    c1?.moveToFirst()
                    c1?.close()
                } catch (_: Throwable) {}
                db.execSQL("VACUUM")
                try {
                    val c2 = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                    c2?.moveToFirst()
                    c2?.close()
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun backupStateToPersistentFile(file: java.io.File): Boolean {
        return try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())

            // 1. Books
            val booksArray = JSONArray()
            for (b in getAllBooks()) {
                val obj = JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("author", b.author)
                    put("coverUrl", b.coverUrl)
                    put("filePath", b.filePath)
                    put("lastRead", b.lastRead)
                    put("progress", b.progress)
                    put("readTimeLeft", b.readTimeLeft)
                    put("currentChapter", b.currentChapter)
                    put("currentPage", b.currentPage)
                    put("scrollPos", b.scrollPos)
                    put("isDownloaded", b.isDownloaded)
                    put("downloadUrl", b.downloadUrl)
                    put("fileSize", b.fileSize)
                    put("language", b.language)
                }
                booksArray.put(obj)
            }
            root.put("books", booksArray)

            // 2. Bookmarks
            val bookmarksArray = JSONArray()
            for (bm in getAllBookmarks()) {
                val obj = JSONObject().apply {
                    put("id", bm.id)
                    put("bookTitle", bm.bookTitle)
                    put("chapter", bm.chapter)
                    put("quote", bm.quote)
                    put("color", bm.color.name)
                    put("note", bm.note)
                    put("timestamp", bm.timestamp)
                    put("pageNumber", bm.pageNumber)
                }
                bookmarksArray.put(obj)
            }
            root.put("bookmarks", bookmarksArray)

            // 3. Wishlist
            val wishlistArray = JSONArray()
            for (w in getAllWishlist()) {
                val obj = JSONObject().apply {
                    put("id", w.id)
                    put("title", w.title)
                    put("author", w.author)
                    put("note", w.note)
                    put("addedAt", w.addedAt)
                }
                wishlistArray.put(obj)
            }
            root.put("wishlist", wishlistArray)

            // 4. Completed
            val completedArray = JSONArray()
            for (cid in getAllCompletedBookIds()) {
                completedArray.put(cid)
            }
            root.put("completed", completedArray)

            // 5. Settings
            val settingsObj = JSONObject()
            for ((k, v) in getAllSettings()) {
                settingsObj.put(k, v)
            }
            root.put("settings", settingsObj)

            file.parentFile?.mkdirs()
            file.writeText(root.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreStateFromPersistentFile(file: java.io.File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return false
            val root = JSONObject(jsonStr)

            val db = writableDatabase
            db.beginTransaction()
            try {
                // 1. Books
                val booksArray = root.optJSONArray("books")
                if (booksArray != null) {
                    for (i in 0 until booksArray.length()) {
                        val obj = booksArray.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put(COL_BOOK_ID, obj.getString("id"))
                            put(COL_BOOK_TITLE_MAIN, obj.optString("title", ""))
                            put(COL_BOOK_AUTHOR, obj.optString("author", ""))
                            put(COL_BOOK_COVER, obj.optString("coverUrl", ""))
                            put(COL_BOOK_FILE_PATH, obj.optString("filePath", ""))
                            put(COL_BOOK_LAST_READ, obj.optString("lastRead", "Never read"))
                            put(COL_BOOK_PROGRESS, obj.optInt("progress", 0))
                            put(COL_BOOK_TIME_LEFT, obj.optString("readTimeLeft", "10h left"))
                            put(COL_BOOK_CURRENT_CHAPTER, obj.optInt("currentChapter", 0))
                            put(COL_BOOK_CURRENT_PAGE, obj.optInt("currentPage", 0))
                            put(COL_BOOK_SCROLL_POS, obj.optInt("scrollPos", 0))
                            put(COL_BOOK_CHAPTERS_JSON, "")
                            put(COL_BOOK_IS_DOWNLOADED, if (obj.optBoolean("isDownloaded", false)) 1 else 0)
                            put(COL_BOOK_DOWNLOAD_URL, obj.optString("downloadUrl", ""))
                            put(COL_BOOK_FILE_SIZE, obj.optLong("fileSize", 0L))
                            put(COL_BOOK_ADDED_AT, obj.optLong("addedAt", System.currentTimeMillis()))
                        }
                        db.insertWithOnConflict(TABLE_BOOKS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }

                // 2. Bookmarks
                val bookmarksArray = root.optJSONArray("bookmarks")
                if (bookmarksArray != null) {
                    for (i in 0 until bookmarksArray.length()) {
                        val obj = bookmarksArray.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put(COL_BOOKMARK_ID, obj.getLong("id"))
                            put(COL_BOOK_TITLE, obj.optString("bookTitle", ""))
                            put(COL_CHAPTER, obj.optString("chapter", ""))
                            put(COL_QUOTE, obj.optString("quote", ""))
                            put(COL_COLOR, obj.optString("color", "GOLD"))
                            put(COL_BOOKMARK_NOTE, obj.optString("note", ""))
                            put(COL_TIMESTAMP, obj.optLong("timestamp", System.currentTimeMillis()))
                            put(COL_BOOKMARK_PAGE, obj.optInt("pageNumber", 1))
                        }
                        db.insertWithOnConflict(TABLE_BOOKMARKS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }

                // 3. Wishlist
                val wishlistArray = root.optJSONArray("wishlist")
                if (wishlistArray != null) {
                    for (i in 0 until wishlistArray.length()) {
                        val obj = wishlistArray.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put(COL_WISHLIST_ID, obj.getString("id"))
                            put(COL_WISHLIST_TITLE, obj.optString("title", ""))
                            put(COL_WISHLIST_AUTHOR, obj.optString("author", ""))
                            put(COL_WISHLIST_NOTE, obj.optString("note", obj.optString("notes", "")))
                            put(COL_WISHLIST_ADDED_AT, obj.optString("addedAt", "Recently"))
                        }
                        db.insertWithOnConflict(TABLE_WISHLIST, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }

                // 4. Completed
                val completedArray = root.optJSONArray("completed")
                if (completedArray != null) {
                    for (i in 0 until completedArray.length()) {
                        val bookId = completedArray.getString(i)
                        val cv = ContentValues().apply {
                            put(COL_CB_BOOK_ID, bookId)
                            put(COL_CB_COMPLETED_AT, System.currentTimeMillis().toString())
                        }
                        db.insertWithOnConflict(TABLE_COMPLETED_BOOKS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }

                // 5. Settings
                val settingsObj = root.optJSONObject("settings")
                if (settingsObj != null) {
                    val keys = settingsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = settingsObj.getString(k)
                        val cv = ContentValues().apply {
                            put(COL_SETTING_KEY, k)
                            put(COL_SETTING_VALUE, v)
                        }
                        db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }

                db.setTransactionSuccessful()
                true
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearPageCacheForBook(bookId: String) {
        try {
            val db = writableDatabase
            db.delete(TABLE_PAGE_CACHE, "$COL_PC_BOOK_ID = ?", arrayOf(bookId))
        } catch (_: Exception) {}
    }

    fun clearAllPageCache() {
        try {
            val db = writableDatabase
            db.delete(TABLE_PAGE_CACHE, null, null)
        } catch (_: Exception) {}
    }

    fun purgeDemoBooks() {
        try {
            val db = writableDatabase
            val demoIds = arrayOf("book-kafka", "book-alice", "book-artofwar", "1", "2", "3", "demo-kafka", "demo-alice", "demo-artofwar")
            val placeholders = demoIds.joinToString(",") { "?" }
            db.delete(TABLE_BOOKS, "$COL_BOOK_ID IN ($placeholders)", demoIds)
            db.delete(TABLE_CHAPTERS, "$COL_CHAP_BOOK_ID IN ($placeholders)", demoIds)
            db.delete(TABLE_PAGE_CACHE, "$COL_PC_BOOK_ID IN ($placeholders)", demoIds)
            db.delete(TABLE_READING_LIST, "$COL_RL_BOOK_ID IN ($placeholders)", demoIds)
            db.delete(TABLE_BOOKS, "($COL_BOOK_FILE_PATH IS NULL OR $COL_BOOK_FILE_PATH = '') AND $COL_BOOK_IS_DOWNLOADED = 0", null)
            db.delete(TABLE_BOOKMARKS, "$COL_BOOK_TITLE IN (?, ?, ?)", arrayOf("The Metamorphosis", "Alice's Adventures in Wonderland", "The Art of War"))
        } catch (_: Exception) {}
    }

    private fun serializeParagraphs(paragraphs: List<String>): String {
        val array = JSONArray()
        paragraphs.forEach { array.put(it) }
        return array.toString()
    }

    private fun deserializeParagraphs(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun serializePages(pages: List<Pair<String, String>>): String {
        val array = JSONArray()
        pages.forEach { (first, second) ->
            val obj = JSONObject()
            obj.put("title", first)
            obj.put("content", second)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializePages(json: String?): List<Pair<String, String>> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<Pair<String, String>>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Pair(obj.optString("title", ""), obj.optString("content", "")))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun serializeChapters(chapters: List<Chapter>): String {
        val array = JSONArray()
        chapters.forEach { chap ->
            val obj = JSONObject()
            obj.put("title", chap.title)
            obj.put("subtitle", chap.subtitle)
            obj.put("readTime", chap.readTime)
            val paras = JSONArray()
            chap.paragraphs.forEach { paras.put(it) }
            obj.put("paragraphs", paras)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeChapters(json: String?): List<Chapter> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<Chapter>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val parasArray = obj.optJSONArray("paragraphs") ?: JSONArray()
                val paras = mutableListOf<String>()
                for (j in 0 until parasArray.length()) {
                    paras.add(parasArray.getString(j))
                }
                list.add(
                    Chapter(
                        title = obj.optString("title", "Chapter ${i + 1}"),
                        subtitle = obj.optString("subtitle", ""),
                        readTime = obj.optString("readTime", "15 mins"),
                        paragraphs = paras
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    // --- App Settings Persistence ---

    fun setSetting(key: String, value: String) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_SETTING_KEY, key)
                put(COL_SETTING_VALUE, value)
            }
            db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {}
    }

    fun setSettings(settings: Map<String, String>) {
        if (settings.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues()
                for ((key, value) in settings) {
                    values.clear()
                    values.put(COL_SETTING_KEY, key)
                    values.put(COL_SETTING_VALUE, value)
                    db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {}
    }

    fun getSetting(key: String, defaultValue: String = ""): String {
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_SETTINGS, arrayOf(COL_SETTING_VALUE), "$COL_SETTING_KEY = ?", arrayOf(key), null, null, null)
            cursor.use {
                if (it.moveToFirst()) {
                    return it.getString(0) ?: defaultValue
                }
            }
        } catch (_: Exception) {}
        return defaultValue
    }

    fun getAllSettings(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_SETTINGS, arrayOf(COL_SETTING_KEY, COL_SETTING_VALUE), null, null, null, null, null)
            cursor.use {
                while (it.moveToNext()) {
                    map[it.getString(0)] = it.getString(1)
                }
            }
        } catch (_: Exception) {}
        return map
    }

    // --- Book Characters CRUD ---

    fun getCharacters(bookId: String): List<BookCharacter> {
        val list = mutableListOf<BookCharacter>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_CHARACTERS,
                null,
                "$COL_CHAR_BOOK_ID = ?",
                arrayOf(bookId),
                null,
                null,
                "$COL_CHAR_NAME ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        BookCharacter(
                            id = it.getLong(it.getColumnIndexOrThrow(COL_CHAR_ID)),
                            bookId = it.getString(it.getColumnIndexOrThrow(COL_CHAR_BOOK_ID)),
                            name = it.getString(it.getColumnIndexOrThrow(COL_CHAR_NAME)),
                            role = it.getString(it.getColumnIndexOrThrow(COL_CHAR_ROLE)),
                            firstAppearanceChapter = it.getString(it.getColumnIndexOrThrow(COL_CHAR_FIRST_SEEN)) ?: "",
                            summary = it.getString(it.getColumnIndexOrThrow(COL_CHAR_SUMMARY)),
                            keyEvents = it.getString(it.getColumnIndexOrThrow(COL_CHAR_EVENTS)) ?: "",
                            isSpoiler = it.getInt(it.getColumnIndexOrThrow(COL_CHAR_IS_SPOILER)) == 1,
                            createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CHAR_CREATED_AT))
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun insertCharacter(character: BookCharacter): Long {
        return try {
            val db = writableDatabase
            // 1. If explicit positive ID provided, check and update
            if (character.id > 0) {
                val cursor = db.query(TABLE_CHARACTERS, arrayOf(COL_CHAR_ID), "$COL_CHAR_ID = ?", arrayOf(character.id.toString()), null, null, null)
                val exists = cursor.use { it.moveToFirst() }
                if (exists) {
                    val values = ContentValues().apply {
                        put(COL_CHAR_BOOK_ID, character.bookId)
                        put(COL_CHAR_NAME, character.name)
                        put(COL_CHAR_ROLE, character.role)
                        put(COL_CHAR_FIRST_SEEN, character.firstAppearanceChapter)
                        put(COL_CHAR_SUMMARY, character.summary)
                        put(COL_CHAR_EVENTS, character.keyEvents)
                        put(COL_CHAR_IS_SPOILER, if (character.isSpoiler) 1 else 0)
                    }
                    db.update(TABLE_CHARACTERS, values, "$COL_CHAR_ID = ?", arrayOf(character.id.toString()))
                    return character.id
                }
            }

            // 2. Prevent duplication: check if matching character already exists for this book
            val cursor = db.query(
                TABLE_CHARACTERS,
                arrayOf(COL_CHAR_ID),
                "$COL_CHAR_BOOK_ID = ? AND LOWER($COL_CHAR_NAME) = LOWER(?)",
                arrayOf(character.bookId, character.name.trim()),
                null,
                null,
                null
            )
            val existingId = cursor.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            }

            if (existingId > 0) {
                val values = ContentValues().apply {
                    put(COL_CHAR_NAME, character.name)
                    put(COL_CHAR_ROLE, character.role)
                    put(COL_CHAR_FIRST_SEEN, character.firstAppearanceChapter)
                    put(COL_CHAR_SUMMARY, character.summary)
                    put(COL_CHAR_EVENTS, character.keyEvents)
                    put(COL_CHAR_IS_SPOILER, if (character.isSpoiler) 1 else 0)
                }
                db.update(TABLE_CHARACTERS, values, "$COL_CHAR_ID = ?", arrayOf(existingId.toString()))
                return existingId
            }

            // 3. New insert
            val values = ContentValues().apply {
                put(COL_CHAR_BOOK_ID, character.bookId)
                put(COL_CHAR_NAME, character.name)
                put(COL_CHAR_ROLE, character.role)
                put(COL_CHAR_FIRST_SEEN, character.firstAppearanceChapter)
                put(COL_CHAR_SUMMARY, character.summary)
                put(COL_CHAR_EVENTS, character.keyEvents)
                put(COL_CHAR_IS_SPOILER, if (character.isSpoiler) 1 else 0)
                put(COL_CHAR_CREATED_AT, character.createdAt)
            }
            db.insert(TABLE_CHARACTERS, null, values)
        } catch (_: Exception) { -1L }
    }

    fun saveCharacters(bookId: String, characters: List<BookCharacter>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (char in characters) {
                insertCharacter(char.copy(bookId = bookId))
            }
            db.setTransactionSuccessful()
        } catch (_: Exception) {
        } finally {
            db.endTransaction()
        }
    }

    fun deduplicateCharacters(bookId: String) {
        try {
            val db = writableDatabase
            val raw = getCharacters(bookId)
            val unique = mutableListOf<BookCharacter>()
            val idsToDelete = mutableListOf<Long>()

            for (c in raw) {
                val matchIdx = unique.indexOfFirst {
                    it.name.trim().equals(c.name.trim(), ignoreCase = true)
                }
                if (matchIdx == -1) {
                    unique.add(c)
                } else {
                    idsToDelete.add(c.id)
                }
            }

            if (idsToDelete.isNotEmpty()) {
                db.beginTransaction()
                try {
                    for (delId in idsToDelete) {
                        db.delete(TABLE_CHARACTERS, "$COL_CHAR_ID = ?", arrayOf(delId.toString()))
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        } catch (_: Exception) {}
    }

    fun deleteCharacter(id: Long): Boolean {
        return try {
            val db = writableDatabase
            db.delete(TABLE_CHARACTERS, "$COL_CHAR_ID = ?", arrayOf(id.toString())) > 0
        } catch (_: Exception) { false }
    }

    fun clearCharacters(bookId: String): Boolean {
        return try {
            val db = writableDatabase
            db.delete(TABLE_CHARACTERS, "$COL_CHAR_BOOK_ID = ?", arrayOf(bookId)) > 0
        } catch (_: Exception) { false }
    }

    // --- Book Lore CRUD ---

    fun getLore(bookId: String): List<BookLore> {
        val list = mutableListOf<BookLore>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_LORE,
                null,
                "$COL_LORE_BOOK_ID = ?",
                arrayOf(bookId),
                null,
                null,
                "$COL_LORE_TITLE ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        BookLore(
                            id = it.getLong(it.getColumnIndexOrThrow(COL_LORE_ID)),
                            bookId = it.getString(it.getColumnIndexOrThrow(COL_LORE_BOOK_ID)),
                            title = it.getString(it.getColumnIndexOrThrow(COL_LORE_TITLE)),
                            category = it.getString(it.getColumnIndexOrThrow(COL_LORE_CATEGORY)),
                            firstAppearanceChapter = it.getString(it.getColumnIndexOrThrow(COL_LORE_FIRST_SEEN)) ?: "",
                            description = it.getString(it.getColumnIndexOrThrow(COL_LORE_DESCRIPTION)),
                            keyFacts = it.getString(it.getColumnIndexOrThrow(COL_LORE_KEY_FACTS)) ?: "",
                            isSpoiler = it.getInt(it.getColumnIndexOrThrow(COL_LORE_IS_SPOILER)) == 1,
                            createdAt = it.getLong(it.getColumnIndexOrThrow(COL_LORE_CREATED_AT))
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun insertLore(lore: BookLore): Long {
        return try {
            val db = writableDatabase
            if (lore.id > 0) {
                val cursor = db.query(TABLE_LORE, arrayOf(COL_LORE_ID), "$COL_LORE_ID = ?", arrayOf(lore.id.toString()), null, null, null)
                val exists = cursor.use { it.moveToFirst() }
                if (exists) {
                    val values = ContentValues().apply {
                        put(COL_LORE_BOOK_ID, lore.bookId)
                        put(COL_LORE_TITLE, lore.title)
                        put(COL_LORE_CATEGORY, lore.category)
                        put(COL_LORE_FIRST_SEEN, lore.firstAppearanceChapter)
                        put(COL_LORE_DESCRIPTION, lore.description)
                        put(COL_LORE_KEY_FACTS, lore.keyFacts)
                        put(COL_LORE_IS_SPOILER, if (lore.isSpoiler) 1 else 0)
                    }
                    db.update(TABLE_LORE, values, "$COL_LORE_ID = ?", arrayOf(lore.id.toString()))
                    return lore.id
                }
            }

            // Check if matching lore title already exists for this book
            val cursor = db.query(
                TABLE_LORE,
                arrayOf(COL_LORE_ID),
                "$COL_LORE_BOOK_ID = ? AND LOWER($COL_LORE_TITLE) = LOWER(?)",
                arrayOf(lore.bookId, lore.title.trim()),
                null,
                null,
                null
            )
            val existingId = cursor.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            }

            if (existingId > 0) {
                val values = ContentValues().apply {
                    put(COL_LORE_TITLE, lore.title)
                    put(COL_LORE_CATEGORY, lore.category)
                    put(COL_LORE_FIRST_SEEN, lore.firstAppearanceChapter)
                    put(COL_LORE_DESCRIPTION, lore.description)
                    put(COL_LORE_KEY_FACTS, lore.keyFacts)
                    put(COL_LORE_IS_SPOILER, if (lore.isSpoiler) 1 else 0)
                }
                db.update(TABLE_LORE, values, "$COL_LORE_ID = ?", arrayOf(existingId.toString()))
                return existingId
            }

            val values = ContentValues().apply {
                put(COL_LORE_BOOK_ID, lore.bookId)
                put(COL_LORE_TITLE, lore.title)
                put(COL_LORE_CATEGORY, lore.category)
                put(COL_LORE_FIRST_SEEN, lore.firstAppearanceChapter)
                put(COL_LORE_DESCRIPTION, lore.description)
                put(COL_LORE_KEY_FACTS, lore.keyFacts)
                put(COL_LORE_IS_SPOILER, if (lore.isSpoiler) 1 else 0)
                put(COL_LORE_CREATED_AT, lore.createdAt)
            }
            db.insert(TABLE_LORE, null, values)
        } catch (_: Exception) { -1L }
    }

    fun saveLoreList(bookId: String, loreList: List<BookLore>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (lore in loreList) {
                insertLore(lore.copy(bookId = bookId))
            }
            db.setTransactionSuccessful()
        } catch (_: Exception) {
        } finally {
            db.endTransaction()
        }
    }

    fun deduplicateLore(bookId: String) {
        try {
            val db = writableDatabase
            val raw = getLore(bookId)
            val unique = mutableListOf<BookLore>()
            val idsToDelete = mutableListOf<Long>()

            for (l in raw) {
                val matchIdx = unique.indexOfFirst {
                    it.title.trim().equals(l.title.trim(), ignoreCase = true)
                }
                if (matchIdx == -1) {
                    unique.add(l)
                } else {
                    idsToDelete.add(l.id)
                }
            }

            if (idsToDelete.isNotEmpty()) {
                db.beginTransaction()
                try {
                    for (delId in idsToDelete) {
                        db.delete(TABLE_LORE, "$COL_LORE_ID = ?", arrayOf(delId.toString()))
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        } catch (_: Exception) {}
    }

    fun deleteLore(id: Long): Boolean {
        return try {
            val db = writableDatabase
            db.delete(TABLE_LORE, "$COL_LORE_ID = ?", arrayOf(id.toString())) > 0
        } catch (_: Exception) { false }
    }

    fun clearLore(bookId: String): Boolean {
        return try {
            val db = writableDatabase
            db.delete(TABLE_LORE, "$COL_LORE_BOOK_ID = ?", arrayOf(bookId)) > 0
        } catch (_: Exception) { false }
    }
}
