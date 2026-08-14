package com.example.cargomanifestapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ManifestEntity::class, ManifestFileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ManifestDatabase : RoomDatabase() {
    abstract fun manifestDao(): ManifestDao

    companion object {
        @Volatile private var INSTANCE: ManifestDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE manifest_files ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getDatabase(context: Context): ManifestDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ManifestDatabase::class.java,
                    "manifest_search_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
