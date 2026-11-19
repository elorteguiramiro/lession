package com.RamiroElortegui.lession.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.RamiroElortegui.lession.data.room.EmgSessionEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onSessionClick: (Long) -> Unit,
    onNavigateToAnalysis: () -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    var sessionToDelete by remember { mutableStateOf<EmgSessionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Historial de Sesiones") },
                actions = {
                    TextButton(onClick = onNavigateToAnalysis) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Análisis comparativo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Análisis")
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
        ) {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Aún no hay sesiones registradas.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionItemCard(
                            session = session,
                            onClick = { onSessionClick(session.id) },
                            onDeleteClick = { sessionToDelete = session }
                        )
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (sessionToDelete != null) {
            AlertDialog(
                onDismissRequest = { sessionToDelete = null },
                title = { Text("Eliminar Sesión") },
                text = { Text("¿Está seguro de que desea eliminar esta sesión de forma permanente?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            sessionToDelete?.let { viewModel.deleteSession(it.id) }
                            sessionToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun SessionItemCard(
    session: EmgSessionEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(session.finishedAt))
    val durationSeconds = (session.finishedAt - session.startedAt) / 1000

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge { Text(session.source) }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Duración: ${durationSeconds}s", style = MaterialTheme.typography.bodySmall)
                Text(text = "Muestras: ${session.sampleCount}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Freq: ${session.sampleRateHz} Hz", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "MAV: ${String.format(Locale.getDefault(), "%.2f", session.meanAbsoluteValue)}", style = MaterialTheme.typography.bodySmall)
                Text(text = "RMS: ${String.format(Locale.getDefault(), "%.2f", session.rmsValue)}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Máx: ${String.format(Locale.getDefault(), "%.2f", session.maximumValue)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
