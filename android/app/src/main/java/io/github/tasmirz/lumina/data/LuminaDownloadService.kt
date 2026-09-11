package io.github.tasmirz.lumina.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.tasmirz.lumina.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class DownloadProgressState(
    val bookId: String,
    val title: String,
    val progress: Int, // 0..100, or -1 for indeterminate
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

class LuminaDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "lumina_downloads"
        const val NOTIFICATION_ID_BASE = 3000

        const val ACTION_START_DOWNLOAD = "io.github.tasmirz.lumina.action.START_DOWNLOAD"
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_DOWNLOAD_URL = "extra_download_url"

        private val _downloadStates = MutableStateFlow<Map<String, DownloadProgressState>>(emptyMap())
        val downloadStates: StateFlow<Map<String, DownloadProgressState>> = _downloadStates.asStateFlow()

        fun downloadBook(
            context: Context,
            bookId: String,
            title: String,
            author: String,
            coverUrl: String,
            downloadUrl: String
        ) {
            val intent = Intent(context, LuminaDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_AUTHOR, author)
                putExtra(EXTRA_COVER_URL, coverUrl)
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Book Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and status of EPUB book downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return START_NOT_STICKY
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Book"
            val author = intent.getStringExtra(EXTRA_AUTHOR) ?: "Unknown"
            val coverUrl = intent.getStringExtra(EXTRA_COVER_URL) ?: ""
            val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: return START_NOT_STICKY

            startDownload(bookId, title, author, coverUrl, downloadUrl)
        }
        return START_NOT_STICKY
    }

    private fun startDownload(
        bookId: String,
        title: String,
        author: String,
        coverUrl: String,
        downloadUrl: String
    ) {
        val notificationId = NOTIFICATION_ID_BASE + (bookId.hashCode() and 0x7FFF)

        // Initial notification
        val initialNotif = buildProgressNotification(title, 0, "Starting download...")
        startForeground(notificationId, initialNotif)

        _downloadStates.value = _downloadStates.value + (bookId to DownloadProgressState(
            bookId = bookId,
            title = title,
            progress = 0
        ))

        serviceScope.launch {
            try {
                val epubDir = LuminaStorageManager.getPersistentEpubDirectory(this@LuminaDownloadService)
                val sanitized = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                val cleanName = (if (sanitized.isNotBlank()) sanitized else "book_${System.currentTimeMillis()}") + ".epub"
                var destFile = File(epubDir, "${System.currentTimeMillis()}_$cleanName")
                try {
                    destFile.parentFile?.mkdirs()
                } catch (_: Throwable) {}

                var lastReportedPercent = -1
                var lastUpdateTime = 0L

                val downloadSuccess = executeDownload(
                    downloadUrl = downloadUrl,
                    destFile = destFile,
                    onProgress = { bytesRead, totalBytes ->
                        val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
                        val now = System.currentTimeMillis()
                        if (percent != lastReportedPercent && (now - lastUpdateTime > 350 || percent == 100)) {
                            lastReportedPercent = percent
                            lastUpdateTime = now
                            _downloadStates.value = _downloadStates.value + (bookId to DownloadProgressState(
                                bookId = bookId,
                                title = title,
                                progress = percent
                            ))
                            val progressText = if (percent >= 0) "$percent% • ${formatBytes(bytesRead)}" else formatBytes(bytesRead)
                            notificationManager.notify(
                                notificationId,
                                buildProgressNotification(title, percent, progressText)
                            )
                        }
                    }
                )

                if (downloadSuccess) {
                    // Verify file is a valid ZIP/EPUB by checking magic header PK\u0003\u0004
                    if (destFile.length() < 100 || !isValidZip(destFile)) {
                        destFile.delete()
                        throw IllegalStateException("Downloaded file is not a valid EPUB archive (server may have sent an HTML error or block page).")
                    }

                    // Parse and insert into repository singleton
                    val repository = BookRepository.getInstance(applicationContext)
                    destFile.inputStream().use { stream ->
                        val parsed = EpubParser.parseEpub(stream, cleanName, applicationContext)
                        val finalId = if (bookId.isNotBlank()) bookId else parsed.id
                        val finalTitle = if (parsed.title.isNotBlank() && !parsed.title.startsWith("Document:", ignoreCase = true)) {
                            parsed.title
                        } else if (title.isNotBlank()) {
                            title
                        } else {
                            parsed.title
                        }
                        val finalAuthor = if (parsed.author.isNotBlank() && parsed.author != "Unknown Author") {
                            parsed.author
                        } else if (author.isNotBlank()) {
                            author
                        } else {
                            parsed.author
                        }
                        val finalCover = if (coverUrl.isNotBlank() && (parsed.coverUrl.isBlank() || parsed.coverUrl.startsWith("http"))) {
                            coverUrl
                        } else {
                            parsed.coverUrl
                        }

                        val readyBook = parsed.copy(
                            id = finalId,
                            title = finalTitle,
                            author = finalAuthor,
                            coverUrl = finalCover,
                            filePath = destFile.absolutePath,
                            fileSize = destFile.length(),
                            isDownloaded = true,
                            downloadUrl = downloadUrl
                        )
                        repository.addBook(readyBook)

                        // If book was in wishlist, update wishlist
                        try {
                            val wishlist = repository.wishlistBooks.value
                            val matchInWishlist = wishlist.find { it.id == finalId || it.title.equals(finalTitle, ignoreCase = true) }
                            if (matchInWishlist != null) {
                                repository.removeFromWishlist(matchInWishlist.id)
                            }
                        } catch (_: Throwable) {}
                    }

                    _downloadStates.value = _downloadStates.value + (bookId to DownloadProgressState(
                        bookId = bookId,
                        title = title,
                        progress = 100,
                        isComplete = true
                    ))

                    notificationManager.notify(
                        notificationId,
                        buildCompleteNotification(title)
                    )
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val reason = when (e) {
                    is SocketTimeoutException -> "Connection timed out while downloading."
                    is UnknownHostException -> "Unable to reach server. Please check internet connection."
                    is IllegalStateException -> e.message ?: "Invalid EPUB file received."
                    else -> e.message ?: "Download encountered an unexpected error."
                }

                _downloadStates.value = _downloadStates.value + (bookId to DownloadProgressState(
                    bookId = bookId,
                    title = title,
                    progress = 0,
                    isFailed = true,
                    errorMessage = reason
                ))

                notificationManager.notify(
                    notificationId,
                    buildFailureNotification(title, reason, bookId, author, coverUrl, downloadUrl)
                )
            } finally {
                // If no more active downloads, stop foreground
                val hasActive = _downloadStates.value.values.any { !it.isComplete && !it.isFailed }
                if (!hasActive) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
            }
        }
    }

    private fun executeDownload(
        downloadUrl: String,
        destFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Boolean {
        var currentUrl = downloadUrl.trim()
        if (currentUrl.contains("standardebooks.org") && !currentUrl.contains("source=download") && currentUrl.endsWith(".epub")) {
            currentUrl = if (currentUrl.contains("?")) "$currentUrl&source=download" else "$currentUrl?source=download"
        }

        var redirects = 0
        while (redirects < 7) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "application/epub+zip,application/octet-stream,*/*")
            }

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("HTTP redirect ($code) without Location header.")
                currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                redirects++
                continue
            } else if (code == 200) {
                val contentType = conn.contentType ?: ""
                // Handle Standard Ebooks intermediate HTML meta-refresh
                if (contentType.contains("html", ignoreCase = true) || contentType.contains("xhtml", ignoreCase = true)) {
                    val htmlBody = conn.inputStream.bufferedReader().use { it.readText() }
                    val refreshMatch = Regex("""meta[^>]+url=([^"'>\s]+)""", RegexOption.IGNORE_CASE).find(htmlBody)
                    val targetHref = refreshMatch?.groupValues?.getOrNull(1)
                        ?: Regex("""href="([^"]+\.epub(\?source=download)?)"""", RegexOption.IGNORE_CASE).find(htmlBody)?.groupValues?.getOrNull(1)
                    if (!targetHref.isNullOrBlank()) {
                        currentUrl = if (targetHref.startsWith("http")) targetHref else URL(url, targetHref).toString()
                        redirects++
                        continue
                    } else {
                        throw IllegalStateException("Server returned an HTML page instead of EPUB content.")
                    }
                }

                val totalBytes = conn.contentLengthLong
                var bytesReadTotal = 0L
                val buffer = ByteArray(8192)

                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        var bytes = input.read(buffer)
                        while (bytes != -1) {
                            output.write(buffer, 0, bytes)
                            bytesReadTotal += bytes
                            onProgress(bytesReadTotal, totalBytes)
                            bytes = input.read(buffer)
                        }
                        output.flush()
                    }
                }
                return true
            } else {
                val errorMsg = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText().take(150) }
                } catch (_: Exception) { null }
                val detail = if (!errorMsg.isNullOrBlank()) ": $errorMsg" else ""
                throw IllegalStateException("Server responded with HTTP $code ($errorMsg)$detail")
            }
        }
        throw IllegalStateException("Too many redirects encountered while resolving download URL.")
    }

    private fun isValidZip(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }

    private fun buildProgressNotification(title: String, percent: Int, progressText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $title")
            .setContentText(progressText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (percent >= 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(100, 0, true)
        }
        return builder.build()
    }

    private fun buildCompleteNotification(title: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("\"$title\" is ready to read in your Library.")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun buildFailureNotification(
        title: String,
        reason: String,
        bookId: String,
        author: String,
        coverUrl: String,
        downloadUrl: String
    ): Notification {
        val retryIntent = Intent(this, LuminaDownloadService::class.java).apply {
            action = ACTION_START_DOWNLOAD
            putExtra(EXTRA_BOOK_ID, bookId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_AUTHOR, author)
            putExtra(EXTRA_COVER_URL, coverUrl)
            putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
        }
        val retryPendingIntent = PendingIntent.getService(
            this, (bookId.hashCode() and 0x7FFF), retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed: $title")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Failed to download \"$title\":\n$reason"))
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_rotate, "Retry", retryPendingIntent)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
