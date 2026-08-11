package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cargo_table")
data class CargoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Field Bukti Timbang
    val awbNo: String = "",
    val flightNo: String = "",
    val pti: String = "",
    val description: String = "",

    // Field Stowing + Export
    val noPag: String,
    val customer: String,
    val pcsQty: String,   // dulunya qty
    val weight: String,   // dulunya qtyWt
    val subTotal: String
)
