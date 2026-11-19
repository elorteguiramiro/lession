package com.RamiroElortegui.lession.data.analysis

import com.RamiroElortegui.lession.data.simulation.EmgSample
import kotlin.math.sqrt

data class ActivityInterval(
    val startTimeMs: Long,
    val endTimeMs: Long
) {
    val durationMs: Long get() = endTimeMs - startTimeMs
}

data class ActivityAnalysisResult(
    val intervals: List<ActivityInterval>,
    val totalAccumulatedMs: Long,
    val longestIntervalMs: Long
)

object ActivityDetector {
    fun analyze(
        samples: List<EmgSample>,
        threshold: Float,
        minDurationMs: Long,
        windowMs: Long = 250L
    ): ActivityAnalysisResult {
        if (samples.isEmpty()) return ActivityAnalysisResult(emptyList(), 0L, 0L)

        // Compute moving RMS for each sample
        val rmsValues = mutableListOf<Pair<Long, Float>>()
        for (i in samples.indices) {
            val current = samples[i]
            val windowStartTime = current.timestampMs - windowMs
            val windowSamples = samples.subList(0, i + 1).filter { it.timestampMs >= windowStartTime }
            if (windowSamples.isNotEmpty()) {
                val sumSq = windowSamples.sumOf { (it.value * it.value).toDouble() }
                val rms = sqrt(sumSq / windowSamples.size).toFloat()
                rmsValues.add(Pair(current.timestampMs, rms))
            }
        }

        // Detect intervals where RMS >= threshold
        val intervals = mutableListOf<ActivityInterval>()
        var activeStart: Long? = null

        for ((timeMs, rms) in rmsValues) {
            if (rms >= threshold) {
                if (activeStart == null) {
                    activeStart = timeMs
                }
            } else {
                if (activeStart != null) {
                    val duration = timeMs - activeStart
                    if (duration >= minDurationMs) {
                        intervals.add(ActivityInterval(activeStart, timeMs))
                    }
                    activeStart = null
                }
            }
        }
        if (activeStart != null && rmsValues.isNotEmpty()) {
            val endTime = rmsValues.last().first
            val duration = endTime - activeStart
            if (duration >= minDurationMs) {
                intervals.add(ActivityInterval(activeStart, endTime))
            }
        }

        val totalMs = intervals.sumOf { it.durationMs }
        val longestMs = intervals.maxOfOrNull { it.durationMs } ?: 0L

        return ActivityAnalysisResult(
            intervals = intervals,
            totalAccumulatedMs = totalMs,
            longestIntervalMs = longestMs
        )
    }
}
