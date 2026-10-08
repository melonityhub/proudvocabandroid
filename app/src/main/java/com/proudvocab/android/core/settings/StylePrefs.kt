package com.proudvocab.android.core.settings

import kotlinx.serialization.Serializable

/**
 * Every one of these targets carries its **own** font family, size, colour,
 * background, weight, spacing, line height and alignment.
 *
 * Nothing is shared between them on purpose: the subtitle of the language you
 * are learning, its translation, the app menus, the dictionary, the flashcards,
 * the games and the archive each stay independently configurable, so changing
 * one never bleeds into another.
 */
enum class StyleTarget(
    val key: String,
    /** Base size in sp; the user setting is a multiplier on top of it. */
    val baseSizeSp: Float,
    val defaultColorHex: String?,
    val defaultBackgroundHex: String?,
    val defaultAlign: TextAlignPref,
    val defaultBold: Boolean = false
) {
    APP("app", 15f, null, null, TextAlignPref.START),
    SUBTITLE_PRIMARY("subtitle_primary", 22f, "#FFFFFFFF", null, TextAlignPref.CENTER, true),
    SUBTITLE_SECONDARY("subtitle_secondary", 18f, "#FFCBD5E1", null, TextAlignPref.CENTER),
    TRANSCRIPT("transcript", 14f, null, null, TextAlignPref.START),
    WORD_CARD("word_card", 24f, null, null, TextAlignPref.START, true),
    WORD_TRANSLATION("word_translation", 16f, null, null, TextAlignPref.START),
    DICTIONARY("dictionary", 16f, null, null, TextAlignPref.START),
    FLASHCARD_FRONT("flashcard_front", 30f, null, null, TextAlignPref.CENTER, true),
    FLASHCARD_BACK("flashcard_back", 22f, null, null, TextAlignPref.CENTER),
    GAME("game", 22f, null, null, TextAlignPref.CENTER, true),
    ARCHIVE_WORD("archive_word", 17f, null, null, TextAlignPref.START, true),
    ARCHIVE_TRANSLATION("archive_translation", 14f, null, null, TextAlignPref.START);

    companion object {
        fun fromKey(key: String): StyleTarget? = entries.firstOrNull { it.key == key }
        val ALL: List<StyleTarget> get() = entries.toList()
    }
}

@Serializable
enum class TextAlignPref { START, CENTER, END }

/** Font keys. Anything not listed here is a path to an imported font file. */
object FontKeys {
    const val SYSTEM = "system"
    const val SANS = "sans"
    const val SERIF = "serif"
    const val MONO = "mono"
    const val DEVICE_PREFIX = "device:"
    const val CUSTOM_PREFIX = "file:"
    const val BUNDLED_PREFIX = "bundled:"

    fun isBuiltIn(key: String) = key == SYSTEM || key == SANS || key == SERIF || key == MONO
    fun isDevice(key: String) = key.startsWith(DEVICE_PREFIX)
    fun isCustom(key: String) = key.startsWith(CUSTOM_PREFIX)
    fun isBundled(key: String) = key.startsWith(BUNDLED_PREFIX)

    fun deviceName(key: String) = key.removePrefix(DEVICE_PREFIX)
    fun customName(key: String) = key.removePrefix(CUSTOM_PREFIX)
    fun bundledName(key: String) = key.removePrefix(BUNDLED_PREFIX)
}

@Serializable
data class TextStylePref(
    /** @see FontKeys */
    val font: String = FontKeys.SYSTEM,
    /** Size multiplier applied on top of the target's base size. */
    val scale: Float = 1f,
    val bold: Boolean? = null,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** `#AARRGGBB` or null to follow the theme. */
    val color: String? = null,
    val background: String? = null,
    val letterSpacingSp: Float = 0f,
    val lineHeight: Float = 1.25f,
    val align: TextAlignPref = TextAlignPref.START,
    /** Shadow / outline size in dp; subtitles use a subtle one by default. */
    val shadowDp: Float = 0f
) {
    fun sizeSp(target: StyleTarget): Float = target.baseSizeSp * scale

    companion object {
        fun defaultFor(target: StyleTarget) = TextStylePref(
            color = target.defaultColorHex,
            background = target.defaultBackgroundHex,
            align = target.defaultAlign,
            bold = if (target.defaultBold) true else null,
            shadowDp = when (target) {
                StyleTarget.SUBTITLE_PRIMARY, StyleTarget.SUBTITLE_SECONDARY -> 2f
                else -> 0f
            },
            lineHeight = when (target) {
                StyleTarget.SUBTITLE_PRIMARY, StyleTarget.SUBTITLE_SECONDARY -> 1.2f
                StyleTarget.FLASHCARD_FRONT, StyleTarget.FLASHCARD_BACK -> 1.15f
                else -> 1.35f
            }
        )
    }
}
