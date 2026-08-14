package com.example.cargomanifestapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ManifestDao {
    @Query("SELECT * FROM manifest_items ORDER BY id DESC")
    fun observeAll(): Flow<List<ManifestEntity>>

    @Query("SELECT * FROM manifest_items WHERE " +
            "(:query = '' OR pti LIKE '%' || :query || '%' OR customer LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR no LIKE '%' || :query || '%' OR flightNo LIKE '%' || :query || '%' OR destination LIKE '%' || :query || '%' OR fromStation LIKE '%' || :query || '%') " +
            "ORDER BY year DESC, id DESC LIMIT 300")
    suspend fun search(query: String): List<ManifestEntity>

    @Query("SELECT COUNT(*) FROM manifest_items")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM manifest_files")
    suspend fun fileCount(): Int

    @Query("SELECT * FROM manifest_files WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getFile(sourceKey: String): ManifestFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: ManifestFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ManifestEntity>)

    @Query("DELETE FROM manifest_items WHERE sourceKey = :sourceKey")
    suspend fun deleteItemsForSource(sourceKey: String)

    @Query("DELETE FROM manifest_files WHERE sourceKey = :sourceKey")
    suspend fun deleteFile(sourceKey: String)

    @Query("DELETE FROM manifest_items")
    suspend fun clearItems()

    @Query("DELETE FROM manifest_files")
    suspend fun clearFiles()
}
