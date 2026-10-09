package com.proudvocab.android.core.data

import com.proudvocab.android.core.srs.Rating
import com.proudvocab.android.core.srs.SrsAlgorithm
import com.proudvocab.android.core.srs.SrsScheduler
import com.proudvocab.android.core.srs.SrsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Everything the UI needs to know about the learner's own vocabulary. */
class VocabRepository(private val dao: VocabDao) {

    companion object {
        private const val DAY_MS = 86_400_000L
    }

    val words: Flow<List<SavedWord>> = dao.observeWords()
    val wordCount: Flow<Int> = dao.observeWordCount()
    val learnedCount: Flow<Int> = dao.observeLearnedCount()
    val newCount: Flow<Int> = dao.observeNewCount()
    val gameStats: Flow<List<GameStat>> = dao.observeGameStats()
    val studyDays: Flow<List<StudyDay>> = dao.observeStudyDays(400)
    val favourites: Flow<List<FavouriteEntry>> = dao.observeFavourites()
    val history: Flow<List<HistoryEntry>> = dao.observeHistory(40)

    fun dueCount(now: Long = System.currentTimeMillis()): Flow<Int> = dao.observeDueCount(now)

    suspend fun find(word: String, language: String): SavedWord? = dao.findWord(word, language)

    suspend fun allWords(): List<SavedWord> = dao.allWords()

    /** Inserts a word, or enriches the existing one without losing progress. */
    suspend fun save(
        word: String,
        language: String,
        translation: String = "",
        contextSentence: String = "",
        sourceTitle: String = "",
        cefr: String = "",
        phonetic: String = "",
        partOfSpeech: String = "",
        kind: Int = 0,
        tags: String = ""
    ): SavedWord {
        val key = word.trim()
        val existing = dao.findWord(key, language)
        return if (existing == null) {
            val entity = SavedWord(
                word = key,
                language = language,
                translation = translation,
                contextSentence = contextSentence,
                sourceTitle = sourceTitle,
                cefr = cefr,
                phonetic = phonetic,
                partOfSpeech = partOfSpeech,
                kind = kind,
                tags = tags
            )
            dao.insertWord(entity)
            dao.findWord(key, language) ?: entity
        } else {
            val merged = existing.copy(
                translation = translation.ifBlank { existing.translation },
                contextSentence = contextSentence.ifBlank { existing.contextSentence },
                sourceTitle = sourceTitle.ifBlank { existing.sourceTitle },
                cefr = cefr.ifBlank { existing.cefr },
                phonetic = phonetic.ifBlank { existing.phonetic },
                partOfSpeech = partOfSpeech.ifBlank { existing.partOfSpeech },
                tags = mergeTags(existing.tags, tags)
            )
            if (merged != existing) dao.updateWord(merged)
            merged
        }
    }

    private fun mergeTags(a: String, b: String): String {
        val set = LinkedHashSet<String>()
        a.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { set += it }
        b.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { set += it }
        return set.joinToString(",")
    }

    suspend fun update(word: SavedWord) = dao.updateWord(word)

    suspend fun delete(word: SavedWord) = dao.deleteWord(word)

    suspend fun deleteById(id: Long) = dao.deleteWordById(id)

    /** Applies a review grade and writes the new schedule back. */
    suspend fun rate(word: SavedWord, rating: Rating, algorithm: SrsAlgorithm): SavedWord {
        val state = SrsScheduler.next(
            state = SrsState(
                intervalDays = word.intervalDays,
                easeFactor = word.easeFactor,
                nextReview = word.nextReview,
                reviewCount = word.reviewCount,
                streak = word.streak
            ),
            rating = rating,
            algorithm = algorithm
        )
        val updated = word.copy(
            intervalDays = state.intervalDays,
            easeFactor = state.easeFactor,
            nextReview = state.nextReview,
            reviewCount = state.reviewCount,
            streak = state.streak,
            learned = word.learned || (rating == Rating.EASY && word.reviewCount >= 3)
        )
        dao.updateWord(updated)
        recordStudy(reviews = 1)
        return updated
    }

    suspend fun setLearned(word: SavedWord, learned: Boolean) {
        dao.updateWord(word.copy(learned = learned))
    }

    suspend fun recordStudy(reviews: Int = 0, newWords: Int = 0) {
        val today = todayKey()
        val existing = dao.allStudyDays().firstOrNull { it.date == today }
        dao.upsertStudyDay(
            StudyDay(
                date = today,
                reviews = (existing?.reviews ?: 0) + reviews,
                newWords = (existing?.newWords ?: 0) + newWords
            )
        )
    }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Current streak in days, computed from the study-day rows. */
    suspend fun currentStreak(): Int {
        val days = dao.allStudyDays().map { it.date }.toSet()
        if (days.isEmpty()) return 0
        val cal = Calendar.getInstance()
        if (!days.contains(todayKey())) cal.add(Calendar.DAY_OF_YEAR, -1)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var streak = 0
        while (true) {
            val key = fmt.format(cal.time)
            if (days.contains(key)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return streak
    }

    suspend fun bestStreak(): Int {
        val days = dao.allStudyDays().map { it.date }.sorted()
        if (days.isEmpty()) return 0
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            val prev = fmt.parse(days[i - 1]) ?: continue
            val cur = fmt.parse(days[i]) ?: continue
            val diff = ((cur.time - prev.time) / DAY_MS).toInt()
            run = if (diff == 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    suspend fun resetScheduling() = dao.resetScheduling()
    suspend fun clearWords() = dao.clearWords()

    /** Clears the study-day rows behind the streak and the activity heat-map. */
    suspend fun clearStudyDays() = dao.clearStudyDays()

    // ------------------------------------------------------------- games
    suspend fun recordGame(game: String, correct: Int, wrong: Int, score: Int) {
        val existing = dao.gameStat(game)
        dao.upsertGameStat(
            GameStat(
                id = existing?.id ?: 0L,
                game = game,
                correct = (existing?.correct ?: 0) + correct,
                wrong = (existing?.wrong ?: 0) + wrong,
                plays = (existing?.plays ?: 0) + 1,
                bestScore = maxOf(existing?.bestScore ?: 0, score)
            )
        )
    }

    suspend fun clearGameStats() = dao.clearGameStats()

    // -------------------------------------------------------- favourites
    suspend fun toggleFavourite(word: String, language: String): Boolean =
        if (dao.isFavourite(word, language) > 0) {
            dao.deleteFavourite(word, language)
            false
        } else {
            dao.insertFavourite(FavouriteEntry(word = word, language = language))
            true
        }

    suspend fun addHistory(word: String, language: String) {
        dao.insertHistory(HistoryEntry(word = word, language = language))
    }

    suspend fun clearHistory() = dao.clearHistory()

    /** Wipes the favourites table ("erase everything" used to skip it). */
    suspend fun clearFavourites() = dao.clearFavourites()

    suspend fun replaceAll(words: List<SavedWord>) {
        dao.clearWords()
        words.forEach { dao.insertWord(it) }
    }
}
