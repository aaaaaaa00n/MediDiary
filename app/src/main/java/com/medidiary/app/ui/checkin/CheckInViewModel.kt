package com.medidiary.app.ui.checkin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.audio.AudioRecorder
import com.medidiary.app.domain.ExtractionSanitizer
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.ModelResponseParser
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.domain.RecordType
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared by CheckIn → Transcript → Review. Holds proposals in memory only; nothing is written
 * to Room until [save] is called from the Review screen (core rule #2).
 */
class CheckInViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MediDiaryApp).container
    val recorder = AudioRecorder()

    private val _state = MutableStateFlow(CheckInState(mockMode = c.pipeline.isMockMode()))
    val state: StateFlow<CheckInState> = _state.asStateFlow()

    private var recordJob: Job? = null
    private var wavFile: File? = null

    /** App settings (the EN | BN toggle on the home screen reads/writes the speech language). */
    val settings = c.settings.settings

    fun setAsrLanguage(code: String) = c.settings.update { it.copy(asrLanguage = code) }

    fun refreshMode() = _state.update { it.copy(mockMode = c.pipeline.isMockMode()) }

    fun startRecording() {
        if (_state.value.busy != Busy.NONE) return
        val wav = File(getApplication<Application>().cacheDir, "checkin_${System.currentTimeMillis()}.wav")
        wavFile = wav
        _state.update { it.copy(busy = Busy.RECORDING, error = null, info = null) }
        recordJob = viewModelScope.launch {
            runCatching { recorder.record(wav) }
                .onFailure { e -> _state.update { it.copy(busy = Busy.NONE, error = e.message ?: "Recording failed") } }
        }
    }

    /** Stops recording, transcribes, deletes the audio, then calls [onTranscript]. */
    fun stopAndTranscribe(onTranscript: () -> Unit) {
        val job = recordJob ?: return
        recorder.requestStop()
        viewModelScope.launch {
            job.join()
            val wav = wavFile ?: return@launch
            if (_state.value.error != null) return@launch
            if (recorder.elapsedMs.value < 700) {
                wav.delete()
                _state.update { it.copy(busy = Busy.NONE, error = "Recording too short — hold on a little longer.") }
                return@launch
            }
            _state.update { it.copy(busy = Busy.TRANSCRIBING) }
            val t0 = System.currentTimeMillis()
            val result = runCatching { c.pipeline.transcribe(wav) }
            wav.delete() // privacy: raw audio never outlives transcription
            wavFile = null
            result.onSuccess { text ->
                _state.update {
                    it.copy(
                        busy = Busy.NONE,
                        transcript = listOf(it.transcript.trim(), text.trim()).filter { s -> s.isNotEmpty() }.joinToString(" "),
                        asrMs = System.currentTimeMillis() - t0,
                        mockMode = c.pipeline.isMockMode(),
                    )
                }
                onTranscript()
            }.onFailure { e ->
                _state.update { it.copy(busy = Busy.NONE, error = "Transcription failed: ${e.message}") }
            }
        }
    }

    fun cancel() {
        recorder.requestStop()
        c.pipeline.cancel()
    }

    fun setTranscript(text: String) = _state.update { it.copy(transcript = text) }

    fun appendToTranscript(word: String) = _state.update {
        it.copy(transcript = (it.transcript.trimEnd() + " " + word).trim())
    }

    /** Phase 4 + 5: Gemma → parse → sanitize → deterministic DGDA lookup. */
    fun extract(onDone: () -> Unit) {
        val transcript = _state.value.transcript.trim()
        if (transcript.isEmpty() || _state.value.busy != Busy.NONE) return
        _state.update { it.copy(busy = Busy.EXTRACTING, error = null, info = null) }
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val raw = runCatching { c.pipeline.extract(transcript) }.getOrElse { e ->
                _state.update { it.copy(busy = Busy.NONE, error = "Extraction failed: ${e.message}") }
                return@launch
            }
            val llmMs = System.currentTimeMillis() - t0
            val parsed = ModelResponseParser.parse(raw)
            val clean = ExtractionSanitizer.sanitize(parsed.records)
            val matched = clean.map { rec ->
                if (rec.type != RecordType.MEDICINE) rec
                else {
                    val m = c.dgdaLookup.match(rec.name)
                    rec.copy(genericSuggestion = m.genericName, genericMatchStatus = m.status)
                }
            }
            val dropped = parsed.records.size - clean.size + parsed.rejectedCount
            val info = buildList {
                parsed.error?.let { add("The AI output could not be read ($it). Add items manually below.") }
                if (dropped > 0) add("$dropped unclear item(s) were filtered out.")
                if (matched.isEmpty() && parsed.error == null) add("No medicines or symptoms were found. Add items manually if needed.")
            }.joinToString(" ").ifEmpty { null }
            _state.update {
                it.copy(
                    busy = Busy.NONE, proposals = matched, rawModelOutput = raw, llmMs = llmMs,
                    info = info, mockMode = c.pipeline.isMockMode(),
                )
            }
            onDone()
        }
    }

    fun updateProposal(updated: ProposedRecord) = _state.update { s ->
        s.copy(proposals = s.proposals.map { if (it.localId == updated.localId) updated else it })
    }

    /** Re-run the deterministic matcher after the user edits a medicine name. */
    fun rematch(record: ProposedRecord) {
        if (record.type != RecordType.MEDICINE || record.genericMatchStatus == MatchStatus.MANUAL) return
        viewModelScope.launch {
            val m = c.dgdaLookup.match(record.name)
            updateProposal(record.copy(genericSuggestion = m.genericName, genericMatchStatus = m.status))
        }
    }

    fun removeProposal(id: String) = _state.update { s -> s.copy(proposals = s.proposals.filterNot { it.localId == id }) }

    fun addManual(type: String) = _state.update { s ->
        s.copy(proposals = s.proposals + ProposedRecord(type = type, name = "", genericMatchStatus = MatchStatus.UNMATCHED))
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val toSave = s.proposals.filter { it.checked && it.name.isNotBlank() }
        if (toSave.isEmpty()) {
            _state.update { it.copy(error = "Tick at least one item to save.") }
            return
        }
        _state.update { it.copy(busy = Busy.SAVING, error = null) }
        viewModelScope.launch {
            runCatching { c.diaryRepository.saveVerified(s.transcript, toSave) }
                .onSuccess {
                    _state.value = CheckInState(info = "Saved ${toSave.size} item(s) to your diary.", mockMode = c.pipeline.isMockMode())
                    onSaved()
                }
                .onFailure { e -> _state.update { it.copy(busy = Busy.NONE, error = "Save failed: ${e.message}") } }
        }
    }

    fun discard() {
        _state.value = CheckInState(mockMode = c.pipeline.isMockMode())
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    override fun onCleared() {
        recorder.requestStop()
        wavFile?.delete()
    }
}
