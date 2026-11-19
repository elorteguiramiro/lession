package com.RamiroElortegui.lession.ui.screens.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.RamiroElortegui.lession.data.room.EmgRepository
import com.RamiroElortegui.lession.data.room.SessionWithSamples
import com.RamiroElortegui.lession.util.CsvExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val sessionWithSamples: SessionWithSamples? = null,
    val isExporting: Boolean = false
)

class DetailViewModel(application: Application, private val sessionId: Long) : AndroidViewModel(application) {
    private val repository = EmgRepository(application)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val data = repository.getSessionDetail(sessionId)
            _uiState.update { it.copy(sessionWithSamples = data) }
        }
    }

    fun exportToCsv(uri: Uri, onResult: (Boolean, String) -> Unit) {
        val data = _uiState.value.sessionWithSamples
        if (data == null || data.samples.isEmpty()) {
            onResult(false, "No hay muestras para exportar.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val success = CsvExporter.exportSessionToUri(getApplication(), uri, data)
            _uiState.update { it.copy(isExporting = false) }
            if (success) {
                onResult(true, "CSV exportado exitosamente.")
            } else {
                onResult(false, "Error al exportar el archivo CSV.")
            }
        }
    }
}
