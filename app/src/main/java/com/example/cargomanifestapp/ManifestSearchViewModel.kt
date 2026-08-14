package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManifestSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ManifestDatabase.getDatabase(application).manifestDao()
    private val importer = ManifestExcelImporter(application)
    private val scanMutex = Mutex()

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

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    init {
        // One search job at a time. Fast typing cancels the previous query instead of
        // creating dozens of concurrent Room queries that compete with the importer.
        viewModelScope.launch {
            _query
                .debounce(250)
                .collectLatest { value ->
                    searchNow(value)
                }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun load() {
        viewModelScope.launch {
            refreshStats()
            if (_query.value.isNotBlank()) searchNow(_query.value)
        }
    }

    fun scanFolder(uri: Uri) {
        viewModelScope.launch {
            scanMutex.withLock {
                _busy.value = true
                _progress.value = 0
                _message.value = "Menyiapkan database Manifest..."

                try {
                    getApplication<Application>()
                        .getSharedPreferences("manifest_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("manifest_tree_uri", uri.toString())
                        .apply()

                    val result = importer.scanFolderTree(uri) { done, _, rows ->
                        _progress.value = done
                        _message.value = "Membaca file Excel: $done | Data baru: $rows"
                    }

                    refreshStats()
                    _message.value = buildString {
                        append("Selesai: ${result.filesImported} file baru/diperbarui, ")
                        append("${result.filesSkipped} file sudah tersimpan, ")
                        append("${result.rowsImported} baris baru.")
                        if (result.errors.isNotEmpty()) {
                            append(" Gagal: ${result.errors.size} file.")
                        }
                    }
                    if (_query.value.isNotBlank()) searchNow(_query.value)
                } catch (e: Exception) {
                    _message.value = "Gagal: ${e.message ?: "folder tidak dapat dibaca"}"
                } finally {
                    _busy.value = false
                }
            }
        }
    }

    fun scanSavedFolder() {
        val value = getApplication<Application>()
            .getSharedPreferences("manifest_settings", Context.MODE_PRIVATE)
            .getString("manifest_tree_uri", null)
            ?: return
        scanFolder(Uri.parse(value))
    }

    private suspend fun refreshStats() {
        _totalRows.value = dao.count()
        _fileCount.value = dao.fileCount()
    }

    private suspend fun searchNow(value: String) {
        val q = value.trim()

        // Do not load the first 100 database records while the user is not searching.
        // This keeps the UI light during a large background import.
        if (q.isBlank()) {
            _results.value = emptyList()
            return
        }

        _results.value = dao.search(q)
    }

    fun clearDatabase() {
        viewModelScope.launch {
            scanMutex.withLock {
                dao.clearItems()
                dao.clearFiles()
                _totalRows.value = 0
                _fileCount.value = 0
                _results.value = emptyList()
                _message.value = "Database Manifest dikosongkan."
            }
        }
    }
}
