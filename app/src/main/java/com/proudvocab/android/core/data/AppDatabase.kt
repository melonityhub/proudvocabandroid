package com.proudvocab.android.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedWord::class,
        HistoryEntry::class,
        FavouriteEntry::class,
        GameStat::class,
        StudyDay::class
    ],
    version = 1,
    // Schema export is deliberately off: with only room.schemaLocation set,
    // the debug and release KSP tasks read AND write the same shared JSON and
    // the writer truncates before writing, so CI dies intermittently with
    // "Empty schema file". Nothing consumes the exported schema today (DB
    // version 1, no migrations, no migration tests). Re-enable together with
    // the Room Gradle plugin (which routes each variant to its own folder) or
    // with a committed schema file once real migrations exist.
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vocabDao(): VocabDao

    companion object {
        private const val NAME = "proudvocab.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
    }
}
