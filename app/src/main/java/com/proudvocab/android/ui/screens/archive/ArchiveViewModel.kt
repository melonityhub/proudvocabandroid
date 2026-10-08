package com.proudvocab.android.ui.screens.archive

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proudvocab.android.ProudVocabApplication
import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.export.DeckExporter
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.WordKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortMode { NEWEST, OLDEST, ALPHA, LEVEL }
enum class FilterMode { ALL, IDIOM, PHRASAL, LEARNED }

data class ArchiveUiState(
    val words: List<SavedWord> = emptyList(),
    val query: String = "",
    val sort: SortMode = SortMode.NEWEST,
    val filter: FilterMode = FilterMode.ALL,
    val expandedId: Long? = null,
    val message: String? = null,
    val confirmDelete: SavedWord? = null
) {
    val visible: List<SavedWord>
        get() {
            var list = words
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                list = list.filter {
                    it.word.contains(q, ignoreCase = true) ||
                        it.translation.contains(q, ignoreCase = true) ||
                        it.tags.contains(q, ignoreCase = true)
                }
            }
            list = when (filter) {
                FilterMode.ALL -> list
                FilterMode.IDIOM -> list.filter { it.kind == WordKind.IDIOM.id }
                FilterMode.PHRASAL -> list.filter { it.kind == WordKind.PHRASAL.id }
                FilterMode.LEARNED -> list.filter { it.learned }
            }
            list = when (sort) {
                SortMode.NEWEST -> list.sortedByDescending { it.timestamp }
                SortMode.OLDEST -> list.sortedBy { it.timestamp }
                SortMode.ALPHA -> list.sortedBy { it.word.lowercase() }
                SortMode.LEVEL -> list.sortedBy { levelOrder(it.cefr) }
            }
            return list
        }

    private fun levelOrder(level: String): Int = when (level.uppercase()) {
        "A1" -> 0; "A2" -> 1; "B1" -> 2; "B2" -> 3
        "C1" -> 4; "C2" -> 5
        else -> 6
    }
}

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProudVocabApplication
    private val vocab = app.vocabRepository

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settingsState: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            vocab.words.collect { words ->
                _uiState.value = _uiState.value.copy(words = words)
            }
        }
        viewModelScope.launch {
            app.settings.settings.collect { _settings.value = it }
        }
    }

    fun onQuery(value: String) = _uiState.set { it.copy(query = value) }
    fun setSort(mode: SortMode) = _uiState.set { it.copy(sort = mode) }
    fun setFilter(mode: FilterMode) = _uiState.set { it.copy(filter = mode) }
    fun expand(id: Long?) = _uiState.set { it.copy(expandedId = id) }
    fun askDelete(word: SavedWord?) = _uiState.set { it.copy(confirmDelete = word) }

    fun confirmDelete() {
        val word = _uiState.value.confirmDelete ?: return
        viewModelScope.launch {
            vocab.delete(word)
            _uiState.set { it.copy(confirmDelete = null) }
        }
    }

    fun toggleLearned(word: SavedWord) {
        viewModelScope.launch { vocab.setLearned(word, !word.learned) }
    }

    fun addTag(word: SavedWord, tag: String) {
        if (tag.isBlank()) return
        viewModelScope.launch {
            val merged = (word.tagsList + tag.trim()).distinct().joinToString(",")
            vocab.update(word.copy(tags = merged))
        }
    }

    fun updateTranslation(word: SavedWord, translation: String) {
        viewModelScope.launch { vocab.update(word.copy(translation = translation)) }
    }

    // ------------------------------------------------------------- export

    fun exportJson(onReady: (Intent?) -> Unit) {
        val words = _uiState.value.visible
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                writeExport("proudvocab-words.json", DeckExporter.toJson(words))
            }
            onReady(file?.let { shareIntent(it, "application/json") })
        }
    }

    fun exportAnki(onReady: (Intent?) -> Unit) {
        val words = _uiState.value.visible
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                writeExport("proudvocab-anki.csv", DeckExporter.toAnkiCsv(words))
            }
            onReady(file?.let { shareIntent(it, "text/csv") })
        }
    }

    private fun writeExport(name: String, content: String): File? = runCatching {
        val dir = File(getApplication<Application>().filesDir, "exports").apply { mkdirs() }
        File(dir, name).apply { writeText(content) }
    }.getOrNull()

    private fun shareIntent(file: File, mime: String): Intent {
        val context = getApplication<Application>()
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ------------------------------------------------------------- import

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            // Reading a backup file is disk I/O; it used to happen on the UI
            // thread straight from the picker callback.
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text == null) {
                _uiState.set { it.copy(message = "read_error") }
                return@launch
            }
            val words = withContext(Dispatchers.Default) { DeckExporter.fromJson(text) }
            if (words.isEmpty()) {
                _uiState.set { it.copy(message = "parse_error") }
                return@launch
            }
            words.forEach {
                // Keep every field the backup carries — dropping sourceTitle,
                // cefr, phonetic and partOfSpeech here silently stripped the
                // metadata of anything imported from this screen.
                vocab.save(
                    it.word, it.language, it.translation, it.contextSentence,
                    sourceTitle = it.sourceTitle,
                    cefr = it.cefr,
                    phonetic = it.phonetic,
                    partOfSpeech = it.partOfSpeech,
                    kind = it.kind,
                    tags = it.tags
                )
            }
            _uiState.set { it.copy(message = "imported") }
        }
    }

    fun consumeMessage() = _uiState.set { it.copy(message = null) }

    private inline fun <T> MutableStateFlow<T>.set(block: (T) -> T) {
        value = block(value)
    }
}
