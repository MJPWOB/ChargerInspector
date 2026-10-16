package com.example.evinspector.domain

/** Builds labels such as CH01..CH52 from a prefix and a charger count. */
object ChargerLabels {
    const val DEFAULT_PREFIX = "CH"
    const val DEFAULT_COUNT = 52
    const val MIN_COUNT = 1
    const val MAX_COUNT = 200
    const val MAX_PREFIX_LENGTH = 6

    /** At least two digits; widens for larger fleets so labels stay the same length (CH001..CH120). */
    fun digitsFor(count: Int): Int = maxOf(2, count.toString().length)

    fun label(prefix: String, number: Int, count: Int): String =
        prefix + number.toString().padStart(digitsFor(count), '0')

    fun normalizePrefix(raw: String): String = raw.trim().uppercase()

    fun isValidPrefix(prefix: String): Boolean =
        prefix.length in 1..MAX_PREFIX_LENGTH && prefix.all { it in 'A'..'Z' || it in '0'..'9' }

    fun isValidCount(count: Int): Boolean = count in MIN_COUNT..MAX_COUNT
}
