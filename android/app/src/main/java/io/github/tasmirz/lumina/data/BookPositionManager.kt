package io.github.tasmirz.lumina.data

import io.github.tasmirz.lumina.model.ReadingPosition
import kotlinx.coroutines.launch

/**
 * Handles reading position tracking, progress percentages, verified page bookmarks,
 * and debounced SQLite updates.
 */
object BookPositionManager {

    fun resetReadingProgress(repository: BookRepository) {
        val prefs = repository.prefs
        repository._books.value = repository._books.value.map { b ->
            prefs.edit()
                .putInt("${b.id}_chapter", 0)
                .putInt("${b.id}_page", 0)
                .putInt("${b.id}_scroll", 0)
                .apply()
            b.copy(progress = 0, currentChapter = 0, currentPage = 0, scrollPos = 0)
        }
    }

    fun setReadingProgressPercent(repository: BookRepository, bookId: String, percent: Int) {
        val book = repository._books.value.find { it.id == bookId } ?: return
        val totalChapters = book.chapters.size
        if (totalChapters == 0) return
        val clampedPercent = percent.coerceIn(0, 100)
        val targetChapter = ((clampedPercent.toFloat() / 100f) * (totalChapters - 1)).toInt().coerceIn(0, totalChapters - 1)
        updateReadingPosition(
            repository = repository,
            bookId = bookId,
            chapterIdx = targetChapter,
            pageIdx = 0,
            scrollPos = 0,
            progressPct = clampedPercent
        )
    }

    fun getReadTillPercent(repository: BookRepository, bookId: String): Int {
        val inMemory = repository._readTillMap.value[bookId]
        if (inMemory != null) return inMemory
        val persisted = repository.prefs.getInt("${bookId}_read_till", -1)
        if (persisted != -1) {
            repository._readTillMap.value = repository._readTillMap.value + (bookId to persisted)
            return persisted
        }
        val bookProg = repository._books.value.find { it.id == bookId }?.progress ?: 0
        repository._readTillMap.value = repository._readTillMap.value + (bookId to bookProg)
        return bookProg
    }

    fun getVerifiedPage(repository: BookRepository, bookId: String): Int {
        return repository.prefs.getInt("${bookId}_verified_page", -1)
    }

    fun setVerifiedPage(repository: BookRepository, bookId: String, page: Int) {
        repository.prefs.edit().putInt("${bookId}_verified_page", page).apply()
        repository.persistSettingToDb("verified_page_${bookId}", page.toString())
    }

    fun forceSetReadTillPercent(repository: BookRepository, bookId: String, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        repository._readTillMap.value = repository._readTillMap.value + (bookId to clamped)
        repository.prefs.edit().putInt("${bookId}_read_till", clamped).apply()
        repository.persistSettingToDb("read_till_${bookId}", clamped.toString())
    }

    fun forceSetLastReadPosition(
        repository: BookRepository,
        bookId: String,
        chapterIdx: Int,
        pageIdx: Int,
        scrollPos: Int,
        progressPct: Int
    ) {
        val clamped = progressPct.coerceIn(0, 100)
        repository._readTillMap.value = repository._readTillMap.value + (bookId to clamped)
        repository.prefs.edit().putInt("${bookId}_read_till", clamped).apply()
        repository.persistSettingToDb("read_till_${bookId}", clamped.toString())
        setVerifiedPage(repository, bookId, pageIdx)

        val updated = repository._books.value.map {
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
        repository._books.value = updated

        repository.prefs.edit()
            .putInt("${bookId}_chapter", chapterIdx)
            .putInt("${bookId}_page", pageIdx)
            .putInt("${bookId}_scroll", scrollPos)
            .putInt("${bookId}_progress", clamped)
            .apply()

        repository.repoScope.launch {
            try {
                val book = repository._books.value.find { it.id == bookId }
                if (book != null) {
                    repository.dbHelper.updateReadingProgress(bookId, book.title, book.author, clamped, "${100 - clamped}m left")
                    repository.dbHelper.updateBookProgress(bookId, chapterIdx, pageIdx, scrollPos, clamped)
                }
            } catch (_: Exception) {}
        }
    }

    fun updateReadingPosition(
        repository: BookRepository,
        bookId: String,
        chapterIdx: Int,
        pageIdx: Int,
        scrollPos: Int,
        progressPct: Int
    ) {
        val currentPos = repository._readingPosition.value
        if (currentPos.bookId == bookId &&
            currentPos.chapterIndex == chapterIdx &&
            currentPos.pageIndex == pageIdx &&
            currentPos.scrollPos == scrollPos &&
            currentPos.progressPct == progressPct
        ) {
            return
        }

        repository._readingPosition.value = ReadingPosition(bookId, chapterIdx, pageIdx, scrollPos, progressPct)

        // Persist per book in prefs immediately
        repository.prefs.edit()
            .putInt("${bookId}_chapter", chapterIdx)
            .putInt("${bookId}_page", pageIdx)
            .putInt("${bookId}_scroll", scrollPos)
            .putInt("${bookId}_progress", progressPct)
            .apply()

        // Incremental advance: furthest place scrolled/read is read till
        val currentReadTill = getReadTillPercent(repository, bookId)
        if (progressPct > currentReadTill) {
            val clamped = progressPct.coerceIn(0, 100)
            repository._readTillMap.value = repository._readTillMap.value + (bookId to clamped)
            repository.prefs.edit().putInt("${bookId}_read_till", clamped).apply()
            repository.persistSettingToDb("read_till_${bookId}", clamped.toString())
        }

        // Debounce updating _books.value and SQLite reading progress updates
        repository.readingPositionSaveJob?.cancel()
        repository.readingPositionSaveJob = repository.repoScope.launch {
            kotlinx.coroutines.delay(800)
            try {
                val updated = repository._books.value.map {
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
                repository._books.value = updated

                val book = updated.find { it.id == bookId }
                if (book != null) {
                    repository.dbHelper.updateReadingProgress(bookId, book.title, book.author, progressPct, "${100 - progressPct}m left")
                    repository.dbHelper.updateBookProgress(bookId, chapterIdx, pageIdx, scrollPos, progressPct)
                }
            } catch (_: Exception) {}
        }
    }

    fun updateCharacterCheckpoint(
        repository: BookRepository,
        bookId: String,
        checkpointChapter: Int,
        checkpointPage: Int
    ) {
        val updated = repository._books.value.map {
            if (it.id == bookId) {
                it.copy(
                    characterCheckpointChapter = checkpointChapter,
                    characterCheckpointPage = checkpointPage
                )
            } else it
        }
        repository._books.value = updated
        repository.repoScope.launch {
            try {
                repository.dbHelper.updateCharacterCheckpoint(bookId, checkpointChapter, checkpointPage)
            } catch (_: Exception) {}
        }
    }
}
