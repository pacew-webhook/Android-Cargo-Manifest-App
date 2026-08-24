package com.example.cargomanifestapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun WmxSenderDialog(
    groups: List<ManifestGroup>,
    savedSenders: List<String>,
    initialSenders: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val draft = remember { mutableStateMapOf<String, String>() }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Satu FocusRequester untuk setiap kolom PENGIRIM.
    // Tombol Enter akan memindahkan fokus ke kolom berikutnya.
    val focusRequesters = remember(groups) {
        List(groups.size) { FocusRequester() }
    }

    LaunchedEffect(groups, initialSenders) {
        draft.clear()
        groups.forEach { group -> draft[group.groupKey] = initialSenders[group.groupKey].orEmpty() }
        errorText = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PENGIRIM WMX", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Customer otomatis menjadi PENERIMA. Isi PENGIRIM, atau pilih nama yang pernah digunakan.",
                    fontSize = 13.sp
                )
                groups.forEachIndexed { index, group ->
                    Column(Modifier.fillMaxWidth()) {
                        Text("${index + 1}. ${group.summary.pti} • Penerima: ${group.summary.customer}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = draft[group.groupKey].orEmpty(),
                            onValueChange = { value ->
                                draft[group.groupKey] = value.uppercase(Locale.getDefault())
                                errorText = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters.getOrNull(index) ?: FocusRequester())
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                        val next = index + 1
                                        if (next < focusRequesters.size) {
                                            focusRequesters[next].requestFocus()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (index < groups.lastIndex) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    val next = index + 1
                                    if (next < focusRequesters.size) {
                                        focusRequesters[next].requestFocus()
                                    }
                                },
                                onDone = { /* tetap di kolom terakhir */ }
                            ),
                            label = { Text("PENGIRIM") }
                        )
                        if (savedSenders.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Pengirim tersimpan", fontSize = 11.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(savedSenders) { sender ->
                                    OutlinedButton(onClick = { draft[group.groupKey] = sender.uppercase(Locale.getDefault()) }) {
                                        Text(sender, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                errorText?.let { Text(it, fontSize = 12.sp) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        confirmButton = {
            Button(onClick = {
                val missing = groups.count { draft[it.groupKey].orEmpty().trim().isBlank() }
                if (missing > 0) {
                    errorText = "PENGIRIM belum diisi untuk $missing data."
                } else {
                    onConfirm(draft.mapValues { it.value.trim().uppercase(Locale.getDefault()) })
                }
            }) { Text("Lanjut Preview") }
        }
    )
}
