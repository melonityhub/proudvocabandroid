package com.proudvocab.android.core.util

import android.content.Context
import java.util.Locale

/**
 * Synchronous mirror of the "app language" setting.
 *
 * The language has to be known before the first `setContentView`, so it cannot
 * live in DataStore (which is asynchronous); this tiny preference keeps the
 * synchronous copy, while [com.proudvocab.android.core.settings.AppSettings]
 * stays the source of truth for the rest of the app.
 */
class LocaleStore(context: Context) {

    private val prefs = context.getSharedPreferences("proudvocab_ui", Context.MODE_PRIVATE)

    /** Empty means "follow the device". */
    var language: String
        get() = prefs.getString(KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY, value).apply()

    companion object {
        private const val KEY = "app_language"
    }
}

/** Applies [language] to [context], returning a context that uses it. */
object LocaleHelper {

    fun apply(context: Context, language: String): Context {
        if (language.isBlank()) return context
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    /** Reverse of [apply]: the device locale, kept for date/number fallbacks. */
    fun systemLanguage(): String =
        Locale.getDefault().toLanguageTag().substringBefore('-')
}
