package com.proudvocab.android.ui.screens.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proudvocab.android.ProudVocabApplication
import com.proudvocab.android.core.data.GameStat
import com.proudvocab.android.core.data.SavedWord
import com.proudvocab.android.core.games.GameEngine
import com.proudvocab.android.core.games.GameQuestion
import com.proudvocab.android.core.games.GameType
import com.proudvocab.android.core.games.MatchPair
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.core.srs.Rating
import com.proudvocab.android.core.srs.SrsScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReviewTab { SRS, GAMES }

data class ReviewUiState(
    val tab: ReviewTab = ReviewTab.SRS,
    // ---- SRS ------------------------------------------------------------
    val dueCount: Int = 0,
    val newCount: Int = 0,
    val totalCount: Int = 0,
    val learnedCount: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val heatmap: Map<String, Int> = emptyMap(),
    val session: List<SavedWord> = emptyList(),
    val sessionIndex: Int = 0,
    val revealed: Boolean = false,
    val finished: Boolean = false,
    val sessionCorrect: Int = 0,
    val sessionWrong: Int = 0,
    val intervals: Map<Rating, Double> = emptyMap(),
    val loadingSession: Boolean = false,
    // ---- games ----------------------------------------------------------
    val activeGame: GameType? = null,
    val questions: List<GameQuestion> = emptyList(),
    val matchPairs: List<MatchPair> = emptyList(),
    val gameIndex: Int = 0,
    val gameScore: Int = 0,
    val gameCorrect: Int = 0,
    val gameWrong: Int = 0,
    val gameFinished: Boolean = false,
    val lastAnswerCorrect: Boolean? = null,
    val gameStats: List<GameStat> = emptyList()
) {
    val currentCard: SavedWord? get() = session.getOrNull(sessionIndex)
    val currentQuestion: GameQuestion? get() = questions.getOrNull(gameIndex)
    val sessionProgress: Float
        get() = if (session.isEmpty()) 0f else sessionIndex.toFloat() / session.size.toFloat()
    val gameProgress: Float
        get() = if (questions.isEmpty()) 0f else gameIndex.toFloat() / questions.size.toFloat()
}

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ProudVocabApplication
    private val vocab = app.vocabRepository
    private val settings = app.settings

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settingsState: StateFlow<AppSettings> = _settings.asStateFlow()

    private var allWords: List<SavedWord> = emptyList()

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                _settings.value = s
                refreshCounts(s)
            }
        }
        viewModelScope.launch {
            vocab.words.collect { words ->
                allWords = words
                _uiState.value = _uiState.value.copy(totalCount = words.size)
                refreshCounts(_settings.value)
            }
        }
        viewModelScope.launch {
            vocab.learnedCount.collect { count ->
                _uiState.value = _uiState.value.copy(learnedCount = count)
            }
        }
        viewModelScope.launch {
            vocab.studyDays.collect { days ->
                _uiState.value = _uiState.value.copy(
                    heatmap = days.associate { it.date to (it.reviews + it.newWords) }
                )
            }
        }
        viewModelScope.launch {
            vocab.gameStats.collect { stats ->
                _uiState.value = _uiState.value.copy(gameStats = stats)
            }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                streak = vocab.currentStreak(),
                bestStreak = vocab.bestStreak()
            )
        }
    }

    private suspend fun refreshCounts(s: AppSettings) {
        val now = System.currentTimeMillis()
        val due = allWords.count {
            !it.learned && it.reviewCount > 0 && it.nextReview <= now
        }
        val fresh = allWords.count { !it.learned && it.reviewCount == 0 }
        _uiState.value = _uiState.value.copy(dueCount = due, newCount = fresh)
    }

    fun setTab(tab: ReviewTab) = _uiState.update { it.copy(tab = tab) }

    // ------------------------------------------------------------------ srs

    fun startSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingSession = true)
            val s = _settings.value
            val now = System.currentTimeMillis()
            val due = allWords
                .filter { !it.learned && it.reviewCount > 0 && it.nextReview <= now }
                .sortedBy { it.nextReview }
            val fresh = allWords
                .filter { !it.learned && it.reviewCount == 0 }
                .take(s.newLimit)
            val session = (due + fresh).take(s.sessionLimit)
            _uiState.value = _uiState.value.copy(
                session = session,
                sessionIndex = 0,
                revealed = false,
                finished = session.isEmpty(),
                sessionCorrect = 0,
                sessionWrong = 0,
                loadingSession = false,
                intervals = emptyMap()
            )
            updateIntervals()
        }
    }

    private fun updateIntervals() {
        val card = _uiState.value.currentCard ?: run {
            _uiState.value = _uiState.value.copy(intervals = emptyMap())
            return
        }
        val state = com.proudvocab.android.core.srs.SrsState(
            intervalDays = card.intervalDays,
            easeFactor = card.easeFactor,
            nextReview = card.nextReview,
            reviewCount = card.reviewCount,
            streak = card.streak
        )
        _uiState.value = _uiState.value.copy(
            intervals = SrsScheduler.previewIntervals(state, _settings.value.srsAlgorithmEnum())
        )
    }

    fun reveal() {
        _uiState.value = _uiState.value.copy(revealed = true)
    }

    fun rate(rating: Rating) {
        val state = _uiState.value
        val card = state.currentCard ?: return
        viewModelScope.launch {
            vocab.rate(card, rating, _settings.value.srsAlgorithmEnum())
            val wasCorrect = rating != Rating.AGAIN
            val nextIndex = state.sessionIndex + 1
            _uiState.value = _uiState.value.copy(
                sessionIndex = nextIndex,
                revealed = false,
                finished = nextIndex >= state.session.size,
                sessionCorrect = state.sessionCorrect + if (wasCorrect) 1 else 0,
                sessionWrong = state.sessionWrong + if (wasCorrect) 0 else 1
            )
            updateIntervals()
        }
    }

    fun quitSession() {
        _uiState.value = _uiState.value.copy(
            session = emptyList(),
            sessionIndex = 0,
            finished = false,
            revealed = false
        )
    }

    fun markLearned(word: SavedWord, learned: Boolean) {
        viewModelScope.launch { vocab.setLearned(word, learned) }
    }

    // ---------------------------------------------------------------- games

    fun startGame(type: GameType) {
        viewModelScope.launch {
            val s = _settings.value
            val pool = allWords.filter { s.gameShowLearned || !it.learned }
            val questions = GameEngine.build(type, pool)
            _uiState.value = _uiState.value.copy(
                activeGame = type,
                questions = questions,
                matchPairs = GameEngine.matchPairs(questions),
                gameIndex = 0,
                gameScore = 0,
                gameCorrect = 0,
                gameWrong = 0,
                gameFinished = questions.isEmpty(),
                lastAnswerCorrect = null
            )
        }
    }

    fun answer(guess: String) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        val correct = question.isCorrect(guess)
        _uiState.value = state.copy(
            gameCorrect = state.gameCorrect + if (correct) 1 else 0,
            gameWrong = state.gameWrong + if (correct) 0 else 1,
            gameScore = state.gameScore + if (correct) 10 else 0,
            lastAnswerCorrect = correct
        )
    }

    fun nextQuestion() {
        val state = _uiState.value
        val next = state.gameIndex + 1
        if (next >= state.questions.size) {
            finishGame()
        } else {
            _uiState.value = state.copy(gameIndex = next, lastAnswerCorrect = null)
        }
    }

    fun finishGame() {
        val state = _uiState.value
        val type = state.activeGame ?: return
        _uiState.value = state.copy(gameFinished = true)
        viewModelScope.launch {
            vocab.recordGame(type.key, state.gameCorrect, state.gameWrong, state.gameScore)
        }
    }

    fun exitGame() {
        _uiState.value = _uiState.value.copy(
            activeGame = null,
            questions = emptyList(),
            matchPairs = emptyList(),
            gameIndex = 0,
            gameFinished = false,
            lastAnswerCorrect = null
        )
    }

    private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
        value = block(value)
    }
}
