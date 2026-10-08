package com.proudvocab.android

import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.games.GameEngine
import com.proudvocab.android.core.games.GameType
import com.proudvocab.android.core.subtitle.SubtitleParser
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app runs on Persian devices, where `Locale.getDefault()` is `fa-IR` and
 * `java.util.Formatter` happily renders `42` as `۴۲`. Anything that formats
 * without an explicit locale therefore behaves differently in the field than it
 * does on a CI runner.
 */
class LocaleFormattingTest {

    private lateinit var previous: Locale

    @Before
    fun usePersianLocale() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previous)
    }

    @Test
    fun `time codes keep ascii digits unless persian digits are asked for`() {
        val oneHourTwoMinutesFiveSeconds = 3_725_000L
        assertEquals(
            "1:02:05",
            SubtitleParser.formatTime(oneHourTwoMinutesFiveSeconds, persianDigits = false)
        )
        assertEquals(
            "۱:۰۲:۰۵",
            SubtitleParser.formatTime(oneHourTwoMinutesFiveSeconds, persianDigits = true)
        )
        assertEquals("2:05", SubtitleParser.formatTime(125_000L, persianDigits = false))
    }

    @Test
    fun `persian headwords can be blanked out of their own sentence`() {
        val blanked = GameEngine.blankOut("من هر روز کتاب می‌خوانم", "کتاب")
        assertEquals("من هر روز ____ می‌خوانم", blanked)
    }

    @Test
    fun `the fill in the blank game produces questions for persian decks`() {
        val deck = listOf(
            SavedWord(id = 1, word = "کتاب", translation = "book", contextSentence = "من هر روز کتاب می‌خوانم"),
            SavedWord(id = 2, word = "میز", translation = "table", contextSentence = "میز چوبی است"),
            SavedWord(id = 3, word = "پنجره", translation = "window", contextSentence = "پنجره باز است"),
            SavedWord(id = 4, word = "دوست", translation = "friend", contextSentence = "دوست من آمد")
        )
        val questions = GameEngine.build(GameType.FILL_BLANK, deck, seed = 7L)
        assertTrue("no questions built: $questions", questions.isNotEmpty())
        assertTrue(questions.all { it.context.contains("____") })
    }
}
