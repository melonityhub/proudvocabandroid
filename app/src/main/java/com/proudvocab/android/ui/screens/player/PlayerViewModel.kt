package com.proudvocab.android.ui.screens.player

import android.app.Application
import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.proudvocab.android.ProudVocabApplication
import com.proudvocab.android.core.dict.WordEntry
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.settings.WordKind
import com.proudvocab.android.core.subtitle.SubtitleCue
import com.proudvocab.android.core.subtitle.SubtitleParser
import com.proudvocab.android.core.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SUBTITLE_BYTES = 20L * 1024L * 1024L

private class SubtitleTooLargeException : IOException()

data class WordLookup(
    val word: String,
    val contextSentence: String = "",
    val translation: String? = null,
    val entry: WordEntry? = null,
    val cefr: String? = null,
    val kind: Int = WordKind.WORD.id,
    val saved: Boolean = false,
    val tags: List<String> = emptyList(),
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
    /**
     * Set when ExoPlayer gives up on the current file. Surfacing this is the
     * difference between "the video silently does not play" and the learner
     * being told why, with a retry button.
     */
    val playbackError: String? = null,
    val lookup: WordLookup? = null,
    val lineTranslations: Map<Int, String> = emptyMap(),
    val failedLineTranslations: Set<Int> = emptySet(),
    val translatingLine: Boolean = false,
    val translatingLineIndex: Int? = null,
    val sessionSavedWords: Int = 0,
    val message: String? = null
) {
    val activeCue: SubtitleCue? get() = cues.getOrNull(activeIndex)
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

    /**
     * The cues as the player sees them, i.e. shifted by the subtitle delay.
     * Recomputing the whole list on every access (the position poller touches
     * it several times per tick) is needless garbage for a 2 000-cue movie, so
     * it is memoised and only rebuilt when the cues or the delay change.
     */
    private val _shiftedCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val shiftedCues: StateFlow<List<SubtitleCue>> = _shiftedCues.asStateFlow()

    private var released = false
    private var positionJob: Job? = null
    private var subtitleLoadJob: Job? = null
    private var subtitleLoadRequestId = 0L
    private var translationJob: Job? = null
    private var translationRequestId = 0L
    private var lookupJob: Job? = null
    private var restoreAttempted = false
    private val translationCache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > 256
    }

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = playerOrNull ?: return
            _uiState.update {
                it.copy(
                    durationMs = current.duration.coerceAtLeast(0L),
                    isPlaying = current.isPlaying
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Without this the learner taps a video and simply nothing happens.
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    playbackError = describeError(error)
                )
            }
        }
    }

    /**
     * Created once, on the main thread, together with the ViewModel — never
     * lazily from a composable. A getter that builds a fresh player whenever
     * the field is null could hand out a player after [onCleared] released the
     * real one, and that one would never be released.
     */
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build()
        .apply { addListener(playbackListener) }

    /** The player for [androidx.media3.ui.PlayerView]; null once released. */
    val playerOrNull: ExoPlayer?
        get() = if (released) null else exoPlayer

    private fun describeError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "file_not_found"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "no_access"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "unsupported"

        else -> "code_" + error.errorCode
    }

    private fun describeError(error: Throwable): String =
        (error as? PlaybackException)?.let { describeError(it) }
            ?: error.javaClass.simpleName

    private fun cancelLineTranslation() {
        translationRequestId += 1L
        translationJob?.cancel()
        translationJob = null
    }

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                val previous = _settingsState.value
                val translationSettingsChanged =
                    previous.learningLanguage != s.learningLanguage ||
                        previous.translationLanguage != s.translationLanguage ||
                        previous.translationEngine != s.translationEngine ||
                        previous.onlineFallback != s.onlineFallback
                val delayChanged = _uiState.value.delayMs != s.subtitleDelayMs
                _settingsState.value = s
                if (translationSettingsChanged || delayChanged) cancelLineTranslation()
                _uiState.update { state ->
                    if (delayChanged || translationSettingsChanged) {
                        state.copy(
                            delayMs = s.subtitleDelayMs,
                            lineTranslations = emptyMap(),
                            failedLineTranslations = emptySet(),
                            translatingLine = false,
                            translatingLineIndex = null
                        )
                    } else state
                }
            }
        }
        // Rebuild the shifted cue list only when its inputs actually change.
        viewModelScope.launch {
            _uiState.collect { state ->
                val next = SubtitleParser.shift(state.cues, state.delayMs)
                if (next != _shiftedCues.value) _shiftedCues.value = next
            }
        }
        startPositionPolling()
    }

    // ---------------------------------------------------------------- media

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                val p = playerOrNull
                if (p != null) {
                    val pos = p.currentPosition
                    val state = _uiState.value
                    val cues = _shiftedCues.value
                    val index = SubtitleParser.cueIndexAt(cues, pos)
                    val previousCue = cues.getOrNull(state.activeIndex)
                    if (_settingsState.value.subtitleShadowing && previousCue != null &&
                        pos >= previousCue.endMs && p.isPlaying
                    ) {
                        p.pause()
                    }
                    if (pos != state.positionMs || index != state.activeIndex) {
                        _uiState.update {
                            it.copy(
                                positionMs = pos,
                                durationMs = p.duration.coerceAtLeast(0L),
                                isPlaying = p.isPlaying,
                                activeIndex = index
                            )
                        }
                        if (!state.loadingSubtitle && index >= 0 &&
                            state.lineTranslations[index] == null &&
                            state.translatingLineIndex != index &&
                            _settingsState.value.subtitleDual &&
                            !_settingsState.value.subtitleShadowing &&
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

    /**
     * Re-opens the video and subtitle the learner used last time.
     *
     * The URIs are remembered in settings, but reading them back needs the
     * persistable grant that was taken when they were first picked; a file on
     * an SD card that has since been removed, or a provider that never offered
     * persistable grants, simply reports "open it again" instead of failing
     * quietly with a black screen.
     */
    fun restoreLastMedia() {
        if (restoreAttempted) return
        restoreAttempted = true
        if (_uiState.value.videoUri != null) return
        viewModelScope.launch {
            val snapshot = settings.snapshot()
            val video = snapshot.lastVideoUri.takeIf { it.isNotBlank() }
            val subtitle = snapshot.lastSubtitleUri.takeIf { it.isNotBlank() }
            if (video == null && subtitle == null) return@launch
            val context: Context = getApplication()
            // `snapshot()` suspends, and an "Open with ProudVocab" intent can
            // have loaded a different file in the meantime — never overwrite it.
            if (video != null && _uiState.value.videoUri == null) {
                val uri = runCatching { Uri.parse(video) }.getOrNull()
                if (uri != null && withContext(Dispatchers.IO) { canRead(context, uri) }) {
                    openVideo(context, uri, rememberTitle = false, autoPlay = false)
                } else {
                    settings.update { s -> s.copy(lastVideoUri = "") }
                    _uiState.update { it.copy(message = "restore_video_failed") }
                }
            }
            if (subtitle != null && _uiState.value.cues.isEmpty()) {
                val uri = runCatching { Uri.parse(subtitle) }.getOrNull()
                if (uri != null && withContext(Dispatchers.IO) { canRead(context, uri) }) {
                    loadSubtitle(context, uri)
                } else {
                    settings.update { s -> s.copy(lastSubtitleUri = "") }
                    _uiState.update { it.copy(message = "restore_subtitle_failed") }
                }
            }
        }
    }

    private fun canRead(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    fun openVideo(
        context: Context,
        uri: Uri,
        title: String? = null,
        rememberTitle: Boolean = true,
        autoPlay: Boolean = true
    ) {
        cancelLineTranslation()
        val p = playerOrNull ?: return
        _uiState.update {
            it.copy(
                videoUri = uri,
                videoTitle = title ?: lastSegment(uri),
                activeIndex = -1,
                positionMs = 0L,
                durationMs = 0L,
                playbackError = null,
                lineTranslations = emptyMap(),
                failedLineTranslations = emptySet(),
                translatingLine = false,
                translatingLineIndex = null,
                message = null
            )
        }
        runCatching {
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
            // Restoring the last file loads it ready to go but must not start
            // blaring the moment the app is opened.
            p.playWhenReady = autoPlay
        }.onFailure { error ->
            _uiState.update { it.copy(playbackError = describeError(error)) }
        }
        if (rememberTitle) {
            viewModelScope.launch { settings.update { s -> s.copy(lastVideoUri = uri.toString()) } }
        }
    }

    /** Re-prepares the file that is already loaded — the "Try again" button. */
    fun retryPlayback() {
        val uri = _uiState.value.videoUri ?: return
        openVideo(getApplication(), uri, title = _uiState.value.videoTitle, rememberTitle = false)
    }

    /** Called when the player screen leaves the composition (tab switch, back). */
    fun pauseForLeave() {
        runCatching { playerOrNull?.pause() }
    }

    fun loadSubtitle(context: Context, uri: Uri) {
        cancelLineTranslation()
        subtitleLoadJob?.cancel()
        val requestId = ++subtitleLoadRequestId
        subtitleLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingSubtitle = true,
                    subtitleError = null,
                    translatingLine = false,
                    translatingLineIndex = null
                )
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching { SubtitleParser.parse(readSubtitleBytes(context, uri)) }
                }
                if (requestId != subtitleLoadRequestId) return@launch
                result.onSuccess { cues ->
                    _uiState.update {
                        it.copy(
                            cues = cues,
                            activeIndex = -1,
                            loadingSubtitle = false,
                            subtitleError = if (cues.isEmpty()) "empty" else null,
                            subtitleName = lastSegment(uri),
                            lineTranslations = emptyMap(),
                            failedLineTranslations = emptySet(),
                            translatingLine = false,
                            translatingLineIndex = null
                        )
                    }
                    settings.update { s -> s.copy(lastSubtitleUri = uri.toString()) }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingSubtitle = false,
                            subtitleError = if (error is SubtitleTooLargeException) "too_large" else "error"
                        )
                    }
                }
            } finally {
                if (requestId == subtitleLoadRequestId) subtitleLoadJob = null
            }
        }
    }

    fun clearSubtitle() {
        subtitleLoadRequestId += 1L
        subtitleLoadJob?.cancel()
        subtitleLoadJob = null
        cancelLineTranslation()
        _uiState.update {
            it.copy(
                cues = emptyList(),
                activeIndex = -1,
                subtitleName = "",
                loadingSubtitle = false,
                subtitleError = null,
                lineTranslations = emptyMap(),
                failedLineTranslations = emptySet(),
                translatingLine = false,
                translatingLineIndex = null
            )
        }
    }

    fun togglePlay() {
        val p = playerOrNull ?: return
        if (_uiState.value.playbackError != null) {
            retryPlayback()
            return
        }
        if (p.isPlaying) p.pause() else p.play()
        _uiState.update { it.copy(isPlaying = p.isPlaying) }
    }

    fun seekBy(deltaMs: Long) {
        val p = playerOrNull ?: return
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(if (p.duration > 0L) target.coerceAtMost(p.duration) else target)
    }

    fun seekTo(positionMs: Long) {
        val p = playerOrNull ?: return
        val position = positionMs.coerceAtLeast(0L)
        p.seekTo(if (p.duration > 0L) position.coerceAtMost(p.duration) else position)
    }

    fun seekToCue(index: Int) {
        val cues = _shiftedCues.value
        val cue = cues.getOrNull(index) ?: return
        playerOrNull?.seekTo(cue.startMs)
        _uiState.update { it.copy(activeIndex = index, positionMs = cue.startMs) }
    }

    fun nextCue() = seekToCue(_uiState.value.activeIndex + 1)
    fun previousCue() {
        val index = _uiState.value.activeIndex
        seekToCue(if (index <= 0) 0 else index - 1)
    }

    fun repeatCue() {
        val cue = _uiState.value.activeCue ?: return
        val p = playerOrNull ?: return
        p.seekTo(cue.startMs)
        if (!p.isPlaying) p.play()
    }

    fun setSpeed(value: Float) {
        val bounded = value.coerceIn(0.25f, 3f)
        playerOrNull?.setPlaybackSpeed(bounded)
        _uiState.update { it.copy(speed = bounded) }
    }

    fun setDelay(deltaMs: Long) = setSubtitleDelay(_uiState.value.delayMs + deltaMs)

    fun setSubtitleDelay(valueMs: Long) {
        val bounded = valueMs.coerceIn(-5_000L, 5_000L)
        cancelLineTranslation()
        _uiState.update {
            it.copy(
                delayMs = bounded,
                lineTranslations = emptyMap(),
                failedLineTranslations = emptySet(),
                translatingLine = false,
                translatingLineIndex = null
            )
        }
        viewModelScope.launch { settings.update { it.copy(subtitleDelayMs = bounded) } }
    }

    fun resetDelay() = setSubtitleDelay(0L)

    // ------------------------------------------------------------ translate

    fun translateLine(index: Int) {
        val state = _uiState.value
        val cue = _shiftedCues.value.getOrNull(index) ?: return
        if (state.lineTranslations[index] != null || state.translatingLineIndex == index) return

        cancelLineTranslation()
        val requestId = translationRequestId
        val s = _settingsState.value
        val key = "${s.learningLanguage}|${s.translationLanguage}|${s.translationEngine}|${s.onlineFallback}|${cue.text}"
        translationCache[key]?.let { cached ->
            _uiState.update { current ->
                current.copy(
                    lineTranslations = current.lineTranslations + (index to cached),
                    failedLineTranslations = current.failedLineTranslations - index,
                    translatingLine = false,
                    translatingLineIndex = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                failedLineTranslations = it.failedLineTranslations - index,
                translatingLine = true,
                translatingLineIndex = index
            )
        }
        translationJob = viewModelScope.launch {
            val result = try {
                translation.translate(
                    text = cue.text,
                    source = s.learningLanguage,
                    target = s.translationLanguage
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            val latestSettings = _settingsState.value
            if (requestId != translationRequestId ||
                latestSettings.learningLanguage != s.learningLanguage ||
                latestSettings.translationLanguage != s.translationLanguage ||
                latestSettings.translationEngine != s.translationEngine ||
                latestSettings.onlineFallback != s.onlineFallback ||
                _shiftedCues.value.getOrNull(index)?.text != cue.text
            ) return@launch

            result.fold(
                onSuccess = { translated ->
                    if (translated.text.isBlank()) {
                        _uiState.update {
                            it.copy(
                                failedLineTranslations = it.failedLineTranslations + index,
                                translatingLine = false,
                                translatingLineIndex = null
                            )
                        }
                    } else {
                        translationCache[key] = translated.text
                        _uiState.update {
                            it.copy(
                                lineTranslations = it.lineTranslations + (index to translated.text),
                                failedLineTranslations = it.failedLineTranslations - index,
                                translatingLine = false,
                                translatingLineIndex = null
                            )
                        }
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            failedLineTranslations = it.failedLineTranslations + index,
                            translatingLine = false,
                            translatingLineIndex = null
                        )
                    }
                }
            )
            translationJob = null
        }
    }

    fun translateActiveLine() = translateLine(_uiState.value.activeIndex)

    // ------------------------------------------------------------- look up

    fun lookUp(word: String, contextSentence: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val s = _settingsState.value
            val clean = word.trim()
            if (clean.isBlank()) return@launch

            val cefr = dictionary.cefr(clean)
            val isIdiom = dictionary.isIdiom(clean, s.translationLanguage)
            val isPhrasal = dictionary.isPhrasal(clean, s.translationLanguage)
            val kind = when {
                isIdiom -> WordKind.IDIOM.id
                isPhrasal -> WordKind.PHRASAL.id
                else -> WordKind.WORD.id
            }
            val savedWord = safelyOrNull { vocab.find(clean, s.learningLanguage) }
            _uiState.update {
                it.copy(
                    lookup = WordLookup(
                        word = clean,
                        contextSentence = contextSentence,
                        cefr = cefr,
                        kind = kind,
                        loading = true,
                        saved = savedWord != null,
                        tags = savedWord?.tagsList.orEmpty()
                    )
                )
            }
            if (s.autoPauseOnLookup) playerOrNull?.pause()

            // Fetch rich dictionary data independently, but always ask the
            // selected translation engine for the translation itself.
            val entry = dictionary.entry(clean)
            val glossMatchesDirection = when {
                s.learningLanguage == "en" && s.translationLanguage == "fa" -> entry?.isPersian == false
                s.learningLanguage == "fa" && s.translationLanguage == "en" -> entry?.isPersian == true
                s.learningLanguage == "auto" && s.translationLanguage == "fa" -> entry?.isPersian == false
                s.learningLanguage == "auto" && s.translationLanguage == "en" -> entry?.isPersian == true
                else -> false
            }
            val offlineGloss = if (glossMatchesDirection) {
                entry?.shortGloss?.takeIf { it.isNotBlank() }
                    ?: dictionary.gloss(clean).takeIf { it.isNotBlank() }
            } else null

            // Engines report failures through Result, but a bug or a native
            // error inside one of them must still end as a failed lookup and
            // never as an uncaught exception in viewModelScope.
            val result = try {
                translation.translate(
                    text = clean,
                    source = s.learningLanguage,
                    target = s.translationLanguage
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            val latestSettings = _settingsState.value
            val latestLookup = _uiState.value.lookup
            if (latestLookup == null || latestLookup.word != clean ||
                latestLookup.contextSentence != contextSentence ||
                latestSettings.learningLanguage != s.learningLanguage ||
                latestSettings.translationLanguage != s.translationLanguage ||
                latestSettings.translationEngine != s.translationEngine ||
                latestSettings.onlineFallback != s.onlineFallback
            ) return@launch

            result.onSuccess { translated ->
                _uiState.update {
                    it.copy(
                        lookup = it.lookup?.copy(
                            translation = translated.text,
                            entry = entry,
                            loading = false,
                            offline = translated.offline,
                            error = null
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
                            offline = !offlineGloss.isNullOrBlank(),
                            error = error.message ?: "Translation failed"
                        )
                    )
                }
            }
            safelyOrNull { vocab.addHistory(clean, s.learningLanguage) }
        }
    }

    /**
     * Runs a best-effort database call. Any failure becomes null, but
     * cancellation is always rethrown so structured concurrency still works.
     */
    private suspend fun <T> safelyOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        android.util.Log.w("ProudVocab", "lookup side-effect failed", error)
        null
    }

    fun dismissLookup() {
        lookupJob?.cancel()
        lookupJob = null
        _uiState.update { it.copy(lookup = null) }
        if (_settingsState.value.autoRewind) {
            seekBy(-_settingsState.value.rewindSeconds * 1000L)
        }
    }

    fun toggleSaveLookup() {
        val lookup = _uiState.value.lookup ?: return
        viewModelScope.launch {
            // Database failures must become a message, never an uncaught
            // exception: viewModelScope has no handler, so it would kill the app.
            runCatching {
                val s = _settingsState.value
                val existing = vocab.find(lookup.word, s.learningLanguage)
                if (_uiState.value.lookup?.word == lookup.word) {
                if (existing != null) {
                    vocab.delete(existing)
                    _uiState.update {
                        it.copy(lookup = it.lookup?.copy(saved = false, tags = emptyList()))
                    }
                } else {
                    val savedWord = vocab.save(
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
                    vocab.recordStudy(newWords = 1)
                    _uiState.update {
                        it.copy(
                            lookup = it.lookup?.copy(saved = true, tags = savedWord.tagsList),
                            sessionSavedWords = it.sessionSavedWords + 1
                        )
                    }
                }
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                android.util.Log.w("ProudVocab", "saving the word failed", error)
                _uiState.update { it.copy(message = "save_fail") }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun readSubtitleBytes(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("cannot read file")
        return input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val single = source.read()
                    if (single < 0) break
                    total += 1
                    if (total > MAX_SUBTITLE_BYTES) throw SubtitleTooLargeException()
                    output.write(single)
                    continue
                }
                total += count
                if (total > MAX_SUBTITLE_BYTES) throw SubtitleTooLargeException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun lastSegment(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "media"

    override fun onCleared() {
        released = true
        positionJob?.cancel()
        subtitleLoadJob?.cancel()
        translationJob?.cancel()
        lookupJob?.cancel()
        exoPlayer.removeListener(playbackListener)
        runCatching { exoPlayer.release() }
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
