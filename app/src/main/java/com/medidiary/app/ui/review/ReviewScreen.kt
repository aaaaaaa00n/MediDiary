package com.medidiary.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.domain.RecordType
import com.medidiary.app.ui.checkin.Busy
import com.medidiary.app.ui.checkin.CheckInViewModel
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MOCK_MODE_TEXT
import com.medidiary.app.ui.components.MessageBanner
import com.medidiary.app.ui.theme.MedicineBlue
import com.medidiary.app.ui.theme.MedicineBlueContainer
import com.medidiary.app.ui.theme.SymptomAmber
import com.medidiary.app.ui.theme.SymptomAmberContainer

/** Screen 3 — Verification & Approval (CLAUDE.md §7). Only ticked cards are saved. */
@Composable
fun ReviewScreen(vm: CheckInViewModel, onSaved: () -> Unit, onDiscard: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProposedRecord?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    val medicines = state.proposals.filter { it.type == RecordType.MEDICINE }
    val symptoms = state.proposals.filter { it.type == RecordType.SYMPTOM }
    val checkedCount = state.proposals.count { it.checked && it.name.isNotBlank() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (state.busy == Busy.NONE) vm.save(onSaved) },
                icon = { Icon(Icons.Filled.CheckCircle, null) },
                text = { Text("Confirm & Save to Diary ($checkedCount)") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Review before saving", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "AI suggestions are not saved until you confirm. Untick or delete anything that is wrong.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.mockMode) item { MessageBanner(MOCK_MODE_TEXT, BannerKind.MOCK) }
            state.info?.let { item { MessageBanner(it, BannerKind.INFO) } }
            state.error?.let { item { MessageBanner(it, BannerKind.ERROR) } }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("You said", style = MaterialTheme.typography.labelLarge)
                        Text(state.transcript, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item { SectionHeader("Medicines", Icons.Filled.Medication, MedicineBlue) { vm.addManual(RecordType.MEDICINE) } }
            if (medicines.isEmpty()) item { EmptyHint("No medicines found.") }
            items(medicines, key = { it.localId }) { rec ->
                RecordCard(rec, MedicineBlue, MedicineBlueContainer, vm::updateProposal, { editing = rec }, { vm.removeProposal(rec.localId) })
            }

            item { SectionHeader("Symptoms", Icons.Filled.Sick, SymptomAmber) { vm.addManual(RecordType.SYMPTOM) } }
            if (symptoms.isEmpty()) item { EmptyHint("No symptoms found.") }
            items(symptoms, key = { it.localId }) { rec ->
                RecordCard(rec, SymptomAmber, SymptomAmberContainer, vm::updateProposal, { editing = rec }, { vm.removeProposal(rec.localId) })
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.discard(); onDiscard() }) { Text("Discard all") }
                    if (state.rawModelOutput != null) TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Hide AI output" else "Show raw AI output")
                    }
                }
                state.llmMs?.let {
                    Text(
                        "Extracted on-device in %.1f s".format(it / 1000.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showRaw) Text(state.rawModelOutput.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // Auto-open the editor for freshly added blank manual items.
    val blank = state.proposals.firstOrNull { it.name.isBlank() }
    val dialogRecord = editing ?: blank
    if (dialogRecord != null) {
        EditRecordDialog(
            initial = dialogRecord,
            onDismiss = {
                if (dialogRecord.name.isBlank()) vm.removeProposal(dialogRecord.localId)
                editing = null
            },
            onSave = { updated ->
                vm.updateProposal(updated)
                if (updated.name != dialogRecord.name) vm.rematch(updated)
                editing = null
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) { Icon(Icons.Filled.Add, null); Text("Add") }
    }
}

@Composable
private fun EmptyHint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordCard(
    rec: ProposedRecord,
    accent: Color,
    container: Color,
    onChange: (ProposedRecord) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (rec.checked) container else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = rec.checked, onCheckedChange = { onChange(rec.copy(checked = it)) })
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(rec.name.ifBlank { "(empty)" }, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rec.timePhrase?.let { AssistChip(onClick = onEdit, label = { Text("🕒 $it") }) }
                    rec.note?.let { AssistChip(onClick = onEdit, label = { Text("📝 $it") }) }
                    if (rec.type == RecordType.MEDICINE) GenericPill(rec)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

@Composable
private fun GenericPill(rec: ProposedRecord) {
    val (label, color) = when {
        rec.genericSuggestion == null -> "Unmatched Brand" to MaterialTheme.colorScheme.outline
        rec.genericMatchStatus == MatchStatus.EXACT -> "DGDA Match: ${rec.genericSuggestion}" to Color(0xFF2E7D32)
        rec.genericMatchStatus == MatchStatus.MANUAL -> "Generic (you): ${rec.genericSuggestion}" to Color(0xFF2E7D32)
        else -> "Possible DGDA match: ${rec.genericSuggestion}?" to Color(0xFF8D6E00)
    }
    Box(Modifier.padding(top = 8.dp)) {
        Text("[$label]", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun EditRecordDialog(initial: ProposedRecord, onDismiss: () -> Unit, onSave: (ProposedRecord) -> Unit) {
    var name by remember(initial.localId) { mutableStateOf(initial.name) }
    var time by remember(initial.localId) { mutableStateOf(initial.timePhrase.orEmpty()) }
    var note by remember(initial.localId) { mutableStateOf(initial.note.orEmpty()) }
    var generic by remember(initial.localId) { mutableStateOf(initial.genericSuggestion.orEmpty()) }
    val isMedicine = initial.type == RecordType.MEDICINE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMedicine) "Medicine" else "Symptom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (as you said it)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("When (e.g. সকালে, before lunch)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
                if (isMedicine) {
                    OutlinedTextField(generic, { generic = it }, label = { Text("Generic name (optional)") }, singleLine = true)
                    Text(
                        "Leave generic as suggested, or type it from the medicine strip. It is never invented by AI.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val genericChanged = isMedicine && generic.trim() != initial.genericSuggestion.orEmpty()
                onSave(
                    initial.copy(
                        name = name.trim(),
                        timePhrase = time.trim().ifEmpty { null },
                        note = note.trim().ifEmpty { null },
                        genericSuggestion = if (isMedicine) generic.trim().ifEmpty { null } else null,
                        genericMatchStatus = when {
                            !isMedicine -> MatchStatus.UNMATCHED
                            genericChanged && generic.isNotBlank() -> MatchStatus.MANUAL
                            genericChanged -> MatchStatus.UNMATCHED
                            name.trim() != initial.name -> MatchStatus.UNMATCHED // triggers rematch
                            else -> initial.genericMatchStatus
                        },
                        checked = true,
                    ),
                )
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
