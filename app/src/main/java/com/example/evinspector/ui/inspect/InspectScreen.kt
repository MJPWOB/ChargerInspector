package com.example.evinspector.ui.inspect

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save & Return") }
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
                items(Component.entries, key = { it.name }) { component ->
                    ComponentCard(
                        component = component,
                        input = ui.inputs[component] ?: ComponentInput(),
                        photos = ui.photos[component].orEmpty(),
                        photoFile = vm::photoFile,
                        onResult = { vm.setResult(component, it) },
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
        if (unanswered > 0) add("$unanswered of ${Component.entries.size} components not answered")
        if (missing > 0) add("$missing failure(s) need a description")
    }.joinToString("  •  ")
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    component.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                PassFailToggle(input.result, onResult)
            }

            if (input.result == CheckResult.FAIL) {
                OutlinedTextField(
                    value = input.note,
                    onValueChange = { if (it.length <= InspectionRules.MAX_NOTE_LENGTH) onNote(it) },
                    label = { Text("Describe the damage (required)") },
                    isError = input.note.isBlank(),
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
                    OutlinedButton(onClick = onTakePhoto) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Add photo (${photos.size}/${InspectionRules.MAX_PHOTOS_PER_COMPONENT})")
                    }
                }
            }
        }
    }
}

@Composable
private fun PassFailToggle(value: CheckResult?, onChange: (CheckResult) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleChoice("Pass", value == CheckResult.PASS, StatusStyle.container(ChargerStatus.CLEAR)) {
            onChange(CheckResult.PASS)
        }
        ToggleChoice("Fail", value == CheckResult.FAIL, StatusStyle.container(ChargerStatus.ISSUES)) {
            onChange(CheckResult.FAIL)
        }
    }
}

@Composable
private fun ToggleChoice(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 16.dp)
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            contentPadding = padding,
        ) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding) { Text(label) }
    }
}

@Composable
private fun PhotoThumbnail(file: File, onRemove: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { PhotoProcessor.decodeThumbnail(file)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Damage photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(26.dp)
                .background(Color(0x99000000), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
