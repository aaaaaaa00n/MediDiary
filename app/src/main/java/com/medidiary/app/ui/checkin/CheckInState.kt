package com.medidiary.app.ui.checkin

import com.medidiary.app.domain.ProposedRecord

enum class Busy { NONE, RECORDING, TRANSCRIBING, EXTRACTING, SAVING }

data class CheckInState(
    val busy: Busy = Busy.NONE,
    val transcript: String = "",
    val proposals: List<ProposedRecord> = emptyList(),
    val error: String? = null,
    val info: String? = null,
    val rawModelOutput: String? = null,
    val asrMs: Long? = null,
    val llmMs: Long? = null,
    val mockMode: Boolean = false,
)
