package com.RamiroElortegui.lession.ui.screens.devices

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.RamiroElortegui.lession.data.ble.BleConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    viewModel: DevicesViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceName by viewModel.connectedDeviceName.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val services by viewModel.gattServices.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            // Keep connection alive when navigating back unless user explicitly disconnects,
            // or if desired disconnect on leave. Wait, rule 7 says: "Al volver a la pantalla, conservar correctamente el dispositivo seleccionado y el estado real de conexión."
            // So do NOT disconnect on leave unless explicitly requested, but close gatt on app/finish if needed.
        }
    }

    val displayName = deviceName ?: selectedDevice?.name?.ifBlank { selectedDevice?.address } ?: "Dispositivo BLE"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Dispositivo: $displayName", style = MaterialTheme.typography.titleMedium)
                    selectedDevice?.let { dev ->
                        Text(text = "MAC: ${dev.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Estado: " + when (connectionState) {
                            is BleConnectionState.Disconnected -> "Desconectado"
                            is BleConnectionState.Connecting -> "Conectando…"
                            is BleConnectionState.Connected -> "Conectado"
                            is BleConnectionState.DiscoveringServices -> "Descubriendo servicios…"
                            is BleConnectionState.ServicesDiscovered -> "Conectado"
                            is BleConnectionState.Error -> (connectionState as BleConnectionState.Error).message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (connectionState) {
                            is BleConnectionState.Connected, is BleConnectionState.ServicesDiscovered -> MaterialTheme.colorScheme.primary
                            is BleConnectionState.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Action Buttons based on Real GATT State
            when (connectionState) {
                is BleConnectionState.Disconnected -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { viewModel.connectToSelectedDevice() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Conectar")
                        }
                        Text(
                            text = "Conectá el dispositivo para descubrir sus servicios",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is BleConnectionState.Connecting -> {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Conectando…")
                    }
                }
                is BleConnectionState.DiscoveringServices -> {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Descubriendo servicios…")
                    }
                }
                is BleConnectionState.Connected, is BleConnectionState.ServicesDiscovered -> {
                    Button(
                        onClick = { viewModel.disconnectDevice() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Desconectar")
                    }
                }
                is BleConnectionState.Error -> {
                    Button(
                        onClick = { viewModel.connectToSelectedDevice() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Reintentar conexión")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Services Section
            when (connectionState) {
                is BleConnectionState.Disconnected, is BleConnectionState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dispositivo desconectado. Conectá para buscar servicios GATT.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is BleConnectionState.Connecting, is BleConnectionState.DiscoveringServices -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Estableciendo conexión y buscando servicios...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is BleConnectionState.Connected, is BleConnectionState.ServicesDiscovered -> {
                    Text(
                        text = "Servicios GATT encontrados (${services.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (services.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Conectado. Esperando servicios GATT...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(services) { service ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Servicio UUID: ${service.uuid}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Características (${service.characteristics.size}):",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        service.characteristics.forEach { characteristic ->
                                            val propsText = getCharacteristicPropertiesText(characteristic.properties)
                                            Text(
                                                text = "• UUID: ${characteristic.uuid}\n  Propiedades: $propsText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCharacteristicPropertiesText(properties: Int): String {
    val props = mutableListOf<String>()
    if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("Lectura")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("Escritura")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("Notificación")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("Indicación")
    if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("Escritura sin respuesta")
    return if (props.isEmpty()) "Ninguna" else props.joinToString(", ")
}
