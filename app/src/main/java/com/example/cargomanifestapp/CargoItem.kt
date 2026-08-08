package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cargo_table")
data class CargoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pti: String,
    val pcsQty: String,
    val weight: String,
    val subTotal: String,
    val description: String,
    val customer: String,
    val noPag: String
)
