package com.proudvocab.android.ui.screens.player

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.proudvocab.android.ProudVocabApplication
import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.dict.WordEntry
import com.proudvocab.android.core.model.Languages
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.WordKind
import com.proudvocab.android.core.subtitle.SubtitleCue
import com.proudvocab.android.core.subtitle.SubtitleParser
import com.proudvocab.android.core.util.TextUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordLookup(
    val word: String,
    val contextSentence: String = "",
    val translation: String? = null,
    val entry: WordEntry? = null,
    val cefr: String? = null,
    val kind: Int = WordKind.WORD.id,
    val saved: Boolean = false,
    val loading: Boolean = true,
    val offline: Boolean = false,
    val error: String? = null
)

data class PlayerUiState(
    val videoUri: Uri? = null,
    val videoTitle: String = "",
    val subtitleName: String = "",
    val cues: List<SubtitleCue> = emptyList(),
    val activeIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val delayMs: Long = 0L,
    val loadingSubtitle: Boolean = false,
    val subtitleError: String? = null,
    val lookup: WordLookup? = null,
    val lineTranslations: Map<Int, String> = emptyMap(),
    val translatingLine: Boolean = false,
    val sessionSavedWords: Int = 0,
    val message: String? = null
) {
    val activeCue: SubtitleCue? get() = cues.getOrNull(activeIndex)
    val shiftedCues: List<SubtitleCue> get() = SubtitleParser.shift(cues, delayMs)
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProudVocabApplication
    private val settings = app.settings
    private val vocab = app.vocabRepository
    private val dictionary = app.dictionary
    private val translation = app.translation

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _settingsState = MutableStateFlow(AppSettings())
    val settingsState: StateFlow<AppSettings> = _settingsState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var positionJob: Job? = null
    private var translationJob: Job? = null
    private val translationCache = LinkedHashMap<String, String>(256, 0.75f, true)

    val player: ExoPlayer
        get() = exoPlayer ?: ExoPlayer.Builder(getApplication()).build().also { exoPlayer = it }

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                _settingsState.value = s
            }
        }
        startPositionPolling()
    }

    // ---------------------------------------------------------------- media

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                val p = exoPlayer
                if (p != null) {
                    val pos = p.currentPosition
                    val state = _uiState.value
                    val index = SubtitleParser.cueIndexAt(state.shiftedCues, pos)
                    if (pos != state.positionMs || index != state.activeIndex) {
                        _uiState.update {
                            it.copy(
                                positionMs = pos,
                                durationMs = p.duration.coerceAtLeast(0L),
                                isPlaying = p.isPlaying,
                                activeIndex = index
                            )
                        }
                        if (index >= 0 && state.lineTranslations[index] == null &&
                            _settingsState.value.subtitleDual &&
                            _settingsState.value.subtitleTranslateWholeLine
                        ) {
                            translateLine(index)
                        }
                    } else {
                        _uiState.update {
                            it.copy(durationMs = p.duration.coerceAtLeast(0L), isPlaying = p.isPlaying)
                        }
                    }
                }
                delay(120)
            }
        }
    }

    fun openVideo(context: Context, uri: Uri, title: String? = null) {
        val p = player
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _uiState.update { it.copy(isPlaying = p.isPlaying) }
            }
        })
        _uiState.update {
            it.copy(
                videoUri = uri,
                videoTitle = title ?: lastSegment(uri),
                message = null
            )
        }
        viewModelScope.launch { settings.update { s -> s.copy(lastVideoUri = uri.toString()) } }
    }

    fun loadSubtitle(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingSubtitle = true, subtitleError = null) }
            val result = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("cannot read file")
                SubtitleParser.parse(bytes)
            }
            result.onSuccess { cues ->
                _uiState.update {
                    it.copy(
                        cues = cues,
                        loadingSubtitle = false,
                        subtitleError = if (cues.isEmpty()) "empty" else null,
                        subtitleName = lastSegment(uri),
                        lineTranslations = emptyMap()
                    )
                }
                settings.update { s -> s.copy(lastSubtitleUri = uri.toString()) }
            }.onFailure {
                _uiState.update {
                    it.copy(loadingSubtitle = false, subtitleError = it.subtitleError ?: "error")
                }
            }
        }
    }

    fun clearSubtitle() {
        _uiState.update { it.copy(cues = emptyList(), subtitleName = "", lineTranslations = emptyMap()) }
    }

    fun togglePlay() {
        val p = player
        if (p.isPlaying) p.pause() else p.play()
        _uiState.update { it.copy(isPlaying = p.isPlaying) }
    }

    fun seekBy(deltaMs: Long) {
        val p = player
        p.seekTo((p.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun seekToCue(index: Int) {
        val cues = _uiState.value.shiftedCues
        val cue = cues.getOrNull(index) ?: return
        player.seekTo(cue.startMs)
        _uiState.update { it.copy(activeIndex = index, positionMs = cue.startMs) }
    }

    fun nextCue() = seekToCue(_uiState.value.activeIndex + 1)
    fun previousCue() {
        val index = _uiState.value.activeIndex
        seekToCue(if (index <= 0) 0 else index - 1)
    }

    fun repeatCue() {
        val cue = _uiState.value.activeCue ?: return
        player.seekTo(cue.startMs)
        if (!player.isPlaying) player.play()
    }

    fun setSpeed(value: Float) {
        player.setPlaybackSpeed(value.coerceIn(0.25f, 3f))
        _uiState.update { it.copy(speed = value) }
    }

    fun setDelay(deltaMs: Long) {
        _uiState.update {
            it.copy(delayMs = it.delayMs + deltaMs, lineTranslations = emptyMap())
        }
    }

    fun resetDelay() {
        _uiState.update { it.copy(delayMs = 0L, lineTranslations = emptyMap()) }
    }

    // ------------------------------------------------------------ translate

    fun translateLine(index: Int) {
        val state = _uiState.value
        val cue = state.shiftedCues.getOrNull(index) ?: return
        val s = _settingsState.value
        val key = "${s.translationLanguage}|${cue.text}"
        translationCache[key]?.let {
            _uiState.update { st -> st.copy(lineTranslations = st.lineTranslations + (index to it)) }
            return
        }
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _uiState.update { it.copy(translatingLine = true) }
            translation.translate(
                text = cue.text,
                source = s.learningLanguage,
                target = s.translationLanguage
            ).onSuccess { result ->
                translationCache[key] = result.text
                _uiState.update {
                    it.copy(
                        lineTranslations = it.lineTranslations + (index to result.text),
                        translatingLine = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(translatingLine = false) }
            }
        }
    }

    fun translateActiveLine() = translateLine(_uiState.value.activeIndex)

    // ------------------------------------------------------------- look up

    fun lookUp(word: String, contextSentence: String) {
        viewModelScope.launch {
            val s = _settingsState.value
            val clean = word.trim()
            val cefr = dictionary.cefr(clean)
            val isIdiom = dictionary.isIdiom(clean, s.translationLanguage)
            val isPhrasal = dictionary.isPhrasal(clean, s.translationLanguage)
            val kind = when {
                isIdiom -> WordKind.IDIOM.id
                isPhrasal -> WordKind.PHRASAL.id
                else -> WordKind.WORD.id
            }

            _uiState.update {
                it.copy(
                    lookup = WordLookup(
                        word = clean,
                        contextSentence = contextSentence,
                        cefr = cefr,
                        kind = kind,
                        loading = true,
                        saved = vocab.find(clean, s.learningLanguage) != null
                    )
                )
            }
            if (s.autoPauseOnLookup) player.pause()

            // 1 — the offline dictionary first: it is instant and always there
            val entry = dictionary.entry(clean)
            val offlineGloss = entry?.shortGloss?.takeIf { it.isNotBlank() }
                ?: dictionary.gloss(clean).takeIf { it.isNotBlank() }

            if (!offlineGloss.isNullOrBlank() && s.translationLanguage == "fa") {
                _uiState.update {
                    it.copy(
                        lookup = it.lookup?.copy(
                            translation = offlineGloss,
                            entry = entry,
                            loading = false,
                            offline = true
                        )
                    )
                }
            } else {
                val result = translation.translate(
                    text = clean,
                    source = s.learningLanguage,
                    target = s.translationLanguage
                )
                result.onSuccess { translated ->
                    _uiState.update {
                        it.copy(
                            lookup = it.lookup?.copy(
                                translation = translated.text,
                                entry = entry,
                                loading = false,
                                offline = translated.offline
                            )
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            lookup = it.lookup?.copy(
                                translation = offlineGloss,
                                entry = entry,
                                loading = false,
                                offline = true,
                                error = error.message
                            )
                        )
                    }
                }
            }
            vocab.addHistory(clean, s.learningLanguage)
        }
    }

    fun dismissLookup() {
        _uiState.update { it.copy(lookup = null) }
        if (_settingsState.value.autoRewind) {
            seekBy(-_settingsState.value.rewindSeconds * 1000L)
        }
    }

    fun toggleSaveLookup() {
        val lookup = _uiState.value.lookup ?: return
        viewModelScope.launch {
            val s = _settingsState.value
            val existing = vocab.find(lookup.word, s.learningLanguage)
            if (existing != null) {
                vocab.delete(existing)
                _uiState.update { it.copy(lookup = it.lookup?.copy(saved = false)) }
            } else {
                vocab.save(
                    word = lookup.word,
                    language = s.learningLanguage,
                    translation = lookup.translation.orEmpty(),
                    contextSentence = lookup.contextSentence,
                    sourceTitle = _uiState.value.videoTitle,
                    cefr = lookup.cefr.orEmpty(),
                    phonetic = lookup.entry?.phoneticUs.orEmpty(),
                    partOfSpeech = lookup.entry?.meanings?.firstOrNull()?.posNameEn.orEmpty(),
                    kind = lookup.kind
                )
                _uiState.update {
                    it.copy(
                        lookup = it.lookup?.copy(saved = true),
                        sessionSavedWords = it.sessionSavedWords + 1
                    )
                }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun lastSegment(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "media"

    override fun onCleared() {
        positionJob?.cancel()
        translationJob?.cancel()
        runCatching { exoPlayer?.release() }
        exoPlayer = null
        super.onCleared()
    }

    companion object {
        /**
         * Splits a cue into clickable chips: idioms and phrasal verbs stay
         * together, everything else is a single word.
         */
        fun chipsFor(
            text: String,
            translationLanguage: String,
            dictionary: com.proudvocab.android.core.dict.DictionaryManager
        ): List<Pair<String, Int>> {
            val words = TextUtils.tokenize(text).filter { it.isWord }.map { it.text }
            if (words.isEmpty()) return emptyList()
            val phrases = dictionary.findPhrases(words, translationLanguage)
            if (phrases.isEmpty()) {
                return words.map { it to WordKind.WORD.id }
            }
            val consumed = BooleanArray(words.size)
            val byIndex = HashMap<Int, Pair<String, Int>>()
            for (phrase in phrases) {
                byIndex[phrase.firstWordIndex] = phrase.phrase to phrase.kindInt
                for (k in phrase.firstWordIndex until
                    minOf(phrase.firstWordIndex + phrase.wordCount, words.size)) {
                    consumed[k] = true
                }
            }
            val out = ArrayList<Pair<String, Int>>()
            var i = 0
            while (i < words.size) {
                val phrase = byIndex[i]
                if (phrase != null) {
                    out += phrase
                    i += phrase.first.count { it == ' ' } + 1
                } else {
                    if (!consumed[i]) out += words[i] to WordKind.WORD.id
                    i += 1
                }
            }
            return out
        }
    }
}
