package com.example.cargomanifestapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ManifestEntity::class, ManifestFileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ManifestDatabase : RoomDatabase() {
    abstract fun manifestDao(): ManifestDao

    companion object {
        @Volatile private var INSTANCE: ManifestDatabase? = null

        fun getDatabase(context: Context): ManifestDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ManifestDatabase::class.java,
                    "manifest_search_database"
                ).build().also { INSTANCE = it }
            }
    }
}
