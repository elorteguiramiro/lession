package com.RamiroElortegui.lession.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.RamiroElortegui.lession.data.room.SessionWithSamples
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun exportSessionToUri(context: Context, uri: Uri, sessionWithSamples: SessionWithSamples): Boolean {
        return try {
            val session = sessionWithSamples.session
            val samples = sessionWithSamples.samples

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    val startDateStr = dateFormat.format(Date(session.startedAt))
                    val endDateStr = dateFormat.format(Date(session.finishedAt))
                    val durationSec = (session.finishedAt - session.startedAt) / 1000.0

                    // Metadata headers
                    writer.append("# Lession EMG Session Export\n")
                    writer.append(String.format(Locale.US, "session_id,%d\n", session.id))
                    writer.append(String.format(Locale.US, "source,%s\n", session.source))
                    writer.append(String.format(Locale.US, "started_at,%s\n", startDateStr))
                    writer.append(String.format(Locale.US, "finished_at,%s\n", endDateStr))
                    writer.append(String.format(Locale.US, "duration_seconds,%.2f\n", durationSec))
                    writer.append(String.format(Locale.US, "sample_rate_hz,%d\n", session.sampleRateHz))
                    writer.append(String.format(Locale.US, "minimum_value,%.6f\n", session.minimumValue))
                    writer.append(String.format(Locale.US, "maximum_value,%.6f\n", session.maximumValue))
                    writer.append(String.format(Locale.US, "mav,%.6f\n", session.meanAbsoluteValue))
                    writer.append(String.format(Locale.US, "rms,%.6f\n", session.rmsValue))
                    writer.append(String.format(Locale.US, "sample_count,%d\n", session.sampleCount))
                    writer.append("\n")

                    // Table headers
                    writer.append("relative_time_ms,emg_value\n")

                    // Table rows
                    for (sample in samples) {
                        writer.append(String.format(Locale.US, "%d,%.6f\n", sample.relativeTimeMs, sample.value))
                    }
                    writer.flush()
                }
            } ?: return false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun openCsvFile(context: Context, uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Abrir archivo CSV con")
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }
}
