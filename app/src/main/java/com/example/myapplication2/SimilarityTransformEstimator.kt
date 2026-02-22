package com.example.myapplication2

import kotlin.math.atan2
import kotlin.math.sqrt

object SimilarityTransformEstimator {
    data class Correspondence(
        val sourceX: Double,
        val sourceY: Double,
        val targetX: Double,
        val targetY: Double
    )

    fun estimate(points: List<Correspondence>): SimilarityTransform? {
        if (points.size < 2) return null

        val n = points.size.toDouble()
        val meanSX = points.sumOf { it.sourceX } / n
        val meanSY = points.sumOf { it.sourceY } / n
        val meanTX = points.sumOf { it.targetX } / n
        val meanTY = points.sumOf { it.targetY } / n

        var sxx = 0.0
        var sxy = 0.0
        var norm = 0.0

        points.forEach {
            val xs = it.sourceX - meanSX
            val ys = it.sourceY - meanSY
            val xt = it.targetX - meanTX
            val yt = it.targetY - meanTY

            sxx += xt * xs + yt * ys
            sxy += yt * xs - xt * ys
            norm += xs * xs + ys * ys
        }

        if (norm == 0.0) return null

        val a = sxx / norm
        val b = sxy / norm
        val tx = meanTX - (a * meanSX - b * meanSY)
        val ty = meanTY - (b * meanSX + a * meanSY)

        return SimilarityTransform(a, b, tx, ty)
    }
}

data class SimilarityTransform(
    val a: Double,
    val b: Double,
    val tx: Double,
    val ty: Double
) {
    val scale: Double = sqrt(a * a + b * b)
    val rotationDegrees: Double = Math.toDegrees(atan2(b, a))

    fun map(x: Double, y: Double): Pair<Double, Double> {
        val mappedX = a * x - b * y + tx
        val mappedY = b * x + a * y + ty
        return Pair(mappedX, mappedY)
    }
}
