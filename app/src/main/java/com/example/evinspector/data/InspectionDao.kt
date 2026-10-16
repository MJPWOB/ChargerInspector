package com.example.evinspector.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {

    // ---- sessions ----

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE finishedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Query("UPDATE sessions SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun markFinished(id: Long, finishedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // ---- component results (auto-saved drafts) ----

    @Query("SELECT * FROM component_results WHERE sessionId = :sessionId")
    fun observeResults(sessionId: Long): Flow<List<ComponentResultEntity>>

    @Query("SELECT * FROM component_results WHERE sessionId = :sessionId")
    suspend fun getSessionResults(sessionId: Long): List<ComponentResultEntity>

    @Query("SELECT * FROM component_results WHERE sessionId = :sessionId AND chargerNo = :chargerNo")
    suspend fun getResults(sessionId: Long, chargerNo: Int): List<ComponentResultEntity>

    @Upsert
    suspend fun upsertResults(results: List<ComponentResultEntity>)

    @Query(
        "DELETE FROM component_results " +
            "WHERE sessionId = :sessionId AND chargerNo = :chargerNo AND component = :component",
    )
    suspend fun deleteResult(sessionId: Long, chargerNo: Int, component: String)

    // ---- committed chargers ----

    @Query("SELECT * FROM charger_records WHERE sessionId = :sessionId")
    fun observeRecords(sessionId: Long): Flow<List<ChargerRecordEntity>>

    @Query("SELECT * FROM charger_records WHERE sessionId = :sessionId")
    suspend fun getSessionRecords(sessionId: Long): List<ChargerRecordEntity>

    @Upsert
    suspend fun upsertRecord(record: ChargerRecordEntity)

    @Query("DELETE FROM charger_records WHERE sessionId = :sessionId AND chargerNo = :chargerNo")
    suspend fun deleteRecord(sessionId: Long, chargerNo: Int)

    // ---- photos ----

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos WHERE sessionId = :sessionId AND chargerNo = :chargerNo ORDER BY id")
    fun observePhotos(sessionId: Long, chargerNo: Int): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE sessionId = :sessionId AND chargerNo = :chargerNo")
    suspend fun getPhotos(sessionId: Long, chargerNo: Int): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getSessionPhotos(sessionId: Long): List<PhotoEntity>

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("DELETE FROM photos WHERE sessionId = :sessionId")
    suspend fun deleteSessionPhotos(sessionId: Long)

    // ---- reports ----

    @Insert
    suspend fun insertReport(report: ReportEntity): Long

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getReport(id: Long): ReportEntity?

    @Query("SELECT COUNT(*) FROM reports WHERE sessionId = :sessionId")
    suspend fun countReports(sessionId: Long): Int

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteReport(id: Long)

    @Query(
        "SELECT r.id AS reportId, r.sessionId AS sessionId, s.inspectorId AS inspectorId, " +
            "r.generatedAt AS generatedAt, r.pdfPath AS pdfPath, r.csvPath AS csvPath, " +
            "r.inspectedCount AS inspectedCount, r.totalCount AS totalCount, r.issueCount AS issueCount " +
            "FROM reports r INNER JOIN sessions s ON s.id = r.sessionId " +
            "ORDER BY r.generatedAt DESC",
    )
    fun observeReportRows(): Flow<List<ReportRow>>
}
