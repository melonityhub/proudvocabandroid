package com.proudvocab.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.proudvocab.android.ProudVocabApplication
import com.proudvocab.android.core.data.VocabRepository
import com.proudvocab.android.core.dict.DictionaryManager
import com.proudvocab.android.core.settings.SettingsRepository
import com.proudvocab.android.core.translate.TranslationManager
import com.proudvocab.android.ui.theme.FontRepository

data class AppDependencies(
    val settings: SettingsRepository,
    val vocab: VocabRepository,
    val dictionary: DictionaryManager,
    val translation: TranslationManager,
    val fonts: FontRepository
)

val LocalDependencies = staticCompositionLocalOf<AppDependencies> {
    error("No AppDependencies provided")
}

@Composable
fun ProvideDependencies(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as ProudVocabApplication
    CompositionLocalProvider(
        LocalDependencies provides AppDependencies(
            settings = app.settings,
            vocab = app.vocabRepository,
            dictionary = app.dictionary,
            translation = app.translation,
            fonts = app.fonts
        ),
        content = content
    )
}
