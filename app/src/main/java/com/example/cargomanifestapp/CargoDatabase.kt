package com.example.cargomanifestapp

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. TABEL ENTITY (Struktur Data di Database)
@Entity(tableName = "cargo_items")
data class CargoEntity(
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

// 2. DAO (Data Access Object - Query ke Database)
@Dao
interface CargoDao {
    @Query("SELECT * FROM cargo_items ORDER BY id DESC")
    fun getAllCargoItems(): Flow<List<CargoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCargo(cargo: CargoEntity)

    @Delete
    suspend fun deleteCargo(cargo: CargoEntity)

    @Query("DELETE FROM cargo_items")
    suspend fun deleteAll()
}

// 3. DATABASE ROOM CLASS
@Database(entities = [CargoEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cargoDao(): CargoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cargo_manifest_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
