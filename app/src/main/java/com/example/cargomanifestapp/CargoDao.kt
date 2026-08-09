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
    fun getAllCargo(): Flow<List<CargoModel>>

    @Insert
    suspend fun insertCargo(cargo: CargoModel)

    @Update
    suspend fun updateCargo(cargo: CargoModel)

    @Delete
    suspend fun deleteCargo(cargo: CargoModel)

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAllCargo()
}
