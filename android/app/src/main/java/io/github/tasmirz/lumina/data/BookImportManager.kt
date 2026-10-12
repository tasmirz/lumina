package io.github.tasmirz.lumina.data

import android.net.Uri
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.util.PageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles EPUB import, re-parsing, duplicate deduplication, book deletion,
 * filesystem reconciliation, and background library synchronization.
 */
object BookImportManager {

    fun isDuplicateBook(b1: Book, b2: Book): Boolean {
        if (b1.id == b2.id) return true
        if (b1.filePath.isNotBlank() && b2.filePath.isNotBlank()) {
            val p1 = try { File(b1.filePath).canonicalPath } catch (_: Exception) { b1.filePath }
            val p2 = try { File(b2.filePath).canonicalPath } catch (_: Exception) { b2.filePath }
            if (p1 == p2) return true
        }
        val t1 = b1.title.trim().lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
        val t2 = b2.title.trim().lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
        if (t1.isNotBlank() && t1 == t2) {
            val a1 = b1.author.trim().lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
            val a2 = b2.author.trim().lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
            val a1Generic = a1.isBlank() || a1 == "unknown" || a1 == "unknownauthor"
            val a2Generic = a2.isBlank() || a2 == "unknown" || a2 == "unknownauthor"
            if (a1Generic || a2Generic || a1 == a2) {
                return true
            }
        }
        return false
    }

    fun addBook(repository: BookRepository, book: Book) {
        // Undelete if previously deleted
        val currentDeletedIds = repository.prefs.getStringSet("deleted_book_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentDeletedIds.remove(book.id)
        repository.prefs.edit().putStringSet("deleted_book_ids", currentDeletedIds).apply()

        val normTitle = book.title.trim().lowercase()
        if (normTitle.isNotBlank()) {
            val currentDeletedTitles = repository.prefs.getStringSet("deleted_book_titles", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentDeletedTitles.remove(normTitle)
            repository.prefs.edit().putStringSet("deleted_book_titles", currentDeletedTitles).apply()
        }

        if (book.filePath.isNotBlank()) {
            val currentDeletedPaths = repository.prefs.getStringSet("deleted_book_paths", emptySet())?.toMutableSet() ?: mutableSetOf()
            currentDeletedPaths.remove(book.filePath)
            try { currentDeletedPaths.remove(File(book.filePath).canonicalPath) } catch (_: Exception) {}
            repository.prefs.edit().putStringSet("deleted_book_paths", currentDeletedPaths).apply()
        }

        // Check for existing duplicate book in memory or DB
        val existing = repository._books.value.find { isDuplicateBook(it, book) }
            ?: repository.dbHelper.getAllBooks().find { isDuplicateBook(it, book) }

        val finalBook = if (existing != null) {
            // Merge into single book: preserve reading progress & bookmarks if existing had them
            val mergedProgress = if (existing.progress > 0 && book.progress == 0) existing.progress else book.progress
            val mergedChapter = if (existing.currentChapter > 0 && book.currentChapter == 0) existing.currentChapter else book.currentChapter
            val mergedPage = if (existing.currentPage > 0 && book.currentPage == 0) existing.currentPage else book.currentPage
            val mergedScroll = if (existing.scrollPos > 0 && book.scrollPos == 0) existing.scrollPos else book.scrollPos
            val mergedLastRead = if (existing.lastRead != "Never read" && existing.lastRead != "Just now" && book.lastRead == "Just now") existing.lastRead else book.lastRead
            val mergedPath = if (book.filePath.isNotBlank() && File(book.filePath).exists()) book.filePath else existing.filePath
            val mergedSize = if (book.fileSize > 0L) book.fileSize else existing.fileSize
            val mergedChapters = if (book.chapters.isNotEmpty()) book.chapters else existing.chapters
            val mergedCover = if (book.coverUrl.isNotBlank() && !book.coverUrl.startsWith("http")) book.coverUrl else (existing.coverUrl.ifBlank { book.coverUrl })

            // Clean up old duplicate DB record if IDs differed
            if (existing.id != book.id) {
                repository.dbHelper.deleteBook(existing.id)
                repository.chapterCache.remove(existing.id)
            }

            book.copy(
                id = book.id,
                progress = mergedProgress,
                currentChapter = mergedChapter,
                currentPage = mergedPage,
                scrollPos = mergedScroll,
                lastRead = mergedLastRead,
                filePath = mergedPath,
                fileSize = mergedSize,
                chapters = mergedChapters,
                coverUrl = mergedCover
            )
        } else {
            book
        }

        // Atomically replace all duplicate variants in memory with the single merged book
        val updated = listOf(finalBook) + repository._books.value.filterNot { isDuplicateBook(it, finalBook) }
        repository._books.value = updated
        if (finalBook.chapters.isNotEmpty()) {
            repository.chapterCache.put(finalBook.id, finalBook.chapters)
        }
        repository.setActiveBook(finalBook.id)
        repository.dbHelper.insertOrUpdateBook(finalBook, finalBook.filePath, finalBook.isDownloaded, finalBook.downloadUrl, finalBook.fileSize)

        // Update persistent backup
        try {
            val backupFile = LuminaStorageManager.getPersistentBackupFile(repository.context)
            repository.dbHelper.backupStateToPersistentFile(backupFile)
        } catch (_: Exception) {}
    }

    suspend fun importEpubFromUri(repository: BookRepository, uri: Uri): Book? = withContext(Dispatchers.IO) {
        try {
            var fileName: String? = null
            if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
                repository.context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            fileName = cursor.getString(index)
                        }
                    }
                }
            }
            if (fileName.isNullOrBlank()) {
                fileName = uri.path?.substringAfterLast('/')?.substringAfterLast(':') ?: uri.lastPathSegment
            }
            val cleanName = if (!fileName.isNullOrBlank() && fileName.endsWith(".epub", ignoreCase = true)) {
                fileName
            } else {
                "${fileName ?: "Imported"}.epub"
            }

            // Copy to temporary cache file first to parse and check duplicate before committing to persistent storage
            val tempFile = File(repository.context.cacheDir, "temp_import_${System.currentTimeMillis()}.epub")
            repository.context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext null
            }

            val parsedBook = tempFile.inputStream().use { stream ->
                EpubParser.parseEpub(stream, cleanName, repository.context)
            }

            // Check if book already exists in library
            val existing = repository._books.value.find { isDuplicateBook(it, parsedBook) }
                ?: repository.dbHelper.getAllBooks().find { isDuplicateBook(it, parsedBook) }

            if (existing != null && existing.filePath.isNotBlank() && File(existing.filePath).exists()) {
                // Book is already present in library with a valid file - update/re-parse chapters & file while preserving reading progress!
                val destFile = File(existing.filePath)
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()

                val updatedParsed = destFile.inputStream().use { stream ->
                    EpubParser.parseEpub(stream, cleanName, repository.context, existing.id)
                }
                val bookToSave = updatedParsed.copy(
                    id = existing.id,
                    filePath = destFile.absolutePath,
                    fileSize = destFile.length(),
                    isDownloaded = false,
                    progress = existing.progress,
                    currentChapter = existing.currentChapter,
                    currentPage = existing.currentPage,
                    scrollPos = existing.scrollPos,
                    lastRead = existing.lastRead,
                    coverUrl = if (updatedParsed.coverUrl.isNotBlank()) updatedParsed.coverUrl else existing.coverUrl
                )
                PageCache.invalidate(existing.id, repository.dbHelper)
                repository.chapterCache.remove(existing.id)
                repository.dbHelper.saveChaptersForBook(existing.id, bookToSave.chapters)
                addBook(repository, bookToSave)
                withContext(Dispatchers.Main) {
                    repository.setActiveBook(existing.id)
                }
                return@withContext bookToSave
            }

            // Move temp file to persistent EPUB directory
            val epubDir = LuminaStorageManager.getPersistentEpubDirectory(repository.context)
            val destFile = File(epubDir, "${System.currentTimeMillis()}_$cleanName")
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()

            val bookToSave = parsedBook.copy(
                filePath = destFile.absolutePath,
                fileSize = destFile.length(),
                isDownloaded = false
            )
            addBook(repository, bookToSave)
            bookToSave
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun reparseBook(repository: BookRepository, bookId: String): Book? {
        val book = repository._books.value.find { it.id == bookId } ?: repository.dbHelper.getAllBooks().find { it.id == bookId } ?: return null
        if (book.filePath.isBlank()) return null
        val file = File(book.filePath)
        if (!file.exists() || file.length() == 0L) return null

        val parsed = file.inputStream().use { stream ->
            EpubParser.parseEpub(stream, file.name, repository.context, book.id)
        }
        if (parsed.chapters.isEmpty()) return null

        val updatedBook = book.copy(
            title = parsed.title.ifBlank { book.title },
            author = if (parsed.author != "Unknown Author") parsed.author else book.author,
            coverUrl = parsed.coverUrl.ifBlank { book.coverUrl },
            chapters = parsed.chapters,
            language = parsed.language.ifBlank { book.language },
            fileSize = file.length()
        )

        PageCache.invalidate(bookId, repository.dbHelper)
        repository.chapterCache.remove(bookId)
        repository.dbHelper.saveChaptersForBook(bookId, parsed.chapters)
        addBook(repository, updatedBook)
        return updatedBook
    }

    fun removeBook(repository: BookRepository, bookId: String) {
        repository.chapterCache.remove(bookId)
        PageCache.invalidate(bookId, repository.dbHelper)

        // Find target book before removing from memory
        val targetBook = repository._books.value.find { it.id == bookId } ?: repository.dbHelper.getAllBooks().find { it.id == bookId }
        val targetTitle = targetBook?.title?.trim()?.lowercase() ?: ""
        val targetAuthor = targetBook?.author?.trim()?.lowercase() ?: ""
        val targetFilePath = targetBook?.filePath ?: ""
        val targetFileName = targetFilePath.substringAfterLast('/')

        // 1. Record persistently in deleted sets so auto-scan will NEVER re-import it
        val currentDeletedIds = repository.prefs.getStringSet("deleted_book_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentDeletedIds.add(bookId)
        if (targetBook != null) {
            currentDeletedIds.add(targetBook.id)
            if (targetFileName.isNotBlank()) {
                currentDeletedIds.add(targetFileName.removeSuffix(".epub"))
                currentDeletedIds.add(targetFileName)
            }
        }
        repository.prefs.edit().putStringSet("deleted_book_ids", currentDeletedIds).apply()
        repository.persistSettingToDb("deleted_book_ids", currentDeletedIds.joinToString(","))

        val currentDeletedTitles = repository.prefs.getStringSet("deleted_book_titles", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (targetTitle.isNotBlank()) {
            currentDeletedTitles.add(targetTitle)
            val normT = targetTitle.replace(Regex("[^a-z0-9]"), "")
            if (normT.isNotBlank()) currentDeletedTitles.add(normT)
            repository.prefs.edit().putStringSet("deleted_book_titles", currentDeletedTitles).apply()
            repository.persistSettingToDb("deleted_book_titles", currentDeletedTitles.joinToString(","))
        }

        val currentDeletedPaths = repository.prefs.getStringSet("deleted_book_paths", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (targetFilePath.isNotBlank()) {
            currentDeletedPaths.add(targetFilePath)
            try { currentDeletedPaths.add(File(targetFilePath).canonicalPath) } catch (_: Exception) {}
            repository.prefs.edit().putStringSet("deleted_book_paths", currentDeletedPaths).apply()
            repository.persistSettingToDb("deleted_book_paths", currentDeletedPaths.joinToString(","))
        }

        // 2. Remove all matching duplicates from state flow & SQLite
        val updated = repository._books.value.filterNot { it.id == bookId || (targetBook != null && isDuplicateBook(it, targetBook)) }
        repository._books.value = updated
        repository.dbHelper.deleteBook(bookId)
        if (targetBook != null && targetTitle.isNotBlank()) {
            repository.dbHelper.deleteDuplicateBooks(targetTitle, targetAuthor, targetFilePath)
        }

        // 3. Delete physical book EPUB file(s) from persistent & internal storage
        try {
            if (targetFilePath.isNotBlank()) {
                val f = File(targetFilePath)
                if (f.exists()) {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Search all candidate search directories for matching file names, IDs, or titles and delete them
        try {
            val searchDirs = LuminaStorageManager.getAllSearchDirectories(repository.context)
            val targetNormTitle = targetTitle.replace(Regex("[^a-z0-9]"), "")
            for (dir in searchDirs) {
                val candidate1 = File(dir, "${bookId}.epub")
                if (candidate1.exists()) candidate1.delete()
                if (targetBook != null) {
                    val candidate2 = File(dir, "${targetBook.id}.epub")
                    if (candidate2.exists()) candidate2.delete()
                }
                if (targetFileName.isNotBlank() && targetFileName.endsWith(".epub", ignoreCase = true)) {
                    val candidate3 = File(dir, targetFileName)
                    if (candidate3.exists()) candidate3.delete()
                }
                dir.listFiles { f -> f.isFile && f.name.endsWith(".epub", ignoreCase = true) }?.forEach { f ->
                    val fNorm = f.nameWithoutExtension.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                    if ((targetNormTitle.isNotBlank() && fNorm == targetNormTitle) ||
                        f.nameWithoutExtension.equals(bookId, ignoreCase = true) ||
                        (targetBook != null && f.nameWithoutExtension.equals(targetBook.id, ignoreCase = true)) ||
                        f.name in currentDeletedIds ||
                        f.nameWithoutExtension in currentDeletedIds ||
                        f.absolutePath in currentDeletedPaths
                    ) {
                        try { f.delete() } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Delete extracted assets & cover cache files
        try {
            val imgDir = File(repository.context.filesDir, "books/$bookId")
            if (imgDir.exists()) {
                imgDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val coversDir = File(repository.context.filesDir, "covers")
            if (coversDir.exists()) {
                File(coversDir, "${bookId}.webp").takeIf { it.exists() }?.delete()
                File(coversDir, "${bookId}.jpg").takeIf { it.exists() }?.delete()
                File(coversDir, "${bookId}.png").takeIf { it.exists() }?.delete()
                if (targetBook != null) {
                    File(coversDir, "${targetBook.id}.webp").takeIf { it.exists() }?.delete()
                    File(coversDir, "${targetBook.id}.jpg").takeIf { it.exists() }?.delete()
                }
            }
            if (targetBook?.coverUrl?.startsWith("/") == true || targetBook?.coverUrl?.startsWith("file:") == true) {
                val path = targetBook.coverUrl.removePrefix("file:")
                val cFile = File(path)
                if (cFile.exists() && cFile.absolutePath.startsWith(repository.context.filesDir.absolutePath)) {
                    cFile.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Update persistent backup file immediately
        try {
            val backupFile = LuminaStorageManager.getPersistentBackupFile(repository.context)
            repository.dbHelper.backupStateToPersistentFile(backupFile)
        } catch (_: Exception) {}

        // 6. If active book was removed, switch to another book or empty
        if (repository._activeBookId.value == bookId || (targetBook != null && repository._activeBookId.value == targetBook.id)) {
            val fallback = updated.firstOrNull()
            if (fallback != null) {
                repository.setActiveBook(fallback.id)
            } else {
                repository._activeBookId.value = ""
                repository.prefs.edit().remove("active_book_id").apply()
            }
        }
    }

    fun reconcileMissingBookPaths(repository: BookRepository, books: List<Book>) {
        try {
            val searchDirs = LuminaStorageManager.getAllSearchDirectories(repository.context)
            for (b in books) {
                if (b.filePath.isBlank() || !File(b.filePath).exists()) {
                    val originalFileName = b.filePath.substringAfterLast('/')
                    var recoveredFile: File? = null
                    for (dir in searchDirs) {
                        if (originalFileName.isNotBlank()) {
                            val candidate = File(dir, originalFileName)
                            if (candidate.exists() && candidate.length() > 0L) {
                                recoveredFile = candidate
                                break
                            }
                        }
                        val idCandidate = File(dir, "${b.id}.epub")
                        if (idCandidate.exists() && idCandidate.length() > 0L) {
                            recoveredFile = idCandidate
                            break
                        }
                    }
                    if (recoveredFile != null) {
                        try {
                            repository.dbHelper.updateBookFilePath(b.id, recoveredFile.absolutePath, recoveredFile.length())
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun loadAllBooks(repository: BookRepository): List<Book> {
        try {
            repository.dbHelper.purgeDemoBooks()
        } catch (_: Exception) {}

        try {
            val legacyFile = File(repository.context.filesDir, "custom_books.json")
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
        } catch (_: Exception) {}

        val dbDeletedIds = repository.dbHelper.getSetting("deleted_book_ids")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val dbDeletedTitles = repository.dbHelper.getSetting("deleted_book_titles")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val dbDeletedPaths = repository.dbHelper.getSetting("deleted_book_paths")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

        val deletedIds = (repository.prefs.getStringSet("deleted_book_ids", emptySet()) ?: emptySet()) + dbDeletedIds
        val deletedTitles = (repository.prefs.getStringSet("deleted_book_titles", emptySet()) ?: emptySet()) + dbDeletedTitles
        val deletedPaths = (repository.prefs.getStringSet("deleted_book_paths", emptySet()) ?: emptySet()) + dbDeletedPaths

        if (dbDeletedIds.isNotEmpty() || dbDeletedTitles.isNotEmpty()) {
            repository.prefs.edit()
                .putStringSet("deleted_book_ids", deletedIds)
                .putStringSet("deleted_book_titles", deletedTitles)
                .putStringSet("deleted_book_paths", deletedPaths)
                .apply()
        }

        val fromDb = try { repository.dbHelper.getAllBooks() } catch (_: Exception) { emptyList() }

        // Filter out deleted books from DB and purge them from DB
        val activeFromDb = fromDb.filterNot { b ->
            val normT = b.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            val isDeleted = b.id in deletedIds ||
                b.title.trim().lowercase() in deletedTitles ||
                (normT.isNotBlank() && normT in deletedTitles) ||
                (b.filePath.isNotBlank() && (b.filePath in deletedPaths || try { File(b.filePath).canonicalPath in deletedPaths } catch (_: Exception) { false }))
            if (isDeleted) {
                try { repository.dbHelper.deleteBook(b.id) } catch (_: Exception) {}
            }
            isDeleted
        }

        val resolvedBooks = activeFromDb.map { raw ->
            if (raw.coverUrl.contains("images.unsplash.com")) raw.copy(coverUrl = "") else raw
        }

        val validBooks = resolvedBooks.filterNot { b ->
            b.id in setOf("book-kafka", "book-alice", "book-artofwar", "1", "2", "3", "demo-kafka", "demo-alice", "demo-artofwar")
        }

        repository.repoScope.launch(Dispatchers.IO) {
            reconcileMissingBookPaths(repository, validBooks)
        }

        val deduplicated = mutableListOf<Book>()
        val seenSignatures = mutableSetOf<String>()

        for (book in validBooks) {
            val normTitle = book.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            val normAuthor = book.author.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            val signature = if (normTitle.isNotBlank()) "$normTitle::$normAuthor" else book.id
            val pathSig = if (book.filePath.isNotBlank()) {
                try { File(book.filePath).canonicalPath } catch (_: Exception) { book.filePath }
            } else ""

            val isDup = (signature.isNotBlank() && !seenSignatures.add(signature)) ||
                        (pathSig.isNotBlank() && !seenSignatures.add(pathSig))

            if (isDup) {
                val existingMaster = deduplicated.find { isDuplicateBook(it, book) }
                if (existingMaster != null) {
                    if (book.progress > existingMaster.progress) {
                        val merged = existingMaster.copy(
                            progress = book.progress,
                            currentChapter = book.currentChapter,
                            currentPage = book.currentPage,
                            scrollPos = book.scrollPos,
                            lastRead = book.lastRead
                        )
                        deduplicated.remove(existingMaster)
                        deduplicated.add(merged)
                        repository.dbHelper.updateBookProgress(merged.id, merged.currentChapter, merged.currentPage, merged.scrollPos, merged.progress, merged.lastRead)
                    }
                }
                try { repository.dbHelper.deleteBook(book.id) } catch (_: Exception) {}
            } else {
                deduplicated.add(book)
            }
        }

        return deduplicated
    }

    suspend fun autoScanAndLoadPersistentEpubs(repository: BookRepository) = withContext(Dispatchers.IO) {
        try {
            val scannedFiles = LuminaStorageManager.scanEpubFiles(repository.context)
            if (scannedFiles.isEmpty()) return@withContext

            val currentBooks = repository._books.value
            val existingPaths = currentBooks.mapNotNull { it.filePath.takeIf { p -> p.isNotBlank() } }
                .map { try { File(it).canonicalPath } catch (_: Exception) { it } }.toSet()
            val existingTitles = currentBooks.map { it.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }.filter { it.isNotBlank() }.toSet()
            
            val dbDeletedIds = repository.dbHelper.getSetting("deleted_book_ids")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val dbDeletedTitles = repository.dbHelper.getSetting("deleted_book_titles")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val dbDeletedPaths = repository.dbHelper.getSetting("deleted_book_paths")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

            val deletedIds = (repository.prefs.getStringSet("deleted_book_ids", emptySet()) ?: emptySet()) + dbDeletedIds
            val deletedTitles = (repository.prefs.getStringSet("deleted_book_titles", emptySet()) ?: emptySet()) + dbDeletedTitles
            val deletedPaths = (repository.prefs.getStringSet("deleted_book_paths", emptySet()) ?: emptySet()) + dbDeletedPaths

            val dbBooks = try { repository.dbHelper.getAllBooks() } catch (_: Exception) { emptyList() }
            val dbPathMap = dbBooks.filter { it.filePath.isNotBlank() }.associateBy {
                try { File(it.filePath).canonicalPath } catch (_: Exception) { it.filePath }
            }
            val dbFileNameMap = dbBooks.filter { it.filePath.isNotBlank() }.associateBy {
                it.filePath.substringAfterLast('/')
            }

            val newlyDiscoveredBooks = mutableListOf<Book>()
            val updatedBooks = mutableListOf<Book>()

            for (epubFile in scannedFiles) {
                val canonical = try { epubFile.canonicalPath } catch (_: Exception) { epubFile.absolutePath }

                if (canonical in deletedPaths || epubFile.name in deletedIds || epubFile.nameWithoutExtension in deletedIds) {
                    try {
                        val persistentDir = LuminaStorageManager.getPersistentEpubDirectory(repository.context)
                        if (epubFile.absolutePath.startsWith(persistentDir.absolutePath) || epubFile.absolutePath.startsWith(repository.context.filesDir.absolutePath)) {
                            epubFile.delete()
                        }
                    } catch (_: Exception) {}
                    continue
                }

                if (canonical in existingPaths) continue

                val dbMatch = dbPathMap[canonical] ?: dbFileNameMap[epubFile.name]
                if (dbMatch != null) {
                    if (dbMatch.filePath != canonical) {
                        repository.dbHelper.updateBookFilePath(dbMatch.id, canonical, epubFile.length())
                        val updated = dbMatch.copy(filePath = canonical, fileSize = epubFile.length())
                        updatedBooks.add(updated)
                    }
                    continue
                }

                try {
                    val metadataBook = EpubParser.parseBookMetadata(epubFile, repository.context)
                    val normTitle = metadataBook.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                    if (normTitle in existingTitles || metadataBook.title.trim().lowercase() in deletedTitles) {
                        if (metadataBook.title.trim().lowercase() in deletedTitles) {
                            try {
                                val persistentDir = LuminaStorageManager.getPersistentEpubDirectory(repository.context)
                                if (epubFile.absolutePath.startsWith(persistentDir.absolutePath) || epubFile.absolutePath.startsWith(repository.context.filesDir.absolutePath)) {
                                    epubFile.delete()
                                }
                            } catch (_: Exception) {}
                        }
                        continue
                    }
                    if (newlyDiscoveredBooks.any { isDuplicateBook(it, metadataBook) }) {
                        continue
                    }
                    val bookToSave = metadataBook.copy(
                        filePath = epubFile.absolutePath,
                        fileSize = epubFile.length(),
                        isDownloaded = false
                    )
                    repository.dbHelper.insertOrUpdateBook(bookToSave, bookToSave.filePath, bookToSave.isDownloaded, bookToSave.downloadUrl, bookToSave.fileSize)
                    newlyDiscoveredBooks.add(bookToSave)
                    android.util.Log.i("BookRepository", "Auto-loaded EPUB metadata: ${epubFile.name} as \"${bookToSave.title}\"")
                } catch (e: Exception) {
                    android.util.Log.w("BookRepository", "Failed to auto-load EPUB ${epubFile.name}: ${e.message}")
                }
            }

            if (newlyDiscoveredBooks.isNotEmpty() || updatedBooks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    var merged = repository._books.value
                    if (updatedBooks.isNotEmpty()) {
                        val updatedMap = updatedBooks.associateBy { it.id }
                        merged = merged.map { updatedMap[it.id] ?: it }
                    }
                    if (newlyDiscoveredBooks.isNotEmpty()) {
                        val toAppend = newlyDiscoveredBooks.filterNot { newBook ->
                            merged.any { isDuplicateBook(it, newBook) }
                        }
                        if (toAppend.isNotEmpty()) {
                            merged = merged + toAppend
                        }
                    }
                    repository._books.value = merged
                }
            }

            try {
                val backupFile = LuminaStorageManager.getPersistentBackupFile(repository.context)
                repository.dbHelper.backupStateToPersistentFile(backupFile)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "Error in autoScanAndLoadPersistentEpubs: ${e.message}")
        }
    }

    suspend fun refreshLibrary(repository: BookRepository) = withContext(Dispatchers.IO) {
        repository._activeBackgroundTask.value = "Scanning library for EPUBs..."
        try {
            LuminaStorageManager.migrateLegacyFiles(repository.context)
            autoScanAndLoadPersistentEpubs(repository)
            try {
                val backupFile = LuminaStorageManager.getPersistentBackupFile(repository.context)
                repository.dbHelper.backupStateToPersistentFile(backupFile)
            } catch (_: Exception) {}
        } finally {
            repository._activeBackgroundTask.value = null
        }
    }

    fun scanAndSyncPersistentEpubs(repository: BookRepository) {
        repository.repoScope.launch(Dispatchers.IO) {
            refreshLibrary(repository)
        }
    }
}
