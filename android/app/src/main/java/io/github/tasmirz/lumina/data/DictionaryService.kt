package io.github.tasmirz.lumina.data

import io.github.tasmirz.lumina.model.WordDefinition
import io.github.tasmirz.lumina.util.SimpleLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object DictionaryService {

    private val cache = SimpleLruCache<String, WordDefinition>(256)

    suspend fun lookup(rawQuery: String): WordDefinition = withContext(Dispatchers.IO) {
        val cleanWord = rawQuery.trim().split("\\s+".toRegex())[0]
            .replace("[^a-zA-Z]".toRegex(), "")
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

        try {
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$cleanWord")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
                setRequestProperty("Accept", "application/json")
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
                                phonetic = phonetic,
                                partOfSpeech = partOfSpeech,
                                definition = definition,
                                example = example
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

        // Offline / Fallback definition
        val fallback = WordDefinition(
            word = cleanWord.replaceFirstChar { it.uppercase() },
            phonetic = "/$cleanWord/",
            partOfSpeech = "word in text",
            definition = "Contextual reference from active reading: \"$rawQuery\".",
            example = "From Lumina Reader library"
        )
        cache.put(cleanWord, fallback)
        fallback
    }
}
