package com.medidiary.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.ai.ModelKind
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MessageBanner
import java.io.File

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var pendingKind by remember { mutableStateOf<ModelKind?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var name by remember(s.patientName) { mutableStateOf(s.patientName) }

    LaunchedEffect(Unit) { vm.refreshModels() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val k = pendingKind
        if (uri != null && k != null) vm.importModel(k, uri)
        pendingKind = null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        message?.let { MessageBanner(it, BannerKind.INFO) }

        Section("Patient") {
            OutlinedTextField(
                value = name, onValueChange = { name = it; vm.update { st -> st.copy(patientName = it) } },
                label = { Text("Name shown on PDF export (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Speech language") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("bn" to "বাংলা", "en" to "English", "auto" to "Auto-detect").forEach { (code, label) ->
                    FilterChip(selected = s.asrLanguage == code, onClick = { vm.update { it.copy(asrLanguage = code) } }, label = { Text(label) })
                }
            }
        }

        Section("Data retention") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 days", 60 to "60 days", 0 to "Keep all").forEach { (d, label) ->
                    FilterChip(selected = s.retentionDays == d, onClick = { vm.setRetention(d) }, label = { Text(label) })
                }
            }
            Text("Older entries are deleted automatically from this phone.", style = MaterialTheme.typography.bodySmall)
        }

        Section("On-device AI models") {
            ModelKind.entries.forEach { kind ->
                val file: File? = if (kind == ModelKind.WHISPER) models.whisper else models.gemma
                Text(kind.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    file?.let { "Installed · %.0f MB · %s".format(it.length() / 1_048_576.0, it.parent) } ?: "Not installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (file != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (importing?.first == kind) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Copying… %.0f MB".format((importing?.second ?: 0L) / 1_048_576.0), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = importing == null, onClick = { pendingKind = kind; picker.launch(arrayOf("*/*")) }) {
                        Text(if (file == null) "Import file…" else "Replace…")
                    }
                    if (file != null) {
                        OutlinedButton(onClick = { vm.deleteModel(kind) }) { Text("Remove") }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider()
            Text("Faster for developers — push over USB instead of importing:", style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Text(
                    "adb push whisper.bin ${vm.externalModelDir}/${ModelKind.WHISPER.fileName}\n" +
                        "adb push gemma.gguf ${vm.externalModelDir}/${ModelKind.GEMMA.fileName}",
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = vm::refreshModels) { Text("Re-scan") }
        }

        Section("Advanced (extraction)") {
            ToggleRow("Use GGUF chat template (Gemma turns)", s.useChatTemplate) { v -> vm.update { it.copy(useChatTemplate = v) } }
            Text(
                "Turn off only if your fine-tune was trained on plain text without <start_of_turn> markers.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Context", style = MaterialTheme.typography.bodyMedium)
                listOf(512, 1024, 2048).forEach { n ->
                    FilterChip(selected = s.llmContextSize == n, onClick = { vm.update { it.copy(llmContextSize = n) } }, label = { Text("$n") })
                }
            }
            ToggleRow("Allow demo (mock) AI when models are missing", s.allowMockEngines) { v -> vm.update { it.copy(allowMockEngines = v) } }
        }

        Section("Privacy") {
            Text(
                "MediDiary has no internet permission. Recordings, transcripts and diary entries never leave this phone " +
                    "unless you export and share them. Raw audio is deleted right after transcription.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { confirmWipe = true }) { Text("Delete all diary data") }
        }

        Section("About") {
            Text(
                "Patient-reported & AI-assisted, not medically verified. MediDiary does not give diagnoses, dosages or " +
                    "interaction advice.\n\nSpeech: OpenAI Whisper via whisper.cpp (MIT). Extraction: Gemma 3 1B via " +
                    "llama.cpp (MIT); Gemma is provided under and subject to the Gemma Terms of Use. Brand/generic reference: " +
                    "subset of the Bangladesh DGDA registered-drug list (DGHS Bangladesh Core FHIR IG); suggestions only.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("All diary entries on this phone will be permanently deleted. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteAllData(); confirmWipe = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
