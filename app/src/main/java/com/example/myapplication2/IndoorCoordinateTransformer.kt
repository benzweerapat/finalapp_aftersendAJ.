package com.example.myapplication2

import kotlin.math.cos
import kotlin.math.sin

object IndoorCoordinateTransformer {
    fun normalizedToLocalMeters(
        normalizedX: Double,
        normalizedY: Double,
        imageWidth: Int,
        imageHeight: Int,
        originNx: Double,
        originNy: Double,
        axisAngleRad: Double,
        scaleMetersPerPixel: Double
    ): Pair<Double, Double> {
        val px = normalizedX * imageWidth
        val py = normalizedY * imageHeight
        val originPx = originNx * imageWidth
        val originPy = originNy * imageHeight

        val dx = px - originPx
        val dy = py - originPy

        val c = cos(axisAngleRad)
        val s = sin(axisAngleRad)

        val localX = (dx * c + dy * s) * scaleMetersPerPixel
        val localY = (-dx * s + dy * c) * scaleMetersPerPixel
        return Pair(localX, localY)
    }
}
