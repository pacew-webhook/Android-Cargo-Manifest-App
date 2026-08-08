package com.example.cargomanifestapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoDao {

    @Query("SELECT * FROM cargo_table ORDER BY id ASC")
    fun getAllCargo(): Flow<List<CargoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cargo: CargoItem)

    @Update
    suspend fun update(cargo: CargoItem)

    @Delete
    suspend fun delete(cargo: CargoItem)

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAll()
}
