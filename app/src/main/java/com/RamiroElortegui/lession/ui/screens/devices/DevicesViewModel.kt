package com.RamiroElortegui.lession.ui.screens.devices

import android.app.Application
import android.bluetooth.BluetoothGattService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.RamiroElortegui.lession.data.ble.BleConnectionState
import com.RamiroElortegui.lession.data.ble.BleDevice
import com.RamiroElortegui.lession.data.ble.BleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface DevicesUiState {
    data object BluetoothNotAvailable : DevicesUiState
    data object BluetoothDisabled : DevicesUiState
    data object PermissionsRequired : DevicesUiState
    data object Scanning : DevicesUiState
    data object NoDevicesFound : DevicesUiState
    data class DevicesFound(val devices: List<BleDevice>) : DevicesUiState
    data class Error(val message: String) : DevicesUiState
}

class DevicesViewModel(private val application: Application) : ViewModel() {
    private val bleRepository = BleRepository.getInstance(application)

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BleDevice?>(null)
    val selectedDevice: StateFlow<BleDevice?> = _selectedDevice.asStateFlow()

    val connectionState: StateFlow<BleConnectionState> = bleRepository.connectionState
    val connectedDeviceName: StateFlow<String?> = bleRepository.connectedDeviceName
    val gattServices: StateFlow<List<BluetoothGattService>> = bleRepository.gattServices

    val uiState: StateFlow<DevicesUiState> = combine(
        bleRepository.isScanning,
        bleRepository.devicesList,
        bleRepository.errorMessage,
        _hasPermissions
    ) { scanning, devices, error, permissionsGranted ->
        when {
            !bleRepository.isBluetoothSupported() -> DevicesUiState.BluetoothNotAvailable
            !bleRepository.isBluetoothEnabled() -> DevicesUiState.BluetoothDisabled
            !permissionsGranted -> DevicesUiState.PermissionsRequired
            error != null -> DevicesUiState.Error(error)
            scanning && devices.isEmpty() -> DevicesUiState.Scanning
            devices.isNotEmpty() -> DevicesUiState.DevicesFound(devices)
            scanning -> DevicesUiState.Scanning
            else -> DevicesUiState.NoDevicesFound
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevicesUiState.PermissionsRequired
    )

    fun updatePermissionsGranted(granted: Boolean) {
        _hasPermissions.value = granted
    }

    fun startScan() {
        if (!bleRepository.isBluetoothSupported() || !bleRepository.isBluetoothEnabled() || !_hasPermissions.value) {
            return
        }
        bleRepository.startScan()
    }

    fun stopScan() {
        bleRepository.stopScan()
    }

    fun selectDevice(device: BleDevice) {
        _selectedDevice.value = device
    }

    fun connectToDevice(device: BleDevice) {
        selectDevice(device)
        stopScan()
        bleRepository.connect(application, device)
    }

    fun connectToSelectedDevice() {
        _selectedDevice.value?.let { device ->
            stopScan()
            bleRepository.connect(application, device)
        }
    }

    fun disconnectDevice() {
        bleRepository.disconnect()
    }
}
