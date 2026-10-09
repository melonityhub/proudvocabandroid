package com.proudvocab.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.proudvocab.android.core.subtitle.SubtitleCue
import com.proudvocab.android.core.subtitle.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the subtitle parser on a real device (emulator).
 *
 * Why this exists: the JVM unit tests compile regexes with desktop
 * java.util.regex, which is more permissive than the ICU-backed engine
 * Android uses at runtime. 1.0.2 shipped a MicroDVD/ASS pattern that the JVM
 * accepted but Android's ICU rejected with PatternSyntaxException; because
 * the patterns live in an `object`, the very first touch of SubtitleParser
 * threw ExceptionInInitializerError and killed the app at launch. These
 * tests force every parser entry point to run on-device so that class of
 * bug fails CI instead of users' phones.
 */
@RunWith(AndroidJUnit4::class)
class SubtitleParserDeviceTest {

    @Test
    fun srtIsParsedOnDevice() {
        val cues = SubtitleParser.parseSrt(
            "1\n00:00:01,000 --> 00:00:02,500\nHello world\n\n" +
                "2\n00:00:03.000 --> 00:00:04.000\nSecond line\n"
        )
        assertEquals(2, cues.size)
        assertEquals("Hello world", cues[0].text)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(2_500L, cues[0].endMs)
    }

    @Test
    fun vttIsParsedOnDevice() {
        val cues = SubtitleParser.parseVtt(
            "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi there\n"
        )
        assertEquals(1, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals("Hi there", cues[0].text)
    }

    @Test
    fun microDvdIsParsedOnDevice() {
        // {startFrame}{endFrame}text — frames convert at 24 fps.
        val cues = SubtitleParser.parseMicroDvd("{100}{150}Hello")
        assertEquals(1, cues.size)
        assertEquals(4166L, cues[0].startMs) // 100 * 1000 / 24
        assertEquals(6250L, cues[0].endMs)   // 150 * 1000 / 24
        assertEquals("Hello", cues[0].text)
    }

    @Test
    fun assIsParsedOnDeviceAndOverrideBlocksAreStripped() {
        val ass = """
            [Script Info]
            Title: test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello {\i1}world{\i0}
        """.trimIndent()
        val cues = SubtitleParser.parseAss(ass)
        assertEquals(1, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals("Hello world", cues[0].text)
    }

    @Test
    fun windows1256EncodedSrtIsDecodedOnDevice() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nسلام دنیا\n"
        val bytes = srt.toByteArray(charset("windows-1256"))
        val cues = SubtitleParser.parse(bytes)
        assertEquals(1, cues.size)
        assertEquals("سلام دنیا", cues[0].text)
    }

    @Test
    fun shiftLookupAndFormatWorkOnDevice() {
        val cues = listOf(SubtitleCue(0, 1_000L, 2_000L, "one"))
        val shifted = SubtitleParser.shift(cues, 500L)
        assertEquals(1_500L, shifted[0].startMs)
        assertEquals(0, SubtitleParser.cueIndexAt(shifted, 1_600L))
        assertEquals(-1, SubtitleParser.cueIndexAt(shifted, 5_000L))
        assertEquals("0:01", SubtitleParser.formatTime(1_000L))
    }

    private fun charset(name: String) = java.nio.charset.Charset.forName(name)
}
