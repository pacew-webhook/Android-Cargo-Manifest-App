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

    fun save(data: BtbFormData, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val weights = JSONArray()
            data.daftarTimbangan.forEach { weights.put(it) }
            val photos = JSONArray()
            data.photoUris.forEach { photos.put(it) }
            repository.save(
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
            onDone()
        }
    }

    fun delete(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }
}
