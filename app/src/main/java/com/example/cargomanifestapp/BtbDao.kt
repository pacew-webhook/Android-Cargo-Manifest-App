package com.example.cargomanifestapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BtbDao {
    @Insert
    suspend fun insertBtb(btb: BtbEntity): Long

    @Insert
    suspend fun insertPhotos(photos: List<BtbPhotoEntity>)

    @Query("SELECT * FROM btb ORDER BY id DESC")
    fun observeAll(): Flow<List<BtbEntity>>

    @Query("SELECT * FROM btb WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BtbEntity?

    @Query("SELECT * FROM btb_photo WHERE btbId = :btbId ORDER BY id ASC")
    suspend fun getPhotos(btbId: Long): List<BtbPhotoEntity>

    @Query("DELETE FROM btb WHERE id = :id")
    suspend fun deleteBtb(id: Long)

    @Query("DELETE FROM btb_photo WHERE btbId = :btbId")
    suspend fun deletePhotos(btbId: Long)
}
