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
    exportSchema = true
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
