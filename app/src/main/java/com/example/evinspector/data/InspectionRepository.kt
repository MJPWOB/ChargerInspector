package com.example.evinspector.data

import android.content.Context
import androidx.room.withTransaction
import com.example.evinspector.domain.ChargerLabels
import com.example.evinspector.domain.ChargerReport
import com.example.evinspector.domain.ChargerTile
import com.example.evinspector.domain.CheckResult
import com.example.evinspector.domain.Component
import com.example.evinspector.domain.ComponentFinding
import com.example.evinspector.domain.ComponentInput
import com.example.evinspector.domain.CsvExporter
import com.example.evinspector.domain.DashboardData
import com.example.evinspector.domain.InspectionRules
import com.example.evinspector.domain.SessionReport
import com.example.evinspector.domain.TimeFormats
import com.example.evinspector.domain.summary
import com.example.evinspector.report.PdfReportWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

data class GeneratedReport(val reportId: Long, val pdf: File, val csv: File)

/**
 * Single entry point for all persistence. Writes are serialised through [writeLock] and run inside
 * Room transactions, so a crash or power loss can never leave a half-written charger.
 *
 * [appScope] outlives any screen; ViewModels use it for "flush on the way out" saves that must not
 * be cancelled when the screen is closed.
 */
class InspectionRepository(
    private val context: Context,
    private val db: AppDatabase,
    val appScope: CoroutineScope,
) {
    private val dao = db.dao()
    private val writeLock = Mutex()
    private val filesRoot: File get() = context.filesDir

    fun resolve(relativePath: String): File = File(filesRoot, relativePath)

    fun relativePathOf(file: File): String = file.relativeTo(filesRoot).path

    // ------------------------------------------------------------------ sessions

    suspend fun getActiveSessionId(): Long? = dao.getActiveSession()?.id

    suspend fun getSession(sessionId: Long): SessionEntity? = dao.getSession(sessionId)

    suspend fun startSession(inspectorId: String, prefix: String, count: Int): Long =
        dao.insertSession(
            SessionEntity(
                inspectorId = inspectorId,
                startedAt = System.currentTimeMillis(),
                finishedAt = null,
                chargerPrefix = prefix,
                chargerCount = count,
            ),
        )

    suspend fun hasReports(sessionId: Long): Boolean = dao.countReports(sessionId) > 0

    /**
     * Closes the session and frees its storage. Raw photos are deleted (the PDFs keep their own
     * embedded copies). If no report was ever generated there is nothing worth keeping, so the
     * whole session is removed; otherwise it stays so its reports remain in history.
     */
    suspend fun finishSession(sessionId: Long) {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                val keep = dao.countReports(sessionId) > 0
                File(filesRoot, "photos/session_$sessionId").deleteRecursively()
                dao.deleteSessionPhotos(sessionId)
                if (keep) {
                    dao.markFinished(sessionId, System.currentTimeMillis())
                } else {
                    File(filesRoot, "reports/session_$sessionId").deleteRecursively()
                    dao.deleteSession(sessionId)
                }
            }
        }
    }

    // ------------------------------------------------------------------ dashboard

    fun observeDashboard(sessionId: Long): Flow<DashboardData?> =
        combine(
            dao.observeSession(sessionId),
            dao.observeResults(sessionId),
            dao.observeRecords(sessionId),
        ) { session, results, records ->
            if (session == null) {
                null
            } else {
                val answered = results.groupBy { it.chargerNo }.mapValues { (_, rows) -> toAnswerMap(rows) }
                val committed = records.map { it.chargerNo }.toSet()
                DashboardData(
                    inspectorId = session.inspectorId,
                    tiles = (1..session.chargerCount).map { n ->
                        ChargerTile(
                            number = n,
                            label = ChargerLabels.label(session.chargerPrefix, n, session.chargerCount),
                            status = InspectionRules.deriveStatus(answered[n].orEmpty(), n in committed),
                        )
                    },
                )
            }
        }

    private fun toAnswerMap(rows: List<ComponentResultEntity>): Map<Component, CheckResult> =
        rows.mapNotNull { row ->
            val component = Component.fromName(row.component)
            val result = CheckResult.fromName(row.result)
            if (component != null && result != null) component to result else null
        }.toMap()

    // ------------------------------------------------------------------ inspection form

    suspend fun loadInputs(sessionId: Long, chargerNo: Int): Map<Component, ComponentInput> =
        dao.getResults(sessionId, chargerNo).mapNotNull { row ->
            val component = Component.fromName(row.component)
            val result = CheckResult.fromName(row.result)
            if (component != null && result != null) component to ComponentInput(result, row.note) else null
        }.toMap()

    /**
     * Auto-save. Any edit to a charger drops its "committed" flag, so it shows yellow again until
     * the inspector presses Save & Return once more.
     */
    suspend fun saveDraft(sessionId: Long, chargerNo: Int, inputs: Map<Component, ComponentInput>) {
        writeLock.withLock {
            db.withTransaction {
                writeInputs(sessionId, chargerNo, inputs)
                dao.deleteRecord(sessionId, chargerNo)
            }
        }
    }

    /**
     * Save & Return. Returns false (and writes nothing) if the form is not valid. Notes and photos
     * belonging to components that ended up as PASS are discarded.
     */
    suspend fun commit(
        sessionId: Long,
        chargerNo: Int,
        inputs: Map<Component, ComponentInput>,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!InspectionRules.canSave(inputs)) return false

        val cleaned = inputs.mapValues { (_, input) ->
            if (input.result == CheckResult.FAIL) input.copy(note = input.note.trim()) else input.copy(note = "")
        }
        val passed = cleaned.filterValues { it.result == CheckResult.PASS }.keys.map { it.name }.toSet()
        val orphanFiles = mutableListOf<String>()

        writeLock.withLock {
            db.withTransaction {
                writeInputs(sessionId, chargerNo, cleaned)
                dao.getPhotos(sessionId, chargerNo)
                    .filter { it.component in passed }
                    .forEach {
                        dao.deletePhoto(it.id)
                        orphanFiles += it.path
                    }
                dao.upsertRecord(ChargerRecordEntity(sessionId, chargerNo, now))
            }
        }
        withContext(Dispatchers.IO) { orphanFiles.forEach { resolve(it).delete() } }
        return true
    }

    private suspend fun writeInputs(sessionId: Long, chargerNo: Int, inputs: Map<Component, ComponentInput>) {
        val rows = inputs.mapNotNull { (component, input) ->
            input.result?.let {
                ComponentResultEntity(sessionId, chargerNo, component.name, it.name, input.note)
            }
        }
        if (rows.isNotEmpty()) dao.upsertResults(rows)
        for (component in Component.entries) {
            if (inputs[component]?.result == null) dao.deleteResult(sessionId, chargerNo, component.name)
        }
    }

    // ------------------------------------------------------------------ photos

    fun observePhotos(sessionId: Long, chargerNo: Int): Flow<List<PhotoEntity>> =
        dao.observePhotos(sessionId, chargerNo)

    /** Creates the (not yet existing) target file the camera app will write to. */
    fun newPhotoFile(sessionId: Long, chargerNo: Int, component: Component): File {
        val dir = File(filesRoot, "photos/session_$sessionId").apply { mkdirs() }
        return File(dir, "${chargerNo}_${component.name}_${System.currentTimeMillis()}.jpg")
    }

    /** Called after the camera returns. Shrinks the image, then registers it. */
    suspend fun addPhoto(sessionId: Long, chargerNo: Int, component: Component, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = resolve(relativePath)
            val existing = dao.getPhotos(sessionId, chargerNo).count { it.component == component.name }
            val usable = file.exists() &&
                file.length() > 0L &&
                existing < InspectionRules.MAX_PHOTOS_PER_COMPONENT &&
                PhotoProcessor.shrinkInPlace(file)
            if (!usable) {
                file.delete()
                false
            } else {
                writeLock.withLock {
                    db.withTransaction {
                        dao.insertPhoto(
                            PhotoEntity(
                                sessionId = sessionId,
                                chargerNo = chargerNo,
                                component = component.name,
                                path = relativePath,
                                takenAt = System.currentTimeMillis(),
                            ),
                        )
                        dao.deleteRecord(sessionId, chargerNo)
                    }
                }
                true
            }
        }

    /** Deletes a photo that was never registered (camera cancelled, or capture failed). */
    suspend fun discardUnregisteredPhoto(relativePath: String) {
        withContext(Dispatchers.IO) { resolve(relativePath).delete() }
    }

    suspend fun removePhoto(photo: PhotoEntity) {
        writeLock.withLock {
            db.withTransaction {
                dao.deletePhoto(photo.id)
                dao.deleteRecord(photo.sessionId, photo.chargerNo)
            }
        }
        withContext(Dispatchers.IO) { resolve(photo.path).delete() }
    }

    // ------------------------------------------------------------------ reports

    suspend fun generateReports(sessionId: Long): GeneratedReport = withContext(Dispatchers.IO) {
        val session = dao.getSession(sessionId) ?: error("Session $sessionId no longer exists")
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        // Snapshot under the write lock so it lands after any auto-save that is still queued.
        val report = writeLock.withLock { buildSessionReport(session, now, zone) }

        val dir = File(filesRoot, "reports/session_$sessionId").apply { mkdirs() }
        val stamp = TimeFormats.fileStamp(now, zone)
        val pdf = File(dir, "ChargerInspection_$stamp.pdf")
        val csv = File(dir, "ChargerInspection_$stamp.csv")
        try {
            PdfReportWriter().write(report, pdf)
            csv.writeText(CsvExporter.build(report), Charsets.UTF_8)
        } catch (e: Throwable) {
            pdf.delete()
            csv.delete()
            throw e
        }

        val summary = report.summary()
        val id = dao.insertReport(
            ReportEntity(
                sessionId = sessionId,
                generatedAt = now,
                pdfPath = relativePathOf(pdf),
                csvPath = relativePathOf(csv),
                inspectedCount = summary.inspected,
                totalCount = summary.total,
                issueCount = summary.issues,
            ),
        )
        GeneratedReport(id, pdf, csv)
    }

    private suspend fun buildSessionReport(session: SessionEntity, now: Long, zone: ZoneId): SessionReport {
        val rowsByCharger = dao.getSessionResults(session.id).groupBy { it.chargerNo }
        val committedAt = dao.getSessionRecords(session.id).associate { it.chargerNo to it.inspectedAt }
        val photos = dao.getSessionPhotos(session.id).groupBy { it.chargerNo to it.component }

        val chargers = (1..session.chargerCount).map { n ->
            val rows = rowsByCharger[n].orEmpty().associateBy { it.component }
            val findings = Component.entries.map { component ->
                val row = rows[component.name]
                val result = row?.let { CheckResult.fromName(it.result) }
                val failed = result == CheckResult.FAIL
                ComponentFinding(
                    component = component,
                    result = result,
                    note = if (failed) row?.note.orEmpty() else "",
                    photoPaths = if (failed) {
                        photos[n to component.name].orEmpty().map { resolve(it.path).absolutePath }
                    } else {
                        emptyList()
                    },
                )
            }
            val answered = findings.mapNotNull { f -> f.result?.let { f.component to it } }.toMap()
            ChargerReport(
                number = n,
                label = ChargerLabels.label(session.chargerPrefix, n, session.chargerCount),
                status = InspectionRules.deriveStatus(answered, n in committedAt),
                inspectedAtMillis = committedAt[n],
                findings = findings,
            )
        }
        return SessionReport(
            sessionId = session.id,
            inspectorId = session.inspectorId,
            startedAtMillis = session.startedAt,
            generatedAtMillis = now,
            zone = zone,
            chargers = chargers,
        )
    }

    // ------------------------------------------------------------------ history

    fun observeReportRows(): Flow<List<ReportRow>> = dao.observeReportRows()

    suspend fun deleteReport(reportId: Long) {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                val report = dao.getReport(reportId)
                if (report != null) {
                    resolve(report.pdfPath).delete()
                    resolve(report.csvPath).delete()
                    dao.deleteReport(reportId)
                    val session = dao.getSession(report.sessionId)
                    if (session?.finishedAt != null && dao.countReports(report.sessionId) == 0) {
                        dao.deleteSession(report.sessionId)
                        File(filesRoot, "reports/session_${report.sessionId}").deleteRecursively()
                    }
                }
            }
        }
    }
}
