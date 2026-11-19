package com.RamiroElortegui.lession.ui.screens.measurement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.RamiroElortegui.lession.data.EmgSample
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(
    viewModel: MeasurementViewModel,
    onNavigateToDevices: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val bluetoothStatus by viewModel.bluetoothStatusText.collectAsState()
    val isConnected = bluetoothStatus == "Conectado"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Métricas") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToDevices)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = bluetoothStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Estado Bluetooth",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Real Data Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Datos reales · ESP32-C3-EMG (ADC raw 0–4095)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Main Chart Element
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!isConnected) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Conectá el sensor EMG para comenzar",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (uiState.chartSamples.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Esperando notificaciones EMG (ESP32-C3-EMG)...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    EmgChart(
                        samples = uiState.chartSamples,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        lineColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Compact Grid with Exactly Six Metrics (2 rows of 3)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell(label = "Media (MAV)", value = String.format(Locale.getDefault(), "%.1f", uiState.mavValue))
                        MetricCell(label = "Mediana", value = String.format(Locale.getDefault(), "%.1f", uiState.medianValue))
                        MetricCell(label = "Máximo", value = "${uiState.maxValue.toInt()}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell(label = "Mínimo", value = "${uiState.minValue.toInt()}")
                        MetricCell(label = "Duración", value = "${uiState.durationSeconds}s")
                        MetricCell(label = "Muestras", value = "${uiState.totalSamplesCount}")
                    }
                }
            }

            // Action Controls
            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (uiState.measurementState) {
                        MeasurementState.STOPPED, MeasurementState.PAUSED -> {
                            Button(
                                onClick = { viewModel.startMeasurement() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (uiState.measurementState == MeasurementState.PAUSED) "Reanudar" else "Iniciar registro")
                            }
                        }
                        MeasurementState.ACTIVE -> {
                            OutlinedButton(
                                onClick = { viewModel.pauseMeasurement() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Pausar")
                            }
                            Button(
                                onClick = { viewModel.finishSession() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Finalizar")
                            }
                        }
                        MeasurementState.FINISHED -> {
                            Button(
                                onClick = { viewModel.openSummaryDialog() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text(text = "Ver resumen")
                            }
                            OutlinedButton(
                                onClick = { viewModel.resetSession() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text(text = "Nuevo registro")
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = onNavigateToDevices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(text = "Conectá el sensor EMG para comenzar")
                }
            }
        }

        // Summary Dialog upon finishing
        if (uiState.showSummaryDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSummaryDialog() },
                title = { Text(text = "Resumen de Sesión BLE") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Origen: ESP32-C3-EMG")
                        Text("Duración total: ${uiState.durationSeconds} segundos")
                        Text("Total de muestras: ${uiState.totalSamplesCount}")
                        Text("Media (MAV): ${String.format(Locale.getDefault(), "%.1f", uiState.mavValue)}")
                        Text("Mediana: ${String.format(Locale.getDefault(), "%.1f", uiState.medianValue)}")
                        Text("Valor mínimo: ${uiState.minValue.toInt()}")
                        Text("Valor máximo: ${uiState.maxValue.toInt()}")
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (uiState.saveMessage != null) {
                            Text(
                                text = uiState.saveMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.isSessionSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        } else if (uiState.totalSamplesCount == 0) {
                            Text(
                                text = "No se puede guardar: la sesión no contiene muestras.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.saveCurrentSession() },
                            enabled = !uiState.isSessionSaved && uiState.totalSamplesCount > 0
                        ) {
                            Text(if (uiState.isSessionSaved) "Guardada" else "Guardar sesión")
                        }
                        TextButton(
                            onClick = { 
                                viewModel.dismissSummaryDialog()
                                viewModel.resetSession()
                            }
                        ) {
                            Text("Descartar / Nuevo")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MetricCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.width(100.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun EmgChart(
    samples: List<EmgSample>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val axisColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    val labelColorInt = android.graphics.Color.argb(
        (textColor.alpha * 255).toInt(),
        (textColor.red * 255).toInt(),
        (textColor.green * 255).toInt(),
        (textColor.blue * 255).toInt()
    )

    // 1. Sort samples by timestamp
    val sortedSamples = remember(samples) {
        samples.sortedBy { it.timestampMs }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingLeft = 56f
        val paddingRight = 20f
        val paddingTop = 20f
        val paddingBottom = 40f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val minTimeMs = sortedSamples.firstOrNull()?.timestampMs ?: 0L
        val maxTimeMs = sortedSamples.lastOrNull()?.timestampMs ?: (minTimeMs + 1000L)
        val timeSpanMs = (maxTimeMs - minTimeMs).coerceAtLeast(1000L)

        fun timeToX(timeMs: Long): Float {
            val fraction = (timeMs - minTimeMs).toFloat() / timeSpanMs
            return paddingLeft + fraction * chartWidth
        }

        // Fixed ADC range: 0 to 4095
        val chartMin = 0f
        val chartMax = 4095f

        fun valueToY(value: Float): Float {
            val clamped = value.coerceIn(chartMin, chartMax)
            val normalized = (clamped - chartMin) / (chartMax - chartMin)
            return paddingTop + chartHeight - (normalized * chartHeight)
        }

        // 3. EMG Signal line (plotting raw sorted samples directly without filtering/normalization)
        if (sortedSamples.size >= 2) {
            val path = Path()
            sortedSamples.forEachIndexed { index, sample ->
                val x = timeToX(sample.timestampMs)
                val y = valueToY(sample.value)
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 4. Axes & Labels
        val paint = android.graphics.Paint().apply {
            color = labelColorInt
            textSize = 24f
            isAntiAlias = true
        }

        // Y-Axis fixed labels (0, 2047, 4095)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("4095", paddingLeft - 8f, paddingTop + 10f, paint)
        drawContext.canvas.nativeCanvas.drawText("2047", paddingLeft - 8f, paddingTop + chartHeight / 2f + 6f, paint)
        drawContext.canvas.nativeCanvas.drawText("0", paddingLeft - 8f, paddingTop + chartHeight + 8f, paint)

        // X-Axis time labels (seconds)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        val totalSecs = timeSpanMs / 1000f
        val tickCount = 4
        val usedLabels = mutableSetOf<String>()
        for (i in 0 until tickCount) {
            val fraction = i.toFloat() / (tickCount - 1)
            val sec = fraction * totalSecs
            val x = paddingLeft + fraction * chartWidth
            val label = String.format(Locale.getDefault(), "%.1fs", sec)
            if (label !in usedLabels) {
                usedLabels.add(label)
                drawContext.canvas.nativeCanvas.drawText(label, x, height - 8f, paint)
            }
        }

        // Axis frame lines
        drawLine(
            color = axisColor.copy(alpha = 0.4f),
            start = Offset(paddingLeft, paddingTop),
            end = Offset(paddingLeft, paddingTop + chartHeight),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = axisColor.copy(alpha = 0.4f),
            start = Offset(paddingLeft, paddingTop + chartHeight),
            end = Offset(width - paddingRight, paddingTop + chartHeight),
            strokeWidth = 1.dp.toPx()
        )
    }
}
