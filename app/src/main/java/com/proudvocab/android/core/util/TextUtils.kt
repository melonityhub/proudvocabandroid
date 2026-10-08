package com.proudvocab.android.core.util

import com.proudvocab.android.core.dict.FdTables

/**
 * Language of a piece of text, as decided by the original algorithm:
 * if the number of Persian letters/digits is greater than or equal to every
 * other character, the text counts as Persian.
 */
enum class Lang { English, Persian }

/** A single piece of a subtitle line, used for clickable word chips. */
data class Token(
    val text: String,
    val start: Int,
    val end: Int,
    val isWord: Boolean
)

/**
 * Pure-Kotlin port of the string helpers of the FastDic / ProudVocab sources.
 * Everything here is deterministic and unit tested.
 */
object TextUtils {

    private const val PERSIAN_LETTERS = "آبپتثجچحخدذرزژسشصضطظعغفقکكگلمنواأهئيی؟"
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    private val REPLACEMENT: Map<Int, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val map = HashMap<Int, Int>(FdTables.BROKEN_CHARS.size * 2)
        for (i in FdTables.BROKEN_CHARS.indices) {
            map[FdTables.BROKEN_CHARS[i]] = FdTables.CORRECT_CHARS[i]
        }
        map
    }

    private val PE_REPLACEMENT: List<Pair<String, String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        FdTables.PE_TO_NUMBER.toList()
    }

    fun isPersianChar(c: Char): Boolean = c in PERSIAN_LETTERS || c in PERSIAN_DIGITS

    /** `FDLanguageWord.find` */
    fun langOf(text: String?): Lang {
        if (text.isNullOrEmpty()) return Lang.English
        var fa = 0
        for (c in text) if (isPersianChar(c)) fa++
        val other = text.length - fa
        return if (fa >= other) Lang.Persian else Lang.English
    }

    fun isEnglish(text: String?) = langOf(text) == Lang.English
    fun isPersian(text: String?) = langOf(text) == Lang.Persian

    /** `FDCategory.findLanguage` – 1 = Persian, 2 = English. */
    fun categoryOf(text: String?): Int =
        if (isEnglish(text)) 2 else 1

    // ------------------------------------------------------------ numbers

    fun toFarsiDigits(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val out = StringBuilder(s.length)
        for (c in s) {
            val idx = "0123456789".indexOf(c)
            out.append(if (idx >= 0) PERSIAN_DIGITS[idx] else c)
        }
        return out.toString()
    }

    fun toEnglishDigits(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val out = StringBuilder(s.length)
        for (c in s) {
            val i1 = PERSIAN_DIGITS.indexOf(c)
            val i2 = ARABIC_DIGITS.indexOf(c)
            val idx = if (i1 >= 0) i1 else i2
            out.append(if (idx >= 0) "0123456789"[idx] else c)
        }
        return out.toString()
    }

    fun removeDigits(s: String?): String = s?.replace(Regex("[0-9]"), "") ?: ""

    // ------------------------------------------------------------ folding

    /** `StringUtils.charReplacement` */
    fun charReplacement(s: String): String {
        val map = REPLACEMENT
        val out = StringBuilder(s.length)
        var changed = false
        for (c in s) {
            val code = c.code
            val mapped = map[code]
            if (mapped != null && mapped != code) {
                out.append(mapped.toChar())
                changed = true
            } else {
                out.append(c)
            }
        }
        return if (changed) out.toString() else s
    }

    /** `StringUtils.trimmingAndLowerCaseWords` */
    fun trimLower(s: String?): String? {
        if (s == null) return null
        val trimmed = s.trim { it <= ' ' }
        // The original only folds short inputs; long ones are returned as-is.
        if (trimmed.length > 100) return trimmed
        return charReplacement(trimmed).lowercase()
    }

    /** `StringUtils.peCharacterToNumber` */
    fun peToNumber(word: String): String {
        if (word.isEmpty()) return ""
        var out = word
        for ((from, to) in PE_REPLACEMENT) out = out.replace(from, to)
        return out
    }

    /** Escapes SQLite GLOB wildcards. */
    fun globEscape(s: String): String =
        s.replace("[", "[[]").replace("?", "[?]").replace("*", "[*]")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    // ------------------------------------------------------------ tokens

    /**
     * Splits a subtitle line into word / non-word tokens with offsets so that
     * each word can be rendered as an independently clickable chip.
     */
    fun tokenize(text: String): List<Token> {
        val out = ArrayList<Token>()
        if (text.isEmpty()) return out
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val isWordChar = c.isLetterOrDigit() || c == '\'' || c == '-' || c == '’'
            if (!isWordChar) {
                out.add(Token(c.toString(), i, i + 1, false))
                i++
                continue
            }
            var j = i
            while (j < text.length) {
                val d = text[j]
                val ok = d.isLetterOrDigit() || d == '\'' || d == '-' || d == '’'
                if (!ok) break
                j++
            }
            out.add(Token(text.substring(i, j), i, j, true))
            i = j
        }
        return out
    }

    fun wordsOf(text: String): List<String> =
        tokenize(text).filter { it.isWord }.map { it.text }

    // ------------------------------------------------------------ stemming

    private val IRREGULAR = mapOf(
        "went" to "go", "gone" to "go", "brought" to "bring", "fell" to "fall",
        "fallen" to "fall", "got" to "get", "gotten" to "get", "found" to "find",
        "grew" to "grow", "grown" to "grow", "heard" to "hear", "kept" to "keep",
        "laid" to "lay", "led" to "lead", "ran" to "run", "stood" to "stand",
        "took" to "take", "taken" to "take", "tore" to "tear", "torn" to "tear",
        "thought" to "think", "was" to "be", "were" to "be", "been" to "be",
        "am" to "be", "is" to "be", "are" to "be", "has" to "have", "had" to "have",
        "did" to "do", "done" to "do", "made" to "make", "said" to "say",
        "saw" to "see", "seen" to "see", "came" to "come", "knew" to "know",
        "known" to "know", "gave" to "give", "given" to "give"
    )

    /**
     * Very small English stemmer.
     *
     * Stemming is only ever used to *find* a dictionary entry, and a wrong
     * guess is always recoverable, so instead of one guess we return every
     * plausible base form in the order they should be tried.
     */
    fun lemmaCandidates(raw: String): List<String> {
        val w = raw.lowercase().trim('\'', '’', '-', '.', ',', '!', '?', ':', ';', '”', '“', '"')
        val out = LinkedHashSet<String>()
        out.add(w)
        if (w.length <= 2) return out.toList()
        IRREGULAR[w]?.let { out.add(it) }

        fun add(s: String) {
            if (s.length >= 2 && s != w) out.add(s)
        }

        if (w.endsWith("ies") && w.length > 4) add(w.dropLast(3) + "y")
        if (w.endsWith("ves") && w.length > 4) {
            add(w.dropLast(3) + "f")
            add(w.dropLast(3) + "fe")
        }
        if (w.endsWith("oes") && w.length > 4) add(w.dropLast(2))
        if (w.endsWith("ches") || w.endsWith("shes") ||
            w.endsWith("sses") || w.endsWith("xes") || w.endsWith("zes")
        ) add(w.dropLast(2))
        if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) {
            add(w.dropLast(1))
        }
        if (w.endsWith("ied") && w.length > 5) add(w.dropLast(3) + "y")
        if (w.endsWith("ed") && w.length > 4) {
            val stem = w.dropLast(2)
            add(stem)
            if (stem.length > 2 && stem.last() == stem[stem.length - 2] &&
                stem.last() !in "lsz"
            ) add(stem.dropLast(1))
        }
        if (w.endsWith("ing") && w.length > 5) {
            val stem = w.dropLast(3)
            add(stem)
            add(stem + "e")
            if (stem.length > 2 && stem.last() == stem[stem.length - 2] &&
                stem.last() !in "lsz"
            ) add(stem.dropLast(1))
        }
        if (w.endsWith("ly") && w.length > 4) add(w.dropLast(2))
        if (w.endsWith("er") && w.length > 4) {
            add(w.dropLast(2))
            add(w.dropLast(1))
        }
        if (w.endsWith("est") && w.length > 5) {
            add(w.dropLast(3))
            add(w.dropLast(2))
        }
        if (w.endsWith("ily") && w.length > 5) add(w.dropLast(3) + "y")
        return out.toList()
    }

    /** Convenience: the shortest (most stripped) candidate. */
    fun baseForm(word: String): String {
        val c = lemmaCandidates(word)
        return c.minByOrNull { it.length } ?: word
    }

    /** Collapses whitespace and normalises Persian punctuation. */
    fun normalizeForMatch(s: String): String {
        val t = s
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('إ', 'ا')
            .replace('أ', 'ا')
            .replace('آ', 'ا')
            .replace('؟', '?')
            .replace('،', ',')
            .replace('؛', ';')
            .replace('٪', '%')
            .replace(Regex("[\\u064B-\\u0652]"), "")
            .replace(' ', ' ')
            .replace(Regex("\\s+"), " ")
        return t.trim()
    }

    /** Formats a number with Persian digits when the UI language is Persian. */
    fun formatNumber(value: Int, persianDigits: Boolean): String =
        if (persianDigits) toFarsiDigits(value.toString()) else value.toString()

    fun formatNumber(value: Long, persianDigits: Boolean): String =
        if (persianDigits) toFarsiDigits(value.toString()) else value.toString()
}
