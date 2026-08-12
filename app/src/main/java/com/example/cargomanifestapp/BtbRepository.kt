package com.example.cargomanifestapp

import kotlinx.coroutines.flow.Flow

class BtbRepository(private val dao: BtbDao) {
    fun observeAll(): Flow<List<BtbEntity>> = dao.observeAll()

    suspend fun save(
        btb: BtbEntity,
        photoUris: List<String>
    ): Long {
        val id = dao.insertBtb(btb)
        if (photoUris.isNotEmpty()) {
            dao.insertPhotos(photoUris.map { BtbPhotoEntity(btbId = id, photoUri = it) })
        }
        return id
    }

    suspend fun photos(id: Long): List<BtbPhotoEntity> = dao.getPhotos(id)

    suspend fun delete(id: Long) = dao.deleteBtb(id)
}
