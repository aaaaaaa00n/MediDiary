package com.medidiary.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val patientName: String = "",
    /** 0 = keep forever, otherwise 30 or 60 days (proposal §9). */
    val retentionDays: Int = 60,
    /** Whisper language: "bn", "en" or "auto". */
    val asrLanguage: String = "bn",
    /** Wrap the transcript with the GGUF chat template (Gemma turns). Turn off if fine-tuned on raw text. */
    val useChatTemplate: Boolean = true,
    val llmContextSize: Int = 1024,
    val llmMaxTokens: Int = 384,
    /** Allow the scripted mock engines when model files are missing (development only). */
    val allowMockEngines: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        patientName = prefs.getString("patientName", "") ?: "",
        retentionDays = prefs.getInt("retentionDays", 60),
        asrLanguage = prefs.getString("asrLanguage", "bn") ?: "bn",
        useChatTemplate = prefs.getBoolean("useChatTemplate", true),
        llmContextSize = prefs.getInt("llmContextSize", 1024),
        llmMaxTokens = prefs.getInt("llmMaxTokens", 384),
        allowMockEngines = prefs.getBoolean("allowMockEngines", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString("patientName", s.patientName)
            .putInt("retentionDays", s.retentionDays)
            .putString("asrLanguage", s.asrLanguage)
            .putBoolean("useChatTemplate", s.useChatTemplate)
            .putInt("llmContextSize", s.llmContextSize)
            .putInt("llmMaxTokens", s.llmMaxTokens)
            .putBoolean("allowMockEngines", s.allowMockEngines)
            .apply()
        _settings.value = s
    }
}
