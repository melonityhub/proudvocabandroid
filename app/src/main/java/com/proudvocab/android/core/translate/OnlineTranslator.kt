package com.proudvocab.android.core.translate

import com.proudvocab.android.core.settings.TranslationEngine
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Online translation through the same public Google endpoint the ProudVocab
 * extension uses, plus the same public dictionary used for word info.
 */
class OnlineTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun translate(
        text: String,
        source: String,
        target: String
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val chunks = TextChunker.chunk(text)
            val builder = StringBuilder()
            var detected: String? = null
            for (chunk in chunks) {
                val url = buildString {
                    append("https://translate.googleapis.com/translate_a/single")
                    append("?client=it")
                    append("&sl=").append(source.ifBlank { "auto" })
                    append("&tl=").append(target)
                    append("&dt=t")
                    append("&q=").append(URLEncoder.encode(chunk, "UTF-8"))
                }
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string() ?: error("empty response")
                    val parsed = parseTranslate(body)
                    if (parsed.first.isBlank()) error("empty translation")
                    builder.append(parsed.first)
                    if (detected == null && parsed.second.isNotBlank()) detected = parsed.second
                }
            }
            TranslationResult(
                text = builder.toString().trim(),
                engine = TranslationEngine.ONLINE,
                offline = false,
                detectedSource = detected
            )
        }
    }

    /** Extra information for a single English word (part of speech + audio). */
    suspend fun wordInfo(word: String): Result<WordInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.dictionaryapi.dev/api/v2/entries/en/" +
                URLEncoder.encode(word, "UTF-8")
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string() ?: error("empty")
                parseWordInfo(body, word)
            }
        }
    }

    /** Parses `[[["translated","original",...],...],null,"en",...]`. */
    fun parseTranslate(body: String): Pair<String, String> {
        return runCatching {
            val root = JSONArray(body)
            val sentences = root.optJSONArray(0) ?: return "" to ""
            val builder = StringBuilder()
            for (i in 0 until sentences.length()) {
                val item = sentences.optJSONArray(i) ?: continue
                builder.append(item.optString(0, ""))
            }
            val detected = if (root.length() > 2) root.optString(2, "") else ""
            builder.toString() to detected
        }.getOrDefault("" to "")
    }

    private fun parseWordInfo(body: String, word: String): WordInfo {
        val root = JSONArray(body)
        val synonyms = LinkedHashSet<String>()
        val antonyms = LinkedHashSet<String>()
        val parts = LinkedHashSet<String>()
        var audio: String? = null
        for (i in 0 until root.length()) {
            val entry = root.optJSONObject(i) ?: continue
            val meanings = entry.optJSONArray("meanings")
            if (meanings != null) {
                for (m in 0 until meanings.length()) {
                    val meaning = meanings.optJSONObject(m) ?: continue
                    val pos = meaning.optString("partOfSpeech", "")
                    if (pos.isNotBlank()) parts += pos
                    val defs = meaning.optJSONArray("definitions")
                    if (defs != null) {
                        for (d in 0 until defs.length()) {
                            val def = defs.optJSONObject(d) ?: continue
                            val syn = def.optJSONArray("synonyms")
                            if (syn != null) {
                                for (s in 0 until syn.length()) {
                                    val v = syn.optString(s, "")
                                    if (v.isNotBlank()) synonyms += v
                                }
                            }
                            val ant = def.optJSONArray("antonyms")
                            if (ant != null) {
                                for (s in 0 until ant.length()) {
                                    val v = ant.optString(s, "")
                                    if (v.isNotBlank()) antonyms += v
                                }
                            }
                        }
                    }
                }
            }
            val phonetics = entry.optJSONArray("phonetics")
            if (phonetics != null && audio == null) {
                for (p in 0 until phonetics.length()) {
                    val ph = phonetics.optJSONObject(p) ?: continue
                    val a = ph.optString("audio", "")
                    if (a.isNotBlank()) {
                        audio = a
                        break
                    }
                }
            }
        }
        return WordInfo(
            word = word,
            partOfSpeech = parts.firstOrNull() ?: "",
            synonyms = synonyms.toList().take(12),
            antonyms = antonyms.toList().take(12),
            audioUrl = audio
        )
    }
}

data class WordInfo(
    val word: String,
    val partOfSpeech: String = "",
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val audioUrl: String? = null
)
