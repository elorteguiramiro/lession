package com.RamiroElortegui.lession.ui.screens.analysis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.RamiroElortegui.lession.data.room.EmgSessionEntity
import com.RamiroElortegui.lession.util.BoxPlotData
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val boxData = uiState.boxPlotData
    var selectedOutlierInfo by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDetailedStats by remember { mutableStateOf(false) }

    val filterChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Análisis comparativo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            // Compact Filters Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source label: Datos
                    Text(
                        text = "Origen: Datos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Metric filter
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("MAV", "RMS").forEach { metric ->
                            FilterChip(
                                selected = uiState.selectedMetric == metric,
                                onClick = { viewModel.setMetric(metric) },
                                label = { Text(metric) },
                                colors = filterChipColors
                            )
                        }
                    }
                }
            }

            // Content condition: less than 5 sessions
            if (uiState.totalFilteredCount < 5 || boxData == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Se necesitan al menos 5 sesiones",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Se requieren al menos 5 sesiones guardadas con origen 'Datos' para un análisis comparativo útil (actuales: ${uiState.totalFilteredCount}).",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Horizontal Box and Whisker Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Distribución de ${uiState.selectedMetric} entre ${boxData.count} sesiones",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (uiState.totalFilteredCount in 5..9) {
                                    Text(
                                        text = "Muestra exploratoria: ${uiState.totalFilteredCount} sesiones",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Información",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Horizontal Box Plot Canvas with Sessions Rug Plot & Outlier click listener
                        HorizontalBoxPlotCanvas(
                            data = boxData,
                            sessions = uiState.filteredSessions,
                            metric = uiState.selectedMetric,
                            onOutlierClicked = { outlierVal, session ->
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.finishedAt))
                                selectedOutlierInfo = "Valor atípico: ${String.format(Locale.getDefault(), "%.4f", outlierVal)}\nFecha de sesión: $dateStr"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                }

                // Compact Statistical Summary Card with "Ver detalle" toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Resumen Estadístico (${uiState.selectedMetric})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { showDetailedStats = !showDetailedStats }) {
                                Text(if (showDetailedStats) "Ocultar detalle" else "Ver detalle")
                            }
                        }

                        // Primary compact stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem(label = "Sesiones", value = "${boxData.count}")
                            StatItem(label = "Mediana", value = String.format(Locale.getDefault(), "%.4f", boxData.median))
                            StatItem(label = "Rango central (Q1–Q3)", value = "${String.format(Locale.getDefault(), "%.2f", boxData.q1)} – ${String.format(Locale.getDefault(), "%.2f", boxData.q3)}")
                            StatItem(label = "Atípicos", value = "${boxData.outliers.size}")
                        }

                        // Expandable secondary stats
                        AnimatedVisibility(visible = showDetailedStats) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StatItem(label = "Mínimo obs.", value = String.format(Locale.getDefault(), "%.4f", boxData.min))
                                    StatItem(label = "Máximo obs.", value = String.format(Locale.getDefault(), "%.4f", boxData.max))
                                    StatItem(label = "IQR", value = String.format(Locale.getDefault(), "%.4f", boxData.iqr))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StatItem(label = "Bigote inf.", value = String.format(Locale.getDefault(), "%.4f", boxData.lowerWhisker))
                                    StatItem(label = "Bigote sup.", value = String.format(Locale.getDefault(), "%.4f", boxData.upperWhisker))
                                    StatItem(label = "", value = "")
                                }
                                if (abs(boxData.lowerWhisker - boxData.q1) < 0.0001f) {
                                    Text(
                                        text = "• Bigote inferior coincide con Q1",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (abs(boxData.upperWhisker - boxData.q3) < 0.0001f) {
                                    Text(
                                        text = "• Bigote superior coincide con Q3",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Medical Disclaimer
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Análisis descriptivo; no constituye diagnóstico médico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Info Dialog
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Información del Gráfico") },
                text = { Text("La caja contiene el 50% central de las sesiones. Los bigotes muestran el rango sin valores atípicos. Los puntos rojos son sesiones atípicas.") },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Aceptar")
                    }
                }
            )
        }

        // Outlier Info Dialog
        if (selectedOutlierInfo != null) {
            AlertDialog(
                onDismissRequest = { selectedOutlierInfo = null },
                title = { Text("Valor Atípico (Outlier)") },
                text = { Text(selectedOutlierInfo!!) },
                confirmButton = {
                    TextButton(onClick = { selectedOutlierInfo = null }) {
                        Text("Aceptar")
                    }
                }
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun HorizontalBoxPlotCanvas(
    data: BoxPlotData,
    sessions: List<EmgSessionEntity>,
    metric: String,
    onOutlierClicked: (Float, EmgSessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val errorColor = MaterialTheme.colorScheme.error
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    val labelColorInt = android.graphics.Color.argb(
        (textColor.alpha * 255).toInt(),
        (textColor.red * 255).toInt(),
        (textColor.green * 255).toInt(),
        (textColor.blue * 255).toInt()
    )

    val outlierHitRegions = remember { mutableStateOf<List<Triple<Float, Float, Pair<Float, EmgSessionEntity>>>>(emptyList()) }

    Box(modifier = modifier.pointerInput(data, sessions, metric) {
        detectTapGestures(
            onTap = { offset ->
                val hit = outlierHitRegions.value.find { (x, y, _) ->
                    val dx = offset.x - x
                    val dy = offset.y - y
                    (dx * dx + dy * dy) <= (24f * 24f)
                }
                if (hit != null) {
                    onOutlierClicked(hit.third.first, hit.third.second)
                }
            }
        )
    }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val paddingLeft = 32f
            val paddingRight = 32f
            val paddingTop = 12f
            val paddingBottom = 42f

            val chartWidth = width - paddingLeft - paddingRight
            val centerY = paddingTop + (height - paddingTop - paddingBottom) / 2f - 4f

            val rawMin = minOf(data.min, data.outliers.minOrNull() ?: data.min)
            val rawMax = maxOf(data.max, data.outliers.maxOrNull() ?: data.max)
            val span = (rawMax - rawMin).coerceAtLeast(0.0001f)
            val margin = span * 0.12f
            val globalMin = rawMin - margin
            val globalMax = rawMax + margin
            val range = (globalMax - globalMin).coerceAtLeast(0.0001f)

            fun valueToX(value: Float): Float {
                val normalized = (value - globalMin) / range
                return paddingLeft + (normalized * chartWidth)
            }

            val xLowerWhisker = valueToX(data.lowerWhisker)
            val xUpperWhisker = valueToX(data.upperWhisker)
            val xQ1 = valueToX(data.q1)
            val xQ3 = valueToX(data.q3)
            val xMedian = valueToX(data.median)

            val boxHeight = 52f
            val boxTop = centerY - boxHeight / 2f

            // 1. Whiskers line (high contrast, thicker)
            drawLine(
                color = outlineColor,
                start = Offset(xLowerWhisker, centerY),
                end = Offset(xUpperWhisker, centerY),
                strokeWidth = 3.5.dp.toPx()
            )

            // Whisker vertical caps (clearly visible)
            val capHeight = 36f
            drawLine(
                color = outlineColor,
                start = Offset(xLowerWhisker, centerY - capHeight / 2f),
                end = Offset(xLowerWhisker, centerY + capHeight / 2f),
                strokeWidth = 3.5.dp.toPx()
            )
            drawLine(
                color = outlineColor,
                start = Offset(xUpperWhisker, centerY - capHeight / 2f),
                end = Offset(xUpperWhisker, centerY + capHeight / 2f),
                strokeWidth = 3.5.dp.toPx()
            )

            // 2. Interquartile Box (Q1 to Q3)
            val boxLeft = minOf(xQ1, xQ3)
            val boxRight = maxOf(xQ1, xQ3)
            val boxW = (boxRight - boxLeft).coerceAtLeast(4f)

            drawRect(
                color = primaryColor.copy(alpha = 0.25f),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxHeight)
            )
            drawRect(
                color = primaryColor,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxHeight),
                style = Stroke(width = 3.dp.toPx())
            )

            // 3. Median vertical line (deep blue)
            drawLine(
                color = primaryColor,
                start = Offset(xMedian, boxTop),
                end = Offset(xMedian, boxTop + boxHeight),
                strokeWidth = 4.dp.toPx()
            )

            // 4. Rug plot: all sessions as small cyan ticks below the chart
            val rugY = centerY + 36f
            sessions.forEachIndexed { index, session ->
                val valMetric = if (metric == "RMS") session.rmsValue else session.meanAbsoluteValue
                val xPos = valueToX(valMetric)
                val yOffset = (index % 3) * 3f
                drawLine(
                    color = primaryColor.copy(alpha = 0.6f),
                    start = Offset(xPos, rugY + yOffset),
                    end = Offset(xPos, rugY + yOffset + 5f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // 5. Outliers (red dots) & hit regions
            val newHitRegions = mutableListOf<Triple<Float, Float, Pair<Float, EmgSessionEntity>>>()
            data.outliers.forEach { outlierVal ->
                val xOutlier = valueToX(outlierVal)
                val matchingSession = sessions.find { session ->
                    val sVal = if (metric == "RMS") session.rmsValue else session.meanAbsoluteValue
                    abs(sVal - outlierVal) < 0.0001f
                }
                if (matchingSession != null) {
                    newHitRegions.add(Triple(xOutlier, centerY, Pair(outlierVal, matchingSession)))
                }
                drawCircle(
                    color = errorColor,
                    radius = 6.dp.toPx(),
                    center = Offset(xOutlier, centerY)
                )
            }
            outlierHitRegions.value = newHitRegions

            // 6. Dynamic X-Axis Ticks & Labels (avoid duplicate labels)
            val tickCount = 5
            val step = range / (tickCount - 1)
            val decimals = when {
                step < 0.001 -> 4
                step < 0.01 -> 3
                step < 0.1 -> 2
                else -> 2
            }

            val axisY = height - paddingBottom + 8f
            val tickPaint = android.graphics.Paint().apply {
                color = labelColorInt
                textSize = 26f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val usedTexts = mutableSetOf<String>()
            for (i in 0 until tickCount) {
                val fraction = i.toFloat() / (tickCount - 1)
                val value = globalMin + (fraction * range)
                val xPos = valueToX(value)

                val text = String.format(Locale.getDefault(), "%.${decimals}f", value)
                if (text !in usedTexts) {
                    usedTexts.add(text)
                    // Tick line
                    drawLine(
                        color = outlineColor,
                        start = Offset(xPos, axisY),
                        end = Offset(xPos, axisY + 6f),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Tick text
                    drawContext.canvas.nativeCanvas.drawText(text, xPos, axisY + 24f, tickPaint)
                }
            }

            // Axis base link / base line
            drawLine(
                color = outlineColor,
                start = Offset(paddingLeft, axisY),
                end = Offset(width - paddingRight, axisY),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
