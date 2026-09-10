package io.github.tasmirz.lumina.data

import io.github.tasmirz.lumina.model.WordDefinition
import io.github.tasmirz.lumina.util.SimpleLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object DictionaryService {

    private val cache = SimpleLruCache<String, WordDefinition>(256)

    private fun stripHtml(html: String): String {
        return html.replace("<[^>]*>".toRegex(), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }

    suspend fun lookup(rawQuery: String): WordDefinition = withContext(Dispatchers.IO) {
        val cleanWord = rawQuery.trim().split("\\s+".toRegex())[0]
            .replace("[^a-zA-Z\\-]".toRegex(), "")
            .lowercase()

        if (cleanWord.isBlank()) {
            return@withContext WordDefinition(
                word = rawQuery,
                phonetic = "",
                partOfSpeech = "phrase",
                definition = "Selected phrase in text: \"$rawQuery\"",
                example = ""
            )
        }

        val cached = cache.get(cleanWord)
        if (cached != null) {
            return@withContext cached
        }

        // 1. Primary lookup: Wikimedia Wiktionary REST API
        try {
            val encodedWord = URLEncoder.encode(cleanWord, "UTF-8")
            val wiktionaryUrl = URL("https://en.wiktionary.org/api/rest_v1/page/definition/$encodedWord")
            val conn = (wiktionaryUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "LuminaReader/1.0 (Android; OpenSource EPUB Reader)")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val root = JSONObject(response)
                val enEntries = root.optJSONArray("en")
                if (enEntries != null && enEntries.length() > 0) {
                    for (i in 0 until enEntries.length()) {
                        val entry = enEntries.getJSONObject(i)
                        val partOfSpeech = entry.optString("partOfSpeech", "Definition")
                        val definitions = entry.optJSONArray("definitions")
                        if (definitions != null && definitions.length() > 0) {
                            for (j in 0 until definitions.length()) {
                                val defObj = definitions.getJSONObject(j)
                                val rawDef = defObj.optString("definition", "")
                                val cleanDef = stripHtml(rawDef)
                                if (cleanDef.isNotBlank()) {
                                    val examples = defObj.optJSONArray("examples")
                                    val example = if (examples != null && examples.length() > 0) {
                                        stripHtml(examples.getString(0))
                                    } else ""

                                    val result = WordDefinition(
                                        word = cleanWord.replaceFirstChar { it.uppercase() },
                                        phonetic = "/$cleanWord/",
                                        partOfSpeech = partOfSpeech.lowercase(),
                                        definition = cleanDef,
                                        example = example
                                    )
                                    cache.put(cleanWord, result)
                                    return@withContext result
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Wiktionary failed or timed out, attempt fallback
        }

        // 2. Secondary fallback: dictionaryapi.dev
        try {
            val encodedWord = URLEncoder.encode(cleanWord, "UTF-8")
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$encodedWord")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LuminaReader/1.0")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val entry = jsonArray.getJSONObject(0)
                    val word = entry.optString("word", cleanWord)
                    val phonetic = entry.optString("phonetic", "")

                    val meanings = entry.optJSONArray("meanings")
                    if (meanings != null && meanings.length() > 0) {
                        val firstMeaning = meanings.getJSONObject(0)
                        val partOfSpeech = firstMeaning.optString("partOfSpeech", "definition")
                        val definitions = firstMeaning.optJSONArray("definitions")
                        if (definitions != null && definitions.length() > 0) {
                            val firstDef = definitions.getJSONObject(0)
                            val definition = firstDef.optString("definition", "Definition available.")
                            val example = firstDef.optString("example", "")

                            val result = WordDefinition(
                                word = word.replaceFirstChar { it.uppercase() },
                                phonetic = phonetic.ifBlank { "/$cleanWord/" },
                                partOfSpeech = partOfSpeech,
                                definition = stripHtml(definition),
                                example = stripHtml(example)
                            )
                            cache.put(cleanWord, result)
                            return@withContext result
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful offline fallback
        }

        // 3. Offline / Contextual fallback
        val fallback = WordDefinition(
            word = cleanWord.replaceFirstChar { it.uppercase() },
            phonetic = "/$cleanWord/",
            partOfSpeech = "word in text",
            definition = "Reference from reading context: \"$rawQuery\".",
            example = "From Lumina Reader library"
        )
        cache.put(cleanWord, fallback)
        fallback
    }
}
