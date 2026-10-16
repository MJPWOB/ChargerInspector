package com.example.evinspector.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One inspection round. The charger prefix and count are snapshotted here so a historical report
 * keeps its own numbering even if the configuration changes later. A session with finishedAt == null
 * is the active session and is resumed automatically after a crash or restart.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inspectorId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val chargerPrefix: String,
    val chargerCount: Int,
)

/** Exists only for chargers whose inspection was committed with Save & Return. */
@Entity(
    tableName = "charger_records",
    primaryKeys = ["sessionId", "chargerNo"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChargerRecordEntity(
    val sessionId: Long,
    val chargerNo: Int,
    val inspectedAt: Long,
)

/** Auto-saved answer for one component of one charger. [component] and [result] hold enum names. */
@Entity(
    tableName = "component_results",
    primaryKeys = ["sessionId", "chargerNo", "component"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ComponentResultEntity(
    val sessionId: Long,
    val chargerNo: Int,
    val component: String,
    val result: String,
    val note: String,
)

/** [path] is relative to the app's files directory. */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val chargerNo: Int,
    val component: String,
    val path: String,
    val takenAt: Long,
)

/** One generated PDF + CSV pair. Paths are relative to the app's files directory. */
@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val generatedAt: Long,
    val pdfPath: String,
    val csvPath: String,
    val inspectedCount: Int,
    val totalCount: Int,
    val issueCount: Int,
)

/** Join result for the history list (not a table). */
data class ReportRow(
    val reportId: Long,
    val sessionId: Long,
    val inspectorId: String,
    val generatedAt: Long,
    val pdfPath: String,
    val csvPath: String,
    val inspectedCount: Int,
    val totalCount: Int,
    val issueCount: Int,
)
