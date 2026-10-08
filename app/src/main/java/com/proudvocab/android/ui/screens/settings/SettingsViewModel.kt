package com.proudvocab.android.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proudvocab.android.ProudVocabApplication
import com.proudvocab.android.core.export.DeckExporter
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.StyleTarget
import com.proudvocab.android.core.settings.TextAlignPref
import com.proudvocab.android.core.settings.TextStylePref
import com.proudvocab.android.core.settings.ThemeMode
import com.proudvocab.android.core.settings.TranslationEngine
import com.proudvocab.android.core.translate.ModelState
import com.proudvocab.android.core.util.LocaleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsPage {
    ROOT, APPEARANCE, TYPOGRAPHY, SUBTITLES, LANGUAGES,
    TRANSLATION, LEARNING, REVIEW, GAMES, DATA, PERMISSIONS, ABOUT
}

data class SettingsUiState(
    val page: SettingsPage = SettingsPage.ROOT,
    val styleTarget: StyleTarget = StyleTarget.SUBTITLE_PRIMARY,
    val modelState: ModelState = ModelState.Unknown,
    val dictionaryBusy: Boolean = false,
    val message: String? = null,
    /** Bumped whenever the list of installed fonts changes. */
    val fontsVersion: Int = 0
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProudVocabApplication
    private val settings = app.settings
    private val translation = app.translation
    private val dictionary = app.dictionary
    private val localeStore = LocaleStore(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settingsState: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                _settings.value = s
                if (s.engine() == TranslationEngine.OFFLINE ||
                    s.engine() == TranslationEngine.AUTO
                ) {
                    checkModel(s)
                }
            }
        }
    }

    fun navigate(page: SettingsPage) = _uiState.update { it.copy(page = page) }

    fun back(): Boolean {
        val current = _uiState.value.page
        if (current == SettingsPage.ROOT) return false
        _uiState.update { it.copy(page = SettingsPage.ROOT) }
        return true
    }

    // ---------------------------------------------------------- appearance

    fun setTheme(mode: ThemeMode) = launch { settings.setTheme(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { settings.setDynamicColor(enabled) }
    fun setAccent(hex: String?) = launch { settings.setAccent(hex) }
    fun setAnimations(enabled: Boolean) = launch { settings.setAnimations(enabled) }
    fun setPersianDigits(enabled: Boolean) = launch { settings.update { it.copy(usePersianDigits = enabled) } }

    fun setAppLanguage(code: String) = launch {
        settings.setAppLanguage(code)
        localeStore.language = code
    }

    // ----------------------------------------------------------- languages

    fun setLearningLanguage(code: String) = launch { settings.setLearningLanguage(code) }
    fun setTranslationLanguage(code: String) = launch {
        settings.setTranslationLanguage(code)
        checkModel(_settings.value.copy(translationLanguage = code))
    }

    // ------------------------------------------------------------- styling

    fun setStyleTarget(target: StyleTarget) = _uiState.update { it.copy(styleTarget = target) }

    fun updateStyle(target: StyleTarget, block: (TextStylePref) -> TextStylePref) = launch {
        val current = _settings.value.styleFor(target)
        settings.setStyle(target, block(current))
    }

    fun setAlign(target: StyleTarget, align: TextAlignPref) =
        updateStyle(target) { it.copy(align = align) }

    fun resetStyle(target: StyleTarget) = launch { settings.resetStyle(target) }
    fun resetAllStyles() = launch { settings.resetAllStyles() }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            val result = app.fonts.importFont(uri)
            _uiState.update {
                it.copy(
                    message = if (result.isSuccess) "font_ok" else "font_fail",
                    fontsVersion = it.fontsVersion + 1
                )
            }
        }
    }

    fun deleteFont(key: String) = launch {
        app.fonts.deleteFont(
            com.proudvocab.android.ui.theme.FontOption(
                key,
                com.proudvocab.android.ui.theme.FontKeys.customName(key),
                com.proudvocab.android.ui.theme.FontGroup.CUSTOM
            )
        )
        _uiState.update { it.copy(fontsVersion = it.fontsVersion + 1) }
    }

    // ----------------------------------------------------------- subtitles

    fun setSubtitlePosition(value: Float) =
        launch { settings.update { it.copy(subtitlePositionBottom = value) } }
    fun setSubtitleOpacity(value: Float) =
        launch { settings.update { it.copy(subtitleBackgroundOpacity = value) } }
    fun setSubtitleBackground(hex: String) =
        launch { settings.update { it.copy(subtitleBackground = hex) } }
    fun setSubtitleDual(enabled: Boolean) =
        launch { settings.update { it.copy(subtitleDual = enabled) } }
    fun setSubtitleMaxLines(value: Int) =
        launch { settings.update { it.copy(subtitleMaxLines = value) } }
    fun setSubtitleWordChips(enabled: Boolean) =
        launch { settings.update { it.copy(subtitleWordChips = enabled) } }
    fun setSubtitleHighlightCefr(enabled: Boolean) =
        launch { settings.update { it.copy(subtitleHighlightCefr = enabled) } }
    fun setSubtitleHighlightIdioms(enabled: Boolean) =
        launch { settings.update { it.copy(subtitleHighlightIdioms = enabled) } }
    fun setSubtitleShadowing(enabled: Boolean) =
        launch { settings.update { it.copy(subtitleShadowing = enabled) } }
    fun setSubtitleTranslateWholeLine(enabled: Boolean) =
        launch { settings.update { it.copy(subtitleTranslateWholeLine = enabled) } }

    // --------------------------------------------------------- translation

    fun setEngine(engine: TranslationEngine) = launch { settings.setEngine(engine) }
    fun setOnlineFallback(enabled: Boolean) =
        launch { settings.update { it.copy(onlineFallback = enabled) } }
    fun setAutoTranslateLines(enabled: Boolean) =
        launch { settings.update { it.copy(autoTranslateLines = enabled) } }

    fun checkModel(s: AppSettings = _settings.value) {
        viewModelScope.launch {
            _uiState.update { it.copy(modelState = ModelState.Checking) }
            val downloaded = translation.offline.isDownloaded(s.learningLanguage, s.translationLanguage)
            _uiState.update {
                it.copy(modelState = if (downloaded) ModelState.Ready else ModelState.Missing)
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            val s = _settings.value
            _uiState.update { it.copy(modelState = ModelState.Downloading(0)) }
            translation.offline.download(s.learningLanguage, s.translationLanguage)
                .onSuccess {
                    _uiState.update { it.copy(modelState = ModelState.Ready) }
                    settings.update { it.copy(offlineModelDownloaded = true) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(modelState = ModelState.Failed(error.message ?: "download failed"))
                    }
                }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            translation.offline.deleteModel(_settings.value.translationLanguage)
            checkModel()
        }
    }

    fun importDictionary(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(dictionaryBusy = true) }
            dictionary.importDatabase(uri)
                .onSuccess { count ->
                    settings.update {
                        it.copy(
                            dictionaryImported = true,
                            dictionaryWordCount = count,
                            dictionaryName = "imported"
                        )
                    }
                    _uiState.update { it.copy(dictionaryBusy = false, message = "dictionary_ok") }
                }
                .onFailure {
                    _uiState.update { it.copy(dictionaryBusy = false, message = "dictionary_fail") }
                }
        }
    }

    fun removeDictionary() {
        viewModelScope.launch {
            dictionary.removeImported()
            settings.update {
                it.copy(dictionaryImported = false, dictionaryWordCount = 0L, dictionaryName = "")
            }
        }
    }

    // ------------------------------------------------------------ learning

    fun setAutoPause(enabled: Boolean) =
        launch { settings.update { it.copy(autoPauseOnLookup = enabled) } }
    fun setAutoRewind(enabled: Boolean) =
        launch { settings.update { it.copy(autoRewind = enabled) } }
    fun setRewindSeconds(value: Int) =
        launch { settings.update { it.copy(rewindSeconds = value) } }
    fun setShowWordFamily(enabled: Boolean) =
        launch { settings.update { it.copy(showWordFamily = enabled) } }
    fun setShowWordTags(enabled: Boolean) =
        launch { settings.update { it.copy(showWordTags = enabled) } }
    fun setShowWordDetails(enabled: Boolean) =
        launch { settings.update { it.copy(showWordDetails = enabled) } }
    fun setQuickAccess(enabled: Boolean) =
        launch { settings.update { it.copy(quickAccess = enabled) } }

    // -------------------------------------------------------------- review

    fun setSrsAlgorithm(key: String) = launch { settings.update { it.copy(srsAlgorithm = key) } }
    fun setNewLimit(value: Int) = launch { settings.update { it.copy(newLimit = value) } }
    fun setSessionLimit(value: Int) = launch { settings.update { it.copy(sessionLimit = value) } }

    // --------------------------------------------------------------- games

    fun setGameSound(enabled: Boolean) = launch { settings.update { it.copy(gameSound = enabled) } }
    fun setGameAutoPronounce(enabled: Boolean) =
        launch { settings.update { it.copy(gameAutoPronounce = enabled) } }
    fun setGameShowLearned(enabled: Boolean) =
        launch { settings.update { it.copy(gameShowLearned = enabled) } }

    // ---------------------------------------------------------------- data

    // ------------------------------------------------- backup and restore

    private fun writeExport(name: String, content: String): java.io.File? = runCatching {
        val dir = java.io.File(getApplication<Application>().filesDir, "exports").apply { mkdirs() }
        java.io.File(dir, name).apply { writeText(content) }
    }.getOrNull()

    private fun shareIntent(file: java.io.File, mime: String): Intent {
        val context = getApplication<Application>()
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${'$'}{context.packageName}.files",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportJson(onReady: (Intent?) -> Unit) {
        viewModelScope.launch {
            val words = app.vocabRepository.allWords()
            val file = writeExport("proudvocab-words.json", DeckExporter.toJson(words))
            onReady(file?.let { shareIntent(it, "application/json") })
        }
    }

    fun exportAnki(onReady: (Intent?) -> Unit) {
        viewModelScope.launch {
            val words = app.vocabRepository.allWords()
            val file = writeExport("proudvocab-anki.csv", DeckExporter.toAnkiCsv(words))
            onReady(file?.let { shareIntent(it, "text/csv") })
        }
    }

    fun importJson(uri: Uri, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val words = DeckExporter.fromJson(text.orEmpty())
            if (words.isEmpty()) {
                onDone(0)
                return@launch
            }
            words.forEach { word ->
                app.vocabRepository.save(
                    word = word.word,
                    language = word.language,
                    translation = word.translation,
                    contextSentence = word.contextSentence,
                    sourceTitle = word.sourceTitle,
                    cefr = word.cefr,
                    phonetic = word.phonetic,
                    partOfSpeech = word.partOfSpeech,
                    kind = word.kind,
                    tags = word.tags
                )
            }
            onDone(words.size)
        }
    }

    fun resetSrs() = launch { app.vocabRepository.resetScheduling() }
    fun resetGames() = launch { app.vocabRepository.clearGameStats() }
    fun eraseEverything() = launch {
        app.vocabRepository.clearWords()
        app.vocabRepository.clearHistory()
        app.vocabRepository.clearGameStats()
        settings.resetAllStyles()
        settings.update { AppSettings(onboardingCompleted = true) }
    }

    fun showMessage(message: String?) = _uiState.update { it.copy(message = message) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
