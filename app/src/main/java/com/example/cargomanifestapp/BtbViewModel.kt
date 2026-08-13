package com.example.cargomanifestapp

import org.json.JSONArray
import org.json.JSONObject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


private fun btbWeightsToJson(weights: List<Any>): String {
    val array = JSONArray()
    for (item in weights) {
        val obj = JSONObject()
        val cls = item::class
        fun value(name: String): Any? = try {
            cls.members.firstOrNull { it.name == name }?.call(item)
        } catch (_: Exception) { null }

        value("beratAsli")?.let { obj.put("beratAsli", it.toString().toDoubleOrNull() ?: 0.0) }
        value("beratPembulatan")?.let { obj.put("beratPembulatan", it.toString().toDoubleOrNull() ?: 0.0) }
        value("beratFinal")?.let { obj.put("beratFinal", it.toString().toDoubleOrNull() ?: 0.0) }
        value("weight")?.let { obj.put("weight", it.toString().toDoubleOrNull() ?: 0.0) }
        value("kg")?.let { obj.put("kg", it.toString().toDoubleOrNull() ?: 0.0) }
        array.put(obj)
    }
    return array.toString()
}

class BtbViewModel(private val repository: BtbRepository) : ViewModel() {
    val btbs = repository.observeBtbs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        data: BtbFormData,
        onDone: (oldPhotoUris: List<String>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val photos = org.json.JSONArray().apply {
                data.photoUris.forEach { put(it) }
            }

            val oldPhotos = repository.save(
                BtbEntity(
                    id = data.id,
                    hariTanggal = data.hariTanggal,
                    customerName = data.customerName,
                    trademarks = data.trademarks,
                    jenisBarang = data.jenisBarang,
                    daftarTimbanganJson = btbWeightsToJson(data.daftarTimbangan),
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
