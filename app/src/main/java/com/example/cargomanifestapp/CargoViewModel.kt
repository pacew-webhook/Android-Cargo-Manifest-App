package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileOutputStream

class CargoViewModel(application: Application) : AndroidViewModel(application) {

    private val _cargoList = MutableStateFlow<List<CargoItem>>(emptyList())
    val cargoList: StateFlow<List<CargoItem>> = _cargoList.asStateFlow()

    private val _importedAwbNo = MutableStateFlow("")
    val importedAwbNo: StateFlow<String> = _importedAwbNo.asStateFlow()

    private val _importedFlightNo = MutableStateFlow("")
    val importedFlightNo: StateFlow<String> = _importedFlightNo.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredCargoList: StateFlow<List<CargoItem>> = combine(cargoList, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter {
            it.pti.contains(query, ignoreCase = true) ||
            it.customer.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.noPag.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalPcs: StateFlow<Int> = cargoList.map { list ->
        list.sumOf { it.pcsQty.toIntOrNull()?: 0 }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val totalWeight: StateFlow<Double> = cargoList.map { list ->
        list.sumOf { parseDoubleOrZero(it.subTotal) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ==========================================
    // 1. KELOLA DATA LOCAL
    // ==========================================
    fun addCargo(
        awbNo: String = "", flightNo: String = "", pti: String = "",
        pcsQty: String = "", weight: String = "", subTotal: String = "",
        description: String = "", customer: String = "", noPag: String = ""
    ) {
        val currentList = _cargoList.value.toMutableList()

        val isExactDuplicate = currentList.any {
            it.pti.equals(pti, ignoreCase = true) &&
            it.description.equals(description, ignoreCase = true) &&
            it.pcsQty.equals(pcsQty, ignoreCase = true) &&
            it.customer.equals(customer, ignoreCase = true) &&
            it.subTotal.equals(subTotal, ignoreCase = true) &&
            it.noPag.equals(noPag, ignoreCase = true)
        }

        if (isExactDuplicate) {
            Toast.makeText(getApplication(), "Data persis sama sudah ada di list!", Toast.LENGTH_SHORT).show()
            return
        }

        val newItem = CargoItem(
            id = System.currentTimeMillis(),
            awbNo = awbNo, flightNo = flightNo, pti = pti,
            pcsQty = pcsQty, weight = weight, subTotal = subTotal,
            description = description, customer = customer, noPag = noPag
        )
        currentList.add(newItem)
        _cargoList.value = currentList
        Toast.makeText(getApplication(), "Data Berhasil Ditambahkan", Toast.LENGTH_SHORT).show()
    }

    fun updateCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index!= -1) {
            currentList[index] = item
            _cargoList.value = currentList
            Toast.makeText(getApplication(), "Data Berhasil Diperbarui", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteCargo(item: CargoItem) {
        val currentList = _cargoList.value.toMutableList()
        currentList.removeAll { it.id == item.id }
        _cargoList.value = currentList
    }

    fun clearAll() {
        _cargoList.value = emptyList()
        _importedAwbNo.value = ""
        _importedFlightNo.value = ""
    }

    // ==========================================
    // 2. IMPORT DATA FROM EXCEL
    // ==========================================
    fun importFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri
