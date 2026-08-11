package com.example.cargomanifestapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoDao {

    @Query("SELECT * FROM cargo_table ORDER BY id DESC")
    fun getAllCargo(): Flow<List<CargoItem>>

    @Insert
    suspend fun insertCargo(cargo: CargoItem)

    @Insert
    suspend fun insertAll(cargoItems: List<CargoItem>)

    @Update
    suspend fun updateCargo(cargo: CargoItem)

    @Delete
    suspend fun deleteCargo(cargo: CargoItem)

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAllCargo()
}
