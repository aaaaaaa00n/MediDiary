package com.medidiary.app.ui.timeline

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.RecordType
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MessageBanner
import com.medidiary.app.ui.theme.MedicineBlue
import com.medidiary.app.ui.theme.MedicineBlueContainer
import com.medidiary.app.ui.theme.SymptomAmber
import com.medidiary.app.ui.theme.SymptomAmberContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Screen 4 — Timeline & History (CLAUDE.md §7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(vm: TimelineViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    val preset by vm.preset.collectAsStateWithLifecycle()
    val type by vm.typeFilter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DiaryRecord?>(null) }
    var deleting by remember { mutableStateOf<DiaryRecord?>(null) }

    val dayFmt = remember { SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()) }
    val shortFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val grouped = remember(records) { records.groupBy { TimelineViewModel.startOfDay(it.loggedDate) }.toSortedMap(compareByDescending { it }) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Timeline") },
            actions = {
                IconButton(onClick = { vm.export(pdf = true) { context.startActivity(it) } }) {
                    Icon(Icons.Filled.PictureAsPdf, "Export PDF")
                }
                IconButton(onClick = { vm.export(pdf = false) { context.startActivity(it) } }) {
                    Icon(Icons.Filled.TableChart, "Export CSV")
                }
            },
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangePreset.entries.forEach { p ->
                    FilterChip(
                        selected = preset == p,
                        onClick = { if (p == RangePreset.CUSTOM) showPicker = true else vm.selectPreset(p) },
                        label = {
                            Text(
                                if (p == RangePreset.CUSTOM && preset == p) "${shortFmt.format(Date(range.from))} – ${shortFmt.format(Date(range.to))}"
                                else p.label,
                            )
                        },
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilter.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { vm.typeFilter.value = t }, label = { Text(t.label) })
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search medicine or symptom") },
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                MessageBanner(it, BannerKind.INFO)
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (records.isEmpty()) {
                item {
                    Text(
                        "No entries for this period. A missed check-in means \"no entry recorded\", not \"no medicine taken\".",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    Text(dayFmt.format(Date(day)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(list, key = { it.id }) { r ->
                    TimelineCard(r, timeFmt.format(Date(r.loggedDate)), onEdit = { editing = r }, onDelete = { deleting = r })
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val s = state.selectedStartDateMillis!!
                        vm.setCustomRange(s, state.selectedEndDateMillis ?: s)
                        showPicker = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, Modifier.weight(1f))
        }
    }

    editing?.let { r -> EditDialog(r, onDismiss = { editing = null }, onSave = { vm.update(it); editing = null }) }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete entry?") },
            text = { Text("\"${r.originalName}\" will be removed from this phone permanently.") },
            confirmButton = { TextButton(onClick = { vm.delete(r); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimelineCard(r: DiaryRecord, time: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val med = r.type == RecordType.MEDICINE
    Card(colors = CardDefaults.cardColors(containerColor = if (med) MedicineBlueContainer else SymptomAmberContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (med) "💊 " else "🤒 ") + r.originalName,
                    style = MaterialTheme.typography.titleMedium, color = if (med) MedicineBlue else SymptomAmber,
                )
                val details = listOfNotNull(
                    r.timePhrase,
                    if (med) when {
                        r.genericSuggestion == null -> "Unmatched brand"
                        r.genericMatchStatus == MatchStatus.FUZZY -> "${r.genericSuggestion} (suggested)"
                        else -> r.genericSuggestion
                    } else null,
                    r.note,
                ).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
                Text("Logged $time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

@Composable
private fun EditDialog(r: DiaryRecord, onDismiss: () -> Unit, onSave: (DiaryRecord) -> Unit) {
    var name by remember { mutableStateOf(r.originalName) }
    var time by remember { mutableStateOf(r.timePhrase.orEmpty()) }
    var note by remember { mutableStateOf(r.note.orEmpty()) }
    var generic by remember { mutableStateOf(r.genericSuggestion.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("When") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
                if (r.type == RecordType.MEDICINE) {
                    OutlinedTextField(generic, { generic = it }, label = { Text("Generic name") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val g = generic.trim().ifEmpty { null }
                onSave(
                    r.copy(
                        originalName = name.trim(),
                        timePhrase = time.trim().ifEmpty { null },
                        note = note.trim().ifEmpty { null },
                        genericSuggestion = if (r.type == RecordType.MEDICINE) g else null,
                        genericMatchStatus = when {
                            r.type != RecordType.MEDICINE -> r.genericMatchStatus
                            g != r.genericSuggestion -> if (g == null) MatchStatus.UNMATCHED else MatchStatus.MANUAL
                            else -> r.genericMatchStatus
                        },
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
