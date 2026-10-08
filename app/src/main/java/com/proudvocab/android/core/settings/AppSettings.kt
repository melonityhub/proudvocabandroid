package com.proudvocab.android.core.settings

import kotlinx.serialization.Serializable

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark"), AMOLED("amoled");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class TranslationEngine(val key: String) {
    AUTO("auto"),
    ONLINE("online"),
    OFFLINE("offline"),
    DICTIONARY("dictionary");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** What a saved word actually is — used for filters and games. */
enum class WordKind(val id: Int) { WORD(0), IDIOM(1), PHRASAL(2), COLLOCATION(3) }

@Serializable
data class AppSettings(

    // ---------------------------------------------------------- appearance
    val theme: String = ThemeMode.SYSTEM.key,
    val dynamicColor: Boolean = true,
    val accentHex: String? = null,
    /** Empty string = follow the device language. */
    val appLanguage: String = "",
    val usePersianDigits: Boolean = true,

    // ---------------------------------------------------------- languages
    val learningLanguage: String = "en",
    val translationLanguage: String = "fa",

    // -------------------------------------------------------- translation
    val translationEngine: String = TranslationEngine.AUTO.key,
    val onlineFallback: Boolean = true,

    // ------------------------------------------------------------ offline
    val dictionaryImported: Boolean = false,
    val dictionaryName: String = "",
    val dictionaryWordCount: Long = 0L,
    val offlineModelDownloaded: Boolean = false,

    // ---------------------------------------------------------- subtitles
    val subtitlePositionBottom: Float = 0.12f,
    val subtitleBackground: String = "#FF0A0F1E",
    val subtitleBackgroundOpacity: Float = 0.85f,
    val subtitleDual: Boolean = true,
    val subtitleMaxLines: Int = 2,
    val subtitleWordChips: Boolean = true,
    val subtitleHighlightCefr: Boolean = true,
    val subtitleHighlightIdioms: Boolean = true,
    val subtitleShadowing: Boolean = false,
    val subtitleDelayMs: Long = 0L,
    val subtitleTranslateWholeLine: Boolean = true,

    // ----------------------------------------------------------- learning
    val autoPauseOnLookup: Boolean = false,
    val autoRewind: Boolean = false,
    val rewindSeconds: Int = 3,
    val showWordFamily: Boolean = false,
    val showWordTags: Boolean = false,
    val showWordDetails: Boolean = false,
    val quickAccess: Boolean = true,

    // ------------------------------------------------------------- review
    val srsAlgorithm: String = "classic",
    val newLimit: Int = 10,
    val sessionLimit: Int = 20,

    // -------------------------------------------------------------- games
    val gameSound: Boolean = true,
    val gameAutoPronounce: Boolean = true,
    val gameShowLearned: Boolean = false,

    // -------------------------------------------------------------- state
    val onboardingCompleted: Boolean = false,
    val lastVideoUri: String = "",
    val lastSubtitleUri: String = "",

    /** Per-target text styling, keyed by [StyleTarget.key]. */
    val styles: Map<String, TextStylePref> = emptyMap()
) {
    fun styleFor(target: StyleTarget): TextStylePref =
        styles[target.key] ?: TextStylePref.defaultFor(target)

    fun withStyle(target: StyleTarget, pref: TextStylePref): AppSettings {
        val next = styles.toMutableMap()
        next[target.key] = pref
        return copy(styles = next)
    }

    fun themeMode(): ThemeMode = ThemeMode.fromKey(theme)
    fun engine(): TranslationEngine = TranslationEngine.fromKey(translationEngine)

    fun srsAlgorithmEnum(): com.proudvocab.android.core.srs.SrsAlgorithm =
        if (srsAlgorithm == "sm2") com.proudvocab.android.core.srs.SrsAlgorithm.SM2
        else com.proudvocab.android.core.srs.SrsAlgorithm.CLASSIC

    companion object {
        /** Every accent the user can pick from, in `#RRGGBB`. */
        val ACCENTS = listOf(
            "#FF6366F1", "#FF8B5CF6", "#FFEC4899", "#FFF43F5E",
            "#FFF97316", "#FFEAB308", "#FF22C55E", "#FF14B8A6",
            "#FF06B6D4", "#FF3B82F6", "#FF0EA5E9", "#FFA855F7"
        )
    }
}
