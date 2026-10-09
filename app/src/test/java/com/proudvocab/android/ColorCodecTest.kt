package com.proudvocab.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.proudvocab.android.core.util.ColorCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorCodecTest {

    @Test
    fun `parseColor builds a valid sRGB color for the shipped defaults`() {
        // These are colours the app ships with (subtitle primary/secondary,
        // subtitle background, accent swatches, game accents). Since Compose
        // 1.7 a packed Color stores the colour-space id in its LOW 6 bits, so
        // constructing Color(argb.toULong()) directly — the raw value-class
        // constructor — leaves the id equal to `argb and 0x3F` (63 for
        // #FFFFFFFF), which crashes with ArrayIndexOutOfBoundsException
        // (length=18; index=63) the first time the colour is converted, e.g.
        // while laying out a Text. toArgb() performs exactly that conversion,
        // so it must not throw for any of them.
        val defaults = listOf(
            "#FFFFFFFF", "#FFCBD5E1", "#FF0A0F1E", "#80000000",
            "#FF22C55E", "#FFF0ABFC", "#FF7DD3FC", "#00000000", "FF22C55E"
        )
        for (hex in defaults) {
            val color = ColorCodec.parseColor(hex)
            checkNotNull(color) { "parseColor($hex) returned null" }
            color.toArgb() // must not throw
        }
    }

    @Test
    fun `parseColor round-trips the components`() {
        val color = ColorCodec.parseColor("#FFCBD5E1")!!
        assertEquals(0xFFCBD5E1.toInt(), color.toArgb())
        assertEquals(1f, color.alpha, 0.001f)
        assertEquals(0xCB / 255f, color.red, 0.001f)
        assertEquals(0xD5 / 255f, color.green, 0.001f)
        assertEquals(0xE1 / 255f, color.blue, 0.001f)
    }

    @Test
    fun `parseColor keeps the alpha channel`() {
        val color = ColorCodec.parseColor("#80FF0000")!!
        assertEquals(0x80 / 255f, color.alpha, 0.001f)
        assertEquals(0xFF0000.toInt(), color.toArgb() and 0x00FFFFFF)
    }

    @Test
    fun `parseColor rejects anything that is not a colour`() {
        assertNull(ColorCodec.parseColor(null))
        assertNull(ColorCodec.parseColor(""))
        assertNull(ColorCodec.parseColor("   "))
        assertNull(ColorCodec.parseColor("red"))
        assertNull(ColorCodec.parseColor("#FFF"))       // 3 digits are not supported
        assertNull(ColorCodec.parseColor("#GGGGGG"))
        assertNull(ColorCodec.parseColor("#12345678901"))
    }

    @Test
    fun `normalize and parse agree`() {
        assertEquals("#FFABCDEF", ColorCodec.normalize("#ABCDEF"))
        assertEquals("#FFABCDEF", ColorCodec.normalize("ABCDEF"))
        assertEquals("#80ABCDEF", ColorCodec.normalize("#80ABCDEF"))
        assertEquals(0xFFABCDEFL, ColorCodec.parse("#FFABCDEF"))
        assertNull(ColorCodec.normalize("#ABCDE"))
    }

    @Test
    fun `parseColor result is a Color that survives copy and lerp`() {
        val color = ColorCodec.parseColor("#FF0A0F1E")!!
        val faded = color.copy(alpha = 0.5f)
        faded.toArgb() // must not throw
        val mid = Color(
            (color.red + Color.White.red) / 2f,
            (color.green + Color.White.green) / 2f,
            (color.blue + Color.White.blue) / 2f,
            1f
        )
        mid.toArgb() // must not throw
    }
}
