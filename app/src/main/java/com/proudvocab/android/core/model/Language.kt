package com.proudvocab.android.core.model

import java.util.Locale

data class AppLanguage(
    /** ISO 639-1 code. */
    val code: String,
    /** Name of the language written in itself. */
    val nativeName: String,
    /** English name, used for sorting and for the search field. */
    val englishName: String,
    /** True for languages that lay text out right-to-left. */
    val rtl: Boolean = false
) {
    /** Locale used for text-to-speech and number formatting. */
    val locale: Locale get() = Locale.forLanguageTag(code)

    fun displayName(inPersian: Boolean): String = nativeName

    override fun toString(): String = nativeName
}

object Languages {

    val ALL: List<AppLanguage> = listOf(
        AppLanguage("en", "English", "English"),
        AppLanguage("fa", "فارسی", "Persian", rtl = true),
        AppLanguage("ar", "العربية", "Arabic", rtl = true),
        AppLanguage("he", "עברית", "Hebrew", rtl = true),
        AppLanguage("ur", "اردو", "Urdu", rtl = true),
        AppLanguage("tr", "Türkçe", "Turkish"),
        AppLanguage("de", "Deutsch", "German"),
        AppLanguage("fr", "Français", "French"),
        AppLanguage("es", "Español", "Spanish"),
        AppLanguage("it", "Italiano", "Italian"),
        AppLanguage("pt", "Português", "Portuguese"),
        AppLanguage("nl", "Nederlands", "Dutch"),
        AppLanguage("ru", "Русский", "Russian"),
        AppLanguage("uk", "Українська", "Ukrainian"),
        AppLanguage("pl", "Polski", "Polish"),
        AppLanguage("sv", "Svenska", "Swedish"),
        AppLanguage("hi", "हिन्दी", "Hindi"),
        AppLanguage("ja", "日本語", "Japanese"),
        AppLanguage("ko", "한국어", "Korean"),
        AppLanguage("zh", "中文", "Chinese"),
        AppLanguage("id", "Bahasa Indonesia", "Indonesian"),
        AppLanguage("vi", "Tiếng Việt", "Vietnamese"),
        AppLanguage("th", "ไทย", "Thai"),
        AppLanguage("el", "Ελληνικά", "Greek"),
        AppLanguage("cs", "Čeština", "Czech"),
        AppLanguage("ro", "Română", "Romanian"),
        AppLanguage("hu", "Magyar", "Hungarian"),
        AppLanguage("da", "Dansk", "Danish"),
        AppLanguage("fi", "Suomi", "Finnish"),
        AppLanguage("no", "Norsk", "Norwegian"),
        AppLanguage("bn", "বাংলা", "Bengali"),
        AppLanguage("ta", "தமிழ்", "Tamil"),
        AppLanguage("te", "తెలుగు", "Telugu"),
        AppLanguage("mr", "मराठी", "Marathi"),
        AppLanguage("sw", "Kiswahili", "Swahili"),
        AppLanguage("af", "Afrikaans", "Afrikaans"),
        AppLanguage("sq", "Shqip", "Albanian"),
        AppLanguage("hy", "Հայերեն", "Armenian"),
        AppLanguage("az", "Azərbaycan", "Azerbaijani"),
        AppLanguage("eu", "Euskara", "Basque"),
        AppLanguage("be", "Беларуская", "Belarusian"),
        AppLanguage("bs", "Bosanski", "Bosnian"),
        AppLanguage("bg", "Български", "Bulgarian"),
        AppLanguage("ca", "Català", "Catalan"),
        AppLanguage("hr", "Hrvatski", "Croatian"),
        AppLanguage("et", "Eesti", "Estonian"),
        AppLanguage("tl", "Filipino", "Filipino"),
        AppLanguage("gl", "Galego", "Galician"),
        AppLanguage("ka", "ქართული", "Georgian"),
        AppLanguage("gu", "ગુજરાતી", "Gujarati"),
        AppLanguage("ht", "Kreyòl ayisyen", "Haitian Creole"),
        AppLanguage("is", "Íslenska", "Icelandic"),
        AppLanguage("ga", "Gaeilge", "Irish"),
        AppLanguage("kk", "Қазақша", "Kazakh"),
        AppLanguage("km", "ខ្មែរ", "Khmer"),
        AppLanguage("kn", "ಕನ್ನಡ", "Kannada"),
        AppLanguage("ky", "Кыргызча", "Kyrgyz"),
        AppLanguage("lo", "ລາວ", "Lao"),
        AppLanguage("lv", "Latviešu", "Latvian"),
        AppLanguage("lt", "Lietuvių", "Lithuanian"),
        AppLanguage("mk", "Македонски", "Macedonian"),
        AppLanguage("ms", "Bahasa Melayu", "Malay"),
        AppLanguage("ml", "മലയാളം", "Malayalam"),
        AppLanguage("mt", "Malti", "Maltese"),
        AppLanguage("mn", "Монгол", "Mongolian"),
        AppLanguage("my", "ဗမာ", "Burmese"),
        AppLanguage("ne", "नेपाली", "Nepali"),
        AppLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        AppLanguage("sk", "Slovenčina", "Slovak"),
        AppLanguage("sl", "Slovenščina", "Slovenian"),
        AppLanguage("sr", "Српски", "Serbian"),
        AppLanguage("si", "සිංහල", "Sinhala"),
        AppLanguage("uz", "Oʻzbekcha", "Uzbek"),
        AppLanguage("cy", "Cymraeg", "Welsh"),
        AppLanguage("yi", "ייִדיש", "Yiddish", rtl = true)
    )

    private val BY_CODE = ALL.associateBy { it.code }

    fun byCode(code: String?): AppLanguage = BY_CODE[code] ?: ALL.first()

    fun isKnown(code: String?): Boolean = BY_CODE.containsKey(code)

    fun isRtl(code: String?): Boolean = BY_CODE[code]?.rtl ?: false

    fun filtered(query: String): List<AppLanguage> {
        if (query.isBlank()) return ALL
        val q = query.trim().lowercase()
        return ALL.filter {
            it.code.startsWith(q, ignoreCase = true) ||
                it.englishName.contains(q, ignoreCase = true) ||
                it.nativeName.contains(q, ignoreCase = true)
        }
    }

    /** Languages the app interface itself has been translated into. */
    val UI_LANGUAGES: List<String> = listOf("en", "fa")
}
