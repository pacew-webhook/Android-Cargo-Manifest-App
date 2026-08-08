package com.example.cargomanifestapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoDao {

    @Query("SELECT * FROM cargo_table")
    fun getAllCargo(): Flow<List<CargoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cargoItem: CargoItem)

    @Delete
    suspend fun delete(cargoItem: CargoItem)

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAll()
}
