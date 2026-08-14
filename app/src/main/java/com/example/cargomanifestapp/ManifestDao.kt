package com.example.cargomanifestapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ManifestDao {

    @Query("""
        SELECT * FROM manifest_items
        ORDER BY
            CASE
                WHEN length(manifestDate) >= 10
                THEN substr(manifestDate, 7, 4) || substr(manifestDate, 4, 2) || substr(manifestDate, 1, 2)
                ELSE ''
            END DESC,
            year DESC,
            id DESC
        LIMIT 300
    """)
    fun observeAll(): Flow<List<ManifestEntity>>

    @RawQuery
    suspend fun searchDynamic(query: SupportSQLiteQuery): List<ManifestEntity>

    @Query("SELECT COUNT(*) FROM manifest_items")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM manifest_files")
    suspend fun fileCount(): Int

    @Query("SELECT * FROM manifest_files WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getFile(sourceKey: String): ManifestFileEntity?

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM manifest_items
            WHERE sourceKey = :sourceKey
              AND (subTotal LIKE '=%%' OR subTotal LIKE '%*%')
        )
    """)
    suspend fun hasFormulaSubTotal(sourceKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: ManifestFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ManifestEntity>)

    @Query("DELETE FROM manifest_items WHERE sourceKey = :sourceKey")
    suspend fun deleteItemsForSource(sourceKey: String)

    @Query("DELETE FROM manifest_files WHERE sourceKey = :sourceKey")
    suspend fun deleteFile(sourceKey: String)

    @Query("SELECT sourceKey FROM manifest_files")
    suspend fun getAllSourceKeys(): List<String>

    @Transaction
    suspend fun replaceFileData(
        sourceKey: String,
        file: ManifestFileEntity,
        items: List<ManifestEntity>
    ) {
        deleteItemsForSource(sourceKey)
        if (items.isNotEmpty()) insertAll(items)
        upsertFile(file)
    }

    @Query("DELETE FROM manifest_items")
    suspend fun clearItems()

    @Query("DELETE FROM manifest_files")
    suspend fun clearFiles()
}
