package com.example.cargomanifestapp

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
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
        viewModelScope.launch {
            _query
                .debounce(250)
                .collectLatest { searchNow(it) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value.take(120)
    }

    fun load() {
        viewModelScope.launch {
            refreshStats()
            searchNow(_query.value)
        }
    }

    fun scanFolder(uri: Uri) {
        if (syncJob?.isActive == true) return

        syncJob = viewModelScope.launch {
            scanMutex.withLock {
                _busy.value = true
                _progress.value = 0
                _message.value = "Menyiapkan sinkronisasi Manifest..."

                try {
                    getApplication<Application>()
                        .getSharedPreferences("manifest_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("manifest_tree_uri", uri.toString())
                        .apply()

                    val result = importer.scanFolderTree(uri) { done, total, rows ->
                        _progress.value = done
                        _message.value = "Membaca Excel: $done/$total | Data baru: $rows"

                        // Do not query Room or run a search for every progress update.
                        // Those extra queries compete with the importer and can cause
                        // ANR/force-close on large folders. Existing database rows remain
                        // searchable because query changes are handled independently.
                    }

                    refreshStats()
                    if (_query.value.isNotBlank()) searchNow(_query.value)
                    _message.value = buildString {
                        append("Selesai: ${result.filesImported} file baru/diperbarui, ")
                        append("${result.filesSkipped} file tidak berubah, ")
                        append("${result.filesNotManifest} file bukan Manifest, ")
                        append("${result.rowsImported} baris baru.")
                        if (result.errors.isNotEmpty()) {
                            append(" ${result.errors.size} file gagal dibaca.")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _message.value = "Gagal sinkronisasi: ${e.message ?: "folder tidak dapat dibaca"}"
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

        try {
            val tokens = q.split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)

            if (tokens.isEmpty()) {
                _results.value = emptyList()
                return
            }

            val columns = listOf(
                "pti", "customer", "description", "no", "flightNo",
                "destination", "fromStation", "manifestDate", "pcs",
                "weightPerPiece", "subTotal", "sourceName"
            )

            val whereParts = ArrayList<String>()
            val args = ArrayList<Any>()

            for (token in tokens) {
                val tokenColumns = columns.joinToString(" OR ") { column ->
                    "$column LIKE ? COLLATE NOCASE"
                }
                whereParts += "($tokenColumns)"
                repeat(columns.size) { args += "%$token%" }
            }

            val sql = buildString {
                append("SELECT * FROM manifest_items WHERE ")
                append(whereParts.joinToString(" AND "))
                append(" ORDER BY ")
                append("CASE WHEN length(manifestDate) >= 10 ")
                append("THEN substr(manifestDate, 7, 4) || substr(manifestDate, 4, 2) || substr(manifestDate, 1, 2) ")
                append("ELSE '' END DESC, year DESC, id DESC LIMIT 100")
            }

            _results.value = dao.searchDynamic(
                SimpleSQLiteQuery(sql, args.toTypedArray())
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the previous result during transient Room activity.
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
        syncJob = null
        super.onCleared()
    }
}
