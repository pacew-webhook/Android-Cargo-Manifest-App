package com.example.cargomanifestapp

import kotlinx.coroutines.flow.Flow

class BtbRepository(private val dao: BtbDao) {
    fun observeBtbs(): Flow<List<BtbEntity>> = dao.observeBtbs()

    suspend fun getPhotos(id: String): List<String> =
        dao.getPhotos(id).map { it.photoUri }

    /**
     * Menyimpan BTB + relasi foto dalam satu transaksi Room.
     * Mengembalikan foto lama agar file yang sudah tidak dipakai dapat
     * dihapus dari storage setelah transaksi sukses.
     */
    suspend fun save(btb: BtbEntity, photoUris: List<String>): List<String> {
        val oldPhotos = dao.getPhotos(btb.id).map { it.photoUri }
        dao.upsertWithPhotos(btb, photoUris)
        return oldPhotos
    }

    suspend fun delete(id: String): List<String> {
        val oldPhotos = dao.getPhotos(id).map { it.photoUri }
        dao.deleteBtb(id)
        return oldPhotos
    }
}
