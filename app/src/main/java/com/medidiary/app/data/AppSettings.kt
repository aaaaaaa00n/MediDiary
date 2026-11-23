package com.medidiary.app.data

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
