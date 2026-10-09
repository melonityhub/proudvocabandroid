package com.proudvocab.android.core.subtitle

import kotlin.math.abs

/** One subtitle line. Times are in milliseconds. */
data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    fun contains(positionMs: Long): Boolean {
        val t = positionMs - OFFSET_TOLERANCE_MS
        return t >= startMs && t < endMs
    }
}

private const val OFFSET_TOLERANCE_MS = 0L

/**
 * A small, dependency-free subtitle reader.
 *
 * Supported: SubRip (.srt), WebVTT (.vtt), SubStation Alpha (.ass/.ssa) and
 * MicroDVD (.sub). Text encoding is detected from the BOM and, when there is
 * none, by trying UTF-8 first and falling back to windows-1256 (the encoding
 * almost every Persian subtitle file uses) and ISO-8859-1.
 */
object SubtitleParser {

    // "00:01:02,500" | "0:01:02.500" | "01:02.500"
    private val SRT_TIME = Regex("(\\d{1,3}):(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})")
    private val SRT_TIME_SHORT = Regex("(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})")
    private val SRT_ARROW = Regex("\\s*-->\\s*")
    private val ASS_TIME = Regex("(\\d+):(\\d{2}):(\\d{2})[.,](\\d{1,3})")
    // Literal braces are matched with character classes ([{] / [}]), never
    // with backslash escapes or bare braces: desktop java.util.regex (the
    // JVM unit tests) tolerates them, but Android's ICU-backed regex engine
    // rejects this exact pattern with PatternSyntaxException. Because these
    // properties live in an `object`, the failure surfaced as
    // ExceptionInInitializerError the first time anything touched
    // SubtitleParser — which crashed the app at launch (the player screen
    // builds PlayerViewModel eagerly). See BUGFIXES-FA.md §1.0.3 and
    // SubtitleParserDeviceTest.
    private val MICRODVD = Regex("^[{](\\d+)[}][{](\\d+)[}](.*)$")
    private val TAG = Regex("<[^>]*>")
    private val ASS_OVERRIDE = Regex("[{][^}]*[}]")

    fun parse(raw: ByteArray): List<SubtitleCue> {
        val text = decode(raw)
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("WEBVTT", ignoreCase = true) -> parseVtt(text)
            trimmed.startsWith("[Script Info]", ignoreCase = true) ||
                trimmed.contains("Dialogue:", ignoreCase = true) -> parseAss(text)
            MICRODVD.containsMatchIn(trimmed.lineSequence().firstOrNull() ?: "") -> parseMicroDvd(text)
            else -> parseSrt(text)
        }
    }

    // ------------------------------------------------------------- decoding

    fun decode(raw: ByteArray): String {
        if (raw.isEmpty()) return ""
        // BOM detection first.
        if (raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()) {
            return String(raw, 3, raw.size - 3, Charsets.UTF_8)
        }
        if (raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte()) {
            return String(raw, 2, raw.size - 2, Charsets.UTF_16LE)
        }
        if (raw.size >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte()) {
            return String(raw, 2, raw.size - 2, Charsets.UTF_16BE)
        }
        // UTF-8 strict (a single invalid byte means "not UTF-8").
        val utf8 = tryDecode(raw, Charsets.UTF_8)
        if (utf8 != null) return utf8
        return tryDecode(raw, charsetOrNull("windows-1256"))
            ?: tryDecode(raw, charsetOrNull("ISO-8859-1"))
            ?: String(raw, Charsets.UTF_8)
    }

    private fun charsetOrNull(name: String): java.nio.charset.Charset? =
        runCatching { java.nio.charset.Charset.forName(name) }.getOrNull()

    private fun tryDecode(raw: ByteArray, cs: java.nio.charset.Charset?): String? {
        if (cs == null) return null
        return runCatching {
            val decoder = cs.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(raw)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ srt

    fun parseSrt(text: String): List<SubtitleCue> {
        val out = ArrayList<SubtitleCue>()
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\\n{2,}"))
        var index = 0
        for (block in blocks) {
            val lines = block.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            var timeLine: String? = null
            val textLines = ArrayList<String>()
            for (line in lines) {
                if (timeLine == null && SRT_ARROW.containsMatchIn(line)) {
                    timeLine = line
                } else if (timeLine != null) {
                    textLines.add(line)
                }
            }
            val times = timeLine?.split(SRT_ARROW) ?: continue
            if (times.size < 2) continue
            val start = parseTimestamp(times[0]) ?: continue
            val end = parseTimestamp(times[1]) ?: (start + 3000L)
            val body = cleanText(textLines.joinToString("\n"))
            if (body.isBlank()) continue
            out.add(SubtitleCue(index++, start, end, body))
        }
        return out
    }

    // ------------------------------------------------------------------ vtt

    fun parseVtt(text: String): List<SubtitleCue> {
        val out = ArrayList<SubtitleCue>()
        val body = text.replace("\r\n", "\n").replace('\r', '\n')
        var index = 0
        var currentTimes: Pair<Long, Long>? = null
        val textLines = ArrayList<String>()

        fun flush() {
            val t = currentTimes ?: return
            val body2 = cleanText(textLines.joinToString("\n"))
            if (body2.isNotBlank()) {
                out.add(SubtitleCue(index++, t.first, t.second, body2))
            }
            currentTimes = null
            textLines.clear()
        }

        for (rawLine in body.split('\n')) {
            val line = rawLine.trim()
            if (line.startsWith("NOTE", ignoreCase = true) ||
                line.startsWith("WEBVTT", ignoreCase = true) ||
                line.startsWith("STYLE", ignoreCase = true) ||
                line.startsWith("REGION", ignoreCase = true)
            ) continue
            if (SRT_ARROW.containsMatchIn(line)) {
                flush()
                val times = line.split(SRT_ARROW)
                val start = parseTimestamp(times.getOrNull(0) ?: "") ?: continue
                val end = parseTimestamp(times.getOrNull(1) ?: "") ?: (start + 3000L)
                currentTimes = start to end
            } else if (line.isBlank()) {
                flush()
            } else if (currentTimes != null) {
                textLines.add(line)
            }
        }
        flush()
        return out
    }

    // ------------------------------------------------------------------ ass

    fun parseAss(text: String): List<SubtitleCue> {
        val out = ArrayList<SubtitleCue>()
        var index = 0
        for (rawLine in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = rawLine.trim()
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            val parts = line.substringAfter(':').split(',')
            if (parts.size < 10) continue
            val start = parseAssTime(parts[1]) ?: continue
            val end = parseAssTime(parts[2]) ?: (start + 3000L)
            val body = cleanText(
                parts.subList(9, parts.size).joinToString(",")
                    .replace(ASS_OVERRIDE, "")
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")
            )
            if (body.isBlank()) continue
            out.add(SubtitleCue(index++, start, end, body))
        }
        return out.sortedWith(compareBy({ it.startMs }, { it.endMs }))
            .mapIndexed { i, cue -> cue.copy(index = i) }
    }

    // ------------------------------------------------------------- microdvd

    fun parseMicroDvd(text: String): List<SubtitleCue> {
        val out = ArrayList<SubtitleCue>()
        var index = 0
        for (rawLine in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val m = MICRODVD.find(rawLine.trim()) ?: continue
            val startFrame = m.groupValues[1].toLongOrNull() ?: continue
            val endFrame = m.groupValues[2].toLongOrNull() ?: continue
            val body = cleanText(
                m.groupValues[3].replace('|', '\n')
                    .replace(ASS_OVERRIDE, "")
            )
            if (body.isBlank()) continue
            // 23.976 fps is the frame rate the format was designed around.
            val start = (startFrame * 1000L) / 24L
            val end = (endFrame * 1000L) / 24L
            out.add(SubtitleCue(index++, start, end, body))
        }
        return out
    }

    // ----------------------------------------------------------------- misc

    fun parseTimestamp(value: String): Long? {
        val v = value.trim().trimEnd(',', '.').trim()
        val long = SRT_TIME.find(v)
        if (long != null) {
            val h = long.groupValues[1].toLongOrNull() ?: return null
            val m = long.groupValues[2].toLongOrNull() ?: return null
            val s = long.groupValues[3].toLongOrNull() ?: return null
            var ms = long.groupValues[4].toLongOrNull() ?: 0L
            while (ms > 0 && ms < 10) ms *= 10
            while (ms >= 1000) ms /= 10
            return h * 3_600_000L + m * 60_000L + s * 1000L + ms
        }
        val short = SRT_TIME_SHORT.find(v)
        if (short != null) {
            val m = short.groupValues[1].toLongOrNull() ?: return null
            val s = short.groupValues[2].toLongOrNull() ?: return null
            var ms = short.groupValues[3].toLongOrNull() ?: 0L
            while (ms > 0 && ms < 10) ms *= 10
            while (ms >= 1000) ms /= 10
            return m * 60_000L + s * 1000L + ms
        }
        return null
    }

    private fun parseAssTime(value: String): Long? {
        val m = ASS_TIME.find(value.trim()) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: return null
        val mi = m.groupValues[2].toLongOrNull() ?: return null
        val s = m.groupValues[3].toLongOrNull() ?: return null
        val cs = m.groupValues[4].toLongOrNull() ?: 0L
        val ms = if (cs < 10) cs * 100 else cs * 10
        return h * 3_600_000L + mi * 60_000L + s * 1000L + ms
    }

    fun cleanText(value: String): String =
        value
            .replace(TAG, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(' ', ' ')
            .trim()

    /** Formats a position as `H:MM:SS` / `M:SS`, in Persian digits when asked. */
    fun formatTime(ms: Long, persianDigits: Boolean = false): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        // Locale.US, not the default locale: on a Persian device the default
        // formatter already emits Persian digits, which made the explicit
        // mapping below a no-op and the "use Persian digits" switch dead.
        val out = if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%d:%02d", m, s)
        }
        return if (persianDigits) {
            out.map { if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it }.joinToString("")
        } else out
    }

    /** Binary search for the cue active at [positionMs]. */
    fun cueIndexAt(cues: List<SubtitleCue>, positionMs: Long): Int {
        if (cues.isEmpty()) return -1
        var lo = 0
        var hi = cues.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val cue = cues[mid]
            if (cue.startMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (result < 0) return -1
        // A gap between cues means "no subtitle right now".
        return if (positionMs < cues[result].endMs) result else -1
    }

    /** Shifts every cue by [offsetMs]; used by the subtitle delay control. */
    fun shift(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> {
        if (offsetMs == 0L) return cues
        return cues.map {
            it.copy(
                startMs = (it.startMs + offsetMs).coerceAtLeast(0L),
                endMs = (it.endMs + offsetMs).coerceAtLeast(0L)
            )
        }
    }

    /** Offset that would move the first cue to [targetMs]. */
    fun suggestDelay(cues: List<SubtitleCue>, targetMs: Long): Long =
        cues.firstOrNull()?.let { targetMs - it.startMs } ?: 0L
}
