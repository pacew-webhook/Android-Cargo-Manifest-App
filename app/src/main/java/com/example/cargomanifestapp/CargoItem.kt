package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cargo_table")
data class CargoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val awbNo: String,
    val flightNo: String,
    val pti: String,
    val qty: String,      // HARUS SAMA
    val qtyWt: String,    // HARUS SAMA
    val subTotal: String,
    val description: String,
    val customer: String,
    val noPag: String
)
