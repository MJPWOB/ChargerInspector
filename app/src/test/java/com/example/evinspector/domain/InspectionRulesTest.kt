package com.example.evinspector.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionRulesTest {

    private fun allAnswered(result: CheckResult = CheckResult.PASS): Map<Component, CheckResult> =
        Component.core.associateWith { result }

    private fun coreInputs(result: CheckResult = CheckResult.PASS, note: String = ""): Map<Component, ComponentInput> =
        Component.core.associateWith { ComponentInput(result, note) }

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
        // Green/blue/red only appear after Save & Return, never from auto-save alone.
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
    fun `committed with a note and no fail is noted (blue)`() {
        val results = allAnswered() + (Component.CABLE_2 to CheckResult.NOTE)
        assertEquals(ChargerStatus.NOTED, InspectionRules.deriveStatus(results, committed = true))
    }

    @Test
    fun `a fail outranks a note`() {
        val results = allAnswered() +
            (Component.CABLE_2 to CheckResult.NOTE) +
            (Component.SCREEN to CheckResult.FAIL)
        assertEquals(ChargerStatus.ISSUES, InspectionRules.deriveStatus(results, committed = true))
    }

    @Test
    fun `committed but incomplete never shows as clear`() {
        val results = mapOf(Component.CABLE_1 to CheckResult.PASS)
        assertEquals(ChargerStatus.PARTIAL, InspectionRules.deriveStatus(results, committed = true))
    }

    @Test
    fun `committed but incomplete never shows as noted either`() {
        val results = mapOf(Component.CABLE_1 to CheckResult.NOTE)
        assertEquals(ChargerStatus.PARTIAL, InspectionRules.deriveStatus(results, committed = true))
    }

    @Test
    fun `misc is optional so a charger without it can be clear`() {
        assertEquals(ChargerStatus.CLEAR, InspectionRules.deriveStatus(allAnswered(), committed = true))
    }

    @Test
    fun `a misc note makes an otherwise clear charger blue`() {
        val results = allAnswered() + (Component.MISC to CheckResult.NOTE)
        assertEquals(ChargerStatus.NOTED, InspectionRules.deriveStatus(results, committed = true))
    }

    @Test
    fun `a misc fail makes an otherwise clear charger red`() {
        val results = allAnswered() + (Component.MISC to CheckResult.FAIL)
        assertEquals(ChargerStatus.ISSUES, InspectionRules.deriveStatus(results, committed = true))
    }

    @Test
    fun `misc alone does not complete a charger`() {
        val results = mapOf(Component.MISC to CheckResult.NOTE)
        assertEquals(ChargerStatus.PARTIAL, InspectionRules.deriveStatus(results, committed = true))
    }

    // ---- validate ----

    @Test
    fun `empty form reports every named component as unanswered but not misc`() {
        val problems = InspectionRules.validate(emptyMap())
        assertEquals(Component.core.size, problems.size)
        assertTrue(problems.all { it.kind == ValidationProblem.Kind.UNANSWERED })
        assertFalse(problems.any { it.component == Component.MISC })
    }

    @Test
    fun `all passes with no notes can be saved`() {
        assertTrue(InspectionRules.canSave(coreInputs(CheckResult.PASS)))
    }

    @Test
    fun `fail without a note blocks saving`() {
        val inputs = coreInputs() + (Component.PISTOL_2 to ComponentInput(CheckResult.FAIL, ""))
        val problems = InspectionRules.validate(inputs)
        assertEquals(listOf(ValidationProblem(Component.PISTOL_2, ValidationProblem.Kind.MISSING_NOTE)), problems)
        assertFalse(InspectionRules.canSave(inputs))
    }

    @Test
    fun `whitespace-only note does not count as a description`() {
        val inputs = coreInputs() + (Component.CABLE_2 to ComponentInput(CheckResult.FAIL, "   \n "))
        assertFalse(InspectionRules.canSave(inputs))
    }

    @Test
    fun `fail with a note can be saved`() {
        val inputs = coreInputs() + (Component.HOLSTER_1 to ComponentInput(CheckResult.FAIL, "Cracked housing"))
        assertTrue(InspectionRules.canSave(inputs))
    }

    @Test
    fun `a photo alone is not enough for a fail`() {
        val inputs = coreInputs() + (Component.HOLSTER_1 to ComponentInput(CheckResult.FAIL, ""))
        assertFalse(InspectionRules.canSave(inputs, mapOf(Component.HOLSTER_1 to 2)))
    }

    @Test
    fun `note with neither comment nor photo blocks saving`() {
        val inputs = coreInputs() + (Component.SCREEN to ComponentInput(CheckResult.NOTE, ""))
        val problems = InspectionRules.validate(inputs)
        assertEquals(listOf(ValidationProblem(Component.SCREEN, ValidationProblem.Kind.MISSING_NOTE)), problems)
    }

    @Test
    fun `note with a comment can be saved`() {
        val inputs = coreInputs() + (Component.SCREEN to ComponentInput(CheckResult.NOTE, "Slightly scuffed"))
        assertTrue(InspectionRules.canSave(inputs))
    }

    @Test
    fun `note with only a photo can be saved`() {
        val inputs = coreInputs() + (Component.SCREEN to ComponentInput(CheckResult.NOTE, ""))
        assertTrue(InspectionRules.canSave(inputs, mapOf(Component.SCREEN to 1)))
    }

    @Test
    fun `note with a photo count of zero is still blocked`() {
        val inputs = coreInputs() + (Component.SCREEN to ComponentInput(CheckResult.NOTE, "  "))
        assertFalse(InspectionRules.canSave(inputs, mapOf(Component.SCREEN to 0)))
    }

    @Test
    fun `leftover note on a pass does not block saving`() {
        // The note is kept if the inspector flips Fail -> Pass, and is purged on save.
        assertTrue(InspectionRules.canSave(coreInputs(CheckResult.PASS, "old note")))
    }

    @Test
    fun `unanswered misc does not block saving`() {
        val inputs = coreInputs() + (Component.MISC to ComponentInput(null, "typed but not selected"))
        assertTrue(InspectionRules.canSave(inputs))
    }

    @Test
    fun `misc fail still needs a description`() {
        val inputs = coreInputs() + (Component.MISC to ComponentInput(CheckResult.FAIL, ""))
        assertEquals(
            listOf(ValidationProblem(Component.MISC, ValidationProblem.Kind.MISSING_NOTE)),
            InspectionRules.validate(inputs),
        )
    }

    @Test
    fun `misc note with a comment can be saved`() {
        val inputs = coreInputs() + (Component.MISC to ComponentInput(CheckResult.NOTE, "Bay lines faded"))
        assertTrue(InspectionRules.canSave(inputs))
    }

    // ---- markAllPass ----

    @Test
    fun `mark all as pass fills every unanswered named component`() {
        val result = InspectionRules.markAllPass(emptyMap())
        assertEquals(Component.core.toSet(), result.keys)
        assertTrue(result.values.all { it.result == CheckResult.PASS })
        assertTrue(InspectionRules.canSave(result))
    }

    @Test
    fun `mark all as pass keeps existing fails and notes`() {
        val start = mapOf(
            Component.CABLE_1 to ComponentInput(CheckResult.FAIL, "Frayed"),
            Component.SCREEN to ComponentInput(CheckResult.NOTE, "Scuffed"),
        )
        val result = InspectionRules.markAllPass(start)
        assertEquals(ComponentInput(CheckResult.FAIL, "Frayed"), result[Component.CABLE_1])
        assertEquals(ComponentInput(CheckResult.NOTE, "Scuffed"), result[Component.SCREEN])
        assertEquals(CheckResult.PASS, result[Component.PISTOL_1]?.result)
    }

    @Test
    fun `mark all as pass never touches misc`() {
        assertEquals(null, InspectionRules.markAllPass(emptyMap())[Component.MISC])
        val withMisc = mapOf(Component.MISC to ComponentInput(CheckResult.NOTE, "x"))
        assertEquals(ComponentInput(CheckResult.NOTE, "x"), InspectionRules.markAllPass(withMisc)[Component.MISC])
    }

    @Test
    fun `mark all as pass drops stray text from unanswered components`() {
        val start = mapOf(Component.CABLE_2 to ComponentInput(null, "typed then cleared"))
        assertEquals(ComponentInput(CheckResult.PASS, ""), InspectionRules.markAllPass(start)[Component.CABLE_2])
    }

    @Test
    fun `mark all as pass on a fully answered form changes nothing`() {
        val start = coreInputs(CheckResult.NOTE, "n")
        assertEquals(start, InspectionRules.markAllPass(start))
    }

    @Test
    fun `hasUnanswered reflects the named components only`() {
        assertTrue(InspectionRules.hasUnanswered(emptyMap()))
        assertTrue(InspectionRules.hasUnanswered(mapOf(Component.MISC to ComponentInput(CheckResult.NOTE, "x"))))
        assertFalse(InspectionRules.hasUnanswered(coreInputs()))
    }

    // ---- misc helpers and the brief ----

    @Test
    fun `fail and note carry detail but pass and nothing do not`() {
        assertTrue(InspectionRules.carriesDetail(CheckResult.FAIL))
        assertTrue(InspectionRules.carriesDetail(CheckResult.NOTE))
        assertFalse(InspectionRules.carriesDetail(CheckResult.PASS))
        assertFalse(InspectionRules.carriesDetail(null))
    }

    @Test
    fun `named components match the brief and misc sits outside them`() {
        assertEquals(
            listOf("Cable 1", "Cable 2", "Pistol 1", "Pistol 2", "Holster 1", "Holster 2", "Screen"),
            Component.core.map { it.label },
        )
        assertEquals("Misc", Component.MISC.label)
        assertFalse(Component.MISC.required)
        assertFalse(Component.MISC in Component.core)
        assertEquals(Component.MISC, Component.entries.last())
    }
}
