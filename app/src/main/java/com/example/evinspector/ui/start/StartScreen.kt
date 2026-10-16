package com.example.evinspector.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evinspector.AppContainer
import com.example.evinspector.data.ChargerConfig
import com.example.evinspector.data.ChargerConfigStore
import com.example.evinspector.data.InspectionRepository
import com.example.evinspector.domain.ChargerLabels
import kotlinx.coroutines.launch

class StartViewModel(
    private val repository: InspectionRepository,
    private val configStore: ChargerConfigStore,
) : ViewModel() {

    private val saved = configStore.load()

    // Deliberately not pre-filled: on a shared device each inspector must identify themselves.
    var inspectorId by mutableStateOf("")
    var prefix by mutableStateOf(saved.prefix)
    var countText by mutableStateOf(saved.count.toString())
    var error by mutableStateOf<String?>(null)
    var starting by mutableStateOf(false)

    fun start(onStarted: (Long) -> Unit) {
        if (starting) return
        val id = inspectorId.trim()
        val normalisedPrefix = ChargerLabels.normalizePrefix(prefix)
        val count = countText.trim().toIntOrNull()

        val message = when {
            id.isEmpty() -> "Enter your name or ID to continue."
            !ChargerLabels.isValidPrefix(normalisedPrefix) ->
                "Charger prefix must be 1 to ${ChargerLabels.MAX_PREFIX_LENGTH} letters or digits."
            count == null || !ChargerLabels.isValidCount(count) ->
                "Charger count must be between ${ChargerLabels.MIN_COUNT} and ${ChargerLabels.MAX_COUNT}."
            else -> null
        }
        error = message
        if (message != null || count == null) return

        starting = true
        viewModelScope.launch {
            configStore.save(ChargerConfig(normalisedPrefix, count))
            val sessionId = repository.startSession(id, normalisedPrefix, count)
            starting = false
            onStarted(sessionId)
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { StartViewModel(container.repository, container.configStore) }
        }
    }
}

@Composable
fun StartScreen(
    vm: StartViewModel,
    onStarted: (Long) -> Unit,
    onHistory: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Charger Inspector", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Enter your name or ID to begin. It is recorded on every report you generate.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = vm.inspectorId,
                onValueChange = { if (it.length <= 40) vm.inspectorId = it },
                label = { Text("Inspector name / ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Chargers", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.prefix,
                    onValueChange = { if (it.length <= ChargerLabels.MAX_PREFIX_LENGTH) vm.prefix = it },
                    label = { Text("Label prefix") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = vm.countText,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) vm.countText = it },
                    label = { Text("How many") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            val prefix = ChargerLabels.normalizePrefix(vm.prefix)
            val count = vm.countText.toIntOrNull()
            if (count != null && ChargerLabels.isValidCount(count) && ChargerLabels.isValidPrefix(prefix)) {
                Text(
                    "Chargers will be labelled ${ChargerLabels.label(prefix, 1, count)} to ${ChargerLabels.label(prefix, count, count)}.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { vm.start(onStarted) },
                enabled = !vm.starting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start inspection") }

            TextButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Previous reports")
            }
        }
    }
}
