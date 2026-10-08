package com.proudvocab.android

import com.proudvocab.android.core.subtitle.SubtitleCue
import com.proudvocab.android.core.subtitle.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        Hello there

        2
        00:00:04,000 --> 00:00:06,000
        General Kenobi!
    """.trimIndent()

    private val vtt = """
        WEBVTT

        00:00:01.000 --> 00:00:03.500
        Hello there

        00:00:04.000 --> 00:00:06.000
        General Kenobi!
    """.trimIndent()

    @Test
    fun `srt cues are parsed with timings and text`() {
        val cues = SubtitleParser.parseSrt(srt)
        assertEquals(2, cues.size)
        assertEquals(0, cues[0].index)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3500L, cues[0].endMs)
        assertEquals("Hello there", cues[0].text)
        assertEquals("General Kenobi!", cues[1].text)
    }

    @Test
    fun `vtt cues are parsed too`() {
        val cues = SubtitleParser.parseVtt(vtt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
    }

    @Test
    fun `microdvd frame numbers are converted`() {
        val cues = SubtitleParser.parseMicroDvd("{100}{150}Hello")
        assertTrue(cues.isNotEmpty())
        assertTrue(cues[0].endMs > cues[0].startMs)
    }

    @Test
    fun `ass dialogue lines are parsed`() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,Hello there
        """.trimIndent()
        val cues = SubtitleParser.parseAss(ass)
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startMs)
    }

    @Test
    fun `timestamps accept both comma and dot separators`() {
        assertEquals(1000L, SubtitleParser.parseTimestamp("00:00:01,000"))
        assertEquals(1000L, SubtitleParser.parseTimestamp("00:00:01.000"))
        assertEquals(61_000L, SubtitleParser.parseTimestamp("00:01:01,000"))
    }

    @Test
    fun `formatting drops the hour when it is zero`() {
        assertEquals("1:23", SubtitleParser.formatTime(83_000L))
        assertEquals("1:01:00", SubtitleParser.formatTime(3_660_000L))
        assertEquals("۱:۲۳", SubtitleParser.formatTime(83_000L, persianDigits = true))
    }

    @Test
    fun `lookup finds the active cue and reports gaps`() {
        val cues = listOf(
            SubtitleCue(0, 1_000L, 2_000L, "one"),
            SubtitleCue(1, 5_000L, 6_000L, "two")
        )
        assertEquals(0, SubtitleParser.cueIndexAt(cues, 1_500L))
        assertEquals(1, SubtitleParser.cueIndexAt(cues, 5_500L))
        assertEquals(-1, SubtitleParser.cueIndexAt(cues, 3_000L))
        assertEquals(-1, SubtitleParser.cueIndexAt(emptyList(), 0L))
    }

    @Test
    fun `shifting never produces negative times`() {
        val cues = listOf(SubtitleCue(0, 1_000L, 2_000L, "one"))
        val shifted = SubtitleParser.shift(cues, -5_000L)
        assertEquals(0L, shifted[0].startMs)
        assertEquals(0L, shifted[0].endMs)
    }

    @Test
    fun `shifting is reversible`() {
        val cues = SubtitleParser.parseSrt(srt)
        val moved = SubtitleParser.shift(cues, 1_234L)
        val back = SubtitleParser.shift(moved, -1_234L)
        assertEquals(cues.map { it.startMs }, back.map { it.startMs })
    }

    @Test
    fun `delay suggestion lines the first cue up with the player`() {
        val cues = listOf(SubtitleCue(0, 2_000L, 3_000L, "one"))
        assertEquals(500L, SubtitleParser.suggestDelay(cues, 2_500L))
        assertEquals(0L, SubtitleParser.suggestDelay(emptyList(), 2_500L))
    }

    @Test
    fun `cleanText strips markup`() {
        assertTrue(SubtitleParser.cleanText("<i>Hello</i> there").contains("Hello"))
        assertEquals("Hello  there", SubtitleParser.cleanText("  Hello  there  "))
    }
}
