package com.proudvocab.android.core.games

import com.proudvocab.android.core.data.SavedWord
import kotlin.random.Random

enum class GameType(val key: String) {
    MULTIPLE_CHOICE("multiple_choice"),
    FILL_BLANK("fill_blank"),
    SCRAMBLE("scramble"),
    MATCH("match"),
    DICTATION("dictation"),
    CONTEXT_CHOICE("context_choice");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: MULTIPLE_CHOICE
    }
}

data class GameQuestion(
    val type: GameType,
    /** Word being tested. */
    val word: String,
    /** What the player has to produce or pick. */
    val answer: String,
    /** Choices for the choice based games (always includes [answer]). */
    val options: List<String> = emptyList(),
    /** Sentence the word came from, blanked out for FILL_BLANK. */
    val context: String = "",
    val scrambled: String = ""
) {
    val isCorrect: (String) -> Boolean
        get() = { guess ->
            guess.trim().equals(answer.trim(), ignoreCase = true)
        }
}

/** One pair in the matching game. */
data class MatchPair(val word: String, val meaning: String)

/**
 * Pure-Kotlin round builder for the six mini games.
 *
 * It never touches Android, so the whole thing is covered by unit tests.
 */
object GameEngine {

    private const val MAX_QUESTIONS = 10
    private const val CHOICES = 4

    fun build(
        type: GameType,
        words: List<SavedWord>,
        seed: Long = System.currentTimeMillis()
    ): List<GameQuestion> {
        val random = Random(seed)
        val pool = words
            .filter { it.word.isNotBlank() && it.translation.isNotBlank() }
            .shuffled(random)
            .take(MAX_QUESTIONS)
        if (pool.isEmpty()) return emptyList()

        return when (type) {
            GameType.MULTIPLE_CHOICE -> pool.map { word ->
                val distractors = distractorsFor(word, words, random)
                GameQuestion(
                    type = type,
                    word = word.word,
                    answer = word.translation,
                    options = (distractors + word.translation).shuffled(random),
                    context = word.contextSentence
                )
            }

            GameType.FILL_BLANK -> pool.mapNotNull { word ->
                val sentence = word.contextSentence
                if (sentence.isBlank()) return@mapNotNull null
                val blanked = blankOut(sentence, word.word) ?: return@mapNotNull null
                GameQuestion(
                    type = type,
                    word = word.word,
                    answer = word.word,
                    options = (distractorsFor(word, words, random, fromWords = true) + word.word)
                        .shuffled(random),
                    context = blanked
                )
            }

            GameType.SCRAMBLE -> pool
                .filter { it.word.length in 3..14 && it.word.none { c -> c.isWhitespace() } }
                .map { word ->
                    GameQuestion(
                        type = type,
                        word = word.word,
                        answer = word.word,
                        scrambled = scramble(word.word, random),
                        context = word.translation
                    )
                }

            GameType.MATCH -> pool.take(6).map { word ->
                GameQuestion(
                    type = type,
                    word = word.word,
                    answer = word.translation,
                    context = word.contextSentence
                )
            }

            GameType.DICTATION -> pool
                .filter { it.word.none { c -> c.isWhitespace() } }
                .map { word ->
                    GameQuestion(
                        type = type,
                        word = word.word,
                        answer = word.word,
                        context = word.translation
                    )
                }

            GameType.CONTEXT_CHOICE -> pool.mapNotNull { word ->
                val sentence = word.contextSentence
                if (sentence.isBlank()) return@mapNotNull null
                val blanked = blankOut(sentence, word.word) ?: return@mapNotNull null
                GameQuestion(
                    type = type,
                    word = word.word,
                    answer = word.word,
                    options = (distractorsFor(word, words, random, fromWords = true) + word.word)
                        .shuffled(random),
                    context = blanked
                )
            }
        }
    }

    fun matchPairs(questions: List<GameQuestion>): List<MatchPair> =
        questions.map { MatchPair(it.word, it.answer) }

    /** Replaces the word inside a sentence with a blank, keeping the case. */
    fun blankOut(sentence: String, word: String): String? {
        // UNICODE_CHARACTER_CLASS: without it \b only recognises ASCII word
        // characters, so a Persian headword never matched inside its own
        // sentence and the game silently produced no questions.
        val match = Regex(
            "\\b" + Regex.escape(word) + "\\b",
            setOf(RegexOption.IGNORE_CASE, RegexOption.UNICODE_CHARACTER_CLASS)
        ).find(sentence) ?: return null
        val range = match.range
        return sentence.substring(0, range.first) + "____" +
            sentence.substring(range.last + 1)
    }

    fun scramble(word: String, random: Random = Random.Default): String {
        if (word.length <= 3) return word
        var candidate = word
        // Never hand back the answer itself.
        repeat(12) {
            val chars = word.toCharArray()
            for (i in chars.size - 1 downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = chars[i]
                chars[i] = chars[j]
                chars[j] = tmp
            }
            candidate = String(chars)
            if (!candidate.equals(word, ignoreCase = true)) return candidate
        }
        return candidate
    }

    private fun distractorsFor(
        target: SavedWord,
        all: List<SavedWord>,
        random: Random,
        fromWords: Boolean = false
    ): List<String> {
        val field: (SavedWord) -> String =
            if (fromWords) { w -> w.word } else { w -> w.translation }
        val wanted = if (fromWords) target.word else target.translation
        return all
            .asSequence()
            .filter { it.word != target.word }
            .map(field)
            .filter { it.isNotBlank() && !it.equals(wanted, ignoreCase = true) }
            .distinct()
            .toList()
            .shuffled(random)
            .take(CHOICES - 1)
            .let { list ->
                if (list.size < CHOICES - 1) {
                    list + List(CHOICES - 1 - list.size) { index -> "— ${index + 1}" }
                } else list
            }
    }
}
