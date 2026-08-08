package com.example.cargomanifestapp

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cargo_table")
data class CargoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val awbNo: String,
    val flightNo: String,
    val pti: String,
    val pcsQty: String,
    val weight: String,
    val subTotal: String,
    val description: String,
    val customer: String
)

@Dao
interface CargoDao {
    @Query("SELECT * FROM cargo_table ORDER BY id DESC")
    fun getAllCargo(): Flow<List<CargoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCargo(cargo: CargoItem)

    @Delete
    suspend fun deleteCargo(cargo: CargoItem)

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAll()
}

@Database(entities = [CargoItem::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
