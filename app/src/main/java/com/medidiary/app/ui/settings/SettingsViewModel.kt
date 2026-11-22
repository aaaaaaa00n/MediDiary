package com.medidiary.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.ai.ModelKind
import com.medidiary.app.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MediDiaryApp).container
    val settings = c.settings.settings
    val models = c.modelManager.status
    val importing = MutableStateFlow<Pair<ModelKind, Long>?>(null)
    val message = MutableStateFlow<String?>(null)

    val externalModelDir: String get() = c.modelManager.externalDirPath()

    fun update(transform: (AppSettings) -> AppSettings) {
        c.settings.update(transform)
    }

    fun setRetention(days: Int) {
        c.settings.update { it.copy(retentionDays = days) }
        viewModelScope.launch {
            val removed = c.diaryRepository.applyRetention(days)
            if (removed > 0) message.value = "Removed $removed entries older than $days days."
        }
    }

    fun refreshModels() {
        c.modelManager.refresh()
    }

    fun importModel(kind: ModelKind, uri: Uri) = viewModelScope.launch {
        importing.value = kind to 0L
        runCatching { c.modelManager.importFromUri(kind, uri) { importing.value = kind to it } }
            .onSuccess { message.value = "${kind.label} installed." }
            .onFailure { message.value = "Import failed: ${it.message}" }
        importing.value = null
    }

    fun deleteModel(kind: ModelKind) = c.modelManager.delete(kind)

    fun deleteAllData() = viewModelScope.launch {
        c.diaryRepository.deleteAll()
        message.value = "All diary entries deleted from this phone."
    }
}
