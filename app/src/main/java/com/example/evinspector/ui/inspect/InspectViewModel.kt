package com.example.evinspector.ui.inspect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evinspector.AppContainer
import com.example.evinspector.data.InspectionRepository
import com.example.evinspector.data.PhotoEntity
import com.example.evinspector.domain.ChargerLabels
import com.example.evinspector.domain.CheckResult
import com.example.evinspector.domain.Component
import com.example.evinspector.domain.ComponentInput
import com.example.evinspector.domain.InspectionRules
import com.example.evinspector.domain.ValidationProblem
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class InspectUi(
    val loaded: Boolean = false,
    val label: String = "",
    val inputs: Map<Component, ComponentInput> = emptyMap(),
    val photos: Map<Component, List<PhotoEntity>> = emptyMap(),
) {
    val problems: List<ValidationProblem> get() = InspectionRules.validate(inputs)
    val canSave: Boolean get() = problems.isEmpty()
}

/**
 * Holds the form for one charger.
 *
 * Auto-save: toggles are written immediately, note text after a short pause, and anything still
 * pending is flushed when the screen stops, closes, or the ViewModel is cleared. Writes go through
 * the app-level scope so closing the screen can never cancel them half way.
 */
class InspectViewModel(
    private val repository: InspectionRepository,
    private val sessionId: Long,
    private val chargerNo: Int,
) : ViewModel() {

    private val inputs = MutableStateFlow<Map<Component, ComponentInput>>(emptyMap())
    private val label = MutableStateFlow<String?>(null) // null until loaded
    private var dirty = false
    private var debounceJob: Job? = null
    private var committing = false

    val ui: StateFlow<InspectUi> =
        combine(inputs, repository.observePhotos(sessionId, chargerNo), label) { answers, photos, loadedLabel ->
            InspectUi(
                loaded = loadedLabel != null,
                label = loadedLabel.orEmpty(),
                inputs = answers,
                photos = photos
                    .mapNotNull { p -> Component.fromName(p.component)?.let { it to p } }
                    .groupBy({ it.first }, { it.second }),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InspectUi())

    init {
        viewModelScope.launch {
            val session = repository.getSession(sessionId)
            // Queued behind any auto-save still pending from a previous visit to this charger.
            inputs.value = repository.appScope.async { repository.loadInputs(sessionId, chargerNo) }.await()
            label.value = session
                ?.let { ChargerLabels.label(it.chargerPrefix, chargerNo, it.chargerCount) }
                ?: "#$chargerNo"
        }
    }

    // ------------------------------------------------------------------ editing

    fun setResult(component: Component, result: CheckResult) {
        val current = inputs.value[component] ?: ComponentInput()
        if (current.result == result) return
        inputs.update { it + (component to current.copy(result = result)) }
        markDirty(immediate = true)
    }

    fun setNote(component: Component, note: String) {
        val current = inputs.value[component] ?: ComponentInput()
        if (current.note == note) return
        inputs.update { it + (component to current.copy(note = note)) }
        markDirty(immediate = false)
    }

    private fun markDirty(immediate: Boolean) {
        dirty = true
        debounceJob?.cancel()
        if (immediate) {
            persistNow()
        } else {
            debounceJob = viewModelScope.launch {
                delay(NOTE_DEBOUNCE_MS)
                persistNow()
            }
        }
    }

    private fun persistNow() {
        if (!dirty) return
        dirty = false
        val snapshot = inputs.value
        repository.appScope.launch { repository.saveDraft(sessionId, chargerNo, snapshot) }
    }

    /** Writes anything still pending. Safe to call repeatedly. */
    fun flush() {
        debounceJob?.cancel()
        persistNow()
    }

    override fun onCleared() {
        persistNow()
    }

    /** Save & Return. [onDone] runs on the main thread only if the form was valid and saved. */
    fun saveAndReturn(onDone: () -> Unit) {
        if (committing) return
        committing = true
        debounceJob?.cancel()
        dirty = false
        val snapshot = inputs.value
        viewModelScope.launch {
            // Runs on the app scope, queued behind any earlier auto-save, so ordering is preserved.
            val saved = repository.appScope.async { repository.commit(sessionId, chargerNo, snapshot) }.await()
            committing = false
            if (saved) onDone()
        }
    }

    // ------------------------------------------------------------------ photos

    fun newPhotoFile(component: Component): File = repository.newPhotoFile(sessionId, chargerNo, component)

    fun relativePathOf(file: File): String = repository.relativePathOf(file)

    fun photoFile(photo: PhotoEntity): File = repository.resolve(photo.path)

    fun onPhotoResult(component: Component, relativePath: String, success: Boolean) {
        repository.appScope.launch {
            if (success) {
                repository.addPhoto(sessionId, chargerNo, component, relativePath)
            } else {
                repository.discardUnregisteredPhoto(relativePath)
            }
        }
    }

    fun removePhoto(photo: PhotoEntity) {
        repository.appScope.launch { repository.removePhoto(photo) }
    }

    companion object {
        private const val NOTE_DEBOUNCE_MS = 400L

        fun factory(container: AppContainer, sessionId: Long, chargerNo: Int) = viewModelFactory {
            initializer { InspectViewModel(container.repository, sessionId, chargerNo) }
        }
    }
}
