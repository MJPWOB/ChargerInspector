package com.example.evinspector.domain

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Long-format CSV: exactly one row per charger per component (the seven named parts plus Misc),
 * so it loads straight into a maintenance database. Chargers that were never inspected still
 * appear, with NOT_CHECKED (Misc shows N/A when nothing was reported). `result` is PASS, NOTE,
 * FAIL, NOT_CHECKED or N/A.
 */
object CsvExporter {
    val HEADER = listOf(
        "session_id",
        "inspector_id",
        "charger",
        "charger_status",
        "inspected_at",
        "component",
        "result",
        "description",
        "photo_count",
    )

    private const val EOL = "\r\n"

    fun build(report: SessionReport): String {
        val sb = StringBuilder()
        sb.append(row(HEADER))
        for (charger in report.chargers) {
            val inspectedAt = charger.inspectedAtMillis?.let { iso(it, report) } ?: ""
            for (finding in charger.findings) {
                val detailed = InspectionRules.carriesDetail(finding.result)
                // An unanswered optional item (Misc) is "N/A"; an unanswered named one is "NOT_CHECKED".
                val resultText = finding.result?.name
                    ?: if (finding.component.required) "NOT_CHECKED" else "N/A"
                sb.append(
                    row(
                        listOf(
                            report.sessionId.toString(),
                            report.inspectorId,
                            charger.label,
                            charger.status.name,
                            inspectedAt,
                            finding.component.label,
                            resultText,
                            if (detailed) finding.note.trim() else "",
                            if (detailed) finding.photoPaths.size.toString() else "",
                        ),
                    ),
                )
            }
        }
        return sb.toString()
    }

    private fun iso(millis: Long, report: SessionReport): String =
        Instant.ofEpochMilli(millis)
            .truncatedTo(ChronoUnit.SECONDS)
            .atZone(report.zone)
            .toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun row(cells: List<String>): String = cells.joinToString(",") { escape(it) } + EOL

    /**
     * RFC 4180 quoting, plus a guard against spreadsheet formula injection: free text that starts
     * with = + - @ (or a tab/CR) gets a leading apostrophe so Excel/Sheets treat it as text.
     */
    fun escape(value: String): String {
        var v = value
        if (v.isNotEmpty() && v[0] in FORMULA_TRIGGERS) v = "'$v"
        val needsQuotes = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r')
}
