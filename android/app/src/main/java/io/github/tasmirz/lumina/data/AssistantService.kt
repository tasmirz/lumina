package io.github.tasmirz.lumina.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import io.github.tasmirz.lumina.model.Book
import io.github.tasmirz.lumina.model.BookCharacter
import io.github.tasmirz.lumina.model.BookLore
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class AiProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI_COMPATIBLE("OpenAI / Compatible")
}

sealed class AssistantAction {
    data class NavigateChapter(val targetIndex: Int) : AssistantAction()
    data class NextChapter(val dummy: Unit = Unit) : AssistantAction()
    data class PreviousChapter(val dummy: Unit = Unit) : AssistantAction()
    data class ToggleTts(val play: Boolean) : AssistantAction()
    data class AddNote(val noteContent: String) : AssistantAction()
    data class Answer(val text: String) : AssistantAction()
    data class SwitchTheme(val themeFamily: String? = null, val mode: String? = null) : AssistantAction()
    data class CreateTheme(val name: String, val bgColor: Long, val textColor: Long, val accentColor: Long) : AssistantAction()
    data class UpdateTheme(val name: String, val bgColor: Long? = null, val textColor: Long? = null, val accentColor: Long? = null) : AssistantAction()
    data class JumpToScene(val query: String, val chapterIndex: Int? = null) : AssistantAction()
    data class ControlTts(val action: String) : AssistantAction()
    data class ToggleAutoScroll(val enable: Boolean) : AssistantAction()
}

data class AssistantResponse(
    val answerText: String,
    val executedAction: AssistantAction? = null
)

class AssistantService(private val context: Context? = null) {

    private var speechRecognizer: SpeechRecognizer? = null

    data class LastQuery(
        val provider: AiProvider,
        val apiKey: String,
        val baseUrl: String,
        val modelName: String,
        val bookTitle: String,
        val activeChapterTitle: String,
        val knownContext: String,
        val userQuery: String,
        val spoilerShield: Boolean,
        val language: String = "en"
    )
    private var lastQueryCache: LastQuery? = null

    fun startListening(
        languageCode: String? = null,
        onReady: () -> Unit = {},
        onRmsChanged: (Float) -> Unit = {},
        onPartialResult: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val ctx = context ?: run {
            onError("Speech recognition not available without Android context")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onError("Speech recognition not available on this device")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onReady() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { onRmsChanged(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        else -> "Speech recognition error ($error)"
                    }
                    onError(msg)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onResult(text)
                    } else {
                        onError("No speech recognized")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onPartialResult(text)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val targetLocale = if (!languageCode.isNullOrBlank() && languageCode != "auto") {
            try { Locale.forLanguageTag(languageCode) } catch (_: Exception) { Locale.getDefault() }
        } else {
            Locale.getDefault()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to start speech recognition")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun parseLocalCommand(query: String, totalChapters: Int): AssistantAction? {
        val lower = query.trim().lowercase()

        // Analytical, question, or summary queries must always flow to AI, never intercepted locally
        val isQueryOrSummary = lower.contains("summar") ||
            lower.contains("explain") ||
            lower.contains("what") ||
            lower.contains("who") ||
            lower.contains("why") ||
            lower.contains("how") ||
            lower.contains("tell me") ||
            lower.contains("about") ||
            lower.contains("describe") ||
            lower.contains("overview") ||
            lower.contains("analysis") ||
            lower.contains("meaning") ||
            lower.contains("scene")
        if (isQueryOrSummary) {
            return null
        }

        // Navigation commands
        if (lower.contains("next chapter") || lower == "next") {
            return AssistantAction.NextChapter()
        }
        if (lower.contains("previous chapter") || lower.contains("prev chapter") || lower == "previous") {
            return AssistantAction.PreviousChapter()
        }

        // Strict chapter navigation (e.g. "go to chapter 4", "jump to chapter 4", "open chapter 4", "chapter 4")
        val chapterNavRegex = "^(?:go to|jump to|open|navigate to|switch to)\\s+(?:chapter|section)\\s+(\\d+)\\s*$".toRegex()
        val chapterOnlyRegex = "^(?:chapter|section)\\s+(\\d+)\\s*$".toRegex()
        val chapterNumberMatch = chapterNavRegex.find(lower) ?: chapterOnlyRegex.find(lower)
        if (chapterNumberMatch != null) {
            val num = chapterNumberMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..totalChapters) {
                return AssistantAction.NavigateChapter(num - 1)
            }
        }

        // Theme switching commands
        if (lower.contains("dark mode") || lower.contains("night mode")) {
            return AssistantAction.SwitchTheme(mode = "dark")
        }
        if (lower.contains("light mode") || lower.contains("day mode")) {
            return AssistantAction.SwitchTheme(mode = "light")
        }
        if (lower.contains("auto mode") || lower.contains("system theme")) {
            return AssistantAction.SwitchTheme(mode = "auto")
        }
        if (lower.contains("parchment theme") || lower.contains("parchment")) {
            return AssistantAction.SwitchTheme(themeFamily = "parchment")
        }
        if (lower.contains("linen theme") || lower.contains("linen")) {
            return AssistantAction.SwitchTheme(themeFamily = "linen")
        }
        if (lower.contains("forest theme") || lower.contains("green theme")) {
            return AssistantAction.SwitchTheme(themeFamily = "forest")
        }
        if (lower.contains("paper theme") || lower.contains("warm paper")) {
            return AssistantAction.SwitchTheme(themeFamily = "paper")
        }

        // Theme update commands (e.g. "update theme Solar with bg #112233", "change theme Cyberpunk accent to #00ff00", "modify theme Vintage Sepia bg #000000")
        if (lower.contains("update") || lower.contains("change") || lower.contains("modify") || lower.contains("tweak")) {
            if (lower.contains("theme") || lower.contains("bg") || lower.contains("background") || lower.contains("accent") || lower.contains("text color")) {
                val nameMatch = "(?:update|change|modify|tweak)\\s+(?:theme\\s+)?([a-zA-Z0-9 _-]+?)(?:\\s+(?:with|to|bg|background|text|accent|color|colors)|$)".toRegex(RegexOption.IGNORE_CASE).find(query)
                val rawName = nameMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                val cleanName = rawName.replace("(?i)\\btheme\\b".toRegex(), "").trim()

                val bgHex = "(?:bg|background)\\s*(?:to|is|=)?\\s*#?([0-9a-fA-F]{6})".toRegex(RegexOption.IGNORE_CASE).find(query)?.groupValues?.getOrNull(1)
                val textHex = "(?:text|font|text color)\\s*(?:to|is|=)?\\s*#?([0-9a-fA-F]{6})".toRegex(RegexOption.IGNORE_CASE).find(query)?.groupValues?.getOrNull(1)
                val accentHex = "(?:accent|accent color|highlight)\\s*(?:to|is|=)?\\s*#?([0-9a-fA-F]{6})".toRegex(RegexOption.IGNORE_CASE).find(query)?.groupValues?.getOrNull(1)

                val hexes = "#([0-9a-fA-F]{6})".toRegex().findAll(query).map { it.groupValues[1] }.toList()
                val bg = bgHex?.toLongOrNull(16)?.let { it or 0xFF000000L } ?: (if (hexes.isNotEmpty() && bgHex == null && textHex == null && accentHex == null) hexes[0].toLong(16) or 0xFF000000L else null)
                val text = textHex?.toLongOrNull(16)?.let { it or 0xFF000000L } ?: (if (hexes.size >= 2 && textHex == null && accentHex == null) hexes[1].toLong(16) or 0xFF000000L else null)
                val accent = accentHex?.toLongOrNull(16)?.let { it or 0xFF000000L } ?: (if (hexes.size >= 3 && accentHex == null) hexes[2].toLong(16) or 0xFF000000L else null)

                if (cleanName.isNotBlank() && (bg != null || text != null || accent != null)) {
                    return AssistantAction.UpdateTheme(name = cleanName, bgColor = bg, textColor = text, accentColor = accent)
                }
            }
        }

        // Theme creation commands (e.g. "create a cyberpunk theme", "make a sepia theme", "set an emerald theme", "generate an oled theme")
        if (lower.contains("theme") && (lower.contains("create") || lower.contains("make") || lower.contains("new") || lower.contains("generate") || lower.contains("set") || lower.contains("build") || lower.contains("custom"))) {

            val hexMatches = "#([0-9a-fA-F]{6})".toRegex().findAll(query).map { it.value }.toList()
            val themeName = query.replace("(?i)^(?:create|make|new)\\s+(?:a\\s+)?(?:theme\\s+)?".toRegex(), "")
                .replace("(?i)\\s+theme.*".toRegex(), "")
                .trim().ifBlank { "Custom Theme" }.replaceFirstChar { it.uppercase() }

            return when {
                hexMatches.size >= 2 -> {
                    val bg = hexMatches[0].removePrefix("#").toLong(16) or 0xFF000000L
                    val text = hexMatches[1].removePrefix("#").toLong(16) or 0xFF000000L
                    val accent = if (hexMatches.size >= 3) hexMatches[2].removePrefix("#").toLong(16) or 0xFF000000L else 0xFF63A0FFL
                    AssistantAction.CreateTheme(name = themeName, bgColor = bg, textColor = text, accentColor = accent)
                }
                lower.contains("cyberpunk") || lower.contains("neon") -> {
                    AssistantAction.CreateTheme(name = "Cyberpunk Neon", bgColor = 0xFF0D0D15L, textColor = 0xFFE0E6EDL, accentColor = 0xFF00F0FFL)
                }
                lower.contains("sepia") || lower.contains("vintage") -> {
                    AssistantAction.CreateTheme(name = "Vintage Sepia", bgColor = 0xFFFBF0D9L, textColor = 0xFF5F4B32L, accentColor = 0xFF8C6D46L)
                }
                lower.contains("emerald") || lower.contains("forest") || lower.contains("green") || lower.contains("sage") -> {
                    AssistantAction.CreateTheme(name = "Emerald Night", bgColor = 0xFF0B1712L, textColor = 0xFFD2E5D0L, accentColor = 0xFF4EBA6FL)
                }
                lower.contains("midnight") || lower.contains("ocean") || lower.contains("deep blue") || lower.contains("navy") -> {
                    AssistantAction.CreateTheme(name = "Midnight Blue", bgColor = 0xFF0B1325L, textColor = 0xFFDCE5F5L, accentColor = 0xFF63A0FFL)
                }
                lower.contains("rose") || lower.contains("velvet") || lower.contains("sakura") || lower.contains("pink") -> {
                    AssistantAction.CreateTheme(name = "Rose Velvet", bgColor = 0xFF1F1218L, textColor = 0xFFFCE7F3L, accentColor = 0xFFF472B6L)
                }
                lower.contains("oled") || lower.contains("pure black") || lower.contains("amoled") -> {
                    AssistantAction.CreateTheme(name = "OLED Pure Black", bgColor = 0xFF000000L, textColor = 0xFFEEEEEEL, accentColor = 0xFF90CAF9L)
                }
                lower.contains("sunset") || lower.contains("amber") || lower.contains("warm") -> {
                    AssistantAction.CreateTheme(name = "Warm Sunset", bgColor = 0xFF211510L, textColor = 0xFFFFE8D6L, accentColor = 0xFFFF9F1CL)
                }
                lower.contains("nord") || lower.contains("frost") || lower.contains("arctic") -> {
                    AssistantAction.CreateTheme(name = "Nord Frost", bgColor = 0xFF2E3440L, textColor = 0xFFECEFF4L, accentColor = 0xFF88C0D0L)
                }
                else -> {
                    val name = if (themeName.isNotBlank() && !themeName.equals("Theme", ignoreCase = true)) themeName else "Custom Reading Theme"
                    AssistantAction.CreateTheme(name = name, bgColor = 0xFF18181BL, textColor = 0xFFF4F4F5L, accentColor = 0xFF38BDF8L)
                }
            }
        }

        // Auto-scroll commands
        if (lower.contains("auto scroll") || lower.contains("autoscroll") || lower.contains("start scrolling")) {
            val enable = !lower.contains("stop") && !lower.contains("pause") && !lower.contains("disable")
            return AssistantAction.ToggleAutoScroll(enable)
        }

        // TTS commands
        if (lower.contains("read faster") || lower.contains("speed up")) {
            return AssistantAction.ControlTts("speed_up")
        }
        if (lower.contains("read slower") || lower.contains("slow down")) {
            return AssistantAction.ControlTts("slow_down")
        }
        if (lower.contains("read aloud") || lower.contains("play audio") || lower.contains("start reading") || lower == "play") {
            return AssistantAction.ToggleTts(play = true)
        }
        if (lower.contains("stop reading") || lower.contains("pause audio") || lower == "pause" || lower == "stop") {
            return AssistantAction.ToggleTts(play = false)
        }

        // Note commands
        if (lower.startsWith("note:") || lower.startsWith("note ") || lower.startsWith("take note") || lower.startsWith("write note")) {
            val note = query.replace("^(?i)(?:note:?|take note:?|write note:?)\\s*".toRegex(), "").trim()
            if (note.isNotBlank()) {
                return AssistantAction.AddNote(note)
            }
        }

        // Jump to scene
        if (lower.startsWith("jump to ") || lower.startsWith("go to scene ") || lower.startsWith("find scene ") || lower.startsWith("where does ")) {
            val sceneQuery = query.replace("^(?i)(?:jump to|go to scene|find scene|where does)\\s*".toRegex(), "").trim()
            if (sceneQuery.isNotBlank()) {
                return AssistantAction.JumpToScene(sceneQuery)
            }
        }

        return null
    }

    private fun isSummaryOrContextQuery(query: String): Boolean {
        val l = query.lowercase()
        return l.contains("summar") || l.contains("explain") || l.contains("context") || l.contains("overview") || l.contains("what is happening") || l.contains("what's happening")
    }

    suspend fun retryLastQuery(): AssistantResponse? {
        val last = lastQueryCache ?: return null
        return queryAssistant(
            provider = last.provider,
            apiKey = last.apiKey,
            baseUrl = last.baseUrl,
            modelName = last.modelName,
            bookTitle = last.bookTitle,
            activeChapterTitle = last.activeChapterTitle,
            knownContext = last.knownContext,
            userQuery = last.userQuery,
            spoilerShield = last.spoilerShield,
            language = last.language
        )
    }

    suspend fun queryAssistant(
        provider: AiProvider,
        apiKey: String,
        baseUrl: String,
        modelName: String,
        bookTitle: String,
        activeChapterTitle: String,
        knownContext: String,
        userQuery: String,
        spoilerShield: Boolean = true,
        language: String = "en"
    ): AssistantResponse = withContext(Dispatchers.IO) {
        lastQueryCache = LastQuery(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelName = modelName,
            bookTitle = bookTitle,
            activeChapterTitle = activeChapterTitle,
            knownContext = knownContext,
            userQuery = userQuery,
            spoilerShield = spoilerShield,
            language = language
        )

        // Check for local instant command first
        val localAction = parseLocalCommand(userQuery, 100)
        if (localAction != null) {
            val confirmation = when (localAction) {
                is AssistantAction.SwitchTheme -> "Switching theme to ${localAction.themeFamily ?: localAction.mode} mode."
                is AssistantAction.CreateTheme -> "Created and applied '${localAction.name}' custom theme."
                is AssistantAction.UpdateTheme -> "Updated and applied '${localAction.name}' custom theme."
                is AssistantAction.ToggleAutoScroll -> if (localAction.enable) "Auto-scroll activated." else "Auto-scroll stopped."
                is AssistantAction.ControlTts -> "Adjusting playback: ${localAction.action.replace('_', ' ')}."
                is AssistantAction.ToggleTts -> if (localAction.play) "Starting reading aloud." else "Audio paused."
                is AssistantAction.NextChapter -> "Navigating to next chapter."
                is AssistantAction.PreviousChapter -> "Navigating to previous chapter."
                is AssistantAction.NavigateChapter -> "Jumping to chapter ${localAction.targetIndex + 1}."
                is AssistantAction.JumpToScene -> "Searching scene: “${localAction.query}”..."
                is AssistantAction.AddNote -> "Saved note: ${localAction.noteContent}"
                is AssistantAction.Answer -> localAction.text
            }
            return@withContext AssistantResponse(answerText = confirmation, executedAction = localAction)
        }

        val spoilerRule = if (spoilerShield) {
            "STRICT SPOILER SHIELD ACTIVE: The reader is at chapter '$activeChapterTitle'. DO NOT reveal any upcoming events, character fates, plot twists, or endings after this chapter. Only discuss context provided up to this chapter."
        } else {
            "SPOILER SHIELD OFF: The reader has granted full book permissions. You may freely reference later plot developments, themes, and endings if asked."
        }

        val langInstruction = if (language.isNotBlank() && language != "auto") {
            "LANGUAGE INSTRUCTION: Please reply to the user in language '$language'."
        } else {
            "LANGUAGE INSTRUCTION: Reply in the language matching the user's question or the book's language."
        }

        val systemInstruction = """
            You are Lumina's attentive reading companion for "$bookTitle".
            The reader is reading "$activeChapterTitle".
            $spoilerRule
            $langInstruction
            STRICT RULE: Answer directly and concisely (1-3 sentences).
            Available tools: switch_theme(theme, mode), jump_to_scene(query, chapter_index), control_tts(action), toggle_autoscroll(enable). Use these tools whenever the reader requests changing settings, jumping in the story, controlling speech, or auto-scrolling.
        """.trimIndent()

        if (provider == AiProvider.GEMINI) {
            if (apiKey.isBlank()) {
                if (isSummaryOrContextQuery(userQuery) && knownContext.isNotBlank()) {
                    val cleanLines = knownContext.lines().map { it.trim() }.filter { it.length > 20 }
                    val synopsis = if (cleanLines.isNotEmpty()) cleanLines.take(3).joinToString(" ") else knownContext.take(200)
                    val truncated = if (synopsis.length > 320) synopsis.take(320) + "..." else synopsis
                    return@withContext AssistantResponse(
                        answerText = "📖 Chapter Synopsis ($activeChapterTitle):\n\n\"$truncated\"\n\n💡 Tip: Add your Gemini API key in Settings for deep AI comprehension."
                    )
                }
                return@withContext AssistantResponse(
                    answerText = "To enable intelligent Q&A and assistant tools, please add your Google Gemini API key in Advanced Settings."
                )
            }

            try {
                val model = if (modelName.isNotBlank()) modelName.trim() else "gemini-3.1-flash-lite"
                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${apiKey.trim()}"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }

                val toolsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "switch_theme")
                                put("description", "Switch reader theme family or light/dark/auto mode")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("mode", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "light, dark, or auto")
                                        })
                                        put("theme", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "paper, modern, forest, parchment, linen, or custom")
                                        })
                                    })
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "jump_to_scene")
                                put("description", "Find and jump to a scene, character dialogue, or story event in the book")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("query", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Key phrase or event description to locate in text")
                                        })
                                        put("chapter_index", JSONObject().apply {
                                            put("type", "INTEGER")
                                            put("description", "Optional 0-based chapter index if known")
                                        })
                                    })
                                    put("required", JSONArray().apply { put("query") })
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "control_tts")
                                put("description", "Control audiobook reading voice (play, pause, speed_up, slow_down)")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("action", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "play, pause, speed_up, or slow_down")
                                        })
                                    })
                                    put("required", JSONArray().apply { put("action") })
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "toggle_autoscroll")
                                put("description", "Start or stop hands-free smooth auto scrolling")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("enable", JSONObject().apply {
                                            put("type", "BOOLEAN")
                                            put("description", "true to enable auto-scroll, false to stop")
                                        })
                                    })
                                    put("required", JSONArray().apply { put("enable") })
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "create_theme")
                                put("description", "Create and apply a custom theme with name, background color, text color, and accent color hex strings")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("name", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Name of the custom theme")
                                        })
                                        put("bg_color", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Hex code for background color e.g. #0D0D15")
                                        })
                                        put("text_color", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Hex code for readable text color e.g. #E0E6ED")
                                        })
                                        put("accent_color", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Hex code for accent/highlight color e.g. #00F0FF")
                                        })
                                    })
                                    put("required", JSONArray().apply {
                                        put("name")
                                        put("bg_color")
                                        put("text_color")
                                    })
                                })
                            })
                        })
                        put(JSONObject().apply {
                            put("functionDeclarations", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("name", "update_theme")
                                    put("description", "Update an existing custom theme's colors in place by theme name.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("name", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Name of the existing custom theme to update")
                                            })
                                            put("bg_color", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional new hex code for background color e.g. #0D0D15")
                                            })
                                            put("text_color", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional new hex code for readable text color e.g. #E0E6ED")
                                            })
                                            put("accent_color", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Optional new hex code for accent color e.g. #00F0FF")
                                            })
                                        })
                                        put("required", JSONArray().apply {
                                            put("name")
                                        })
                                    })
                                })
                            })
                        })
                    })
                }

                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "$systemInstruction\n\n[Context so far]:\n${knownContext.take(12000)}\n\n[Reader Message]:\n$userQuery")
                                })
                            })
                        })
                    })
                    put("tools", toolsArray)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.3)
                        put("maxOutputTokens", 600)
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 1024)
                        })
                    })
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JSONObject(responseText)
                    val candidates = json.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")

                    var foundText = ""
                    var toolAction: AssistantAction? = null

                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue

                            // Check function call
                            if (part.has("functionCall")) {
                                val call = part.getJSONObject("functionCall")
                                val fnName = call.optString("name")
                                val args = call.optJSONObject("args") ?: JSONObject()

                                when (fnName) {
                                    "switch_theme" -> {
                                        val m = args.optString("mode", "").takeIf { it.isNotBlank() }
                                        val th = args.optString("theme", "").takeIf { it.isNotBlank() }
                                        toolAction = AssistantAction.SwitchTheme(themeFamily = th, mode = m)
                                        foundText = "Switching theme to ${th ?: m ?: "selected"} mode."
                                    }
                                    "jump_to_scene" -> {
                                        val q = args.optString("query", userQuery)
                                        val cIdx = if (args.has("chapter_index")) args.getInt("chapter_index") else null
                                        toolAction = AssistantAction.JumpToScene(query = q, chapterIndex = cIdx)
                                        foundText = "Searching for scene: “$q”..."
                                    }
                                    "control_tts" -> {
                                        val act = args.optString("action", "play")
                                        toolAction = AssistantAction.ControlTts(act)
                                        foundText = "Audio command: ${act.replace('_', ' ')}."
                                    }
                                    "toggle_autoscroll" -> {
                                        val en = args.optBoolean("enable", true)
                                        toolAction = AssistantAction.ToggleAutoScroll(en)
                                        foundText = if (en) "Auto-scrolling started." else "Auto-scrolling stopped."
                                    }
                                    "create_theme" -> {
                                        val name = args.optString("name", "Custom Theme")
                                        val bgHex = args.optString("bg_color", "#18181B").removePrefix("#")
                                        val textHex = args.optString("text_color", "#F4F4F5").removePrefix("#")
                                        val accentHex = args.optString("accent_color", "#38BDF8").removePrefix("#")
                                        val bg = bgHex.toLongOrNull(16)?.let { it or 0xFF000000L } ?: 0xFF18181BL
                                        val text = textHex.toLongOrNull(16)?.let { it or 0xFF000000L } ?: 0xFFF4F4F5L
                                        val accent = accentHex.toLongOrNull(16)?.let { it or 0xFF000000L } ?: 0xFF38BDF8L
                                        toolAction = AssistantAction.CreateTheme(name = name, bgColor = bg, textColor = text, accentColor = accent)
                                        foundText = "Created and applied custom theme '$name'."
                                    }
                                    "update_theme" -> {
                                        val name = args.optString("name", "").trim()
                                        val bgHex = args.optString("bg_color", "").removePrefix("#").takeIf { it.isNotBlank() }
                                        val textHex = args.optString("text_color", "").removePrefix("#").takeIf { it.isNotBlank() }
                                        val accentHex = args.optString("accent_color", "").removePrefix("#").takeIf { it.isNotBlank() }
                                        val bg = bgHex?.toLongOrNull(16)?.let { it or 0xFF000000L }
                                        val text = textHex?.toLongOrNull(16)?.let { it or 0xFF000000L }
                                        val accent = accentHex?.toLongOrNull(16)?.let { it or 0xFF000000L }
                                        toolAction = AssistantAction.UpdateTheme(name = name, bgColor = bg, textColor = text, accentColor = accent)
                                        foundText = "Updated custom theme '$name'."
                                    }
                                }
                            }

                            val isThought = part.optBoolean("thought", false)
                            if (!isThought && part.has("text")) {
                                val t = part.optString("text", "").trim()
                                if (t.isNotBlank()) {
                                    foundText = t
                                }
                            }
                        }
                    }
                    val text = if (foundText.isNotBlank()) foundText else "I've checked the chapters read so far."
                    AssistantResponse(answerText = text.trim(), executedAction = toolAction)
                } else {
                    val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                    if (isSummaryOrContextQuery(userQuery) && knownContext.isNotBlank()) {
                        val cleanLines = knownContext.lines().map { it.trim() }.filter { it.length > 20 }
                        val synopsis = if (cleanLines.isNotEmpty()) cleanLines.take(3).joinToString(" ") else knownContext.take(200)
                        val truncated = if (synopsis.length > 320) synopsis.take(320) + "..." else synopsis
                        AssistantResponse(answerText = "📖 Chapter Synopsis ($activeChapterTitle):\n\n\"$truncated\"")
                    } else {
                        AssistantResponse(answerText = "Gemini request error ($responseCode): ${errorStream?.take(150) ?: "Check your API key"}")
                    }
                }
            } catch (e: Exception) {
                if (isSummaryOrContextQuery(userQuery) && knownContext.isNotBlank()) {
                    val cleanLines = knownContext.lines().map { it.trim() }.filter { it.length > 20 }
                    val synopsis = if (cleanLines.isNotEmpty()) cleanLines.take(3).joinToString(" ") else knownContext.take(200)
                    val truncated = if (synopsis.length > 320) synopsis.take(320) + "..." else synopsis
                    AssistantResponse(answerText = "📖 Chapter Synopsis ($activeChapterTitle):\n\n\"$truncated\"")
                } else {
                    AssistantResponse(answerText = "Unable to connect to Gemini: ${e.localizedMessage ?: "Network error"}")
                }
            }
        } else {
            // OpenAI Compatible Provider
            val cleanBase = (if (baseUrl.isNotBlank()) baseUrl.trim() else "https://api.openai.com/v1").trimEnd('/')
            if (apiKey.isBlank() && !cleanBase.contains("localhost") && !cleanBase.contains("127.0.0.1") && !cleanBase.contains("10.0.2.2")) {
                if (isSummaryOrContextQuery(userQuery) && knownContext.isNotBlank()) {
                    val cleanLines = knownContext.lines().map { it.trim() }.filter { it.length > 20 }
                    val synopsis = if (cleanLines.isNotEmpty()) cleanLines.take(3).joinToString(" ") else knownContext.take(200)
                    val truncated = if (synopsis.length > 320) synopsis.take(320) + "..." else synopsis
                    return@withContext AssistantResponse(
                        answerText = "📖 Chapter Synopsis ($activeChapterTitle):\n\n\"$truncated\"\n\n💡 Tip: Add your API key in Settings for deep AI comprehension."
                    )
                }
                return@withContext AssistantResponse(
                    answerText = "To enable intelligent Q&A with OpenAI or custom models, please configure your API key in Advanced Settings."
                )
            }
            try {
                val endpoint = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/chat/completions"
                val model = if (modelName.isNotBlank()) modelName.trim() else "gpt-4o-mini"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    if (apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    }
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "$systemInstruction\n\n[Book Context so far]:\n${knownContext.take(12000)}")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userQuery)
                        })
                    })
                    put("temperature", 0.3)
                    put("max_tokens", 500)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JSONObject(responseText)
                    val choices = json.optJSONArray("choices")
                    val firstChoice = choices?.optJSONObject(0)
                    val message = firstChoice?.optJSONObject("message")
                    val content = message?.optString("content") ?: "I couldn't find an answer in the chapters read so far."
                    AssistantResponse(answerText = content.trim())
                } else {
                    val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                    if (isSummaryOrContextQuery(userQuery) && knownContext.isNotBlank()) {
                        val cleanLines = knownContext.lines().map { it.trim() }.filter { it.length > 20 }
                        val synopsis = if (cleanLines.isNotEmpty()) cleanLines.take(3).joinToString(" ") else knownContext.take(200)
                        val truncated = if (synopsis.length > 320) synopsis.take(320) + "..." else synopsis
                        AssistantResponse(answerText = "📖 Chapter Synopsis ($activeChapterTitle):\n\n\"$truncated\"")
                    } else {
                        AssistantResponse(answerText = "AI service error ($responseCode): ${errorStream?.take(150) ?: "Check your API key/Base URL"}")
                    }
                }
            } catch (e: Exception) {
                if (isSummaryOrContextQuery(userQuery) && knownContext.isNotBlank()) {
                    val cleanLines = knownContext.lines().map { it.trim() }.filter { it.length > 20 }
                    val synopsis = if (cleanLines.isNotEmpty()) cleanLines.take(3).joinToString(" ") else knownContext.take(200)
                    val truncated = if (synopsis.length > 320) synopsis.take(320) + "..." else synopsis
                    AssistantResponse(answerText = "📖 Chapter Synopsis ($activeChapterTitle):\n\n\"$truncated\"")
                } else {
                    AssistantResponse(answerText = "Unable to connect to AI service: ${e.localizedMessage ?: "Network error"}")
                }
            }
        }
    }

    data class ExtractionResult(
        val characters: List<BookCharacter>,
        val lore: List<BookLore>
    )

    companion object {
        private fun normalizeEntityName(name: String): String {
            return name.lowercase()
                .replace("^(mr\\.?|mrs\\.?|ms\\.?|dr\\.?|lord|lady|comrade|brother|sister)\\s+".toRegex(), "")
                .replace("['’\"-]".toRegex(), "")
                .trim()
        }

        private fun isSameCharacter(name1: String, name2: String, aliases1: List<String> = emptyList(), aliases2: List<String> = emptyList()): Boolean {
            val n1 = normalizeEntityName(name1)
            val n2 = normalizeEntityName(name2)
            if (n1.isBlank() || n2.isBlank()) return false
            if (n1 == n2) return true
            if (aliases1.any { normalizeEntityName(it) == n2 } || aliases2.any { normalizeEntityName(it) == n1 }) return true
            val t1 = n1.split(" ").filter { it.length > 2 }
            val t2 = n2.split(" ").filter { it.length > 2 }
            if (t1.isNotEmpty() && t2.isNotEmpty()) {
                if (t1 == t2) return true
                if (t1.size == 1 && (t2.first() == t1[0] || t2.last() == t1[0])) return true
                if (t2.size == 1 && (t1.first() == t2[0] || t1.last() == t2[0])) return true
            }
            return false
        }

        private fun isSameLore(title1: String, title2: String): Boolean {
            val t1 = normalizeEntityName(title1)
            val t2 = normalizeEntityName(title2)
            if (t1.isBlank() || t2.isBlank()) return false
            if (t1 == t2) return true
            if (t1.contains(t2) || t2.contains(t1)) {
                if (minOf(t1.length, t2.length) >= 4) return true
            }
            return false
        }

        suspend fun extractCharacters(
            book: Book,
            currentChapterIndex: Int,
            isSpoilerShield: Boolean,
            apiKey: String,
            provider: AiProvider = AiProvider.GEMINI,
            modelName: String = "gemini-3.1-flash-lite",
            customEndpoint: String = "",
            existingCharacters: List<BookCharacter> = emptyList(),
            lastCalculatedChapter: Int = book.characterCheckpointChapter,
            lastCalculatedPage: Int = book.characterCheckpointPage,
            currentPageIndex: Int = book.currentPage
        ): List<BookCharacter> {
            val res = extractCharactersAndLore(
                book = book,
                currentChapterIndex = currentChapterIndex,
                isSpoilerShield = isSpoilerShield,
                apiKey = apiKey,
                provider = provider,
                modelName = modelName,
                customEndpoint = customEndpoint,
                existingCharacters = existingCharacters,
                existingLore = emptyList(),
                lastCalculatedChapter = lastCalculatedChapter,
                lastCalculatedPage = lastCalculatedPage,
                currentPageIndex = currentPageIndex
            )
            return res.characters
        }

        suspend fun extractCharactersAndLore(
            book: Book,
            currentChapterIndex: Int,
            isSpoilerShield: Boolean,
            apiKey: String,
            provider: AiProvider = AiProvider.GEMINI,
            modelName: String = "gemini-3.1-flash-lite",
            customEndpoint: String = "",
            existingCharacters: List<BookCharacter> = emptyList(),
            existingLore: List<BookLore> = emptyList(),
            lastCalculatedChapter: Int = book.characterCheckpointChapter,
            lastCalculatedPage: Int = book.characterCheckpointPage,
            currentPageIndex: Int = book.currentPage
        ): ExtractionResult = withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext ExtractionResult(existingCharacters, existingLore)

            // 1. Zero-Token Guard: if we already extracted up to this chapter and page, do not check again!
            val hasCheckpoint = (existingCharacters.isNotEmpty() || existingLore.isNotEmpty()) && lastCalculatedChapter >= 0
            if (hasCheckpoint && lastCalculatedChapter >= currentChapterIndex && (currentPageIndex <= lastCalculatedPage || lastCalculatedPage == 0)) {
                return@withContext ExtractionResult(existingCharacters, existingLore)
            }

            // 2. Strict Incremental Range: Only scan UNCHECKED chapters
            val startChapter = if (hasCheckpoint && lastCalculatedChapter < currentChapterIndex) lastCalculatedChapter + 1 else 0
            val targetChapter = if (isSpoilerShield && currentChapterIndex >= 0) currentChapterIndex else (book.chapters.size - 1)

            val chaptersToConsider = if (startChapter <= targetChapter && targetChapter < book.chapters.size) {
                book.chapters.subList(startChapter, targetChapter + 1)
            } else if (startChapter == targetChapter && startChapter < book.chapters.size) {
                listOf(book.chapters[startChapter])
            } else {
                book.chapters.take(currentChapterIndex + 1)
            }

            if (chaptersToConsider.isEmpty()) {
                return@withContext ExtractionResult(existingCharacters, existingLore)
            }

            // 3. Compact excerpts to strictly conserve tokens
            val contextBuilder = StringBuilder()
            for (chap in chaptersToConsider) {
                contextBuilder.append("=== ").append(chap.title).append(" ===\n")
                val significant = chap.paragraphs.filter { it.trim().length > 30 }.take(8)
                contextBuilder.append(significant.joinToString("\n\n")).append("\n\n")
                if (contextBuilder.length > 8000) break
            }

            val currentChapterName = book.chapters.getOrNull(currentChapterIndex)?.title ?: "Chapter ${currentChapterIndex + 1}"

            // 4. Compact JSON context of known entities
            val knownContextJson = JSONObject().apply {
                put("reading_context", JSONObject().apply {
                    put("book_title", book.title)
                    put("book_author", book.author)
                    put("previously_analyzed_chapter", if (hasCheckpoint) lastCalculatedChapter + 1 else 0)
                    put("current_reading_chapter", currentChapterIndex + 1)
                    put("current_reading_page", currentPageIndex + 1)
                    put("active_chapter_name", currentChapterName)
                })
                put("known_characters", JSONArray().apply {
                    for (c in existingCharacters.take(25)) {
                        put(JSONObject().apply {
                            put("name", c.name)
                            put("role", c.role)
                            put("summary", c.summary.take(160))
                            if (c.aliases.isNotEmpty()) put("aliases", JSONArray(c.aliases))
                        })
                    }
                })
                put("known_lore", JSONArray().apply {
                    for (l in existingLore.take(20)) {
                        put(JSONObject().apply {
                            put("title", l.title)
                            put("category", l.category)
                            put("description", l.description.take(160))
                        })
                    }
                })
            }.toString()

            val prompt = """
You are Lumina's literary analyst analyzing unread excerpts of "${book.title}" by ${book.author}.

Context & Known Entities:
$knownContextJson

New Book Excerpts (Chapters "${chaptersToConsider.firstOrNull()?.title ?: ""}" to "$currentChapterName"):
$contextBuilder

Instructions:
1. STRICT ANTI-DUPLICATION:
   - Do NOT create duplicate entries for characters or lore already in the known list.
   - If "Winston" or "Smith" is already known, update "Winston Smith" under its canonical full name and add alternate names to "aliases".
   - Do not create separate entries for titles or honorifics.
2. RICH DESCRIPTIONS (can be 4+ sentences):
   - Provide comprehensive, vivid descriptions (3-5+ sentences) detailing character motivations, personality, background, and relationships revealed up to "$currentChapterName".
3. EXTRACT LORE & WORLD-BUILDING:
   - Identify factions, organizations, historical events, technology, locations, philosophies, or key terms (e.g. Ingsoc, Thought Police, Ministry of Truth, Oceania, Tele-screen).
4. SPOILER SHIELD:
   - STRICT SPOILER SHIELD ACTIVE: NEVER mention plot twists, deaths, or events beyond chapter "$currentChapterName".

Respond with ONLY a JSON object with this exact schema:
{
  "characters": [
    {
      "name": "Canonical Full Name",
      "role": "Protagonist / Antagonist / Supporting / Rebel / Official",
      "first_appearance": "Chapter name",
      "summary": "Rich 3-5+ sentence description of personality, actions, and current arc up to this chapter.",
      "key_events": "Key actions, relationships, and revelations up to this point.",
      "aliases": ["Surname", "Nickname"]
    }
  ],
  "lore": [
    {
      "title": "Faction / Location / Concept / Event / Technology Name",
      "category": "Faction / Location / Concept / Event / Technology / Society",
      "first_appearance": "Chapter name",
      "description": "Rich 3-5+ sentence explanation of lore, history, purpose, and significance up to this chapter.",
      "key_facts": "Key established facts or rules."
    }
  ]
}
            """.trimIndent()

            try {
                val jsonText = if (provider == AiProvider.GEMINI) {
                    val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                    val requestBody = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", prompt))
                                })
                            })
                        })
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.2)
                            put("responseMimeType", "application/json")
                        })
                    }

                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 20000
                        readTimeout = 20000
                        setRequestProperty("Content-Type", "application/json")
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }
                    if (conn.responseCode in 200..299) {
                        val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                        val json = JSONObject(responseStr)
                        json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                    } else null
                } else {
                    val endpoint = if (customEndpoint.isNotBlank()) customEndpoint.trimEnd('/') + "/chat/completions"
                    else "https://api.openai.com/v1/chat/completions"

                    val requestBody = JSONObject().apply {
                        put("model", modelName)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        })
                        put("temperature", 0.2)
                    }

                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 20000
                        readTimeout = 20000
                        setRequestProperty("Content-Type", "application/json")
                        if (apiKey.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer $apiKey")
                        }
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }
                    if (conn.responseCode in 200..299) {
                        val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                        val json = JSONObject(responseStr)
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                    } else null
                }

                if (jsonText.isNullOrBlank()) return@withContext ExtractionResult(existingCharacters, existingLore)

                val cleanJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val rootObj = JSONObject(cleanJson)
                val charsArray = rootObj.optJSONArray("characters") ?: JSONArray()
                val loreArray = rootObj.optJSONArray("lore") ?: JSONArray()

                val parsedCharacters = mutableListOf<BookCharacter>()
                for (i in 0 until charsArray.length()) {
                    val obj = charsArray.getJSONObject(i)
                    val name = obj.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val aliasesList = mutableListOf<String>()
                    val aliasesArr = obj.optJSONArray("aliases")
                    if (aliasesArr != null) {
                        for (a in 0 until aliasesArr.length()) {
                            val alias = aliasesArr.optString(a, "").trim()
                            if (alias.isNotBlank()) aliasesList.add(alias)
                        }
                    }

                    parsedCharacters.add(
                        BookCharacter(
                            bookId = book.id,
                            name = name,
                            role = obj.optString("role", "Character").trim(),
                            firstAppearanceChapter = obj.optString("first_appearance", currentChapterName).trim(),
                            summary = obj.optString("summary", "").trim(),
                            keyEvents = obj.optString("key_events", "").trim(),
                            aliases = aliasesList,
                            isSpoiler = false
                        )
                    )
                }

                val parsedLore = mutableListOf<BookLore>()
                for (i in 0 until loreArray.length()) {
                    val obj = loreArray.getJSONObject(i)
                    val title = obj.optString("title", "").trim()
                    if (title.isBlank()) continue
                    parsedLore.add(
                        BookLore(
                            bookId = book.id,
                            title = title,
                            category = obj.optString("category", "World").trim(),
                            firstAppearanceChapter = obj.optString("first_appearance", currentChapterName).trim(),
                            description = obj.optString("description", "").trim(),
                            keyFacts = obj.optString("key_facts", "").trim(),
                            isSpoiler = false
                        )
                    )
                }

                // Smart Canonical Deduplication and Merging for Characters
                val mergedCharacters = existingCharacters.toMutableList()
                for (newChar in parsedCharacters) {
                    val existingIdx = mergedCharacters.indexOfFirst {
                        isSameCharacter(it.name, newChar.name, it.aliases, newChar.aliases)
                    }
                    if (existingIdx != -1) {
                        val existing = mergedCharacters[existingIdx]
                        val canonicalName = if (newChar.name.length >= existing.name.length) newChar.name else existing.name
                        val combinedAliases = (existing.aliases + newChar.aliases + listOf(newChar.name, existing.name))
                            .filter { it.isNotBlank() && !it.equals(canonicalName, ignoreCase = true) }
                            .distinctBy { normalizeEntityName(it) }

                        val combinedSummary = if (newChar.summary.length > existing.summary.length) newChar.summary else existing.summary
                        val combinedEvents = if (newChar.keyEvents.isNotBlank()) {
                            if (existing.keyEvents.isNotBlank() && !existing.keyEvents.contains(newChar.keyEvents)) {
                                "${existing.keyEvents} • ${newChar.keyEvents}"
                            } else newChar.keyEvents
                        } else existing.keyEvents

                        mergedCharacters[existingIdx] = existing.copy(
                            name = canonicalName,
                            role = if (newChar.role.isNotBlank() && newChar.role != "Character") newChar.role else existing.role,
                            summary = combinedSummary,
                            keyEvents = combinedEvents,
                            aliases = combinedAliases,
                            firstAppearanceChapter = existing.firstAppearanceChapter.ifBlank { newChar.firstAppearanceChapter }
                        )
                    } else {
                        mergedCharacters.add(newChar)
                    }
                }

                // Smart Canonical Deduplication and Merging for Lore
                val mergedLore = existingLore.toMutableList()
                for (newLore in parsedLore) {
                    val existingIdx = mergedLore.indexOfFirst {
                        isSameLore(it.title, newLore.title)
                    }
                    if (existingIdx != -1) {
                        val existing = mergedLore[existingIdx]
                        val canonicalTitle = if (newLore.title.length >= existing.title.length) newLore.title else existing.title
                        val combinedDesc = if (newLore.description.length > existing.description.length) newLore.description else existing.description
                        val combinedFacts = if (newLore.keyFacts.isNotBlank()) {
                            if (existing.keyFacts.isNotBlank() && !existing.keyFacts.contains(newLore.keyFacts)) {
                                "${existing.keyFacts} • ${newLore.keyFacts}"
                            } else newLore.keyFacts
                        } else existing.keyFacts

                        mergedLore[existingIdx] = existing.copy(
                            title = canonicalTitle,
                            category = if (newLore.category.isNotBlank() && newLore.category != "World") newLore.category else existing.category,
                            description = combinedDesc,
                            keyFacts = combinedFacts,
                            firstAppearanceChapter = existing.firstAppearanceChapter.ifBlank { newLore.firstAppearanceChapter }
                        )
                    } else {
                        mergedLore.add(newLore)
                    }
                }

                ExtractionResult(mergedCharacters, mergedLore)
            } catch (_: Exception) {
                ExtractionResult(existingCharacters, existingLore)
            }
        }
    }
}
