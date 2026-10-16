package com.example.evinspector.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTest {

    private fun tiles(vararg statuses: ChargerStatus) =
        statuses.mapIndexed { i, s -> ChargerTile(i + 1, "CH0${i + 1}", s) }

    @Test
    fun `inspected counts only saved chargers`() {
        val data = DashboardData(
            "x",
            tiles(
                ChargerStatus.CLEAR,
                ChargerStatus.ISSUES,
                ChargerStatus.PARTIAL,
                ChargerStatus.UNCHECKED,
            ),
        )
        assertEquals(4, data.total)
        assertEquals(2, data.inspected)
    }

    @Test
    fun `export warning counts unchecked and partial separately`() {
        val w = ExportWarning.from(
            tiles(
                ChargerStatus.CLEAR,
                ChargerStatus.PARTIAL,
                ChargerStatus.PARTIAL,
                ChargerStatus.UNCHECKED,
            ),
        )
        assertEquals(1, w.unchecked)
        assertEquals(2, w.partial)
        assertTrue(w.hasWarning)
    }

    @Test
    fun `no export warning when everything is inspected`() {
        val w = ExportWarning.from(tiles(ChargerStatus.CLEAR, ChargerStatus.ISSUES))
        assertFalse(w.hasWarning)
    }
}
