package com.proudvocab.android.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "proudvocab_settings"
)

/**
 * Single source of truth for every user preference.
 *
 * The whole [AppSettings] graph is persisted as one JSON document, so adding a
 * new option never needs a migration — missing keys simply fall back to their
 * default value.
 */
class SettingsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private object Keys {
        val SETTINGS = stringPreferencesKey("settings_json")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        prefs[Keys.SETTINGS]?.let { decode(it) } ?: AppSettings()
    }

    suspend fun snapshot(): AppSettings = settings.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.SETTINGS]?.let { decode(it) } ?: AppSettings()
            prefs[Keys.SETTINGS] = json.encodeToString(block(current))
        }
    }

    suspend fun setStyle(target: StyleTarget, pref: TextStylePref) {
        update { it.withStyle(target, pref) }
    }

    suspend fun resetStyle(target: StyleTarget) {
        update { s ->
            val next = s.styles.toMutableMap()
            next.remove(target.key)
            s.copy(styles = next)
        }
    }

    suspend fun resetAllStyles() {
        update { it.copy(styles = emptyMap()) }
    }

    suspend fun setAppLanguage(code: String) = update { it.copy(appLanguage = code) }
    suspend fun setLearningLanguage(code: String) = update { it.copy(learningLanguage = code) }
    suspend fun setTranslationLanguage(code: String) = update { it.copy(translationLanguage = code) }
    suspend fun setEngine(engine: TranslationEngine) =
        update { it.copy(translationEngine = engine.key) }

    suspend fun setTheme(mode: ThemeMode) = update { it.copy(theme = mode.key) }
    suspend fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }
    suspend fun setAccent(hex: String?) = update { it.copy(accentHex = hex) }
    suspend fun setAnimations(enabled: Boolean) = update { it.copy(animationsEnabled = enabled) }

    suspend fun completeOnboarding() = update { it.copy(onboardingCompleted = true) }

    suspend fun replaceAll(settings: AppSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.SETTINGS] = json.encodeToString(settings)
        }
    }

    private fun decode(value: String): AppSettings? =
        runCatching { json.decodeFromString<AppSettings>(value) }.getOrNull()
}
