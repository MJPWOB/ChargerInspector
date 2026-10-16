package com.example.evinspector.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evinspector.AppContainer
import com.example.evinspector.data.GeneratedReport
import com.example.evinspector.data.InspectionRepository
import com.example.evinspector.domain.DashboardData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReportUi {
    data object Idle : ReportUi
    data object Generating : ReportUi
    data class Ready(val report: GeneratedReport) : ReportUi
    data class Failed(val message: String) : ReportUi
}

class DashboardViewModel(
    private val repository: InspectionRepository,
    val sessionId: Long,
) : ViewModel() {

    /** null while loading. */
    val dashboard: StateFlow<DashboardData?> =
        repository.observeDashboard(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _report = MutableStateFlow<ReportUi>(ReportUi.Idle)
    val report: StateFlow<ReportUi> = _report.asStateFlow()

    fun generate() {
        if (_report.value is ReportUi.Generating) return
        _report.value = ReportUi.Generating
        viewModelScope.launch {
            _report.value = try {
                ReportUi.Ready(repository.generateReports(sessionId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReportUi.Failed(e.message ?: "Something went wrong while building the report.")
            }
        }
    }

    fun dismissReport() {
        _report.value = ReportUi.Idle
    }

    suspend fun hasReports(): Boolean = repository.hasReports(sessionId)

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.finishSession(sessionId)
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer, sessionId: Long) = viewModelFactory {
            initializer { DashboardViewModel(container.repository, sessionId) }
        }
    }
}
