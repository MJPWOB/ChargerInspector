package com.example.evinspector.domain

data class ValidationProblem(val component: Component, val kind: Kind) {
    enum class Kind { UNANSWERED, MISSING_NOTE }
}

object InspectionRules {
    const val MAX_NOTE_LENGTH = 500
    const val MAX_PHOTOS_PER_COMPONENT = 3

    /**
     * Derives a charger's dashboard status.
     *
     * [results] holds only the components that have been answered. [committed] is true once the
     * inspector has pressed Save & Return, and false again as soon as anything is edited after that.
     *
     * - Nothing answered and not committed -> UNCHECKED (grey)
     * - Anything answered but not committed  -> PARTIAL (yellow): paused or incomplete
     * - Committed with all components answered -> ISSUES (red) if any FAIL, else CLEAR (green)
     */
    fun deriveStatus(results: Map<Component, CheckResult>, committed: Boolean): ChargerStatus {
        val allAnswered = results.size == Component.entries.size
        return when {
            committed && allAnswered ->
                if (results.values.any { it == CheckResult.FAIL }) ChargerStatus.ISSUES else ChargerStatus.CLEAR
            committed || results.isNotEmpty() -> ChargerStatus.PARTIAL
            else -> ChargerStatus.UNCHECKED
        }
    }

    /**
     * Everything that must be fixed before Save & Return is allowed: every component answered,
     * and every FAIL carrying a non-blank damage description.
     */
    fun validate(inputs: Map<Component, ComponentInput>): List<ValidationProblem> {
        val problems = mutableListOf<ValidationProblem>()
        for (component in Component.entries) {
            val input = inputs[component] ?: ComponentInput()
            when {
                input.result == null ->
                    problems += ValidationProblem(component, ValidationProblem.Kind.UNANSWERED)
                input.result == CheckResult.FAIL && input.note.isBlank() ->
                    problems += ValidationProblem(component, ValidationProblem.Kind.MISSING_NOTE)
            }
        }
        return problems
    }

    fun canSave(inputs: Map<Component, ComponentInput>): Boolean = validate(inputs).isEmpty()
}
