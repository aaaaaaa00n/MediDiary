package com.medidiary.app.ui.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.ui.checkin.Busy
import com.medidiary.app.ui.checkin.CheckInViewModel
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MOCK_MODE_TEXT
import com.medidiary.app.ui.components.MessageBanner

/** Screen 2 — Transcript Review (CLAUDE.md §7). Human edit happens BEFORE the LLM runs. */
@Composable
fun TranscriptScreen(vm: CheckInViewModel, onExtracted: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val extracting = state.busy == Busy.EXTRACTING

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Check your words", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        MessageBanner("You can fix any misheard medicine names before AI extraction.", BannerKind.INFO)
        if (state.mockMode) { Spacer(Modifier.height(8.dp)); MessageBanner(MOCK_MODE_TEXT, BannerKind.MOCK) }
        state.error?.let { Spacer(Modifier.height(8.dp)); MessageBanner(it, BannerKind.ERROR) }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.transcript,
            onValueChange = vm::setTranscript,
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            label = { Text("Transcript") },
            placeholder = { Text("যেমন: সকালে নাস্তার পর একটা Napa খেয়েছি, মাথা ব্যথা করছে") },
            enabled = !extracting,
        )
        state.asrMs?.let {
            Text(
                "Transcribed on-device in %.1f s".format(it / 1000.0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, enabled = !extracting) { Text("Re-record") }
            Button(
                onClick = { vm.extract(onExtracted) },
                enabled = !extracting && state.transcript.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                if (extracting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                    Text("Extracting on-device…")
                } else {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Process & Extract")
                }
            }
        }
        if (extracting) {
            OutlinedButton(onClick = vm::cancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
        }
    }
}
