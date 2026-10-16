package com.example.evinspector.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CsvExporterTest {

    private val utc = ZoneId.of("UTC")
    private val inspectedAt = Instant.parse("2026-10-16T09:12:03Z").toEpochMilli()

    /** All named components get [default]; Misc stays unanswered, as it does in a real report. */
    private fun findings(
        vararg overrides: Pair<Component, ComponentFinding>,
        default: CheckResult? = CheckResult.PASS,
    ): List<ComponentFinding> {
        val map = overrides.toMap()
        return Component.entries.map { c ->
            map[c] ?: ComponentFinding(c, if (c.required) default else null, "", emptyList())
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
        assertEquals(8, Component.entries.size) // seven named components plus Misc
        assertEquals(1 + 3 * 8, lines(csv).size)
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
    fun `unanswered misc exports as N-A`() {
        val csv = CsvExporter.build(
            report(listOf(ChargerReport(1, "CH01", ChargerStatus.CLEAR, inspectedAt, findings()))),
        )
        assertEquals("7,J. Smith,CH01,CLEAR,2026-10-16T09:12:03Z,Misc,N/A,,", lines(csv).last())
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
    fun `note component exports as NOTE with its comment and photo count`() {
        val note = ComponentFinding(Component.SCREEN, CheckResult.NOTE, "Minor scuff", listOf("a.jpg"))
        val csv = CsvExporter.build(
            report(
                listOf(
                    ChargerReport(4, "CH04", ChargerStatus.NOTED, inspectedAt, findings(Component.SCREEN to note)),
                ),
            ),
        )
        val row = lines(csv).first { it.contains(",Screen,") }
        assertEquals("7,J. Smith,CH04,NOTED,2026-10-16T09:12:03Z,Screen,NOTE,Minor scuff,1", row)
    }

    @Test
    fun `photo-only note exports a blank description and the photo count`() {
        val note = ComponentFinding(Component.MISC, CheckResult.NOTE, "", listOf("a.jpg", "b.jpg"))
        val csv = CsvExporter.build(
            report(
                listOf(
                    ChargerReport(4, "CH04", ChargerStatus.NOTED, inspectedAt, findings(Component.MISC to note)),
                ),
            ),
        )
        assertEquals("7,J. Smith,CH04,NOTED,2026-10-16T09:12:03Z,Misc,NOTE,,2", lines(csv).last())
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
    fun `summary counts chargers and components by status`() {
        val failing = ComponentFinding(Component.CABLE_1, CheckResult.FAIL, "frayed", emptyList())
        val noting = ComponentFinding(Component.SCREEN, CheckResult.NOTE, "scuffed", emptyList())
        val r = report(
            listOf(
                ChargerReport(1, "CH01", ChargerStatus.CLEAR, inspectedAt, findings()),
                ChargerReport(2, "CH02", ChargerStatus.ISSUES, inspectedAt, findings(Component.CABLE_1 to failing)),
                ChargerReport(3, "CH03", ChargerStatus.NOTED, inspectedAt, findings(Component.SCREEN to noting)),
                ChargerReport(4, "CH04", ChargerStatus.PARTIAL, null, findings()),
                ChargerReport(5, "CH05", ChargerStatus.UNCHECKED, null, findings(default = null)),
            ),
        )
        val s = r.summary()
        assertEquals(5, s.total)
        assertEquals(3, s.inspected) // green + blue + red
        assertEquals(1, s.clear)
        assertEquals(1, s.noted)
        assertEquals(1, s.issues)
        assertEquals(1, s.partial)
        assertEquals(1, s.unchecked)
        assertEquals(1, s.failedComponents)
        assertEquals(1, s.notedComponents)
        assertEquals(60, s.percentInspected)
    }

    @Test
    fun `summary of an empty report does not divide by zero`() {
        assertEquals(0, report(emptyList()).summary().percentInspected)
    }
}
