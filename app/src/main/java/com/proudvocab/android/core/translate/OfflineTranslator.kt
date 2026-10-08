package com.proudvocab.android.core.translate

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.proudvocab.android.core.settings.TranslationEngine
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * On-device translation through Google ML Kit.
 *
 * The model is downloaded once (a few tens of megabytes) and from then on the
 * translation works with the phone in airplane mode.
 */
class OfflineTranslator(private val context: Context) {

    private val remoteModelManager by lazy { RemoteModelManager.getInstance() }
    private val translators = HashMap<String, Translator>()
    private val downloadedCache = HashMap<String, Boolean>()

    private val _states = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    private fun key(source: String, target: String) = "$source-$target"

    private fun normalize(code: String): String = code.lowercase(Locale.US).substringBefore('-')

    fun isSupported(code: String): Boolean = normalize(code) in SUPPORTED

    suspend fun isDownloaded(source: String, target: String): Boolean {
        val s = normalize(source)
        val t = normalize(target)
        downloadedCache[key(s, t)]?.let { return it }
        val ok = runCatching {
            remoteModelManager.getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
                .any { it.language == t }
        }.getOrDefault(false)
        downloadedCache[key(s, t)] = ok
        return ok
    }

    /** Downloads the model for this pair. Call before the first translation. */
    suspend fun download(source: String, target: String, requireWifi: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            val s = normalize(source)
            val t = normalize(target)
            setState(s, t, ModelState.Checking)
            runCatching {
                val model = TranslateRemoteModel.Builder(t).build()
                val conditions = DownloadConditions.Builder().apply {
                    if (requireWifi) requireWifi()
                }.build()
                remoteModelManager.download(model, conditions).awaitCompletion()
                downloadedCache[key(s, t)] = true
                setState(s, t, ModelState.Ready)
            }.onFailure {
                setState(s, t, ModelState.Failed(it.message ?: "download failed"))
            }
        }

    suspend fun deleteModel(target: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val model = TranslateRemoteModel.Builder(normalize(target)).build()
            remoteModelManager.deleteDownloadedModel(model).awaitCompletion()
            downloadedCache.clear()
        }
    }

    suspend fun translate(
        text: String,
        source: String,
        target: String
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        val s = if (normalize(source) == "auto") "en" else normalize(source)
        val t = normalize(target)
        runCatching {
            val translator = getTranslator(s, t)
            val chunks = TextChunker.chunk(text, 900)
            val builder = StringBuilder()
            for (chunk in chunks) {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(translator.translate(chunk).await())
            }
            TranslationResult(
                text = builder.toString().trim(),
                engine = TranslationEngine.OFFLINE,
                offline = true
            )
        }
    }

    private suspend fun getTranslator(source: String, target: String): Translator {
        val k = key(source, target)
        translators[k]?.let { return it }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitCompletion()
        translators[k] = translator
        return translator
    }

    private fun setState(source: String, target: String, state: ModelState) {
        val next = _states.value.toMutableMap()
        next[key(source, target)] = state
        _states.value = next
    }

    fun stateFor(source: String, target: String): ModelState =
        _states.value[key(normalize(source), normalize(target))] ?: ModelState.Unknown

    fun closeAll() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
    }

    companion object {
        /** Language pairs ML Kit Translate can run on device. */
        val SUPPORTED = setOf(
            "af", "ar", "be", "bg", "bn", "ca", "cs", "da", "de", "el", "en", "eo", "es",
            "et", "fa", "fi", "fr", "ga", "gl", "gu", "he", "hi", "hr", "hu", "hy", "id",
            "is", "it", "ja", "ka", "kk", "km", "kn", "ko", "ky", "lo", "lt", "lv", "mk",
            "ml", "mn", "mr", "ms", "my", "nb", "ne", "nl", "or", "pa", "pl", "pt", "ro",
            "ru", "si", "sk", "sl", "sq", "sr", "sv", "sw", "ta", "te", "th", "tl", "tr",
            "uk", "ur", "uz", "vi", "zh"
        )
    }
}
