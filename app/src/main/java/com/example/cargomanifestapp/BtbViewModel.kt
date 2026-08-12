package com.example.cargomanifestapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BtbViewModel(private val repository: BtbRepository) : ViewModel() {
    val btbs = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(btb: BtbEntity, photoUris: List<String>, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.save(btb, photoUris)
            onSaved(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
