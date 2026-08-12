package com.example.cargomanifestapp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BtbRepository(private val dao: BtbDao) {
    fun observeBtbs(): Flow<List<BtbEntity>> = dao.observeBtbs()

    suspend fun getPhotos(id: String): List<String> =
        dao.getPhotos(id).map { it.photoUri }

    suspend fun save(btb: BtbEntity, photoUris: List<String>) =
        dao.upsertWithPhotos(btb, photoUris)

    suspend fun delete(id: String) = dao.deleteBtb(id)
}
