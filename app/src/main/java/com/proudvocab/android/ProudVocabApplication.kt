package com.proudvocab.android

import android.app.Application
import android.util.Log
import com.proudvocab.android.core.data.AppDatabase
import com.proudvocab.android.core.data.VocabRepository
import com.proudvocab.android.core.dict.DictionaryManager
import com.proudvocab.android.core.settings.SettingsRepository
import com.proudvocab.android.core.translate.TranslationManager
import com.proudvocab.android.ui.theme.FontRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-rolled dependency container — the app is small enough for it. */
class ProudVocabApplication : Application() {

    /**
     * Background warm-up scope.
     *
     * An exception that escapes a coroutine launched here goes to the thread's
     * uncaught-exception handler, and on Android that terminates the whole
     * process — so a broken dictionary file would kill the app before the first
     * screen is even drawn. The handler logs instead; the lookups themselves
     * already degrade to "no result" when the data is missing.
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "background warm-up failed", error)
        }
    )

    lateinit var settings: SettingsRepository
        private set
    lateinit var vocabRepository: VocabRepository
        private set
    lateinit var dictionary: DictionaryManager
        private set
    lateinit var translation: TranslationManager
        private set
    lateinit var fonts: FontRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        vocabRepository = VocabRepository(AppDatabase.get(this).vocabDao())
        dictionary = DictionaryManager(this)
        translation = TranslationManager(this, settings, dictionary)
        fonts = FontRepository(this)

        // Warm the offline dictionary and the shipped word tables up in the
        // background, so neither the first lookup nor the first subtitle line
        // has to read and parse them on the UI thread.
        appScope.launch(Dispatchers.IO) {
            runCatching {
                dictionary.open()
                dictionary.preload()
            }.onFailure { Log.w(TAG, "dictionary warm-up failed", it) }
        }
    }

    private companion object {
        const val TAG = "ProudVocab"
    }
}
