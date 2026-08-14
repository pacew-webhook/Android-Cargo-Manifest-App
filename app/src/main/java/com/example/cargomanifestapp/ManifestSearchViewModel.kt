package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    // Prevent a second automatic scan from being queued when the screen is
    // recreated while the previous synchronization is still running.
    private var syncJob: Job? = null

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
        // Search is independent from synchronization. It only reads the Room database,
        // so already-indexed data can be searched while new Excel files are still being read.
        viewModelScope.launch {
            _query
                .debounce(250)
                .collectLatest { value ->
                    searchNow(value)
                }
        }
    }

    fun setQuery(value: String) {
        // Avoid pathological queries that could make SQLite/UI work unnecessarily hard.
        _query.value = value.take(120)
    }

    fun load() {
        viewModelScope.launch {
            refreshStats()
            if (_query.value.isNotBlank()) searchNow(_query.value)
        }
    }

    fun scanFolder(uri: Uri) {
        // Ignore duplicate requests while a scan is already active.
        if (syncJob?.isActive == true) return

        syncJob = viewModelScope.launch {
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
                        _message.value = "Membaca file Excel: $done/$total | Data baru: $rows"

                        // Refresh the counters periodically without rebuilding the result list.
                        // Search remains available throughout the import.
                        // Refresh counters/results in small batches. The Room DB remains
                        // searchable while synchronization is running.
                        if (done % 10 == 0 || done == total) {
                            refreshStats()
                            if (_query.value.isNotBlank()) searchNow(_query.value)
                        }
                    }

                    refreshStats()
                    _message.value = buildString {
                        append("Selesai: ${result.filesImported} file baru/diperbarui, ")
                        append("${result.filesSkipped} file sudah tersimpan, ")
                        append("${result.rowsImported} baris baru, ${result.filesIgnored} file non-Manifest dilewati.")
                        if (result.errors.isNotEmpty()) {
                            append(" Gagal: ${result.errors.size} file.")
                        }
                    }
                    if (_query.value.isNotBlank()) searchNow(_query.value)
                } catch (e: CancellationException) {
                    // Cancellation is normal when ViewModel is destroyed. Do not turn it
                    // into an error and, importantly, do not swallow it.
                    throw e
                } catch (e: Exception) {
                    _message.value = "Gagal: ${e.message ?: "folder tidak dapat dibaca"}"
                } finally {
                    _busy.value = false
                }
            }
        }
    }

    fun scanSavedFolder() {
        if (syncJob?.isActive == true) return

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
        if (q.isBlank()) {
            _results.value = emptyList()
            return
        }

        // LIMIT is enforced in DAO so the UI never tries to compose thousands of cards.
        // The query can safely run while the importer is inserting more rows.
        try {
            _results.value = dao.search(q)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A transient database read error during synchronization should not crash the app.
            // Keep the last valid result set instead.
        }
    }

    fun clearDatabase() {
        if (syncJob?.isActive == true) return

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

    override fun onCleared() {
        // viewModelScope will cancel the job. Explicitly clear the reference so a
        // recreated screen cannot treat an old completed job as active.
        syncJob = null
        super.onCleared()
    }
}
