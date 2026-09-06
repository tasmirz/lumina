package org.protidhoni.lumina.data

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
}

class AssistantService(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(
        onReady: () -> Unit = {},
        onRmsChanged: (Float) -> Unit = {},
        onPartialResult: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
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

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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

        // Navigation commands
        if (lower.contains("next chapter") || lower == "next") {
            return AssistantAction.NextChapter()
        }
        if (lower.contains("previous chapter") || lower.contains("prev chapter") || lower == "previous") {
            return AssistantAction.PreviousChapter()
        }

        val chapterNumberMatch = ".*(?:chapter|section)\\s+(\\d+).*".toRegex().find(lower)
        if (chapterNumberMatch != null) {
            val num = chapterNumberMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..totalChapters) {
                return AssistantAction.NavigateChapter(num - 1)
            }
        }

        // TTS commands
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

        return null
    }

    suspend fun queryAssistant(
        provider: AiProvider,
        apiKey: String,
        baseUrl: String,
        modelName: String,
        bookTitle: String,
        activeChapterTitle: String,
        knownContext: String,
        userQuery: String
    ): String = withContext(Dispatchers.IO) {
        val systemInstruction = """
            You are Lumina's attentive reading assistant for the book "$bookTitle".
            The reader is currently reading "$activeChapterTitle".
            STRICT RULE: Only answer based on context up to this chapter. NEVER reveal spoilers or upcoming plot developments from later chapters.
            STRICT RULE: Answer directly and concisely (1-3 sentences). Do NOT provide unsolicited background details unless directly asked.
            If the user asks to take a note or summarize, provide a clean, elegant note.
        """.trimIndent()

        if (provider == AiProvider.GEMINI) {
            if (apiKey.isBlank()) {
                return@withContext "To enable intelligent Q&A and character explanations, please add your Google Gemini API key in Settings."
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

                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "$systemInstruction\n\n[Book Context so far]:\n${knownContext.take(12000)}\n\n[Reader Question]:\n$userQuery")
                                })
                            })
                        })
                    })
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
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i)
                            val isThought = part?.optBoolean("thought", false) ?: false
                            if (!isThought && part?.has("text") == true) {
                                val t = part.optString("text", "").trim()
                                if (t.isNotBlank()) {
                                    foundText = t
                                    break
                                }
                            }
                        }
                    }
                    val text = if (foundText.isNotBlank()) foundText else parts?.optJSONObject(0)?.optString("text") ?: "I couldn't find an answer in the chapters read so far."
                    text.trim()
                } else {
                    val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                    "Gemini request error ($responseCode): ${errorStream?.take(150) ?: "Check your API key"}"
                }
            } catch (e: Exception) {
                "Unable to connect to Gemini: ${e.localizedMessage ?: "Network error"}"
            }
        } else {
            // OpenAI Compatible Provider (OpenAI, Groq, OpenRouter, Ollama, LocalAI)
            val cleanBase = (if (baseUrl.isNotBlank()) baseUrl.trim() else "https://api.openai.com/v1").trimEnd('/')
            if (apiKey.isBlank() && !cleanBase.contains("localhost") && !cleanBase.contains("127.0.0.1") && !cleanBase.contains("10.0.2.2")) {
                return@withContext "To enable intelligent Q&A with OpenAI or custom models, please configure your API key in Advanced Settings."
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
                    put("max_tokens", 400)
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
                    content.trim()
                } else {
                    val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                    "AI service error ($responseCode): ${errorStream?.take(150) ?: "Check your API key/Base URL"}"
                }
            } catch (e: Exception) {
                "Unable to connect to AI service: ${e.localizedMessage ?: "Network error"}"
            }
        }
    }

    suspend fun queryGemini(
        apiKey: String,
        bookTitle: String,
        activeChapterTitle: String,
        knownContext: String,
        userQuery: String
    ): String = queryAssistant(
        provider = AiProvider.GEMINI,
        apiKey = apiKey,
        baseUrl = "",
        modelName = "gemini-1.5-flash",
        bookTitle = bookTitle,
        activeChapterTitle = activeChapterTitle,
        knownContext = knownContext,
        userQuery = userQuery
    )
}
