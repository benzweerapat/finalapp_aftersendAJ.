package com.example.drivetest

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object IndoorCoordinateTransformer {
    private const val LAT_DEGREES_PER_METER = 0.00000899

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

    fun solveHomography(pixelPoints: List<PixelPoint>, realWidth: Double, realHeight: Double): DoubleArray {
        return HomographyLibrary.solveFromQuad(pixelPoints, realWidth, realHeight)
    }

    fun pixelToReal(h: DoubleArray, px: Double, py: Double): Pair<Double, Double> {
        return HomographyLibrary.transformPoint(h, px, py)
    }

    fun realToPixel(h: DoubleArray, realX: Double, realY: Double): Pair<Double, Double> {
        val inv = HomographyLibrary.invert3x3(h)
        return pixelToReal(inv, realX, realY)
    }

    fun realToLatLong(
        realX: Double,
        realY: Double,
        originLatitude: Double,
        originLongitude: Double
    ): Pair<Double, Double> {
        val lat = originLatitude + (realY * LAT_DEGREES_PER_METER)
        val lonScale = longitudeDegreesPerMeter(originLatitude)
        val lon = originLongitude + (realX * lonScale)
        return Pair(lat, lon)
    }

    fun longitudeDegreesPerMeter(latitudeDeg: Double): Double {
        val cosLat = cos(Math.toRadians(latitudeDeg))
        val safeCos = if (abs(cosLat) < 1e-6) 1e-6 else cosLat
        return LAT_DEGREES_PER_METER / safeCos
    }


    fun isPointInsideCalibrationQuad(
        px: Double,
        py: Double,
        p1: PixelPoint,
        p2: PixelPoint,
        p3: PixelPoint,
        p4: PixelPoint
    ): Boolean {
        val pts = listOf(p1, p2, p3, p4)
        var sign = 0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val cross = (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x)
            val current = when {
                cross > 1e-9 -> 1
                cross < -1e-9 -> -1
                else -> 0
            }
            if (current == 0) continue
            if (sign == 0) sign = current
            if (sign != current) return false
        }
        return true
    }
}
