package com.medidiary.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** CLAUDE.md §3.3 */
@Entity(tableName = "diary_sessions")
data class DiarySession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawTranscript: String,
    val isVerified: Boolean = false,
)

@Entity(
    tableName = "diary_records",
    foreignKeys = [
        ForeignKey(
            entity = DiarySession::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("loggedDate")],
)
data class DiaryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val type: String, // "medicine" or "symptom"
    val originalName: String,
    val genericSuggestion: String? = null,
    val genericMatchStatus: String = "UNMATCHED", // "EXACT", "FUZZY", "UNMATCHED", "MANUAL"
    val timePhrase: String? = null,
    val note: String? = null,
    val userConfirmed: Boolean = true,
    val loggedDate: Long = System.currentTimeMillis(),
)

@Entity(tableName = "dgda_medicines")
data class DgdaMedicine(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "brand_name") val brandName: String,
    @ColumnInfo(name = "generic_name") val genericName: String,
    @ColumnInfo(name = "dosage_form") val dosageForm: String? = null,
)

/** Alternative spellings (mostly Bangla script, as Whisper emits them) → dgda_medicines.id */
@Entity(tableName = "dgda_aliases")
data class DgdaAlias(
    @PrimaryKey val alias: String,
    @ColumnInfo(name = "medicine_id") val medicineId: Int,
)
