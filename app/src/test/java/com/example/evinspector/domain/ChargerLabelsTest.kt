package com.example.evinspector.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargerLabelsTest {

    @Test
    fun `default fleet produces CH01 to CH52`() {
        assertEquals("CH01", ChargerLabels.label("CH", 1, 52))
        assertEquals("CH09", ChargerLabels.label("CH", 9, 52))
        assertEquals("CH52", ChargerLabels.label("CH", 52, 52))
    }

    @Test
    fun `labels widen for fleets of 100 or more so they stay uniform`() {
        assertEquals("CH001", ChargerLabels.label("CH", 1, 120))
        assertEquals("CH120", ChargerLabels.label("CH", 120, 120))
    }

    @Test
    fun `fleet of exactly 99 stays at two digits`() {
        assertEquals("CH99", ChargerLabels.label("CH", 99, 99))
    }

    @Test
    fun `prefix is normalised to trimmed upper case`() {
        assertEquals("BAY", ChargerLabels.normalizePrefix("  bay "))
    }

    @Test
    fun `prefix validation`() {
        assertTrue(ChargerLabels.isValidPrefix("CH"))
        assertTrue(ChargerLabels.isValidPrefix("DEPOT1"))
        assertFalse(ChargerLabels.isValidPrefix(""))
        assertFalse(ChargerLabels.isValidPrefix("TOOLONGX"))
        assertFalse(ChargerLabels.isValidPrefix("C-H"))
        assertFalse(ChargerLabels.isValidPrefix("ch")) // must be normalised first
    }

    @Test
    fun `count validation`() {
        assertFalse(ChargerLabels.isValidCount(0))
        assertTrue(ChargerLabels.isValidCount(1))
        assertTrue(ChargerLabels.isValidCount(52))
        assertTrue(ChargerLabels.isValidCount(ChargerLabels.MAX_COUNT))
        assertFalse(ChargerLabels.isValidCount(ChargerLabels.MAX_COUNT + 1))
    }
}
