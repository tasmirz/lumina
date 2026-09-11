package io.github.tasmirz.lumina.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import io.github.tasmirz.lumina.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val bookId: String = "",
    val bookTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val paragraphIndex: Int = 0,
    val paragraphSnippet: String = "",
    val paragraphs: List<String> = emptyList(),
    val isEdgeTts: Boolean = true,
    val voice: String = "en-US-JennyNeural",
    val speed: Float = 1.0f
)

class LuminaAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "lumina_audio_playback"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "io.github.tasmirz.lumina.action.PLAY"
        const val ACTION_PAUSE = "io.github.tasmirz.lumina.action.PAUSE"
        const val ACTION_TOGGLE = "io.github.tasmirz.lumina.action.TOGGLE"
        const val ACTION_PREV = "io.github.tasmirz.lumina.action.PREV"
        const val ACTION_NEXT = "io.github.tasmirz.lumina.action.NEXT"
        const val ACTION_STOP = "io.github.tasmirz.lumina.action.STOP"

        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
        const val EXTRA_CHAPTER_INDEX = "extra_chapter_index"
        const val EXTRA_CHAPTER_TITLE = "extra_chapter_title"
        const val EXTRA_PARAGRAPH_INDEX = "extra_paragraph_index"
        const val EXTRA_PARAGRAPHS = "extra_paragraphs"
        const val EXTRA_IS_EDGE_TTS = "extra_is_edge_tts"
        const val EXTRA_VOICE = "extra_voice"
        const val EXTRA_SPEED = "extra_speed"

        private val _playbackState = MutableStateFlow(AudioPlaybackState())
        val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

        fun startOrUpdate(
            context: Context,
            bookId: String,
            bookTitle: String,
            chapterIndex: Int,
            chapterTitle: String,
            paragraphIndex: Int,
            paragraphs: List<String>,
            isEdgeTts: Boolean,
            voice: String,
            speed: Float
        ) {
            val intent = Intent(context, LuminaAudioService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_BOOK_TITLE, bookTitle)
                putExtra(EXTRA_CHAPTER_INDEX, chapterIndex)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
                putExtra(EXTRA_PARAGRAPH_INDEX, paragraphIndex)
                putStringArrayListExtra(EXTRA_PARAGRAPHS, ArrayList(paragraphs))
                putExtra(EXTRA_IS_EDGE_TTS, isEdgeTts)
                putExtra(EXTRA_VOICE, voice)
                putExtra(EXTRA_SPEED, speed)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun togglePlayPause(context: Context) {
            val intent = Intent(context, LuminaAudioService::class.java).apply {
                action = ACTION_TOGGLE
            }
            context.startService(intent)
        }

        fun nextParagraph(context: Context) {
            val intent = Intent(context, LuminaAudioService::class.java).apply {
                action = ACTION_NEXT
            }
            context.startService(intent)
        }

        fun prevParagraph(context: Context) {
            val intent = Intent(context, LuminaAudioService::class.java).apply {
                action = ACTION_PREV
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LuminaAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var systemTts: TextToSpeech? = null
    private var isSystemTtsReady = false
    private var currentSessionId = 0L
    private var playJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initSystemTts()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audiobook & Narration",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls background audio narration playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun initSystemTts() {
        systemTts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isSystemTtsReady = true
                systemTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        serviceScope.launch {
                            advanceToNextParagraph()
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        serviceScope.launch {
                            advanceToNextParagraph()
                        }
                    }
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_PLAY -> {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: _playbackState.value.bookId
                val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: _playbackState.value.bookTitle
                val chapterIndex = intent.getIntExtra(EXTRA_CHAPTER_INDEX, _playbackState.value.chapterIndex)
                val chapterTitle = intent.getStringExtra(EXTRA_CHAPTER_TITLE) ?: _playbackState.value.chapterTitle
                val paragraphIndex = intent.getIntExtra(EXTRA_PARAGRAPH_INDEX, _playbackState.value.paragraphIndex)
                val paragraphs = intent.getStringArrayListExtra(EXTRA_PARAGRAPHS) ?: ArrayList(_playbackState.value.paragraphs)
                val isEdgeTts = intent.getBooleanExtra(EXTRA_IS_EDGE_TTS, _playbackState.value.isEdgeTts)
                val voice = intent.getStringExtra(EXTRA_VOICE) ?: _playbackState.value.voice
                val speed = intent.getFloatExtra(EXTRA_SPEED, _playbackState.value.speed)

                _playbackState.value = AudioPlaybackState(
                    isPlaying = true,
                    bookId = bookId,
                    bookTitle = bookTitle,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    paragraphIndex = paragraphIndex,
                    paragraphSnippet = paragraphs.getOrNull(paragraphIndex)?.take(100) ?: "",
                    paragraphs = paragraphs,
                    isEdgeTts = isEdgeTts,
                    voice = voice,
                    speed = speed
                )
                startForeground(NOTIFICATION_ID, buildNotification())
                playCurrentParagraph()
            }
            ACTION_PAUSE -> {
                pausePlayback()
            }
            ACTION_TOGGLE -> {
                if (_playbackState.value.isPlaying) {
                    pausePlayback()
                } else {
                    resumePlayback()
                }
            }
            ACTION_NEXT -> {
                advanceToNextParagraph()
            }
            ACTION_PREV -> {
                previousParagraph()
            }
            ACTION_STOP -> {
                stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun pausePlayback() {
        currentSessionId++
        playJob?.cancel()
        playJob = null

        try { mediaPlayer?.pause() } catch (_: Throwable) {}
        try { systemTts?.stop() } catch (_: Throwable) {}

        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun resumePlayback() {
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
        startForeground(NOTIFICATION_ID, buildNotification())
        playCurrentParagraph()
    }

    private fun stopPlayback() {
        currentSessionId++
        playJob?.cancel()
        playJob = null

        mediaPlayer?.let { mp ->
            try { mp.stop() } catch (_: Throwable) {}
            try { mp.reset() } catch (_: Throwable) {}
            try { mp.release() } catch (_: Throwable) {}
        }
        mediaPlayer = null

        try { systemTts?.stop() } catch (_: Throwable) {}

        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    private fun playCurrentParagraph() {
        val state = _playbackState.value
        val paragraphs = state.paragraphs
        val pIdx = state.paragraphIndex

        if (paragraphs.isEmpty() || pIdx !in paragraphs.indices) {
            pausePlayback()
            return
        }

        val rawText = paragraphs[pIdx]
        val cleanText = if (rawText.startsWith("[IMG:") && rawText.endsWith("]")) {
            advanceToNextParagraph()
            return
        } else rawText

        if (cleanText.isBlank()) {
            advanceToNextParagraph()
            return
        }

        _playbackState.value = state.copy(
            paragraphSnippet = cleanText.take(120),
            isPlaying = true
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())

        currentSessionId++
        val sessionId = currentSessionId

        playJob?.cancel()
        playJob = serviceScope.launch {
            if (state.isEdgeTts) {
                val audioBytes = withContext(Dispatchers.IO) {
                    if (sessionId != currentSessionId || !_playbackState.value.isPlaying) return@withContext null
                    EdgeTtsService.synthesizeToBytes(
                        text = cleanText,
                        voice = state.voice,
                        speedMultiplier = state.speed
                    )
                }

                if (sessionId != currentSessionId || !_playbackState.value.isPlaying) return@launch

                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    val tempFile = File(cacheDir, "lumina_bg_tts_${sessionId}.mp3")
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                    }
                    mediaPlayer?.let { old ->
                        try { old.release() } catch (_: Throwable) {}
                    }
                    mediaPlayer = mp

                    try {
                        withContext(Dispatchers.IO) {
                            tempFile.writeBytes(audioBytes)
                        }

                        if (sessionId != currentSessionId || !_playbackState.value.isPlaying) {
                            try { mp.release() } catch (_: Throwable) {}
                            try { tempFile.delete() } catch (_: Throwable) {}
                            return@launch
                        }

                        mp.setDataSource(tempFile.absolutePath)
                        mp.prepare()
                        mp.setOnCompletionListener {
                            try { it.release() } catch (_: Throwable) {}
                            try { tempFile.delete() } catch (_: Throwable) {}
                            if (mediaPlayer == it) mediaPlayer = null
                            if (sessionId == currentSessionId && _playbackState.value.isPlaying) {
                                advanceToNextParagraph()
                            }
                        }
                        mp.setOnErrorListener { it, _, _ ->
                            try { it.release() } catch (_: Throwable) {}
                            try { tempFile.delete() } catch (_: Throwable) {}
                            if (mediaPlayer == it) mediaPlayer = null
                            if (sessionId == currentSessionId && _playbackState.value.isPlaying) {
                                fallbackToSystemTts(cleanText, sessionId)
                            }
                            true
                        }

                        // Silence system TTS before starting MediaPlayer
                        try { systemTts?.stop() } catch (_: Throwable) {}
                        mp.start()
                    } catch (_: Exception) {
                        try { mp.release() } catch (_: Throwable) {}
                        if (mediaPlayer == mp) mediaPlayer = null
                        if (sessionId == currentSessionId && _playbackState.value.isPlaying) {
                            fallbackToSystemTts(cleanText, sessionId)
                        }
                    }
                } else {
                    fallbackToSystemTts(cleanText, sessionId)
                }
            } else {
                fallbackToSystemTts(cleanText, sessionId)
            }
        }
    }

    private fun fallbackToSystemTts(text: String, sessionId: Long) {
        if (sessionId != currentSessionId || !_playbackState.value.isPlaying) return
        mediaPlayer?.let {
            try { it.release() } catch (_: Throwable) {}
        }
        mediaPlayer = null

        val tts = systemTts
        if (tts != null && isSystemTtsReady) {
            tts.setSpeechRate(_playbackState.value.speed)
            try {
                val repo = BookRepository(applicationContext)
                val effLang = repo.getEffectiveLanguage()
                val targetLocale = if (effLang.isNotBlank() && effLang != "auto") {
                    try { Locale.forLanguageTag(effLang) } catch (_: Exception) { Locale.getDefault() }
                } else {
                    Locale.getDefault()
                }
                tts.language = targetLocale
            } catch (_: Throwable) {}
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lumina_utterance_$sessionId")
        } else {
            pausePlayback()
        }
    }

    private fun advanceToNextParagraph() {
        val state = _playbackState.value
        val nextIdx = state.paragraphIndex + 1
        if (nextIdx < state.paragraphs.size) {
            _playbackState.value = state.copy(paragraphIndex = nextIdx)
            playCurrentParagraph()
        } else {
            // Reached chapter end; load next chapter if available
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val repo = BookRepository(applicationContext)
                    val chapters = repo.getChaptersForBook(state.bookId)
                    val nextChapterIdx = state.chapterIndex + 1
                    if (nextChapterIdx in chapters.indices) {
                        val nextChap = chapters[nextChapterIdx]
                        withContext(Dispatchers.Main) {
                            _playbackState.value = state.copy(
                                chapterIndex = nextChapterIdx,
                                chapterTitle = nextChap.title,
                                paragraphIndex = 0,
                                paragraphs = nextChap.paragraphs
                            )
                            playCurrentParagraph()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            pausePlayback()
                        }
                    }
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) {
                        pausePlayback()
                    }
                }
            }
        }
    }

    private fun previousParagraph() {
        val state = _playbackState.value
        if (state.paragraphIndex > 0) {
            _playbackState.value = state.copy(paragraphIndex = state.paragraphIndex - 1)
            playCurrentParagraph()
        } else if (state.chapterIndex > 0) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val repo = BookRepository(applicationContext)
                    val chapters = repo.getChaptersForBook(state.bookId)
                    val prevChapterIdx = state.chapterIndex - 1
                    if (prevChapterIdx in chapters.indices) {
                        val prevChap = chapters[prevChapterIdx]
                        withContext(Dispatchers.Main) {
                            _playbackState.value = state.copy(
                                chapterIndex = prevChapterIdx,
                                chapterTitle = prevChap.title,
                                paragraphIndex = (prevChap.paragraphs.size - 1).coerceAtLeast(0),
                                paragraphs = prevChap.paragraphs
                            )
                            playCurrentParagraph()
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun buildNotification(): Notification {
        val state = _playbackState.value
        val playPauseActionTitle = if (state.isPlaying) "Pause" else "Play"
        val playPauseActionIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = PendingIntent.getService(
            this, 1, Intent(this, LuminaAudioService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePendingIntent = PendingIntent.getService(
            this, 2, Intent(this, LuminaAudioService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            this, 3, Intent(this, LuminaAudioService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this, 4, Intent(this, LuminaAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.bookTitle.ifBlank { "Lumina Reader" })
            .setContentText("${state.chapterTitle.ifBlank { "Reading" }} • Paragraph ${state.paragraphIndex + 1}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.paragraphSnippet.ifBlank { state.chapterTitle }))
            .setContentIntent(openAppIntent)
            .setOngoing(state.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseActionIcon, playPauseActionTitle, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopPlayback()
        systemTts?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }
}
