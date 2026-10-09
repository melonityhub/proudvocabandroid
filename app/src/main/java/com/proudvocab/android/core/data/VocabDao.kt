package com.proudvocab.android.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {

    // ------------------------------------------------------------ words
    @Query("SELECT * FROM saved_words ORDER BY timestamp DESC")
    fun observeWords(): Flow<List<SavedWord>>

    @Query("SELECT * FROM saved_words WHERE id = :id")
    suspend fun wordById(id: Long): SavedWord?

    @Query("SELECT * FROM saved_words WHERE word = :word AND language = :lang LIMIT 1")
    suspend fun findWord(word: String, lang: String): SavedWord?

    @Query("SELECT COUNT(*) FROM saved_words")
    fun observeWordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_words WHERE learned = 1")
    fun observeLearnedCount(): Flow<Int>

    @Query(
        """SELECT COUNT(*) FROM saved_words
           WHERE learned = 0 AND reviewCount > 0 AND nextReview <= :now"""
    )
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM saved_words WHERE learned = 0 AND reviewCount = 0")
    fun observeNewCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: SavedWord): Long

    @Update
    suspend fun updateWord(word: SavedWord)

    @Upsert
    suspend fun upsertWord(word: SavedWord): Long

    @Delete
    suspend fun deleteWord(word: SavedWord)

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteWordById(id: Long)

    @Query("DELETE FROM saved_words")
    suspend fun clearWords()

    @Query(
        """UPDATE saved_words
           SET learned = 0, nextReview = 0, reviewCount = 0,
               intervalDays = 0, easeFactor = 2.5, streak = 0"""
    )
    suspend fun resetScheduling()

    // ---------------------------------------------------------- history
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // -------------------------------------------------------- favourites
    @Query("SELECT * FROM favourites ORDER BY timestamp DESC")
    fun observeFavourites(): Flow<List<FavouriteEntry>>

    @Query("SELECT COUNT(*) FROM favourites WHERE word = :word AND language = :lang")
    suspend fun isFavourite(word: String, lang: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavourite(entry: FavouriteEntry)

    @Query("DELETE FROM favourites WHERE word = :word AND language = :lang")
    suspend fun deleteFavourite(word: String, lang: String)

    @Query("DELETE FROM favourites")
    suspend fun clearFavourites()

    // ------------------------------------------------------------- games
    @Query("SELECT * FROM game_stats")
    fun observeGameStats(): Flow<List<GameStat>>

    @Query("SELECT * FROM game_stats WHERE game = :game LIMIT 1")
    suspend fun gameStat(game: String): GameStat?

    @Upsert
    suspend fun upsertGameStat(stat: GameStat)

    @Query("DELETE FROM game_stats")
    suspend fun clearGameStats()

    // -------------------------------------------------------- study days
    @Query("SELECT * FROM study_days ORDER BY date DESC LIMIT :limit")
    fun observeStudyDays(limit: Int): Flow<List<StudyDay>>

    @Upsert
    suspend fun upsertStudyDay(day: StudyDay)

    @Query("DELETE FROM study_days")
    suspend fun clearStudyDays()

    @Query("SELECT * FROM study_days")
    suspend fun allStudyDays(): List<StudyDay>

    @Query("SELECT * FROM saved_words")
    suspend fun allWords(): List<SavedWord>

    @Query("SELECT * FROM game_stats")
    suspend fun allGameStats(): List<GameStat>
}
