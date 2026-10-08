package com.proudvocab.android

import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.export.DeckExporter
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.settings.TextStylePref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckExporterTest {

    private val words = listOf(
        SavedWord(
            id = 1L,
            word = "serendipity",
            translation = "اتفاق خوش‌یمن",
            contextSentence = "It was pure serendipity.",
            sourceTitle = "Friends S01E01",
            tags = "episode,noun",
            cefr = "C1",
            kind = 0,
            learned = true,
            intervalDays = 6.0,
            streak = 3,
            reviewCount = 3
        ),
        SavedWord(
            id = 2L,
            word = "break the ice",
            translation = "یخ را شکستن",
            contextSentence = "Let's \"break the ice\"; shall we?",
            tags = "idiom",
            kind = 1
        )
    )

    @Test
    fun `json round trips without losing progress`() {
        val json = DeckExporter.toJson(words)
        val back = DeckExporter.fromJson(json)
        assertEquals(words.size, back.size)
        val first = back.first { it.word == "serendipity" }
        assertEquals("اتفاق خوش‌یمن", first.translation)
        assertEquals(6.0, first.intervalDays, 0.0001)
        assertEquals(3, first.reviewCount)
        assertEquals(3, first.streak)
        assertTrue(first.learned)
        assertEquals("C1", first.cefr)
        assertEquals("Friends S01E01", first.sourceTitle)
    }

    @Test
    fun `broken json degrades to an empty list instead of throwing`() {
        assertTrue(DeckExporter.fromJson("not json at all").isEmpty())
        assertTrue(DeckExporter.fromJson("").isEmpty())
    }

    @Test
    fun `unknown fields in a newer backup are ignored`() {
        val withExtra = """
            [{"word":"cat","language":"en","translation":"گربه","somethingNew":42}]
        """.trimIndent()
        val back = DeckExporter.fromJson(withExtra)
        assertEquals(1, back.size)
        assertEquals("cat", back[0].word)
    }

    @Test
    fun `anki csv declares semicolons and four columns`() {
        val csv = DeckExporter.toAnkiCsv(words)
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        assertTrue(lines[0].startsWith("#separator:Semicolon"))
        assertTrue(csv.contains("#columns:Front;Back;Context;Tags"))
    }

    @Test
    fun `anki csv quotes fields that contain separators or quotes`() {
        val csv = DeckExporter.toAnkiCsv(words)
        val row = csv.lineSequence().first { it.contains("break the ice") }
        assertTrue(row.contains("\"\""))
    }

    @Test
    fun `anki csv escapes newlines as html breaks`() {
        val multi = listOf(
            SavedWord(word = "a", translation = "b", contextSentence = "line1\nline2")
        )
        val csv = DeckExporter.toAnkiCsv(multi)
        assertTrue(csv.contains("line1<br>line2"))
        assertTrue(csv.lineSequence().none { it.startsWith("line2") })
    }

    @Test
    fun `every row has exactly three separators`() {
        val csv = DeckExporter.toAnkiCsv(words)
        val dataRows = csv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }
        dataRows.forEach { row ->
            // Quoted semicolons must not be counted, so compare on the unescaped
            // form: a valid Anki row always yields four fields.
            val fields = splitAnkiRow(row)
            assertEquals(4, fields.size)
        }
    }

    private fun splitAnkiRow(row: String): List<String> {
        val out = ArrayList<String>()
        var current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                c == '"' -> {
                    if (quoted && i + 1 < row.length && row[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else quoted = !quoted
                }
                c == ';' && !quoted -> {
                    out.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        out.add(current.toString())
        return out
    }

    // ------------------------------------------------------------- styling

    @Test
    fun `every style target has its own default`() {
        val defaults = StyleTarget.ALL.associateWith { TextStylePref.defaultFor(it) }
        assertEquals(StyleTarget.ALL.size, defaults.size)
        // Subtitles start white and centred; app text follows the theme.
        assertEquals("#FFFFFFFF", defaults[StyleTarget.SUBTITLE_PRIMARY]!!.color)
        assertEquals(null, defaults[StyleTarget.APP]!!.color)
    }

    @Test
    fun `size is the target base multiplied by the user scale`() {
        val pref = TextStylePref(scale = 2f)
        assertEquals(44f, pref.sizeSp(StyleTarget.SUBTITLE_PRIMARY), 0.001f)
        assertEquals(30f, pref.sizeSp(StyleTarget.APP), 0.001f)
    }
}
