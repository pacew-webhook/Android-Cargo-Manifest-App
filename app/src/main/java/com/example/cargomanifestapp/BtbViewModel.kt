package com.example.cargomanifestapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

class BtbViewModel(private val repository: BtbRepository) : ViewModel() {
    val btbs = repository.observeBtbs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        data: BtbFormData,
        onDone: (oldPhotoUris: List<String>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val weights = JSONArray()
            data.daftarTimbangan.forEach { weights.put(it) }

            // photoUrisJson dipertahankan untuk kompatibilitas data lama.
            // Sumber relasi foto tetap btb_photo_table.
            val photos = JSONArray()
            data.photoUris.forEach { photos.put(it) }

            val oldPhotos = repository.save(
                BtbEntity(
                    id = data.id,
                    hariTanggal = data.hariTanggal,
                    customerName = data.customerName,
                    trademarks = data.trademarks,
                    jenisBarang = data.jenisBarang,
                    daftarTimbanganJson = weights.toString(),
                    photoUrisJson = photos.toString()
                ),
                data.photoUris
            )
            onDone(oldPhotos)
        }
    }

    fun delete(
        id: String,
        onDone: (oldPhotoUris: List<String>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val oldPhotos = repository.delete(id)
            onDone(oldPhotos)
        }
    }
}
