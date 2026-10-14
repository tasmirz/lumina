package io.github.tasmirz.lumina.data

import android.content.Context
import io.github.tasmirz.lumina.data.db.LuminaDatabaseHelper
import io.github.tasmirz.lumina.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Handles bookmarks, wishlist, and custom OPDS/catalog endpoints.
 */
object BookBookmarkWishlistManager {

    fun saveBookmarks(context: Context, bookmarks: List<Bookmark>) {
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

    fun loadPersistedBookmarks(context: Context, dbHelper: LuminaDatabaseHelper): List<Bookmark> {
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

    fun addBookmark(
        repository: BookRepository,
        quote: String,
        color: HighlightColor = HighlightColor.GOLD,
        note: String = "",
        chapterTitle: String? = null,
        pageNumber: Int = 0,
        isHighlight: Boolean = false,
        isLastRead: Boolean = false
    ) {
        val book = repository.getActiveBook() ?: return
        val chapter = chapterTitle?.ifBlank { null } ?: book.chapters.getOrNull(book.currentChapter)?.title ?: "Chapter"
        val actualPage = if (pageNumber > 0) pageNumber else (book.currentPage + 1)
        val existingWithoutLastRead = if (isLastRead) {
            repository._bookmarks.value.filterNot { it.bookTitle == book.title && it.isLastRead }
        } else {
            repository._bookmarks.value
        }
        val mark = Bookmark(
            bookTitle = book.title,
            chapter = chapter,
            quote = quote,
            color = color,
            note = note.trim(),
            timestamp = "Just now",
            pageNumber = actualPage,
            isHighlight = isHighlight,
            isLastRead = isLastRead
        )
        val updated = listOf(mark) + existingWithoutLastRead
        repository._bookmarks.value = updated
        repository.dbHelper.insertBookmark(mark)
        saveBookmarks(repository.context, updated)
    }

    fun updateLastReadBookmark(
        repository: BookRepository,
        bookId: String,
        chapterTitle: String,
        pageNumber: Int,
        paragraphSnippet: String
    ) {
        val book = repository._books.value.find { it.id == bookId } ?: return
        val existingWithout = repository._bookmarks.value.filterNot { it.bookTitle == book.title && it.isLastRead }
        val deterministicId = (book.id.hashCode().toLong() and 0x7FFFFFFF) + 8888000000L
        val mark = Bookmark(
            id = deterministicId,
            bookTitle = book.title,
            chapter = chapterTitle,
            quote = paragraphSnippet.ifBlank { if (pageNumber > 0) "Page $pageNumber" else chapterTitle },
            color = HighlightColor.GOLD,
            note = "Last Read Position",
            timestamp = "Auto-saved",
            pageNumber = pageNumber,
            isHighlight = false,
            isLastRead = true
        )
        val updated = listOf(mark) + existingWithout
        repository._bookmarks.value = updated
        repository.repoScope.launch(Dispatchers.IO) {
            repository.dbHelper.insertBookmark(mark)
            saveBookmarks(repository.context, updated)
        }
    }

    fun updateBookmark(repository: BookRepository, bookmark: Bookmark) {
        val updated = repository._bookmarks.value.map { if (it.id == bookmark.id) bookmark else it }
        repository._bookmarks.value = updated
        repository.dbHelper.insertBookmark(bookmark)
        saveBookmarks(repository.context, updated)
    }

    fun removeBookmark(repository: BookRepository, id: Long) {
        val updated = repository._bookmarks.value.filterNot { it.id == id }
        repository._bookmarks.value = updated
        repository.dbHelper.deleteBookmark(id)
        saveBookmarks(repository.context, updated)
    }

    fun addToWishlist(repository: BookRepository, title: String, author: String = "", note: String = "") {
        val item = WishlistBook(
            title = title.trim(),
            author = author.trim(),
            note = note.trim(),
            addedAt = "Today"
        )
        repository.dbHelper.insertWishlist(item)
        repository._wishlistBooks.value = repository.dbHelper.getAllWishlist()
    }

    fun removeFromWishlist(repository: BookRepository, id: String) {
        repository.dbHelper.deleteWishlist(id)
        repository._wishlistBooks.value = repository.dbHelper.getAllWishlist()
    }

    fun addCustomEndpoint(repository: BookRepository, endpoint: CustomCatalogEndpoint) {
        repository.dbHelper.insertCustomEndpoint(endpoint)
        val updated = repository.dbHelper.getAllCustomEndpoints()
        repository._customEndpoints.value = updated
        OnlineEpubService.customEndpoints = updated
        repository.updateReaderSettings { it.copy(customEndpoints = updated) }
    }

    fun updateCustomEndpoint(repository: BookRepository, endpoint: CustomCatalogEndpoint) {
        repository.dbHelper.insertCustomEndpoint(endpoint)
        val updated = repository.dbHelper.getAllCustomEndpoints()
        repository._customEndpoints.value = updated
        OnlineEpubService.customEndpoints = updated
        repository.updateReaderSettings { it.copy(customEndpoints = updated) }
    }

    fun deleteCustomEndpoint(repository: BookRepository, endpointId: String) {
        repository.dbHelper.deleteCustomEndpoint(endpointId)
        val updated = repository.dbHelper.getAllCustomEndpoints()
        repository._customEndpoints.value = updated
        OnlineEpubService.customEndpoints = updated
        repository.updateReaderSettings { it.copy(customEndpoints = updated) }
    }

    fun toggleCustomEndpoint(repository: BookRepository, endpointId: String, isEnabled: Boolean) {
        val current = repository._customEndpoints.value.find { it.id == endpointId } ?: return
        val updatedEndpoint = current.copy(isEnabled = isEnabled)
        updateCustomEndpoint(repository, updatedEndpoint)
    }
}
