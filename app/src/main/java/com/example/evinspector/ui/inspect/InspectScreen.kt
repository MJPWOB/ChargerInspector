package com.example.evinspector.ui.inspect

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.evinspector.data.PhotoEntity
import com.example.evinspector.data.PhotoProcessor
import com.example.evinspector.domain.ChargerStatus
import com.example.evinspector.domain.CheckResult
import com.example.evinspector.domain.Component
import com.example.evinspector.domain.ComponentInput
import com.example.evinspector.domain.InspectionRules
import com.example.evinspector.domain.ValidationProblem
import com.example.evinspector.report.ShareHelper
import com.example.evinspector.ui.theme.StatusStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val BigButtonHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectScreen(
    vm: InspectViewModel,
    onDone: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Anything typed but not yet written is flushed whenever the app leaves the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    val goBack: () -> Unit = {
        vm.flush()
        onDone()
    }
    BackHandler(onBack = goBack)

    // "COMPONENT_NAME|relative/path.jpg" for the photo being captured. Saveable so that it survives
    // the process being killed while the camera app is in the foreground.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val pending = pendingPhoto
        pendingPhoto = null
        val parts = pending?.split("|", limit = 2)
        if (parts != null && parts.size == 2) {
            Component.fromName(parts[0])?.let { vm.onPhotoResult(it, parts[1], success) }
        }
    }

    fun takePhoto(component: Component) {
        val file = vm.newPhotoFile(component)
        val relative = vm.relativePathOf(file)
        pendingPhoto = component.name + "|" + relative
        try {
            cameraLauncher.launch(ShareHelper.uriFor(context, file))
        } catch (e: ActivityNotFoundException) {
            pendingPhoto = null
            vm.onPhotoResult(component, relative, false)
            Toast.makeText(context, "No camera app is available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (ui.loaded) "Inspecting ${ui.label}" else "Inspecting…") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier.navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.loaded && !ui.canSave) {
                        Text(
                            blockerText(ui.problems),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { vm.saveAndReturn(onDone) },
                        enabled = ui.loaded && ui.canSave,
                        modifier = Modifier.fillMaxWidth().height(BigButtonHeight),
                    ) { Text("Save & Return", style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) { padding ->
        if (!ui.loaded) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "mark_all_pass") {
                    MarkAllPassButton(enabled = ui.hasUnanswered, onClick = vm::markAllPass)
                }
                items(Component.entries, key = { it.name }) { component ->
                    ComponentCard(
                        component = component,
                        input = ui.inputs[component] ?: ComponentInput(),
                        photos = ui.photos[component].orEmpty(),
                        photoFile = vm::photoFile,
                        onResult = { picked ->
                            // Misc is optional: tapping the selected button again clears it.
                            if (!component.required && ui.inputs[component]?.result == picked) {
                                vm.clearResult(component)
                            } else {
                                vm.setResult(component, picked)
                            }
                        },
                        onNote = { vm.setNote(component, it) },
                        onTakePhoto = { takePhoto(component) },
                        onRemovePhoto = vm::removePhoto,
                    )
                }
            }
        }
    }
}

private fun blockerText(problems: List<ValidationProblem>): String {
    val unanswered = problems.count { it.kind == ValidationProblem.Kind.UNANSWERED }
    val missing = problems.count { it.kind == ValidationProblem.Kind.MISSING_NOTE }
    return buildList {
        if (unanswered > 0) add("$unanswered of ${Component.core.size} components not answered")
        if (missing > 0) add("$missing item(s) need a description, comment or photo")
    }.joinToString("  •  ")
}

@Composable
private fun MarkAllPassButton(enabled: Boolean, onClick: () -> Unit) {
    val green = StatusStyle.container(ChargerStatus.CLEAR)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            border = BorderStroke(2.dp, if (enabled) green else MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = green),
            modifier = Modifier.fillMaxWidth().height(BigButtonHeight),
        ) { Text("Mark all as pass", style = MaterialTheme.typography.titleMedium) }
        Text(
            "Fills in any component you haven't answered yet. Failures and notes are kept.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComponentCard(
    component: Component,
    input: ComponentInput,
    photos: List<PhotoEntity>,
    photoFile: (PhotoEntity) -> File,
    onResult: (CheckResult) -> Unit,
    onNote: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (PhotoEntity) -> Unit,
) {
    // Misc has no Pass button: leaving it blank means there is nothing to report.
    val options = if (component.required) {
        listOf(CheckResult.PASS, CheckResult.NOTE, CheckResult.FAIL)
    } else {
        listOf(CheckResult.NOTE, CheckResult.FAIL)
    }
    val isNote = input.result == CheckResult.NOTE

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(component.label, style = MaterialTheme.typography.titleLarge)
                if (!component.required) {
                    Text(
                        "Optional: anything not covered above. Leave blank if there is nothing to report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ResultButtons(options = options, selected = input.result, onPick = onResult)

            if (InspectionRules.carriesDetail(input.result)) {
                OutlinedTextField(
                    value = input.note,
                    onValueChange = { if (it.length <= InspectionRules.MAX_NOTE_LENGTH) onNote(it) },
                    label = { Text(if (isNote) "Comment (or add a photo)" else "Describe the damage (required)") },
                    isError = input.note.isBlank() && (!isNote || photos.isEmpty()),
                    supportingText = { Text("${input.note.length}/${InspectionRules.MAX_NOTE_LENGTH}") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (photos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { photo ->
                            PhotoThumbnail(file = photoFile(photo), onRemove = { onRemovePhoto(photo) })
                        }
                    }
                }
                if (photos.size < InspectionRules.MAX_PHOTOS_PER_COMPONENT) {
                    val accent = StatusStyle.forResult(input.result ?: CheckResult.NOTE)
                    OutlinedButton(
                        onClick = onTakePhoto,
                        border = BorderStroke(2.dp, accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("  Add photo (${photos.size}/${InspectionRules.MAX_PHOTOS_PER_COMPONENT})")
                    }
                }
            }
        }
    }
}

/** Large, colour-coded choice buttons: green Pass, blue Note, red Fail. The chosen one is solid. */
@Composable
private fun ResultButtons(
    options: List<CheckResult>,
    selected: CheckResult?,
    onPick: (CheckResult) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            val color = StatusStyle.forResult(option)
            val label = StatusStyle.resultLabel(option)
            val buttonModifier = Modifier.weight(1f).height(BigButtonHeight)
            if (option == selected) {
                Button(
                    onClick = { onPick(option) },
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                    modifier = buttonModifier,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onPick(option) },
                    border = BorderStroke(2.dp, color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                    modifier = buttonModifier,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(file: File, onRemove: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { PhotoProcessor.decodeThumbnail(file)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(30.dp)
                .background(Color(0x99000000), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
