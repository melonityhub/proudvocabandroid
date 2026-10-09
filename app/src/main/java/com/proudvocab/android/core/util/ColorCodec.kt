package com.proudvocab.android.core.util

import androidx.compose.ui.graphics.Color

/**
 * `#RRGGBB` / `#AARRGGBB` <-> packed colour helpers.
 * Pure Kotlin so they are unit testable and usable from settings previews.
 */
object ColorCodec {

    private val HEX = Regex("^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun isValid(value: String): Boolean = HEX.matches(value.trim())

    /** Normalises to `#AARRGGBB`. Returns null when [value] is not a colour. */
    fun normalize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        if (!HEX.matches(v)) return null
        val hex = v.removePrefix("#")
        return if (hex.length == 6) "#FF$hex" else "#$hex"
    }

    /** Parses to an ARGB `Long` (Compose convention). Returns null on error. */
    fun parse(value: String?): Long? {
        val n = normalize(value) ?: return null
        return n.removePrefix("#").toLongOrNull(16)
    }

    /** `ULong` packed form used by `androidx.compose.ui.graphics.Color`. */
    fun parseULong(value: String?): ULong? = parse(value)?.toULong()

    /**
     * Parses to a Compose [Color] (sRGB). Returns null on error.
     *
     * This must go through [Color]'s `Int` constructor: since Compose 1.7 the
     * packed `Color` value stores the colour-space id in its LOW 6 bits and
     * the ARGB components at bits 32-63 (`Color(Int)` does `argb shl 32`).
     * Building `Color(argb.toULong())` directly — the raw value-class
     * constructor — leaves the colour-space id equal to `argb and 0x3F`,
     * which is >= 18 for most colours and crashes with
     * `ArrayIndexOutOfBoundsException: length=18; index=…` the first time the
     * colour is converted (e.g. while laying out a `Text`).
     */
    fun parseColor(value: String?): Color? = parse(value)?.let { Color(it.toInt()) }

    fun toHex(argb: ULong): String {
        val v = argb and 0xFFFFFFFFu
        return "#" + v.toString(16).uppercase().padStart(8, '0')
    }

    fun toHexRgb(argb: ULong): String {
        val v = argb and 0x00FFFFFFu
        return "#" + v.toString(16).uppercase().padStart(6, '0')
    }

    /** 0 = fully transparent, 1 = fully opaque. */
    fun alphaOf(argb: ULong): Float = ((argb shr 24) and 0xFFu).toFloat() / 255f

    fun withAlpha(rgb: ULong, alpha: Float): ULong {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return ((rgb and 0x00FFFFFFu) or (a.toUInt().toULong() shl 24))
    }

    /** Perceived luminance, used to pick a readable text colour. */
    fun luminance(argb: ULong): Double {
        val r = ((argb shr 16) and 0xFFu).toInt() / 255.0
        val g = ((argb shr 8) and 0xFFu).toInt() / 255.0
        val b = (argb and 0xFFu).toInt() / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun isDark(argb: ULong): Boolean = luminance(argb) < 0.5
}
