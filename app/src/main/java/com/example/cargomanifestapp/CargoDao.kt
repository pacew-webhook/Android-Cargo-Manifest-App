package com.example.cargomanifestapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CargoDao {
    @Query("SELECT * FROM cargo_table ORDER BY id DESC")
    fun getAllCargo(): Flow<List<CargoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CargoItem)

    @Update
    suspend fun update(item: CargoItem)

    @Delete
    suspend fun delete(item: CargoItem)

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAll()
}
