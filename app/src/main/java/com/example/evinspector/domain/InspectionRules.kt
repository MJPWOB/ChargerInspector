package com.example.evinspector.domain

data class ValidationProblem(val component: Component, val kind: Kind) {
    enum class Kind { UNANSWERED, MISSING_NOTE }
}

object InspectionRules {
    const val MAX_NOTE_LENGTH = 500
    const val MAX_PHOTOS_PER_COMPONENT = 3

    /** Fail and Note both carry a comment and photos; Pass (or nothing) does not. */
    fun carriesDetail(result: CheckResult?): Boolean =
        result == CheckResult.FAIL || result == CheckResult.NOTE

    /**
     * Derives a charger's dashboard status.
     *
     * [results] holds only the components that have been answered. [committed] is true once the
     * inspector has pressed Save & Return, and false again as soon as anything is edited after that.
     *
     * - Nothing answered and not committed -> UNCHECKED (grey)
     * - Anything answered but not committed -> PARTIAL (yellow): paused or incomplete
     * - Committed with all seven named components answered:
     *   any FAIL -> ISSUES (red); otherwise any NOTE -> NOTED (blue); otherwise CLEAR (green)
     *
     * Misc is optional, so it never blocks completion, but a Misc fail or note counts like any other.
     */
    fun deriveStatus(results: Map<Component, CheckResult>, committed: Boolean): ChargerStatus {
        val coreAnswered = Component.core.all { results.containsKey(it) }
        return when {
            committed && coreAnswered -> when {
                results.values.any { it == CheckResult.FAIL } -> ChargerStatus.ISSUES
                results.values.any { it == CheckResult.NOTE } -> ChargerStatus.NOTED
                else -> ChargerStatus.CLEAR
            }
            committed || results.isNotEmpty() -> ChargerStatus.PARTIAL
            else -> ChargerStatus.UNCHECKED
        }
    }

    /**
     * Everything that must be fixed before Save & Return is allowed:
     * - every named component answered (Misc may stay empty),
     * - every FAIL has a non-blank description,
     * - every NOTE has a comment or at least one photo ([photoCounts] says how many each has).
     */
    fun validate(
        inputs: Map<Component, ComponentInput>,
        photoCounts: Map<Component, Int> = emptyMap(),
    ): List<ValidationProblem> {
        val problems = mutableListOf<ValidationProblem>()
        for (component in Component.entries) {
            val input = inputs[component] ?: ComponentInput()
            val result = input.result
            if (result == null) {
                if (component.required) {
                    problems += ValidationProblem(component, ValidationProblem.Kind.UNANSWERED)
                }
            } else if (result == CheckResult.FAIL && input.note.isBlank()) {
                problems += ValidationProblem(component, ValidationProblem.Kind.MISSING_NOTE)
            } else if (result == CheckResult.NOTE && input.note.isBlank() && (photoCounts[component] ?: 0) == 0) {
                problems += ValidationProblem(component, ValidationProblem.Kind.MISSING_NOTE)
            }
        }
        return problems
    }

    fun canSave(
        inputs: Map<Component, ComponentInput>,
        photoCounts: Map<Component, Int> = emptyMap(),
    ): Boolean = validate(inputs, photoCounts).isEmpty()

    /** True while at least one of the seven named components has no answer. */
    fun hasUnanswered(inputs: Map<Component, ComponentInput>): Boolean =
        Component.core.any { inputs[it]?.result == null }

    /**
     * "Mark all as pass": sets every unanswered named component to PASS. Anything already answered
     * (including Fails and Notes) is left exactly as it is, and Misc is never touched.
     */
    fun markAllPass(inputs: Map<Component, ComponentInput>): Map<Component, ComponentInput> {
        val updated = inputs.toMutableMap()
        for (component in Component.core) {
            if (inputs[component]?.result == null) {
                updated[component] = ComponentInput(CheckResult.PASS)
            }
        }
        return updated
    }
}
