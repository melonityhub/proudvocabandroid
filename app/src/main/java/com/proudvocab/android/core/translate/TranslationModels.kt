package com.proudvocab.android.core.translate

import com.proudvocab.android.core.settings.TranslationEngine

data class TranslationResult(
    val text: String,
    /** Which engine actually produced the text. */
    val engine: TranslationEngine,
    /** True when no network was involved. */
    val offline: Boolean,
    val detectedSource: String? = null
)

/** Lifecycle of an offline model download. */
sealed interface ModelState {
    data object Unknown : ModelState
    data object Checking : ModelState
    data object Missing : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/** Splits long text into chunks the translation endpoints accept. */
object TextChunker {

    private const val MAX = 1_500

    fun chunk(text: String, max: Int = MAX): List<String> {
        if (text.length <= max) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?؟!])\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length + 1 > max && current.isNotEmpty()) {
                out += current.toString().trim()
                current = StringBuilder()
            }
            if (sentence.length > max) {
                if (current.isNotEmpty()) {
                    out += current.toString().trim()
                    current = StringBuilder()
                }
                var rest = sentence
                while (rest.length > max) {
                    var cut = rest.lastIndexOf(' ', max)
                    if (cut <= 0) cut = max
                    out += rest.substring(0, cut).trim()
                    rest = rest.substring(cut).trim()
                }
                if (rest.isNotBlank()) current.append(rest)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotBlank()) out += current.toString().trim()
        return out.ifEmpty { listOf(text) }
    }
}
