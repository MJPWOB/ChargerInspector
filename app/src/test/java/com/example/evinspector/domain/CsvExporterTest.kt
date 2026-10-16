package com.example.evinspector.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CsvExporterTest {

    private val utc = ZoneId.of("UTC")
    private val inspectedAt = Instant.parse("2026-10-16T09:12:03Z").toEpochMilli()

    private fun findings(
        vararg overrides: Pair<Component, ComponentFinding>,
        default: CheckResult? = CheckResult.PASS,
    ): List<ComponentFinding> {
        val map = overrides.toMap()
        return Component.entries.map { c ->
            map[c] ?: ComponentFinding(c, default, "", emptyList())
        }
    }

    private fun report(chargers: List<ChargerReport>) = SessionReport(
        sessionId = 7,
        inspectorId = "J. Smith",
        startedAtMillis = inspectedAt,
        generatedAtMillis = inspectedAt,
        zone = utc,
        chargers = chargers,
    )

    private fun lines(csv: String) = csv.split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun `header matches the documented columns`() {
        val csv = CsvExporter.build(report(emptyList()))
        assertEquals(
            "session_id,inspector_id,charger,charger_status,inspected_at,component,result,description,photo_count",
            lines(csv).single(),
        )
    }

    @Test
    fun `one row per charger per component plus header`() {
        val chargers = (1..3).map {
            ChargerReport(it, "CH0$it", ChargerStatus.CLEAR, inspectedAt, findings())
        }
        val csv = CsvExporter.build(report(chargers))
        assertEquals(1 + 3 * Component.entries.size, lines(csv).size)
    }

    @Test
    fun `rows end with CRLF`() {
        val csv = CsvExporter.build(report(emptyList()))
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun `passing component row`() {
        val csv = CsvExporter.build(
            report(listOf(ChargerReport(1, "CH01", ChargerStatus.CLEAR, inspectedAt, findings()))),
        )
        assertEquals("7,J. Smith,CH01,CLEAR,2026-10-16T09:12:03Z,Cable 1,PASS,,", lines(csv)[1])
    }

    @Test
    fun `failed component carries description and photo count`() {
        val fail = ComponentFinding(Component.PISTOL_1, CheckResult.FAIL, "Cracked, leaking", listOf("a.jpg", "b.jpg"))
        val csv = CsvExporter.build(
            report(
                listOf(
                    ChargerReport(12, "CH12", ChargerStatus.ISSUES, inspectedAt, findings(Component.PISTOL_1 to fail)),
                ),
            ),
        )
        val row = lines(csv).first { it.contains("Pistol 1") }
        assertEquals("7,J. Smith,CH12,ISSUES,2026-10-16T09:12:03Z,Pistol 1,FAIL,\"Cracked, leaking\",2", row)
    }

    @Test
    fun `unchecked charger still exports with NOT_CHECKED and blank timestamp`() {
        val csv = CsvExporter.build(
            report(
                listOf(
                    ChargerReport(
                        5, "CH05", ChargerStatus.UNCHECKED, null,
                        Component.entries.map { ComponentFinding(it, null, "", emptyList()) },
                    ),
                ),
            ),
        )
        assertEquals("7,J. Smith,CH05,UNCHECKED,,Cable 1,NOT_CHECKED,,", lines(csv)[1])
    }

    @Test
    fun `timestamp carries the local offset`() {
        val bst = ZoneId.of("Europe/London") // UTC+01:00 on this date
        val csv = CsvExporter.build(
            report(listOf(ChargerReport(1, "CH01", ChargerStatus.CLEAR, inspectedAt, findings()))).copy(zone = bst),
        )
        assertTrue(lines(csv)[1].contains("2026-10-16T10:12:03+01:00"))
    }

    @Test
    fun `escape quotes fields containing commas quotes and newlines`() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExporter.escape("line1\nline2"))
    }

    @Test
    fun `escape neutralises spreadsheet formulas`() {
        assertEquals("'=SUM(A1:A2)", CsvExporter.escape("=SUM(A1:A2)"))
        assertEquals("'+1", CsvExporter.escape("+1"))
        assertEquals("'-cracked", CsvExporter.escape("-cracked"))
        assertEquals("'@cmd", CsvExporter.escape("@cmd"))
        assertEquals("a=b", CsvExporter.escape("a=b")) // only a leading trigger matters
    }

    @Test
    fun `hostile description is neutralised in the full export`() {
        val fail = ComponentFinding(Component.SCREEN, CheckResult.FAIL, "=HYPERLINK(\"http://x\",\"y\")", emptyList())
        val csv = CsvExporter.build(
            report(
                listOf(
                    ChargerReport(1, "CH01", ChargerStatus.ISSUES, inspectedAt, findings(Component.SCREEN to fail)),
                ),
            ),
        )
        val row = lines(csv).first { it.contains(",Screen,") }
        assertTrue(row.contains("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\""))
    }

    @Test
    fun `summary counts chargers by status`() {
        val failing = ComponentFinding(Component.CABLE_1, CheckResult.FAIL, "frayed", emptyList())
        val r = report(
            listOf(
                ChargerReport(1, "CH01", ChargerStatus.CLEAR, inspectedAt, findings()),
                ChargerReport(2, "CH02", ChargerStatus.ISSUES, inspectedAt, findings(Component.CABLE_1 to failing)),
                ChargerReport(3, "CH03", ChargerStatus.PARTIAL, null, findings()),
                ChargerReport(4, "CH04", ChargerStatus.UNCHECKED, null, findings(default = null)),
            ),
        )
        val s = r.summary()
        assertEquals(4, s.total)
        assertEquals(2, s.inspected)
        assertEquals(1, s.clear)
        assertEquals(1, s.issues)
        assertEquals(1, s.partial)
        assertEquals(1, s.unchecked)
        assertEquals(1, s.failedComponents)
        assertEquals(50, s.percentInspected)
    }

    @Test
    fun `summary of an empty report does not divide by zero`() {
        assertEquals(0, report(emptyList()).summary().percentInspected)
    }
}
