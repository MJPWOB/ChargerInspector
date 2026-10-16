package com.example.evinspector.domain

/**
 * The checkable parts of a charger, in the order they appear on the form. The seven named parts
 * are required on every charger; [MISC] is an optional catch-all for anything else.
 */
enum class Component(val label: String, val required: Boolean = true) {
    CABLE_1("Cable 1"),
    CABLE_2("Cable 2"),
    PISTOL_1("Pistol 1"),
    PISTOL_2("Pistol 2"),
    HOLSTER_1("Holster 1"),
    HOLSTER_2("Holster 2"),
    SCREEN("Screen"),
    MISC("Misc", required = false);

    companion object {
        /** The seven named components that must all be answered before a charger can be saved. */
        val core: List<Component> = Component.entries.filter { it.required }

        fun fromName(name: String): Component? = Component.entries.firstOrNull { it.name == name }
    }
}

/**
 * PASS = fine. NOTE = worth recording (comment and/or photos) but not a fault, so it never turns
 * a charger red. FAIL = a fault; needs a description and turns the charger red.
 */
enum class CheckResult {
    PASS,
    NOTE,
    FAIL;

    companion object {
        fun fromName(name: String): CheckResult? = CheckResult.entries.firstOrNull { it.name == name }
    }
}

/**
 * Dashboard colour states:
 * UNCHECKED = grey, PARTIAL = yellow, CLEAR = green, NOTED = blue (saved, has notes but no
 * failures), ISSUES = red.
 */
enum class ChargerStatus { UNCHECKED, PARTIAL, CLEAR, NOTED, ISSUES }

/** One component's answer as held in the form. `result == null` means not answered yet. */
data class ComponentInput(
    val result: CheckResult? = null,
    val note: String = "",
)

/** What the dashboard needs for one tile. */
data class ChargerTile(
    val number: Int,
    val label: String,
    val status: ChargerStatus,
)

data class DashboardData(
    val inspectorId: String,
    val tiles: List<ChargerTile>,
) {
    val total: Int get() = tiles.size

    /** "Inspected" means saved with Save & Return (green, blue or red), not merely started. */
    val inspected: Int
        get() = tiles.count {
            it.status == ChargerStatus.CLEAR || it.status == ChargerStatus.NOTED || it.status == ChargerStatus.ISSUES
        }
}
