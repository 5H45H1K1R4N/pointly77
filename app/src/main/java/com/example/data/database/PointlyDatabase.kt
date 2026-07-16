package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, StudyMissionEntity::class, AchievementEntity::class, ActivityEntity::class, PomodoroStateEntity::class],
    version = 3,
    exportSchema = false
)
abstract class PointlyDatabase : RoomDatabase() {
    abstract fun pointlyDao(): PointlyDao

    companion object {
        @Volatile
        private var INSTANCE: PointlyDatabase? = null

        fun getDatabase(context: Context): PointlyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PointlyDatabase::class.java,
                    "pointly_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
