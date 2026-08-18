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

    /**
     * Mencari baris Manifest yang sebelumnya dibuat dari data Stowing.
     * Semua field utama Stowing dibandingkan agar saat edit data tidak membuat
     * baris Manifest baru/duplikat.
     */
    @Query(
        """
        SELECT * FROM cargo_table
        WHERE pti = :pti
          AND pcsQty = :pcsQty
          AND weight = :weight
          AND subTotal = :subTotal
          AND description = :description
          AND customer = :customer
          AND noPag = :noPag
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun findExactCargo(
        pti: String,
        pcsQty: String,
        weight: String,
        subTotal: String,
        description: String,
        customer: String,
        noPag: String
    ): CargoItem?

    @Query(
        """
        DELETE FROM cargo_table
        WHERE id = (
            SELECT id FROM cargo_table
            WHERE pti = :pti
              AND pcsQty = :pcsQty
              AND weight = :weight
              AND subTotal = :subTotal
              AND description = :description
              AND customer = :customer
              AND noPag = :noPag
            ORDER BY id DESC
            LIMIT 1
        )
        """
    )
    suspend fun deleteExactCargo(
        pti: String,
        pcsQty: String,
        weight: String,
        subTotal: String,
        description: String,
        customer: String,
        noPag: String
    )

    @Query("DELETE FROM cargo_table")
    suspend fun deleteAllCargo()
}
