package com.yubytech.tracked.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ActivityEvent::class, LocationEvent::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityEventDao(): ActivityEventDao
    abstract fun locationEventDao(): LocationEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tracked_db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
} 