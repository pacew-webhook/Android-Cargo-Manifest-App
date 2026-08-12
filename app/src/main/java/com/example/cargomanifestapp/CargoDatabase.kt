package com.example.cargomanifestapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CargoItem::class, BtbEntity::2lass, BtbPhotoEntity::class], version = 1, exportSchema = false)
abstract class CargoDatabase : RoomDatabase() {
    abstract fun cargoDao(): CargoDao

    companion object {
        @Volatile
        private var INSTANCE: CargoDatabase? = null

        fun getDatabase(context: Context): CargoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CargoDatabase::class.java,
                    "cargo_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
    abstract fun btbDao(): BtbDao
}
