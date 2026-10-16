package com.example.evinspector.data

import android.content.Context
import androidx.core.content.edit
import com.example.evinspector.domain.ChargerLabels

data class ChargerConfig(val prefix: String, val count: Int)

/** Remembers the last charger configuration (label prefix and fleet size) between sessions. */
class ChargerConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("charger_config", Context.MODE_PRIVATE)

    fun load(): ChargerConfig {
        val prefix = prefs.getString(KEY_PREFIX, null)
            ?.takeIf { ChargerLabels.isValidPrefix(it) }
            ?: ChargerLabels.DEFAULT_PREFIX
        val count = prefs.getInt(KEY_COUNT, ChargerLabels.DEFAULT_COUNT)
            .takeIf { ChargerLabels.isValidCount(it) }
            ?: ChargerLabels.DEFAULT_COUNT
        return ChargerConfig(prefix, count)
    }

    fun save(config: ChargerConfig) {
        prefs.edit {
            putString(KEY_PREFIX, config.prefix)
            putInt(KEY_COUNT, config.count)
        }
    }

    private companion object {
        const val KEY_PREFIX = "prefix"
        const val KEY_COUNT = "count"
    }
}
