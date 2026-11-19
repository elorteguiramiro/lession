package com.RamiroElortegui.lession.ui.screens.measurement

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RamiroElortegui.lession.data.room.EmgRepository
import com.RamiroElortegui.lession.data.ble.BleConnectionState
import com.RamiroElortegui.lession.data.ble.BleRepository
import com.RamiroElortegui.lession.data.EmgSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

enum class MeasurementState {
    STOPPED,
    ACTIVE,
    PAUSED,
    FINISHED
}

data class MeasurementUiState(
    val measurementState: MeasurementState = MeasurementState.STOPPED,
    val sampleRateHz: Int = 50, // 50 Hz confirmed frequency
    val chartSamples: List<EmgSample> = emptyList(),
    val totalSamplesCount: Int = 0,
    val lastAdcValue: Int = 0,
    val lastDeviceTimestampMs: Long = 0L,
    val malformedCount: Int = 0,
    val minValue: Float = 0f,
    val maxValue: Float = 0f,
    val meanValue: Float = 0f, // MAV
    val medianValue: Float = 0f,
    val mavValue: Float = 0f,
    val rmsValue: Float = 0f,
    val durationSeconds: Long = 0L,
    val showSummaryDialog: Boolean = false,
    val isSessionSaved: Boolean = false,
    val saveMessage: String? = null
)

class MeasurementViewModel(application: Application) : ViewModel() {
    private val repository = EmgRepository(application)
    private val bleRepository = BleRepository.getInstance(application)
    private var sampleCollectionJob: Job? = null
    private var timerJob: Job? = null
    private var uiThrottleJob: Job? = null

    private val _uiState = MutableStateFlow(MeasurementUiState())
    val uiState: StateFlow<MeasurementUiState> = _uiState.asStateFlow()

    val bluetoothStatusText: StateFlow<String> = combine(
        bleRepository.isScanning,
        bleRepository.errorMessage,
        bleRepository.connectionState
    ) { scanning, error, connectionState ->
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        when {
            !bleRepository.isBluetoothSupported() -> "Bluetooth no disponible"
            !bleRepository.isBluetoothEnabled() -> "Bluetooth no disponible"
            !hasPermission -> "Permisos requeridos"
            connectionState is BleConnectionState.Connecting -> "Conectando..."
            connectionState is BleConnectionState.Connected -> "Conectado"
            connectionState is BleConnectionState.DiscoveringServices -> "Conectado"
            connectionState is BleConnectionState.ServicesDiscovered -> "Conectado"
            connectionState is BleConnectionState.Error -> "Error de conexión"
            error != null -> "Error de Bluetooth"
            scanning -> "Buscando"
            else -> "Desconectado"
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Desconectado"
    )

    private val allSessionSamples = mutableListOf<EmgSample>()
    private val bufferSamples = mutableListOf<EmgSample>()
    private var sessionStartTimeMs: Long = 0L
    private var sessionFinishTimeMs: Long = 0L

    fun startMeasurement() {
        val state = _uiState.value.measurementState
        if (state == MeasurementState.ACTIVE) return

        val isConnected = bleRepository.connectionState.value is BleConnectionState.Connected ||
                bleRepository.connectionState.value is BleConnectionState.ServicesDiscovered
        if (!isConnected) return

        if (state == MeasurementState.STOPPED || state == MeasurementState.FINISHED) {
            allSessionSamples.clear()
            bufferSamples.clear()
            sessionStartTimeMs = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    measurementState = MeasurementState.ACTIVE,
                    chartSamples = emptyList(),
                    totalSamplesCount = 0,
                    lastAdcValue = 0,
                    lastDeviceTimestampMs = 0L,
                    minValue = 0f,
                    maxValue = 0f,
                    meanValue = 0f,
                    medianValue = 0f,
                    mavValue = 0f,
                    rmsValue = 0f,
                    durationSeconds = 0L,
                    showSummaryDialog = false,
                    isSessionSaved = false,
                    saveMessage = null
                )
            }
        } else {
            _uiState.update { it.copy(measurementState = MeasurementState.ACTIVE) }
        }

        startJobs()
    }

    private fun startJobs() {
        sampleCollectionJob?.cancel()
        sampleCollectionJob = viewModelScope.launch {
            bleRepository.emgSamples.collect { sample ->
                if (_uiState.value.measurementState == MeasurementState.ACTIVE) {
                    allSessionSamples.add(sample)
                    synchronized(bufferSamples) {
                        bufferSamples.add(sample)
                    }
                }
            }
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis() - (_uiState.value.durationSeconds * 1000)
            while (isActive) {
                delay(1000)
                val currentDuration = (System.currentTimeMillis() - startTime) / 1000
                _uiState.update { it.copy(durationSeconds = currentDuration) }
            }
        }

        uiThrottleJob?.cancel()
        uiThrottleJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                updateMetricsAndChart()
            }
        }
    }

    private fun updateMetricsAndChart() {
        val samplesToAdd: List<EmgSample>
        synchronized(bufferSamples) {
            if (bufferSamples.isEmpty()) return
            samplesToAdd = bufferSamples.toList()
            bufferSamples.clear()
        }

        val currentChart = _uiState.value.chartSamples.toMutableList()
        currentChart.addAll(samplesToAdd)
        // Ensure chart samples are strictly sorted by timestamp
        currentChart.sortBy { it.timestampMs }
        if (currentChart.size > 150) {
            val trimmed = currentChart.takeLast(150)
            currentChart.clear()
            currentChart.addAll(trimmed)
        }

        val total = allSessionSamples.size
        if (total == 0) return

        val lastSample = samplesToAdd.lastOrNull() ?: allSessionSamples.last()
        val lastAdc = lastSample.value.toInt()
        val lastTimestamp = lastSample.timestampMs

        val min = allSessionSamples.minOf { it.value }
        val max = allSessionSamples.maxOf { it.value }
        val mav = allSessionSamples.map { abs(it.value) }.average().toFloat()
        val rms = sqrt(allSessionSamples.map { it.value * it.value }.average()).toFloat()

        val absValues = allSessionSamples.map { abs(it.value) }.sorted()
        val median = if (absValues.isEmpty()) 0f else {
            val mid = absValues.size / 2
            if (absValues.size % 2 == 0) {
                (absValues[mid - 1] + absValues[mid]) / 2f
            } else {
                absValues[mid]
            }
        }

        val malformed = bleRepository.malformedCount.value

        _uiState.update {
            it.copy(
                chartSamples = currentChart,
                totalSamplesCount = total,
                lastAdcValue = lastAdc,
                lastDeviceTimestampMs = lastTimestamp,
                malformedCount = malformed,
                minValue = min,
                maxValue = max,
                meanValue = mav,
                medianValue = median,
                mavValue = mav,
                rmsValue = rms
            )
        }
    }

    fun pauseMeasurement() {
        if (_uiState.value.measurementState != MeasurementState.ACTIVE) return
        cancelJobs()
        _uiState.update { it.copy(measurementState = MeasurementState.PAUSED) }
    }

    fun finishSession() {
        cancelJobs()
        updateMetricsAndChart()
        sessionFinishTimeMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                measurementState = MeasurementState.FINISHED,
                showSummaryDialog = true
            )
        }
    }

    fun openSummaryDialog() {
        _uiState.update { it.copy(showSummaryDialog = true) }
    }

    fun sendStartCommand() {
        bleRepository.sendCommand("START")
    }

    fun sendStopCommand() {
        bleRepository.sendCommand("STOP")
    }

    fun saveCurrentSession() {
        if (_uiState.value.isSessionSaved || allSessionSamples.isEmpty()) return

        viewModelScope.launch {
            try {
                val min = if (allSessionSamples.isNotEmpty()) allSessionSamples.minOf { it.value } else 0f
                val max = if (allSessionSamples.isNotEmpty()) allSessionSamples.maxOf { it.value } else 0f

                repository.saveSession(
                    source = "BLE",
                    startedAt = sessionStartTimeMs,
                    finishedAt = sessionFinishTimeMs,
                    sampleRateHz = 50, // 50 Hz confirmed frequency
                    minValue = min,
                    maxValue = max,
                    samples = allSessionSamples.sortedBy { it.timestampMs }
                )

                _uiState.update {
                    it.copy(
                        isSessionSaved = true,
                        saveMessage = "¡Sesión guardada exitosamente en el historial!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        saveMessage = "Error al guardar la sesión: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissSummaryDialog() {
        _uiState.update { it.copy(showSummaryDialog = false) }
    }

    fun resetSession() {
        cancelJobs()
        allSessionSamples.clear()
        bufferSamples.clear()
        _uiState.update {
            MeasurementUiState(measurementState = MeasurementState.STOPPED)
        }
    }

    private fun cancelJobs() {
        sampleCollectionJob?.cancel()
        timerJob?.cancel()
        uiThrottleJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        cancelJobs()
    }
}
