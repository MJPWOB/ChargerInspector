package com.example.evinspector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.evinspector.domain.ChargerStatus
import com.example.evinspector.domain.CheckResult

@Composable
fun EvInspectorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * One colour language across the app: grey = unchecked, yellow = partial, green = pass/clear,
 * blue = note, red = fail/issues. Every colour also has a text label, so nothing relies on colour alone.
 */
object StatusStyle {
    fun container(status: ChargerStatus): Color = when (status) {
        ChargerStatus.UNCHECKED -> Color(0xFF757575)
        ChargerStatus.PARTIAL -> Color(0xFFF9A825)
        ChargerStatus.CLEAR -> Color(0xFF2E7D32)
        ChargerStatus.NOTED -> Color(0xFF1565C0)
        ChargerStatus.ISSUES -> Color(0xFFC62828)
    }

    fun onContainer(status: ChargerStatus): Color = when (status) {
        ChargerStatus.PARTIAL -> Color.Black
        else -> Color.White
    }

    fun label(status: ChargerStatus): String = when (status) {
        ChargerStatus.UNCHECKED -> "Not checked"
        ChargerStatus.PARTIAL -> "Partial"
        ChargerStatus.CLEAR -> "All clear"
        ChargerStatus.NOTED -> "Notes"
        ChargerStatus.ISSUES -> "Issues"
    }

    /** Colour of the Pass / Note / Fail buttons, matching the dashboard tile each leads to. */
    fun forResult(result: CheckResult): Color = container(
        when (result) {
            CheckResult.PASS -> ChargerStatus.CLEAR
            CheckResult.NOTE -> ChargerStatus.NOTED
            CheckResult.FAIL -> ChargerStatus.ISSUES
        },
    )

    fun resultLabel(result: CheckResult): String = when (result) {
        CheckResult.PASS -> "Pass"
        CheckResult.NOTE -> "Note"
        CheckResult.FAIL -> "Fail"
    }
}
