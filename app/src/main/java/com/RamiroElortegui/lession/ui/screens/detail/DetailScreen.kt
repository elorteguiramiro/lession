package com.RamiroElortegui.lession.ui.screens.detail

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.RamiroElortegui.lession.data.EmgSample
import com.RamiroElortegui.lession.ui.screens.measurement.EmgChart
import com.RamiroElortegui.lession.util.CsvExporter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val sessionWithSamples = uiState.sessionWithSamples
    val session = sessionWithSamples?.session
    val samples = sessionWithSamples?.samples?.map { EmgSample(it.relativeTimeMs, it.value) } ?: emptyList()

    val defaultFileName = session?.let { "lession_session_${it.id}_${it.finishedAt}.csv" } ?: "lession_session.csv"

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToCsv(uri) { _, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (successInternal(message)) {
                    val opened = CsvExporter.openCsvFile(context, uri)
                    if (!opened) {
                        Toast.makeText(
                            context,
                            "El archivo se guardó correctamente, pero no se encontró una aplicación para abrir archivos CSV.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Exportación cancelada por el usuario.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Detalle de Sesión") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    val hasSamples = (session?.sampleCount ?: 0) > 0

                    IconButton(
                        onClick = {
                            if (hasSamples) {
                                createDocumentLauncher.launch(defaultFileName)
                            } else {
                                Toast.makeText(context, "No hay muestras para exportar.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = hasSamples && !uiState.isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Exportar CSV",
                            tint = if (hasSamples && !uiState.isExporting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dateStr = dateFormat.format(Date(session.finishedAt))
            val durationSeconds = (session.finishedAt - session.startedAt) / 1000

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Origen: ${session.source}", style = MaterialTheme.typography.titleMedium)
                            Badge { Text(session.source) }
                        }
                        Text(text = "Fecha: $dateStr", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Duración: ${durationSeconds}s", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Frecuencia: ${session.sampleRateHz} Hz", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Total Muestras: ${session.sampleCount}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Chart Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (samples.isEmpty()) {
                        Text(text = "No hay muestras guardadas", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        EmgChart(
                            samples = samples,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                }

                // Metrics Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Métricas Guardadas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricDetailItem(label = "Mínimo", value = String.format(Locale.getDefault(), "%.2f", session.minimumValue))
                            MetricDetailItem(label = "Máximo", value = String.format(Locale.getDefault(), "%.2f", session.maximumValue))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricDetailItem(label = "MAV", value = String.format(Locale.getDefault(), "%.2f", session.meanAbsoluteValue))
                            MetricDetailItem(label = "RMS", value = String.format(Locale.getDefault(), "%.2f", session.rmsValue))
                        }
                    }
                }

                // Export Button
                Button(
                    onClick = { createDocumentLauncher.launch(defaultFileName) },
                    enabled = session.sampleCount > 0 && !uiState.isExporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Exportando...")
                    } else {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (session.sampleCount > 0) "Exportar CSV" else "Sin muestras para exportar")
                    }
                }
            }
        }
    }
}

private fun successInternal(message: String): Boolean {
    return message.contains("exitosamente")
}

@Composable
fun MetricDetailItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
