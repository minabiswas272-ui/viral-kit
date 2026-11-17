package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ViralKitEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ViralKitDatabase : RoomDatabase() {

    abstract fun viralKitDao(): ViralKitDao

    companion object {
        private const val DB_NAME = "viralkit_cache.db"

        @Volatile
        private var instance: ViralKitDatabase? = null

        fun getInstance(context: Context): ViralKitDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ViralKitDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
        }
    }
}
