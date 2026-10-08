package com.proudvocab.android

import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.games.GameEngine
import com.proudvocab.android.core.games.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private val words = (1..12).map { i ->
        SavedWord(
            id = i.toLong(),
            word = listOf(
                "serendipity", "meticulous", "resilient", "ambiguous",
                "reluctant", "candid", "pragmatic", "verbose",
                "lucid", "tenacious", "novel", "grain"
            )[i - 1],
            language = "en",
            translation = listOf(
                "اتفاق خوش‌یمن", "دقیق", "تاب‌آور", "مبهم",
                "بی‌میل", "صریح", "عمل‌گرا", "پرحرف",
                "روشن", "سخت‌کوش", "تازه", "دانه"
            )[i - 1],
            contextSentence = "The word ${listOf(
                "serendipity", "meticulous", "resilient", "ambiguous",
                "reluctant", "candid", "pragmatic", "verbose",
                "lucid", "tenacious", "novel", "grain"
            )[i - 1]} appeared in the episode."
        )
    }

    @Test
    fun `every game type produces questions`() {
        GameType.entries.forEach { type ->
            val questions = GameEngine.build(type, words, seed = 7L)
            assertTrue("$type produced nothing", questions.isNotEmpty())
        }
    }

    @Test
    fun `multiple choice always offers the right answer`() {
        GameEngine.build(GameType.MULTIPLE_CHOICE, words, seed = 3L).forEach { q ->
            assertEquals(4, q.options.size)
            assertTrue(q.options.contains(q.answer))
            assertEquals(q.options.toSet().size, q.options.size)
        }
    }

    @Test
    fun `fill in the blank hides the word and still accepts it`() {
        val questions = GameEngine.build(GameType.FILL_BLANK, words, seed = 3L)
        assertTrue(questions.isNotEmpty())
        questions.forEach { q ->
            assertTrue(q.context.contains("____"))
            assertNotEquals(q.word, q.context)
            assertTrue(q.isCorrect(q.answer))
            assertTrue(q.isCorrect(q.answer.uppercase()))
        }
    }

    @Test
    fun `scramble is an anagram that is never the answer itself`() {
        GameEngine.build(GameType.SCRAMBLE, words, seed = 11L).forEach { q ->
            assertNotEquals(q.scrambled, q.word)
            assertEquals(
                q.word.toList().sorted(),
                q.scrambled.toList().sorted()
            )
        }
    }

    @Test
    fun `dictation asks for the word and shows the meaning as a hint`() {
        GameEngine.build(GameType.DICTATION, words, seed = 5L).forEach { q ->
            assertEquals(q.word, q.answer)
            assertTrue(q.context.isNotBlank())
        }
    }

    @Test
    fun `matching is capped and pairs word to meaning`() {
        val questions = GameEngine.build(GameType.MATCH, words, seed = 5L)
        assertTrue(questions.size <= 6)
        val pairs = GameEngine.matchPairs(questions)
        assertEquals(questions.size, pairs.size)
        val first = pairs.first()
        assertTrue(words.any { it.word == first.word && it.translation == first.meaning })
    }

    @Test
    fun `context choice blanks the sentence`() {
        GameEngine.build(GameType.CONTEXT_CHOICE, words, seed = 5L).forEach { q ->
            assertTrue(q.context.contains("____"))
            assertTrue(q.options.contains(q.answer))
        }
    }

    @Test
    fun `an empty deck never produces a round`() {
        GameType.entries.forEach { type ->
            assertTrue(GameEngine.build(type, emptyList()).isEmpty())
        }
    }

    @Test
    fun `words without a translation are skipped instead of crashing`() {
        val partial = listOf(SavedWord(word = "orphan", translation = ""))
        assertTrue(GameEngine.build(GameType.MULTIPLE_CHOICE, partial).isEmpty())
    }

    @Test
    fun `blankOut keeps the rest of the sentence intact`() {
        val blanked = GameEngine.blankOut("I saw the cat on the mat", "cat")
        assertEquals("I saw the ____ on the mat", blanked)
    }

    @Test
    fun `blankOut returns null when the word is absent`() {
        assertEquals(null, GameEngine.blankOut("nothing here", "cat"))
    }

    @Test
    fun `the same seed always builds the same round`() {
        val a = GameEngine.build(GameType.MULTIPLE_CHOICE, words, seed = 42L)
        val b = GameEngine.build(GameType.MULTIPLE_CHOICE, words, seed = 42L)
        assertEquals(a, b)
    }
}
