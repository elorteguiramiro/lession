package com.RamiroElortegui.lession.ui.screens.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.RamiroElortegui.lession.data.room.EmgRepository
import com.RamiroElortegui.lession.data.room.EmgSessionEntity
import com.RamiroElortegui.lession.util.BoxPlotCalculator
import com.RamiroElortegui.lession.util.BoxPlotData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalysisUiState(
    val selectedMetric: String = "MAV", // "MAV" or "RMS"
    val selectedSource: String = "BLE", // "BLE" labeled as "Datos"
    val boxPlotData: BoxPlotData? = null,
    val filteredSessions: List<EmgSessionEntity> = emptyList(),
    val totalFilteredCount: Int = 0
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmgRepository(application)

    private val _selectedMetric = MutableStateFlow("MAV")
    private val selectedSourceVal = "BLE"

    val uiState: StateFlow<AnalysisUiState> = combine(
        repository.allSessions,
        _selectedMetric
    ) { sessions, metric ->
        val filtered = sessions.filter { it.source == selectedSourceVal }
        val boxData = BoxPlotCalculator.calculate(sessions, metric, selectedSourceVal)
        AnalysisUiState(
            selectedMetric = metric,
            selectedSource = selectedSourceVal,
            boxPlotData = boxData,
            filteredSessions = filtered,
            totalFilteredCount = filtered.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalysisUiState()
    )

    fun setMetric(metric: String) {
        _selectedMetric.value = metric
    }
}
