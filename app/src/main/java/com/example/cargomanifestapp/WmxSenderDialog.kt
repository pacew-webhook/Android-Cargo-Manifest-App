package com.example.cargomanifestapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import org.json.JSONArray
import java.util.Locale

/** Penyimpanan daftar PENGIRIM yang pernah digunakan untuk WMX. */
object WmxSenderHistory {
    private const val PREFS = "wmx_sender_history"
    private const val KEY = "senders"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length())
                .map { json.optString(it).trim().uppercase(Locale.getDefault()) }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, values: Collection<String>) {
        val merged = (load(context) + values)
            .map { it.trim().uppercase(Locale.getDefault()) }
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(100)
        val json = JSONArray()
        merged.forEach { json.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }
}

/**
 * Dialog untuk melengkapi PENGIRIM sebelum foto WMX dibuat.
 * Customer dari data Cargo otomatis menjadi PENERIMA.
 */
@Composable
fun WmxSenderDialog(
    groups: List<ManifestGroup>,
    senderByGroupKey: Map<String, String>,
    savedSenders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val draft = remember(groups, senderByGroupKey) {
        mutableStateMapOf<String, String>().apply {
            groups.forEach { group ->
                this[group.groupKey] = senderByGroupKey[group.groupKey].orEmpty()
            }
        }
    }
    var errorText by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Isi Pengirim WMX", fontWeight = FontWeight.Bold)
                Text(
                    "Customer otomatis menjadi PENERIMA. Isi PENGIRIM sebelum foto dibuat.",
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groups.forEachIndexed { index, group ->
                    val item = group.summary
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "${index + 1}. ${item.pti.ifBlank { "Tanpa PTI" }} • Penerima: ${item.customer.ifBlank { "-" }}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            "${item.description.ifBlank { "Tanpa description" }} • Koli: ${item.pcsQty.ifBlank { "0" }}",
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = draft[group.groupKey].orEmpty(),
                            onValueChange = {
                                draft[group.groupKey] = it.uppercase(Locale.getDefault())
                                errorText = null
                            },
                            label = { Text("PENGIRIM") },
                            placeholder = { Text("Contoh: PT ABC") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (savedSenders.isNotEmpty()) {
                            Text(
                                "Pengirim tersimpan — ketuk untuk memilih:",
                                fontSize = 11.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(savedSenders, key = { it }) { sender ->
                                    FilterChip(
                                        selected = draft[group.groupKey].orEmpty() == sender,
                                        onClick = {
                                            draft[group.groupKey] = sender
                                            errorText = null
                                        },
                                        label = { Text(sender, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                errorText?.let {
                    Text(
                        it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val missing = groups.withIndex().filter { (_, group) ->
                        draft[group.groupKey].orEmpty().trim().isBlank()
                    }
                    if (missing.isNotEmpty()) {
                        errorText = "PENGIRIM belum diisi untuk ${missing.size} data. Lengkapi sebelum lanjut."
                    } else {
                        onConfirm(draft.mapValues { it.value.trim().uppercase(Locale.getDefault()) })
                    }
                }
            ) {
                Text("Lanjut Preview")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
