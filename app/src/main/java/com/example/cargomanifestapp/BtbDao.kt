package com.example.cargomanifestapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BtbDao {
    @Query("SELECT * FROM btb_table ORDER BY rowid DESC")
    fun observeBtbs(): Flow<List<BtbEntity>>

    @Query("SELECT * FROM btb_photo_table WHERE btbId = :btbId ORDER BY id ASC")
    suspend fun getPhotos(btbId: String): List<BtbPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBtb(btb: BtbEntity)

    @Insert
    suspend fun insertPhotos(photos: List<BtbPhotoEntity>)

    @Query("DELETE FROM btb_photo_table WHERE btbId = :btbId")
    suspend fun deletePhotos(btbId: String)

    @Query("DELETE FROM btb_table WHERE id = :btbId")
    suspend fun deleteBtb(btbId: String)

    @Transaction
    suspend fun upsertWithPhotos(btb: BtbEntity, photos: List<String>) {
        deletePhotos(btb.id)
        upsertBtb(btb)
        if (photos.isNotEmpty()) {
            insertPhotos(photos.map { BtbPhotoEntity(btbId = btb.id, photoUri = it) })
        }
    }
}
