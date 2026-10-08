package com.proudvocab.android.core.export

import com.proudvocab.android.core.data.SavedWord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Import / export of the learner's own words.
 *
 * The JSON format is the app's own backup format (it round-trips exactly);
 * the Anki export is a plain `front;back;context;tags` CSV that Anki imports
 * with the "Basic" note type.
 */
object DeckExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun toJson(words: List<SavedWord>): String = json.encodeToString(words)

    fun fromJson(text: String): List<SavedWord> =
        runCatching { json.decodeFromString<List<SavedWord>>(text) }.getOrDefault(emptyList())

    /** Anki-compatible CSV: front, back, context, tags. */
    fun toAnkiCsv(words: List<SavedWord>): String {
        val builder = StringBuilder()
        builder.append("#separator:Semicolon\n")
        builder.append("#html:false\n")
        builder.append("#columns:Front;Back;Context;Tags\n")
        for (word in words) {
            builder.append(escape(word.word))
            builder.append(';')
            builder.append(escape(word.translation))
            builder.append(';')
            builder.append(escape(word.contextSentence))
            builder.append(';')
            builder.append(escape(word.tags))
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun escape(value: String): String {
        val needsQuotes = value.contains(';') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"").replace("\n", "<br>")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
