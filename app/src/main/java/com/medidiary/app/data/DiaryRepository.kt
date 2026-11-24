package com.medidiary.app.data

import com.medidiary.app.data.db.AppDatabase
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.data.db.DiarySession
import com.medidiary.app.domain.ProposedRecord
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val db: AppDatabase) {
    private val dao = db.diaryDao()

    /**
     * The ONLY path from AI proposals to storage: called from the Review screen's
     * "Confirm & Save" button with the records the user left checked (core rule #2).
     */
    suspend fun saveVerified(transcript: String, records: List<ProposedRecord>, now: Long = System.currentTimeMillis()): Long {
        val confirmed = records.filter { it.checked && it.name.isNotBlank() }
        require(confirmed.isNotEmpty()) { "Nothing to save" }
        val session = DiarySession(timestamp = now, rawTranscript = transcript, isVerified = true)
        val rows = confirmed.map {
            DiaryRecord(
                sessionId = 0,
                type = it.type,
                originalName = it.name.trim(),
                genericSuggestion = it.genericSuggestion,
                genericMatchStatus = it.genericMatchStatus,
                timePhrase = it.timePhrase?.trim()?.ifEmpty { null },
                note = it.note?.trim()?.ifEmpty { null },
                userConfirmed = true,
                loggedDate = now,
            )
        }
        return dao.insertVerifiedSession(session, rows)
    }

    suspend fun medicineNames(): List<String> = dao.medicineNames()

    fun observe(from: Long, to: Long): Flow<List<DiaryRecord>> = dao.observeRecords(from, to)

    suspend fun between(from: Long, to: Long): List<DiaryRecord> = dao.recordsBetween(from, to)

    suspend fun update(record: DiaryRecord) = dao.updateRecord(record)

    suspend fun delete(record: DiaryRecord) {
        dao.deleteRecord(record)
        dao.deleteEmptySessions()
    }

    /** Returns number of records removed. */
    suspend fun applyRetention(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val removed = dao.deleteRecordsOlderThan(now - days * 24L * 60 * 60 * 1000)
        dao.deleteEmptySessions()
        return removed
    }

    suspend fun deleteAll() = dao.deleteAll()
}
