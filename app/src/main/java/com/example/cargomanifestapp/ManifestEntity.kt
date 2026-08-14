package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manifest_items",
    indices = [
        Index(value = ["pti"]),
        Index(value = ["customer"]),
        Index(value = ["description"]),
        Index(value = ["manifestDate"]),
        Index(value = ["year"]),
        Index(value = ["sourceKey", "rowNumber"], unique = true)
    ]
)
data class ManifestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceKey: String,
    val sourceName: String,
    val sourceLastModified: Long,
    val sheetName: String,
    val rowNumber: Int,
    val no: String = "",
    val pti: String = "",
    val pcs: String = "",
    val weightPerPiece: String = "",
    val subTotal: String = "",
    val description: String = "",
    val customer: String = "",
    val manifestDate: String = "",
    val flightNo: String = "",
    val fromStation: String = "",
    val destination: String = "",
    val year: Int = 0
)
