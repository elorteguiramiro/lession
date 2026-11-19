package com.RamiroElortegui.lession.util

import com.RamiroElortegui.lession.data.room.EmgSessionEntity

data class BoxPlotData(
    val count: Int,
    val min: Float,
    val q1: Float,
    val median: Float,
    val q3: Float,
    val max: Float,
    val iqr: Float,
    val lowerWhisker: Float,
    val upperWhisker: Float,
    val outliers: List<Float>
)

object BoxPlotCalculator {
    fun calculate(sessions: List<EmgSessionEntity>, metric: String, source: String): BoxPlotData? {
        val filtered = sessions.filter { it.source == source }
        if (filtered.isEmpty()) return null

        val values = filtered.map { 
            when (metric) {
                "RMS" -> it.rmsValue
                else -> it.meanAbsoluteValue
            }
        }.sorted()

        val n = values.size
        if (n == 0) return null

        val median = percentile(values, 50.0)
        val q1 = percentile(values, 25.0)
        val q3 = percentile(values, 75.0)
        val iqr = q3 - q1

        val lowerBound = q1 - 1.5f * iqr
        val upperBound = q3 + 1.5f * iqr

        val nonOutliers = values.filter { it in lowerBound..upperBound }
        val min = values.first()
        val max = values.last()

        val lowerWhisker = if (nonOutliers.isNotEmpty()) nonOutliers.first() else min
        val upperWhisker = if (nonOutliers.isNotEmpty()) nonOutliers.last() else max

        val outliers = values.filter { it < lowerWhisker || it > upperWhisker }

        return BoxPlotData(
            count = n,
            min = min,
            q1 = q1,
            median = median,
            q3 = q3,
            max = max,
            iqr = iqr,
            lowerWhisker = lowerWhisker,
            upperWhisker = upperWhisker,
            outliers = outliers
        )
    }

    private fun percentile(sortedList: List<Float>, percentile: Double): Float {
        if (sortedList.isEmpty()) return 0f
        val index = (percentile / 100.0) * (sortedList.size - 1)
        val lower = index.toInt()
        val upper = Math.ceil(index).toInt()
        if (lower == upper) return sortedList[lower]
        val weight = index - lower
        return sortedList[lower] * (1f - weight.toFloat()) + sortedList[upper] * weight.toFloat()
    }
}
