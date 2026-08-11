package com.example.cargomanifestapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CargoItem::class], version = 2, exportSchema = false) // naikin jadi 2
abstract class CargoDatabase : RoomDatabase() {
    abstract fun cargoDao(): CargoDao

    companion object {
        @Volatile private var INSTANCE: CargoDatabase? = null

        fun getInstance(context: Context): CargoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CargoDatabase::class.java,
                    "cargo_db"
                ).fallbackToDestructiveMigration().build() // ini biar ga usah uninstall
                INSTANCE = instance
                instance
            }
        }
    }
}
