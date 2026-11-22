package com.medidiary.app.ui.checkin

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.audio.AudioRecorder
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MOCK_MODE_TEXT
import com.medidiary.app.ui.components.MessageBanner

private val QUICK_CHIPS = listOf("Napa", "Paracetamol", "Seclo", "Gas", "Headache", "জ্বর", "মাথা ব্যথা", "সকালে", "রাতে")

/** Screen 1 — Daily Check-In (CLAUDE.md §7). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(vm: CheckInViewModel, onTranscriptReady: () -> Unit, onTypeInstead: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val amplitude by vm.recorder.amplitude.collectAsStateWithLifecycle()
    val elapsed by vm.recorder.elapsedMs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recording = state.busy == Busy.RECORDING

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }
    LaunchedEffect(Unit) { vm.refreshMode() }
    // Auto-stop at 60 s.
    LaunchedEffect(elapsed, recording) {
        if (recording && elapsed >= AudioRecorder.MAX_SECONDS * 1000L) vm.stopAndTranscribe(onTranscriptReady)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Today's check-in", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "আজ কোন ওষুধ খেয়েছেন, কেমন লাগছে — বাংলা বা English-এ বলুন।\nWhat medicines did you take and how do you feel?",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (state.mockMode) MessageBanner(MOCK_MODE_TEXT, BannerKind.MOCK)
        state.info?.let { Spacer(Modifier.height(8.dp)); MessageBanner(it, BannerKind.INFO) }
        state.error?.let { Spacer(Modifier.height(8.dp)); MessageBanner(it, BannerKind.ERROR) }

        Spacer(Modifier.height(28.dp))
        MicButton(
            recording = recording,
            busy = state.busy == Busy.TRANSCRIBING,
            amplitude = amplitude,
            onClick = {
                when {
                    recording -> vm.stopAndTranscribe(onTranscriptReady)
                    state.busy != Busy.NONE -> Unit
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> vm.startRecording()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        val secs = elapsed / 1000
        Text(
            when (state.busy) {
                Busy.RECORDING -> "%d:%02d / 1:00 — tap to stop".format(secs / 60, secs % 60)
                Busy.TRANSCRIBING -> "Transcribing on-device…"
                else -> "Tap the microphone to start"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        AmplitudeBars(amplitude, recording)
        if (state.busy == Busy.TRANSCRIBING) {
            TextButton(onClick = vm::cancel) { Text("Cancel") }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onTypeInstead, enabled = state.busy == Busy.NONE) {
            Icon(Icons.Filled.Keyboard, null)
            Spacer(Modifier.size(8.dp))
            Text("Type instead")
        }
        Spacer(Modifier.height(12.dp))
        Text("Quick entry", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            QUICK_CHIPS.forEach { chip ->
                AssistChip(
                    onClick = { vm.appendToTranscript(chip); onTypeInstead() },
                    label = { Text(chip) },
                    enabled = state.busy == Busy.NONE,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Audio stays on this phone and is deleted right after transcription.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MicButton(recording: Boolean, busy: Boolean, amplitude: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(1f, 1.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ring")
    val level by animateFloatAsState(1f + amplitude * 0.25f, label = "level")
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        if (recording) {
            Box(
                Modifier.size(140.dp).scale(ring * level)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), CircleShape),
            )
        }
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(132.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ),
            enabled = !busy,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, "Record", Modifier.size(56.dp))
        }
    }
}

@Composable
private fun AmplitudeBars(amplitude: Float, active: Boolean) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(32) { add(0f) } } }
    LaunchedEffect(amplitude, active) {
        history.removeAt(0)
        history.add(if (active) amplitude else 0f)
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth(0.8f).height(48.dp)) {
        val w = size.width / history.size
        history.forEachIndexed { i, a ->
            val h = (a.coerceIn(0.04f, 1f)) * size.height
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.85f else 0.25f),
                topLeft = Offset(i * w + w * 0.2f, (size.height - h) / 2),
                size = Size(w * 0.6f, h),
                cornerRadius = CornerRadius(w * 0.3f),
            )
        }
    }
}
