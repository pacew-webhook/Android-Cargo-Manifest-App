package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "btb")
data class BtbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tanggal: String,
    val customer: String,
    val trademark: String,
    val jenisBarang: String,
    val totalBerat: Double
)
