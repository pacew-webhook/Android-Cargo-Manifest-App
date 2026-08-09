package com.example.cargomanifestapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoDao {
    @Query("SELECT * FROM cargo_table")
    fun getAllCargo(): Flow<List<CargoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCargo(item: CargoItem)
}
