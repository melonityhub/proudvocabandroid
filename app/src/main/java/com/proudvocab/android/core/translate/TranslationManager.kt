package com.proudvocab.android.core.translate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.proudvocab.android.core.dict.DictionaryManager
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.SettingsRepository
import com.proudvocab.android.core.settings.TranslationEngine
import kotlinx.coroutines.flow.first

/**
 * Chooses between the online engine, the on-device ML Kit model and the plain
 * offline dictionary, following the user's "Translation engine" setting.
 *
 * Whatever the setting, the manager always tries to come back with *some*
 * answer: online → on-device → dictionary.
 */
class TranslationManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val dictionary: DictionaryManager
) {

    val online = OnlineTranslator()
    val offline = OfflineTranslator(context)
    private val dictionaryTranslator = DictionaryTranslator(dictionary)

    private val cache = object : LinkedHashMap<String, TranslationResult>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationResult>): Boolean =
            size > 300
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun translate(
        text: String,
        source: String,
        target: String,
        engine: TranslationEngine? = null
    ): Result<TranslationResult> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("empty text"))

        val settingsSnapshot = settings.snapshot()
        val chosen = engine ?: settingsSnapshot.engine()
        val cacheKey = "$chosen|$source|$target|$trimmed"
        synchronized(cache) { cache[cacheKey] }?.let { return Result.success(it) }

        val result = runEngine(trimmed, source, target, chosen, settingsSnapshot)
        result.onSuccess { synchronized(cache) { cache[cacheKey] = it } }
        return result
    }

    private suspend fun runEngine(
        text: String,
        source: String,
        target: String,
        engine: TranslationEngine,
        settings: AppSettings
    ): Result<TranslationResult> {
        val order = when (engine) {
            TranslationEngine.ONLINE -> listOf(TranslationEngine.ONLINE, TranslationEngine.OFFLINE, TranslationEngine.DICTIONARY)
            TranslationEngine.OFFLINE -> listOf(TranslationEngine.OFFLINE, TranslationEngine.DICTIONARY)
            TranslationEngine.DICTIONARY -> listOf(TranslationEngine.DICTIONARY)
            TranslationEngine.AUTO ->
                if (isOnline()) listOf(
                    TranslationEngine.ONLINE,
                    TranslationEngine.OFFLINE,
                    TranslationEngine.DICTIONARY
                ) else listOf(
                    TranslationEngine.OFFLINE,
                    TranslationEngine.DICTIONARY
                )
        }.let { list ->
            if (settings.onlineFallback) list else list.filter { it != TranslationEngine.DICTIONARY }
        }

        var lastError: Throwable? = null
        for (candidate in order) {
            val attempt = when (candidate) {
                TranslationEngine.ONLINE -> if (isOnline()) {
                    online.translate(text, source, target)
                } else null

                TranslationEngine.OFFLINE ->
                    if (offline.isSupported(target)) offline.translate(text, source, target) else null

                TranslationEngine.DICTIONARY ->
                    dictionaryTranslator.translate(text, target == "fa")
            }
            if (attempt != null) {
                attempt.onSuccess { return Result.success(it) }
                attempt.onFailure { lastError = it }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("no translation engine available"))
    }

    suspend fun wordInfo(word: String): Result<WordInfo> = online.wordInfo(word)

    fun clearCache() = synchronized(cache) { cache.clear() }
}
