package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CargoViewModel(
    application: Application,
    private val cargoDao: CargoDao
) : AndroidViewModel(application) {

    private val _cargoList = MutableStateFlow<List<CargoItem>>(emptyList())
    val cargoList: StateFlow<List<CargoItem>> = _cargoList.asStateFlow()

    init {
        loadCargoData()
    }

    private fun loadCargoData() {
        viewModelScope.launch {
            cargoDao.getAllCargo().collect { list ->
                _cargoList.value = list
            }
        }
    }

    fun addCargo(
        awbNo: String,
        flightNo: String,
        pti: String,
        pcsQty: String,
        weight: String,
        subTotal: String,
        description: String,
        customer: String,
        noPag: String
    ) {
        viewModelScope.launch {
            val cleanCustomer = customer.trim().uppercase()
            val cleanDescription = description.trim().uppercase()
            val cleanNoPag = noPag.trim().uppercase()

            val existingItem = cargoList.value.find { 
                it.customer.equals(cleanCustomer, ignoreCase = true) && 
                it.description.equals(cleanDescription, ignoreCase = true) &&
                it.noPag.equals(cleanNoPag, ignoreCase = true) &&
                cleanCustomer.isNotEmpty()
            }

            if (existingItem != null) {
                val currentPcs = existingItem.pcsQty.toIntOrNull() ?: 0
                val newPcs = pcsQty.trim().toIntOrNull() ?: 0
                val updatedPcs = (currentPcs + newPcs).toString()

                val currentSubTotal = existingItem.subTotal.toDoubleOrNull() ?: 0.0
                val newSubTotal = subTotal.trim().toDoubleOrNull() ?: 0.0
                val totalCalc = currentSubTotal + newSubTotal
                val updatedSubTotal = if (totalCalc % 1.0 == 0.0) {
                    totalCalc.toLong().toString()
                } else {
                    totalCalc.toString()
                }

                val updatedItem = existingItem.copy(
                    awbNo = if (awbNo.isNotBlank()) awbNo.trim().uppercase() else existingItem.awbNo,
                    flightNo = if (flightNo.isNotBlank()) flightNo.trim().uppercase() else existingItem.flightNo,
                    pti = if (pti.isNotBlank()) pti.trim().uppercase() else existingItem.pti,
                    pcsQty = updatedPcs,
                    weight = weight.trim().ifEmpty { existingItem.weight },
                    subTotal = updatedSubTotal,
                    noPag = cleanNoPag
                )
                cargoDao.update(updatedItem)
            } else {
                cargoDao.insert(
                    CargoItem(
                        awbNo = awbNo.trim().uppercase(),
                        flightNo = flightNo.trim().uppercase(),
                        pti = pti.trim().uppercase(),
                        pcsQty = pcsQty.trim(),
                        weight = weight.trim(),
                        subTotal = subTotal.trim(),
                        description = cleanDescription,
                        customer = cleanCustomer,
                        noPag = cleanNoPag
                    )
                )
            }
        }
    }

    fun updateCargo(item: CargoItem) {
        viewModelScope.launch {
            cargoDao.update(item)
        }
    }

    fun deleteCargo(item: CargoItem) {
        viewModelScope.launch {
            cargoDao.delete(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            cargoDao.clearAll()
        }
    }

    fun exportToExcel(context: Context) {
        Toast.makeText(context, "Fitur Export Excel dipanggil", Toast.LENGTH_SHORT).show()
    }
}
