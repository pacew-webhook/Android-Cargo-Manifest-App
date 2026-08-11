package com.example.cargomanifestapp

data class CargoItem(
    val id: Long, // UDAH LONG
    val awbNo: String = "",
    val flightNo: String = "",
    val pti: String = "",
    val pcsQty: String = "",
    val weight: String = "",
    val subTotal: String = "",
    val description: String = "",
    val customer: String = "",
    val noPag: String = ""
)
