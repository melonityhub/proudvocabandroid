package com.proudvocab.android

import com.proudvocab.android.core.util.ColorCodec
import com.proudvocab.android.core.util.Lang
import com.proudvocab.android.core.util.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Persian/English text handling is the core of the dictionary search. */
class TextUtilsTest {

    @Test
    fun `language detection`() {
        assertEquals(Lang.Persian, TextUtils.langOf("سلام دنیا"))
        assertEquals(Lang.English, TextUtils.langOf("hello world"))
        assertTrue(TextUtils.isEnglish("the cat sat"))
        assertTrue(TextUtils.isPersian("کتاب"))
    }

    @Test
    fun `ascii digits become farsi digits`() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", TextUtils.toFarsiDigits("0123456789"))
        assertEquals("ساعت ۱۲", TextUtils.toFarsiDigits("ساعت 12"))
    }

    @Test
    fun `farsi digits round trip back to ascii`() {
        assertEquals("1234", TextUtils.toEnglishDigits("۱۲۳۴"))
        assertEquals("1234", TextUtils.toEnglishDigits("١٢٣٤"))
    }

    @Test
    fun `persian words map to a stable numeric key`() {
        val a = TextUtils.peToNumber("سلام".trim())
        val b = TextUtils.peToNumber("سلام".trim())
        assertEquals(a, b)
        assertTrue(a.isNotBlank())
    }

    @Test
    fun `trimLower folds case and trims`() {
        assertEquals("hello", TextUtils.trimLower("  Hello  "))
        assertNull(TextUtils.trimLower(null))
    }

    @Test
    fun `normalising for matching folds arabic variants`() {
        assertEquals("\u06A9\u062A\u0627\u0628", TextUtils.normalizeForMatch("\u0643\u062A\u0627\u0628"))
        assertEquals("a b", TextUtils.normalizeForMatch("a\n  b"))
    }

    @Test
    fun `wordsOf keeps only word tokens in order`() {
        assertEquals(listOf("One", "two", "three"), TextUtils.wordsOf("One, two — three!"))
    }

    @Test
    fun `tokenize reports non word tokens too`() {
        val tokens = TextUtils.tokenize("hi!")
        assertTrue(tokens.any { !it.isWord })
        assertEquals(listOf("hi"), tokens.filter { it.isWord }.map { it.text })
    }

    @Test
    fun `glob escaping protects sqlite wildcards`() {
        assertEquals("a[[]b]", TextUtils.globEscape("a[b]"))
        assertEquals("100[*]", TextUtils.globEscape("100*"))
        assertEquals("who[?]", TextUtils.globEscape("who?"))
    }

    @Test
    fun `levenshtein distance is symmetric and bounded`() {
        assertEquals(0, TextUtils.levenshtein("same", "same"))
        assertEquals(
            TextUtils.levenshtein("kitten", "sitting"),
            TextUtils.levenshtein("sitting", "kitten")
        )
        assertTrue(TextUtils.levenshtein("abc", "xyz") <= 3)
    }

    @Test
    fun `lemma candidates always include the word itself`() {
        val candidates = TextUtils.lemmaCandidates("running")
        assertTrue(candidates.contains("running"))
        assertTrue(TextUtils.lemmaCandidates("went").contains("go"))
    }

    @Test
    fun `base form is the shortest candidate`() {
        assertTrue(TextUtils.baseForm("cats").length <= 4)
    }

    @Test
    fun `formatNumber honours the persian digit switch`() {
        assertEquals("128", TextUtils.formatNumber(128, false))
        assertEquals("۱۲۸", TextUtils.formatNumber(128, true))
    }

    // ------------------------------------------------------------- colours

    @Test
    fun `colour parsing accepts both rgb and argb`() {
        assertEquals("#FFFF0000", ColorCodec.normalize("#FF0000"))
        assertEquals("#80FF0000", ColorCodec.normalize("#80FF0000"))
        assertEquals("#FFFF0000", ColorCodec.normalize("FF0000"))
        assertEquals(0xFFFF0000uL, ColorCodec.parseULong("#FF0000"))
        assertEquals(0x80FF0000uL, ColorCodec.parseULong("#80FF0000"))
        assertEquals(0xFFFF0000uL, ColorCodec.parseULong("FF0000"))
    }

    @Test
    fun `broken colours parse to null instead of throwing`() {
        assertNull(ColorCodec.parse("nope"))
        assertNull(ColorCodec.parse(""))
        assertNull(ColorCodec.parse("#12345"))
        assertNull(ColorCodec.parse(null))
    }

    @Test
    fun `hex round trips`() {
        val packed: ULong = ColorCodec.parseULong("#123456")!!
        assertEquals("#FF123456", ColorCodec.toHex(packed))
        assertEquals("#123456", ColorCodec.toHexRgb(packed))
    }

    @Test
    fun `alpha and luminance helpers behave`() {
        assertEquals(0.0, ColorCodec.alphaOf(0x00FFFFFFuL).toDouble(), 0.001)
        assertEquals(1.0, ColorCodec.alphaOf(0xFFFFFFFFuL).toDouble(), 0.001)
        assertTrue(ColorCodec.isDark(0xFF000000uL))
        assertFalse(ColorCodec.isDark(0xFFFFFFFFuL))
        assertEquals(0x80123456uL, ColorCodec.withAlpha(0xFF123456uL, 0.502f))
        assertEquals(0x00123456uL, ColorCodec.withAlpha(0xFF123456uL, 0f))
    }
}
