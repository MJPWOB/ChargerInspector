package com.example.evinspector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.evinspector.domain.ChargerStatus

@Composable
fun EvInspectorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Status colours from the brief (grey / green / red / yellow). Every status also has a text
 * label, so the dashboard is still readable for colour-blind users.
 */
object StatusStyle {
    fun container(status: ChargerStatus): Color = when (status) {
        ChargerStatus.UNCHECKED -> Color(0xFF757575)
        ChargerStatus.PARTIAL -> Color(0xFFF9A825)
        ChargerStatus.CLEAR -> Color(0xFF2E7D32)
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
        ChargerStatus.ISSUES -> "Issues"
    }
}
