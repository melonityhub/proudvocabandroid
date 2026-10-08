package com.proudvocab.android.ui.screens.dictionary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proudvocab.android.ProudVocabApplication
import com.proudvocab.android.core.dict.DictSearchResult
import com.proudvocab.android.core.dict.WordEntry
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.WordKind
import com.proudvocab.android.core.translate.TranslationResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class DictionaryTab { SEARCH, TRANSLATE, WORDS }

data class DictionaryUiState(
    val query: String = "",
    val results: List<DictSearchResult> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val entry: WordEntry? = null,
    val entryWord: String = "",
    val searching: Boolean = false,
    val offline: Boolean = true,
    val databaseWords: Long = 0L,
    val savedWords: Set<String> = emptySet(),
    val favourites: Set<String> = emptySet(),
    val history: List<String> = emptyList(),
    // free-text translator
    val translateInput: String = "",
    val translateOutput: TranslationResult? = null,
    val translating: Boolean = false,
    val tab: DictionaryTab = DictionaryTab.SEARCH
)

@OptIn(FlowPreview::class)
class DictionaryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProudVocabApplication
    private val settings = app.settings
    private val dictionary = app.dictionary
    private val translation = app.translation
    private val vocab = app.vocabRepository

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settingsState: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queryFlow = MutableStateFlow("")
    private var translateJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            vocab.words.collect { words ->
                _uiState.updateSafely { it.copy(savedWords = words.map { w -> w.word }.toSet()) }
            }
        }
        viewModelScope.launch {
            vocab.favourites.collect { favs ->
                _uiState.updateSafely { it.copy(favourites = favs.map { f -> f.word }.toSet()) }
            }
        }
        viewModelScope.launch {
            vocab.history.collect { history ->
                _uiState.updateSafely { it.copy(history = history.map { h -> h.word }.distinct()) }
            }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(databaseWords = dictionary.wordCount())
        }

        _queryFlow
            .debounce(220)
            .distinctUntilChanged()
            .onEach { query -> performSearch(query) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        _uiState.updateSafely { it.copy(query = value) }
        _queryFlow.value = value
    }

    fun search(query: String) {
        _uiState.updateSafely { it.copy(query = query) }
        viewModelScope.launch { performSearch(query) }
    }

    private suspend fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.updateSafely { it.copy(results = emptyList(), suggestions = emptyList(), searching = false) }
            return
        }
        _uiState.updateSafely { it.copy(searching = true) }
        val results = dictionary.search(trimmed)
        val suggestions = if (results.isEmpty()) dictionary.suggestions(trimmed) else emptyList()
        _uiState.updateSafely {
            it.copy(results = results, suggestions = suggestions, searching = false)
        }
    }

    fun open(result: DictSearchResult) = openWord(result.word)

    fun openWord(word: String) {
        viewModelScope.launch {
            _uiState.updateSafely { it.copy(entry = null, entryWord = word) }
            val entry = dictionary.entry(word)
            _uiState.updateSafely { it.copy(entry = entry) }
            if (entry != null || word.isNotBlank()) {
                vocab.addHistory(word, _settings.value.learningLanguage)
            }
        }
    }

    fun closeEntry() = _uiState.updateSafely { it.copy(entry = null, entryWord = "") }

    fun saveWord(word: String, translationText: String, kind: Int = WordKind.WORD.id) {
        viewModelScope.launch {
            val entry = dictionary.entry(word)
            vocab.save(
                word = word,
                language = _settings.value.learningLanguage,
                translation = translationText.ifBlank { entry?.shortGloss.orEmpty() },
                cefr = entry?.cefr.orEmpty(),
                phonetic = entry?.phoneticUs.orEmpty(),
                partOfSpeech = entry?.meanings?.firstOrNull()?.posNameEn.orEmpty(),
                kind = kind
            )
            dictionary.cefr(word)
        }
    }

    fun toggleFavourite(word: String) {
        viewModelScope.launch {
            vocab.toggleFavourite(word, _settings.value.learningLanguage)
        }
    }

    // ------------------------------------------------------ text translate

    fun onTranslateInput(value: String) {
        _uiState.updateSafely { it.copy(translateInput = value) }
    }

    fun translateText() {
        val text = _uiState.value.translateInput.trim()
        if (text.isBlank()) return
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _uiState.updateSafely { it.copy(translating = true) }
            delay(180)
            val s = _settings.value
            translation.translate(text, s.learningLanguage, s.translationLanguage)
                .onSuccess { result ->
                    _uiState.updateSafely { it.copy(translateOutput = result, translating = false) }
                }
                .onFailure {
                    _uiState.updateSafely {
                        it.copy(
                            translating = false,
                            translateOutput = TranslationResult(
                                text = "",
                                engine = s.engine(),
                                offline = true
                            )
                        )
                    }
                }
        }
    }

    fun swapLanguages() {
        viewModelScope.launch {
            val s = _settings.value
            settings.setLearningLanguage(s.translationLanguage)
            settings.setTranslationLanguage(s.learningLanguage)
        }
    }

    fun setTab(tab: DictionaryTab) = _uiState.updateSafely { it.copy(tab = tab) }

    private inline fun <T> MutableStateFlow<T>.updateSafely(block: (T) -> T) {
        value = block(value)
    }
}
