package com.example.cargomanifestapp

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "btb_photo_table",
    foreignKeys = [
        ForeignKey(
            entity = BtbEntity::class,
            parentColumns = ["id"],
            childColumns = ["btbId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("btbId")]
)
data class BtbPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val btbId: String,
    val photoUri: String
)
