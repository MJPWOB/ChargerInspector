package com.example.evinspector.domain

/** The seven checkable parts of a charger, in the order they appear on the form. */
enum class Component(val label: String) {
    CABLE_1("Cable 1"),
    CABLE_2("Cable 2"),
    PISTOL_1("Pistol 1"),
    PISTOL_2("Pistol 2"),
    HOLSTER_1("Holster 1"),
    HOLSTER_2("Holster 2"),
    SCREEN("Screen");

    companion object {
        fun fromName(name: String): Component? = entries.firstOrNull { it.name == name }
    }
}

enum class CheckResult {
    PASS,
    FAIL;

    companion object {
        fun fromName(name: String): CheckResult? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Dashboard colour states:
 * UNCHECKED = grey, PARTIAL = yellow, CLEAR = green, ISSUES = red.
 */
enum class ChargerStatus { UNCHECKED, PARTIAL, CLEAR, ISSUES }

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

    /** "Inspected" means saved with Save & Return (green or red), not merely started. */
    val inspected: Int
        get() = tiles.count { it.status == ChargerStatus.CLEAR || it.status == ChargerStatus.ISSUES }
}
