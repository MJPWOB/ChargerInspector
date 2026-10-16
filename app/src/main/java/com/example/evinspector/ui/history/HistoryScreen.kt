package com.example.evinspector.ui.history

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evinspector.AppContainer
import com.example.evinspector.data.InspectionRepository
import com.example.evinspector.data.ReportRow
import com.example.evinspector.domain.TimeFormats
import com.example.evinspector.report.ShareHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId

class HistoryViewModel(private val repository: InspectionRepository) : ViewModel() {

    /** null while loading. */
    val rows: StateFlow<List<ReportRow>?> =
        repository.observeReportRows()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun file(relativePath: String): File = repository.resolve(relativePath)

    fun delete(reportId: Long) {
        repository.appScope.launch { repository.deleteReport(reportId) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HistoryViewModel(container.repository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ReportRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Previous reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val list = rows
        when {
            list == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No reports yet.\nReports you generate appear here so you can view or share them again.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.reportId }) { row ->
                    ReportCard(row, vm, onDelete = { pendingDelete = row })
                }
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this report?") },
            text = { Text("The PDF and CSV will be permanently removed from this device. Make sure you have shared them first.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(row.reportId)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReportCard(row: ReportRow, vm: HistoryViewModel, onDelete: () -> Unit) {
    val context = LocalContext.current
    val pdf = vm.file(row.pdfPath)
    val csv = vm.file(row.csvPath)
    val available = pdf.exists() && csv.exists()
    val sizes = "PDF ${Formatter.formatShortFileSize(context, pdf.length())}, CSV ${Formatter.formatShortFileSize(context, csv.length())}"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                TimeFormats.human(row.generatedAt, ZoneId.systemDefault()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Inspector: ${row.inspectorId}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${row.inspectedCount}/${row.totalCount} inspected  •  ${row.issueCount} with issues",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (available) sizes else "Files are no longer on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = pdf.exists(),
                    onClick = {
                        if (!ShareHelper.openPdf(context, pdf)) {
                            Toast.makeText(context, "No PDF viewer is installed.", Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text("View PDF") }
                TextButton(enabled = available, onClick = { ShareHelper.shareReports(context, pdf, csv) }) {
                    Text("Share")
                }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
