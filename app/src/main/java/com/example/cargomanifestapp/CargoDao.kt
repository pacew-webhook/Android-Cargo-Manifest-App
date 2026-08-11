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
    fun getAll(): Flow<List<CargoItem>> // HAPUS CARGO

    @Insert
    suspend fun insert(cargo: CargoItem) // HAPUS CARGO

    @Update
    suspend fun update(cargo: CargoItem) // HAPUS CARGO

    @Delete
    suspend fun delete(cargo: CargoItem) // HAPUS CARGO

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAll() // HAPUS CARGO
}
