package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manifest_files")
data class ManifestFileEntity(
    @PrimaryKey val sourceKey: String,
    val sourceName: String,
    val lastModified: Long,
    val rowCount: Int,
    val importedAt: Long
)
