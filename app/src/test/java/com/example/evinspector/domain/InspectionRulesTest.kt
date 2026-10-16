package com.example.evinspector.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionRulesTest {

    private fun allAnswered(result: CheckResult = CheckResult.PASS): Map<Component, CheckResult> =
        Component.entries.associateWith { result }

    // ---- deriveStatus ----

    @Test
    fun `nothing answered and not committed is unchecked`() {
        assertEquals(ChargerStatus.UNCHECKED, InspectionRules.deriveStatus(emptyMap(), committed = false))
    }

    @Test
    fun `some answered but not committed is partial`() {
        val results = mapOf(Component.CABLE_1 to CheckResult.PASS)
        assertEquals(ChargerStatus.PARTIAL, InspectionRules.deriveStatus(results, committed = false))
    }

    @Test
    fun `fully answered but not yet saved is still partial`() {
        // Green/red only appear after Save & Return, never from auto-save alone.
        assertEquals(ChargerStatus.PARTIAL, InspectionRules.deriveStatus(allAnswered(), committed = false))
    }

    @Test
    fun `committed with all passes is clear`() {
        assertEquals(ChargerStatus.CLEAR, InspectionRules.deriveStatus(allAnswered(), committed = true))
    }

    @Test
    fun `committed with one fail is issues`() {
        val results = allAnswered() + (Component.SCREEN to CheckResult.FAIL)
        assertEquals(ChargerStatus.ISSUES, InspectionRules.deriveStatus(results, committed = true))
    }

    @Test
    fun `committed but incomplete never shows as clear`() {
        val results = mapOf(Component.CABLE_1 to CheckResult.PASS)
        assertEquals(ChargerStatus.PARTIAL, InspectionRules.deriveStatus(results, committed = true))
    }

    // ---- validate ----

    @Test
    fun `empty form reports every component as unanswered`() {
        val problems = InspectionRules.validate(emptyMap())
        assertEquals(Component.entries.size, problems.size)
        assertTrue(problems.all { it.kind == ValidationProblem.Kind.UNANSWERED })
    }

    @Test
    fun `all passes with no notes can be saved`() {
        val inputs = Component.entries.associateWith { ComponentInput(CheckResult.PASS) }
        assertTrue(InspectionRules.canSave(inputs))
    }

    @Test
    fun `fail without a note blocks saving`() {
        val inputs = Component.entries.associateWith { ComponentInput(CheckResult.PASS) } +
            (Component.PISTOL_2 to ComponentInput(CheckResult.FAIL, ""))
        val problems = InspectionRules.validate(inputs)
        assertEquals(listOf(ValidationProblem(Component.PISTOL_2, ValidationProblem.Kind.MISSING_NOTE)), problems)
        assertFalse(InspectionRules.canSave(inputs))
    }

    @Test
    fun `whitespace-only note does not count as a description`() {
        val inputs = Component.entries.associateWith { ComponentInput(CheckResult.PASS) } +
            (Component.CABLE_2 to ComponentInput(CheckResult.FAIL, "   \n "))
        assertFalse(InspectionRules.canSave(inputs))
    }

    @Test
    fun `fail with a note can be saved`() {
        val inputs = Component.entries.associateWith { ComponentInput(CheckResult.PASS) } +
            (Component.HOLSTER_1 to ComponentInput(CheckResult.FAIL, "Cracked housing"))
        assertTrue(InspectionRules.canSave(inputs))
    }

    @Test
    fun `leftover note on a pass does not block saving`() {
        // The note is kept if the inspector flips Fail -> Pass, and is purged on save.
        val inputs = Component.entries.associateWith { ComponentInput(CheckResult.PASS, "old note") }
        assertTrue(InspectionRules.canSave(inputs))
    }

    @Test
    fun `component list matches the brief`() {
        assertEquals(
            listOf("Cable 1", "Cable 2", "Pistol 1", "Pistol 2", "Holster 1", "Holster 2", "Screen"),
            Component.entries.map { it.label },
        )
    }
}
