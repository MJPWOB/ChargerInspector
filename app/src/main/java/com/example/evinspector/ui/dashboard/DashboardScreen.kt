package com.example.evinspector.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.evinspector.domain.ChargerStatus
import com.example.evinspector.domain.ChargerTile
import com.example.evinspector.domain.ExportWarning
import com.example.evinspector.report.ShareHelper
import com.example.evinspector.ui.theme.StatusStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onOpenCharger: (Int) -> Unit,
    onHistory: () -> Unit,
    onFinished: () -> Unit,
) {
    val data by vm.dashboard.collectAsStateWithLifecycle()
    val reportUi by vm.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmGenerate by remember { mutableStateOf(false) }
    // null = dialog hidden; otherwise whether this session already has a generated report.
    var confirmFinish by remember { mutableStateOf<Boolean?>(null) }

    val current = data
    val warning = current?.let { ExportWarning.from(it.tiles) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Charger inspection")
                        if (current != null) {
                            Text("Inspector: ${current.inspectorId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Previous reports") },
                            onClick = {
                                menuOpen = false
                                onHistory()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Finish session") },
                            onClick = {
                                menuOpen = false
                                scope.launch { confirmFinish = vm.hasReports() }
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = {
                            if (warning != null && warning.hasWarning) confirmGenerate = true else vm.generate()
                        },
                        enabled = current != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("Generate Reports  •  ${current?.inspected ?: 0}/${current?.total ?: 0} Inspected")
                    }
                }
            }
        },
    ) { padding ->
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Legend() }
                items(current.tiles, key = { it.number }) { tile ->
                    ChargerTileView(tile) { onOpenCharger(tile.number) }
                }
            }
        }
    }

    if (confirmGenerate && warning != null) {
        AlertDialog(
            onDismissRequest = { confirmGenerate = false },
            title = { Text("Some chargers are not fully inspected") },
            text = {
                val parts = buildList {
                    if (warning.unchecked > 0) add("${warning.unchecked} not checked")
                    if (warning.partial > 0) add("${warning.partial} partially checked")
                }
                Text(
                    parts.joinToString(" and ") +
                        ". The report will list them as not inspected. You can go back and finish them first.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmGenerate = false
                    vm.generate()
                }) { Text("Generate anyway") }
            },
            dismissButton = { TextButton(onClick = { confirmGenerate = false }) { Text("Go back") } },
        )
    }

    when (val state = reportUi) {
        ReportUi.Idle -> Unit
        ReportUi.Generating -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Generating reports") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Building the PDF and CSV…")
                }
            },
            confirmButton = {},
        )
        is ReportUi.Ready -> AlertDialog(
            onDismissRequest = vm::dismissReport,
            title = { Text("Reports ready") },
            text = {
                Text("The PDF and CSV are saved on this device and listed under Previous reports. Share them now?")
            },
            confirmButton = {
                Button(onClick = { ShareHelper.shareReports(context, state.report.pdf, state.report.csv) }) {
                    Text("Share")
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissReport) { Text("Done") } },
        )
        is ReportUi.Failed -> AlertDialog(
            onDismissRequest = vm::dismissReport,
            title = { Text("Could not generate reports") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = vm::dismissReport) { Text("OK") } },
        )
    }

    confirmFinish?.let { hasReports ->
        AlertDialog(
            onDismissRequest = { confirmFinish = null },
            title = { Text("Finish this session?") },
            text = {
                Text(
                    if (hasReports) {
                        "Photos stored on this device will be deleted to free space. Your generated reports stay available under Previous reports."
                    } else {
                        "No report has been generated for this session. Finishing now permanently deletes all of its results and photos."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = null
                    vm.finish(onFinished)
                }) { Text(if (hasReports) "Finish" else "Delete and finish") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    // Five entries no longer fit on one line on narrow phones, so let them wrap.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (status in ChargerStatus.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(StatusStyle.container(status)),
                )
                Spacer(Modifier.width(4.dp))
                Text(StatusStyle.label(status), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ChargerTileView(tile: ChargerTile, onClick: () -> Unit) {
    val background = StatusStyle.container(tile.status)
    val foreground = StatusStyle.onContainer(tile.status)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${tile.label}, ${StatusStyle.label(tile.status)}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tile.label, color = foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(StatusStyle.label(tile.status), color = foreground, fontSize = 11.sp)
        }
    }
}
