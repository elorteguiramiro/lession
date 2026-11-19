package com.RamiroElortegui.lession.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.RamiroElortegui.lession.data.EmgSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

val EMG_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
val EMG_TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // ESP32 -> Android (NOTIFY)
val EMG_RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Android -> ESP32 (WRITE)
val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data class Connecting(val deviceName: String) : BleConnectionState
    data object Connected : BleConnectionState
    data object DiscoveringServices : BleConnectionState
    data object ServicesDiscovered : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

class BleRepository private constructor(context: Context) {
    private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private val _scannedDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devicesList = MutableStateFlow<List<BleDevice>>(emptyList())
    val devicesList: StateFlow<List<BleDevice>> = _devicesList.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // GATT Connection state
    private var bluetoothGatt: BluetoothGatt? = null
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _gattServices = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    val gattServices: StateFlow<List<BluetoothGattService>> = _gattServices.asStateFlow()

    // Real EMG samples shared flow from BLE notifications
    private val _emgSamples = MutableSharedFlow<EmgSample>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val emgSamples: SharedFlow<EmgSample> = _emgSamples.asSharedFlow()

    private val _malformedCount = MutableStateFlow(0)
    val malformedCount: StateFlow<Int> = _malformedCount.asStateFlow()

    private val rxBuffer = ByteArrayOutputStream()
    private val repositoryScope = CoroutineScope(Dispatchers.Main + Job())
    private var scanTimeoutJob: Job? = null

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!isBluetoothSupported()) {
            _errorMessage.value = "Bluetooth no soportado en este dispositivo."
            return
        }
        if (!isBluetoothEnabled()) {
            _errorMessage.value = "Bluetooth desactivado. Por favor, actívelo."
            return
        }

        if (_isScanning.value) return

        _scannedDevices.value = emptyMap()
        _devicesList.value = emptyList()
        _errorMessage.value = null
        _isScanning.value = true

        try {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)

            scanTimeoutJob?.cancel()
            scanTimeoutJob = repositoryScope.launch {
                delay(15_000)
                stopScan()
            }
        } catch (e: SecurityException) {
            _isScanning.value = false
            _errorMessage.value = "Permisos de Bluetooth requeridos."
        } catch (e: Exception) {
            _isScanning.value = false
            _errorMessage.value = "Error al iniciar el escaneo: ${e.localizedMessage}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        scanTimeoutJob?.cancel()
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Ignore security exception on stop scan
        } catch (e: Exception) {
            // Ignore if scanner was already stopped or bluetooth turned off
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BleDevice) {
        val currentState = _connectionState.value
        if (currentState is BleConnectionState.Connecting || currentState is BleConnectionState.Connected || currentState is BleConnectionState.DiscoveringServices) {
            return
        }

        disconnect()

        val displayName = device.name.ifBlank { device.address }
        _connectedDeviceName.value = displayName
        _connectionState.value = BleConnectionState.Connecting(displayName)

        try {
            val remoteDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            bluetoothGatt = remoteDevice?.connectGatt(context.applicationContext, false, gattCallback)
        } catch (e: SecurityException) {
            _connectionState.value = BleConnectionState.Error("Permisos de conexión requeridos.")
        } catch (e: Exception) {
            _connectionState.value = BleConnectionState.Error("Error al conectar: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            // Ignore
        }
        bluetoothGatt = null
        _gattServices.value = emptyList()
        _connectionState.value = BleConnectionState.Disconnected
        synchronized(rxBuffer) {
            rxBuffer.reset()
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(EMG_SERVICE_UUID)
        val rxChar = service?.getCharacteristic(EMG_RX_CHARACTERISTIC_UUID)
        if (rxChar != null) {
            try {
                @Suppress("DEPRECATION")
                rxChar.value = command.toByteArray(StandardCharsets.UTF_8)
                @Suppress("DEPRECATION")
                rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rxChar)
            } catch (e: SecurityException) {
                _errorMessage.value = "Permisos requeridos para enviar comando."
            } catch (e: Exception) {
                _errorMessage.value = "Error al enviar comando: ${e.localizedMessage}"
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val name = device.name ?: scanRecord?.deviceName ?: ""
            val serviceUuids = scanRecord?.serviceUuids

            // Strict filtering: Only ESP32 LESSION (name == "ESP32 LESSION" or service UUID matches)
            val matchesName = name.equals("ESP32 LESSION", ignoreCase = true)
            val matchesService = serviceUuids?.any { it.uuid == EMG_SERVICE_UUID } == true

            if (matchesName || matchesService) {
                val address = device.address ?: return
                val displayName = if (name.isBlank()) "ESP32 LESSION" else name
                val rssi = result.rssi

                val bleDevice = BleDevice(
                    name = displayName,
                    address = address,
                    rssi = rssi
                )

                val currentMap = _scannedDevices.value.toMutableMap()
                currentMap[address] = bleDevice
                _scannedDevices.value = currentMap
                _devicesList.value = currentMap.values.sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _errorMessage.value = "Falló el escaneo BLE (Código: $errorCode)"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = BleConnectionState.Connected
                    _connectionState.value = BleConnectionState.DiscoveringServices
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnect()
                }
            } else {
                _connectionState.value = BleConnectionState.Error("Error de conexión GATT (status: $status)")
                disconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _gattServices.value = gatt.services ?: emptyList()
                _connectionState.value = BleConnectionState.ServicesDiscovered

                try {
                    val service = gatt.getService(EMG_SERVICE_UUID)
                    val txChar = service?.getCharacteristic(EMG_TX_CHARACTERISTIC_UUID)
                    val rxChar = service?.getCharacteristic(EMG_RX_CHARACTERISTIC_UUID)

                    // Validate that TX and RX characteristics are present
                    if (txChar != null && rxChar != null) {
                        val success = gatt.setCharacteristicNotification(txChar, true)
                        if (success) {
                            val descriptor = txChar.getDescriptor(CCCD_UUID)
                            if (descriptor != null) {
                                @Suppress("DEPRECATION")
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    } else {
                        _connectionState.value = BleConnectionState.Error("Características TX/RX no encontradas en el servicio EMG.")
                    }
                } catch (e: SecurityException) {
                    // Ignore security exception if permissions missing
                } catch (e: Exception) {
                    // Ignore
                }
            } else {
                _connectionState.value = BleConnectionState.Error("Error al descubrir servicios (status: $status)")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            processIncomingBytes(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            processIncomingBytes(value)
        }

        private fun processIncomingBytes(bytes: ByteArray?) {
            if (bytes == null || bytes.isEmpty()) return
            synchronized(rxBuffer) {
                for (b in bytes) {
                    if (b == 0x0A.toByte()) { // LF
                        val lineBytes = rxBuffer.toByteArray()
                        rxBuffer.reset()
                        parseLine(lineBytes)
                    } else {
                        if (rxBuffer.size() > 1024) {
                            rxBuffer.reset() // Prevent unbounded buffer growth if LF is missing
                        }
                        rxBuffer.write(b.toInt())
                    }
                }
            }
        }

        private fun parseLine(lineBytes: ByteArray) {
            try {
                val line = String(lineBytes, StandardCharsets.UTF_8).trim()
                if (line.isEmpty()) return
                val parts = line.split(",")
                if (parts.size >= 2) {
                    val tMs = parts[0].trim().toLongOrNull()
                    val adc = parts[1].trim().toIntOrNull()
                    if (tMs != null && adc != null && adc in 0..4095) {
                        val sample = EmgSample(timestampMs = tMs, value = adc.toFloat())
                        _emgSamples.tryEmit(sample)
                    } else {
                        _malformedCount.update { it + 1 }
                    }
                } else {
                    _malformedCount.update { it + 1 }
                }
            } catch (e: Exception) {
                _malformedCount.update { it + 1 }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BleRepository? = null

        fun getInstance(context: Context): BleRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = BleRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
