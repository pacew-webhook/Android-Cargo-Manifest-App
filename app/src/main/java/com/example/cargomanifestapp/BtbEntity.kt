package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "btb_table")
data class BtbEntity(
    @PrimaryKey val id: String,
    val hariTanggal: String,
    val customerName: String,
    val trademarks: String,
    val jenisBarang: String,
    val daftarTimbanganJson: String,
    /**
     * Dipertahankan untuk kompatibilitas dengan database v2/v3.
     * Untuk operasi baru, relasi foto diambil dari btb_photo_table.
     */
    val photoUrisJson: String
)
