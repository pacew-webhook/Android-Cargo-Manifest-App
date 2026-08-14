package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManifestSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ManifestDatabase.getDatabase(application).manifestDao()
    private val importer = ManifestExcelImporter(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<List<ManifestEntity>>(emptyList())
    val results: StateFlow<List<ManifestEntity>> = _results.asStateFlow()
    private val _totalRows = MutableStateFlow(0)
    val totalRows: StateFlow<Int> = _totalRows.asStateFlow()
    private val _fileCount = MutableStateFlow(0)
    val fileCount: StateFlow<Int> = _fileCount.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    fun setQuery(value: String) {
        _query.value = value
        search(value)
    }

    fun load() {
        viewModelScope.launch {
            _totalRows.value = dao.count()
            _fileCount.value = dao.fileCount()
            searchNow(_query.value)
        }
    }

    fun scanFolder(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "Membaca semua file Excel..."
            try {
                getApplication<Application>().getSharedPreferences("manifest_settings", Context.MODE_PRIVATE)
                    .edit().putString("manifest_tree_uri", uri.toString()).apply()
                val result = importer.scanFolderTree(uri)
                _totalRows.value = dao.count()
                _fileCount.value = dao.fileCount()
                _message.value = buildString {
                    append("Selesai: ${result.filesImported} file baru/diperbarui, ")
                    append("${result.filesSkipped} file sudah tersimpan, ")
                    append("${result.rowsImported} baris baru.")
                    if (result.errors.isNotEmpty()) append(" Gagal: ${result.errors.size} file.")
                }
                searchNow(_query.value)
            } catch (e: Exception) {
                _message.value = "Gagal: ${e.message ?: "folder tidak dapat dibaca"}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun scanSavedFolder() {
        val value = getApplication<Application>().getSharedPreferences("manifest_settings", Context.MODE_PRIVATE)
            .getString("manifest_tree_uri", null) ?: return
        runCatching { scanFolder(Uri.parse(value)) }
    }

    private fun search(value: String) {
        viewModelScope.launch { searchNow(value) }
    }

    private suspend fun searchNow(value: String) {
        _results.value = dao.search(value.trim())
    }

    fun clearDatabase() {
        viewModelScope.launch {
            dao.clearItems()
            dao.clearFiles()
            _totalRows.value = 0
            _fileCount.value = 0
            _results.value = emptyList()
            _message.value = "Database Manifest dikosongkan."
        }
    }
}
