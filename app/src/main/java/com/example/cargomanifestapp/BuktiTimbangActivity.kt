package com.example.cargomanifestapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope 
import com.example.cargomanifestapp.ExcelHelper
import kotlinx.coroutines.launch
import java.io.File

class BuktiTimbangActivity : AppCompatActivity() {

    private lateinit var db: CargoDatabase
    private lateinit var excelHelper: ExcelHelper

    private lateinit var etAwb: EditText
    private lateinit var etFlight: EditText
    private lateinit var etPti: EditText
    private lateinit var etQty: EditText
    private lateinit var etQtyWt: EditText
    private lateinit var etBerat: EditText
    private lateinit var etDesc: EditText
    private lateinit var etCustomer: EditText
    private lateinit var etNoPag: EditText
    private lateinit var ivFoto: ImageView

    private val FOLDER_NAME = "Manifest"
    private val PERMISSION_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bukti_timbang)

        db = CargoDatabase.getInstance(this)
        excelHelper = ExcelHelper(this, db)

        requestStoragePermission()
        initViews()
        setListeners()
    }

    private fun initViews() {
        etAwb = findViewById(R.id.etAwb)
        etFlight = findViewById(R.id.etFlight)
        etPti = findViewById(R.id.etPti)
        etQty = findViewById(R.id.etQty)
        etQtyWt = findViewById(R.id.etQtyWt)
        etBerat = findViewById(R.id.etBerat)
        etDesc = findViewById(R.id.etDesc)
        etCustomer = findViewById(R.id.etCustomer)
        etNoPag = findViewById(R.id.etNoPag)
        ivFoto = findViewById(R.id.ivFoto)
    }

    private fun setListeners() {
        findViewById<Button>(R.id.btnSimpan).setOnClickListener { simpanData() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { showFilePickerDialog() }
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportKeFolder() }
        findViewById<Button>(R.id.btnAmbilFoto).setOnClickListener {
            Toast.makeText(this, "Fitur Kamera belum dipasang", Toast.LENGTH_SHORT).show()
        }
    }

    // 1. SIMPAN DATA KE ROOM
    private fun simpanData() {
    if (etAwb.text.isEmpty() || etPti.text.isEmpty()) {
        Toast.makeText(this, "AWB dan PTI wajib diisi", Toast.LENGTH_SHORT).show()
        return
    }

    val cargoItem = CargoItem(
        awbNo = etAwb.text.toString(),
        flightNo = etFlight.text.toString(),
        pti = etPti.text.toString(),
        pcsQty = etQty.text.toString(), // UDAH GANTI
        weight = etQtyWt.text.toString(), // UDAH GANTI
        subTotal = etBerat.text.toString(),
        description = etDesc.text.toString(),
        customer = etCustomer.text.toString(),
        noPag = etNoPag.text.toString()
    )
    lifecycleScope.launch {
        db.cargoDao().insertCargo(cargoItem)
        Toast.makeText(this@BuktiTimbangActivity, "Data Tersimpan", Toast.LENGTH_SHORT).show()
        clearForm()
    }
    }

    // 2. EXPORT KE FOLDER Documents/Manifest
    private fun exportKeFolder() {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()

        val file = File(folder, "Manifest_${System.currentTimeMillis()}.xlsx")

        lifecycleScope.launch {
            excelHelper.exportExcelToFile(file)
            FileProvider.getUriForFile(this@BuktiTimbangActivity, "${packageName}.provider", file)
            Toast.makeText(this@BuktiTimbangActivity, "Export Berhasil ke:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    // 3. IMPORT DENGAN PILIH FILE DARI FOLDER
    private fun showFilePickerDialog() {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)
        if (!folder.exists()) {
            Toast.makeText(this, "Buat folder $FOLDER_NAME di Documents dulu", Toast.LENGTH_LONG).show()
            return
        }

        val files = folder.listFiles { f -> f.extension.equals("xlsx", true) }
        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "Gak ada file.xlsx di folder $FOLDER_NAME", Toast.LENGTH_LONG).show()
            return
        }

        val fileNames = files.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih File untuk Import")
            .setItems(fileNames) { _, which ->
                importDariFile(files[which])
            }
            .show()
    }

    private fun importDariFile(file: File) {
        lifecycleScope.launch {
            excelHelper.importExcelFromFile(file)
            Toast.makeText(this@BuktiTimbangActivity, "Import ${file.name} Selesai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearForm() {
        etAwb.text.clear(); etFlight.text.clear(); etPti.text.clear()
        etQty.text.clear(); etQtyWt.text.clear(); etBerat.text.clear()
        etDesc.text.clear(); etCustomer.text.clear(); etNoPag.text.clear()
    }

    private fun requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_CODE)
        }
    }
}
