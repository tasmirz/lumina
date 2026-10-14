package io.github.tasmirz.lumina.data

import io.github.tasmirz.lumina.data.db.LuminaDatabaseHelper
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.SceneMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles FTS (full-text search) scene queries, background indexing, and storage cache calculation.
 */
object BookSearchManager {

    fun searchScenes(repository: BookRepository, bookId: String, query: String): List<SceneMatch> {
        val book = repository._books.value.find { it.id == bookId }
        if (book != null && !repository.dbHelper.isBookFtsIndexed(bookId) && book.chapters.isNotEmpty()) {
            repository.dbHelper.indexEntireBook(bookId, book.chapters)
        }
        return repository.dbHelper.searchScenes(bookId, query)
    }

    fun searchAllBooks(repository: BookRepository, query: String): List<SceneMatch> {
        return repository.dbHelper.searchAllBooks(query)
    }

    fun clearFtsIndex(repository: BookRepository) {
        repository.dbHelper.clearFtsIndex()
    }

    fun getFtsIndexCount(repository: BookRepository): Int {
        return repository.dbHelper.getFtsIndexCount()
    }

    fun getImageCacheSizeBytes(repository: BookRepository): Long {
        return try {
            fun dirSize(dir: File): Long {
                var size = 0L
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) dirSize(file) else file.length()
                }
                return size
            }
            var total = dirSize(repository.context.cacheDir)
            val coversDir = File(repository.context.filesDir, "covers")
            if (coversDir.exists()) total += dirSize(coversDir)
            val booksDir = File(repository.context.filesDir, "books")
            if (booksDir.exists()) total += dirSize(booksDir)
            total
        } catch (_: Exception) {
            0L
        }
    }

    fun clearImageCache(repository: BookRepository) {
        try {
            repository.context.cacheDir.deleteRecursively()
            repository.context.cacheDir.mkdirs()
            val coversDir = File(repository.context.filesDir, "covers")
            if (coversDir.exists()) {
                coversDir.deleteRecursively()
                coversDir.mkdirs()
            }
        } catch (_: Exception) {}
    }

    fun getDatabaseSizeBytes(repository: BookRepository): Long {
        return try {
            val dbFile = repository.context.getDatabasePath(LuminaDatabaseHelper.DATABASE_NAME)
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

    fun indexBookIfNeeded(repository: BookRepository, book: Book) {
        if (!repository.enableFtsIndexing.value) return
        if (book.chapters.isEmpty()) return
        repository.repoScope.launch(Dispatchers.IO) {
            if (repository.dbHelper.isBookFtsIndexed(book.id)) return@launch
            try {
                repository._isIndexingActive.value = true
                repository._indexingProgress.value = "Indexing ${book.title}..."
                val count = repository.dbHelper.indexEntireBook(book.id, book.chapters)
                repository._indexingProgress.value = "Indexed $count paragraphs"
            } catch (_: Exception) {
                repository._indexingProgress.value = "Indexing failed"
            } finally {
                repository._isIndexingActive.value = false
            }
        }
    }

    fun indexEntireBookNow(repository: BookRepository, book: Book, onComplete: ((Int) -> Unit)? = null) {
        repository.repoScope.launch(Dispatchers.IO) {
            repository._isIndexingActive.value = true
            repository._indexingProgress.value = "Indexing ${book.title}..."
            try {
                val count = repository.dbHelper.indexEntireBook(book.id, book.chapters)
                repository._indexingProgress.value = "Indexed $count paragraphs"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(count)
                }
            } catch (_: Exception) {
            } finally {
                repository._isIndexingActive.value = false
            }
        }
    }
}
