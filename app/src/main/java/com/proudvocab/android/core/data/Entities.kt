package com.proudvocab.android.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A word (or idiom / phrasal verb) the learner saved while watching. */
@Entity(
    tableName = "saved_words",
    indices = [Index(value = ["word", "language"], unique = true), Index(value = ["timestamp"])]
)
@Serializable
data class SavedWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val language: String = "en",
    val translation: String = "",
    val contextSentence: String = "",
    val sourceTitle: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val tags: String = "",
    val cefr: String = "",
    val phonetic: String = "",
    val partOfSpeech: String = "",
    /** @see com.proudvocab.android.core.settings.WordKind */
    val kind: Int = 0,
    val learned: Boolean = false,
    // ---- scheduling ---------------------------------------------------
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    val nextReview: Long = 0L,
    val reviewCount: Int = 0,
    val streak: Int = 0,
    val note: String = ""
) {
    val tagsList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "history",
    indices = [Index(value = ["word", "language"], unique = true)]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val language: String = "en",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "favourites",
    indices = [Index(value = ["word", "language"], unique = true)]
)
data class FavouriteEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val language: String = "en",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "game_stats", indices = [Index(value = ["game"], unique = true)])
data class GameStat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: String,
    val correct: Int = 0,
    val wrong: Int = 0,
    val plays: Int = 0,
    val bestScore: Int = 0
)

/** One row per day the learner studied; drives the activity heat-map. */
@Entity(tableName = "study_days", primaryKeys = ["date"])
data class StudyDay(
    val date: String,
    val reviews: Int = 0,
    val newWords: Int = 0
)
