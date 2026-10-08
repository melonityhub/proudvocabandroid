package com.proudvocab.android

import android.app.Application
import com.proudvocab.android.core.data.AppDatabase
import com.proudvocab.android.core.data.VocabRepository
import com.proudvocab.android.core.dict.DictionaryManager
import com.proudvocab.android.core.settings.SettingsRepository
import com.proudvocab.android.core.translate.TranslationManager
import com.proudvocab.android.ui.theme.FontRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-rolled dependency container — the app is small enough for it. */
class ProudVocabApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            dictionary.open()
            dictionary.preload()
        }
    }
}
