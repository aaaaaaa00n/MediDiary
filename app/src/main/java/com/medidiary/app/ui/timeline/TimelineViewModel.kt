package com.medidiary.app.ui.timeline

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.domain.RecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class RangePreset(val label: String) { TODAY("Today"), WEEK("Last 7 days"), MONTH("Last 30 days"), CUSTOM("Custom") }
enum class TypeFilter(val label: String) { ALL("All"), MEDICINES("Medicines Only"), SYMPTOMS("Symptoms Only") }

data class DateRange(val from: Long, val to: Long)

class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MediDiaryApp).container

    val preset = MutableStateFlow(RangePreset.WEEK)
    val range = MutableStateFlow(rangeFor(RangePreset.WEEK))
    val typeFilter = MutableStateFlow(TypeFilter.ALL)
    val query = MutableStateFlow("")
    val message = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<DiaryRecord>> =
        combine(range.flatMapLatest { c.diaryRepository.observe(it.from, it.to) }, typeFilter, query) { list, type, q ->
            list.filter { r ->
                when (type) {
                    TypeFilter.ALL -> true
                    TypeFilter.MEDICINES -> r.type == RecordType.MEDICINE
                    TypeFilter.SYMPTOMS -> r.type == RecordType.SYMPTOM
                }
            }.filter { r ->
                q.isBlank() || listOfNotNull(r.originalName, r.genericSuggestion, r.note, r.timePhrase)
                    .any { it.contains(q.trim(), ignoreCase = true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectPreset(p: RangePreset) {
        preset.value = p
        if (p != RangePreset.CUSTOM) range.value = rangeFor(p)
    }

    fun setCustomRange(fromUtcMidnight: Long, toUtcMidnight: Long) {
        preset.value = RangePreset.CUSTOM
        // DatePicker returns UTC midnight; convert to local start/end of day.
        range.value = DateRange(startOfLocalDay(fromUtcMidnight), endOfLocalDay(toUtcMidnight))
    }

    fun update(record: DiaryRecord) = viewModelScope.launch { c.diaryRepository.update(record) }
    fun delete(record: DiaryRecord) = viewModelScope.launch { c.diaryRepository.delete(record) }

    fun export(pdf: Boolean, onReady: (Intent) -> Unit) = viewModelScope.launch {
        val r = range.value
        val rows = records.value
        if (rows.isEmpty()) {
            message.value = "Nothing to export for this filter."
            return@launch
        }
        val name = c.settings.settings.value.patientName
        runCatching {
            withContext(Dispatchers.IO) {
                if (pdf) c.exportManager.exportPdf(rows, name, r.from, r.to) else c.exportManager.exportCsv(rows, name)
            }
        }.onSuccess { onReady(c.exportManager.shareIntent(it)) }
            .onFailure { message.value = "Export failed: ${it.message}" }
    }

    companion object {
        fun rangeFor(p: RangePreset, now: Long = System.currentTimeMillis()): DateRange {
            val end = endOfDay(now)
            val days = when (p) {
                RangePreset.TODAY -> 0
                RangePreset.WEEK -> 6
                RangePreset.MONTH -> 29
                RangePreset.CUSTOM -> 6
            }
            val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -days) }
            return DateRange(startOfDay(cal.timeInMillis), end)
        }

        fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
            timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun endOfDay(t: Long): Long = startOfDay(t) + 24L * 60 * 60 * 1000 - 1

        private fun localFromUtc(utcMidnight: Long): Long {
            val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
            return Calendar.getInstance().apply {
                clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }

        fun startOfLocalDay(utcMidnight: Long) = localFromUtc(utcMidnight)
        fun endOfLocalDay(utcMidnight: Long) = endOfDay(localFromUtc(utcMidnight))
    }
}
