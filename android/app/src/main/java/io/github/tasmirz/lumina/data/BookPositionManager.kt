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

        val dynamicTime = computeDynamicTimeRemaining(repository, bookId, clamped)
        repository.repoScope.launch {
            try {
                val book = repository._books.value.find { it.id == bookId }
                if (book != null) {
                    repository.dbHelper.updateReadingProgress(bookId, book.title, book.author, clamped, dynamicTime)
                    repository.dbHelper.updateBookProgress(bookId, chapterIdx, pageIdx, scrollPos, clamped, "Just now", dynamicTime)
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
                val dynamicTime = computeDynamicTimeRemaining(repository, bookId, progressPct)
                val updated = repository._books.value.map {
                    if (it.id == bookId) {
                        it.copy(
                            currentChapter = chapterIdx,
                            currentPage = pageIdx,
                            scrollPos = scrollPos,
                            progress = progressPct,
                            lastRead = "Just now",
                            readTimeLeft = dynamicTime
                        )
                    } else it
                }
                repository._books.value = updated

                val book = updated.find { it.id == bookId }
                if (book != null) {
                    repository.dbHelper.updateReadingProgress(bookId, book.title, book.author, progressPct, dynamicTime)
                    repository.dbHelper.updateBookProgress(bookId, chapterIdx, pageIdx, scrollPos, progressPct, "Just now", dynamicTime)
                }
            } catch (_: Exception) {}
        }
    }

    fun computeDynamicTimeRemaining(
        repository: BookRepository,
        bookId: String,
        currentProgressPct: Int
    ): String {
        val clampedProg = currentProgressPct.coerceIn(0, 100)
        if (clampedProg >= 100) return "Finished"

        val learnedWpm = repository.prefs.getInt("learned_wpm_${bookId}", repository.prefs.getInt("global_learned_wpm", 220)).coerceIn(80, 800)
        val book = repository._books.value.find { it.id == bookId }

        val remainingFraction = (100 - clampedProg) / 100.0f
        val totalCharsEst = if (book != null && book.chapters.isNotEmpty()) {
            val sum = book.chapters.sumOf { chap -> chap.paragraphs.sumOf { it.length } }
            if (sum > 500) sum else book.chapters.size * 3200
        } else {
            50_000
        }
        val remainingChars = (totalCharsEst * remainingFraction).toInt()
        val charsPerMin = (learnedWpm * 5.0f).coerceAtLeast(300f)
        val minsLeft = kotlin.math.ceil(remainingChars / charsPerMin).toInt().coerceAtLeast(1)

        return if (minsLeft >= 60) {
            val hours = minsLeft / 60
            val remMins = minsLeft % 60
            if (remMins > 0) "${hours}h ${remMins}m left" else "${hours}h left"
        } else {
            "${minsLeft}m left"
        }
    }

    fun recordReadingDwell(
        repository: BookRepository,
        bookId: String,
        charsRead: Int,
        dwellSeconds: Long
    ) {
        if (charsRead <= 20 || dwellSeconds < 3L || dwellSeconds > 180L) return
        val wpmThisSession = ((charsRead / 5.0f) / (dwellSeconds / 60.0f)).toInt().coerceIn(60, 900)
        val currentLearned = repository.prefs.getInt("learned_wpm_${bookId}", repository.prefs.getInt("global_learned_wpm", 220))
        // Smooth exponential moving average (EMA alpha = 0.25)
        val updatedWpm = ((currentLearned * 0.75f) + (wpmThisSession * 0.25f)).toInt().coerceIn(80, 800)

        repository.prefs.edit()
            .putInt("learned_wpm_${bookId}", updatedWpm)
            .putInt("global_learned_wpm", updatedWpm)
            .apply()
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
