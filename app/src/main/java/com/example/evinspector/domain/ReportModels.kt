package com.example.evinspector.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One component's recorded outcome inside a report. Photo paths are absolute and only set for FAIL and NOTE. */
data class ComponentFinding(
    val component: Component,
    val result: CheckResult?,
    val note: String,
    val photoPaths: List<String>,
)

data class ChargerReport(
    val number: Int,
    val label: String,
    val status: ChargerStatus,
    val inspectedAtMillis: Long?,
    val findings: List<ComponentFinding>,
) {
    val failedFindings: List<ComponentFinding>
        get() = findings.filter { it.result == CheckResult.FAIL }

    val notedFindings: List<ComponentFinding>
        get() = findings.filter { it.result == CheckResult.NOTE }
}

/** Everything a report generator needs; contains no Android types so it can be unit tested. */
data class SessionReport(
    val sessionId: Long,
    val inspectorId: String,
    val startedAtMillis: Long,
    val generatedAtMillis: Long,
    val zone: ZoneId,
    val chargers: List<ChargerReport>,
)

data class ReportSummary(
    val total: Int,
    val inspected: Int,
    val clear: Int,
    val noted: Int,
    val issues: Int,
    val partial: Int,
    val unchecked: Int,
    val failedComponents: Int,
    val notedComponents: Int,
) {
    val percentInspected: Int
        get() = if (total == 0) 0 else (inspected * 100) / total
}

fun SessionReport.summary(): ReportSummary {
    val clear = chargers.count { it.status == ChargerStatus.CLEAR }
    val noted = chargers.count { it.status == ChargerStatus.NOTED }
    val issues = chargers.count { it.status == ChargerStatus.ISSUES }
    return ReportSummary(
        total = chargers.size,
        inspected = clear + noted + issues,
        clear = clear,
        noted = noted,
        issues = issues,
        partial = chargers.count { it.status == ChargerStatus.PARTIAL },
        unchecked = chargers.count { it.status == ChargerStatus.UNCHECKED },
        failedComponents = chargers.sumOf { it.failedFindings.size },
        notedComponents = chargers.sumOf { it.notedFindings.size },
    )
}

/** Shown before generating reports when some chargers are not fully inspected. */
data class ExportWarning(val unchecked: Int, val partial: Int) {
    val hasWarning: Boolean get() = unchecked + partial > 0

    companion object {
        fun from(tiles: List<ChargerTile>) = ExportWarning(
            unchecked = tiles.count { it.status == ChargerStatus.UNCHECKED },
            partial = tiles.count { it.status == ChargerStatus.PARTIAL },
        )
    }
}

object TimeFormats {
    private val human = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun human(millis: Long, zone: ZoneId): String =
        human.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun fileStamp(millis: Long, zone: ZoneId): String =
        fileStamp.format(Instant.ofEpochMilli(millis).atZone(zone))
}
