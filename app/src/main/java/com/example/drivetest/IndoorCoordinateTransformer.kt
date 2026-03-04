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
        require(pixelPoints.size == 4) { "Need exactly 4 pixel points" }
        val realPoints = listOf(
            PixelPoint(0.0, 0.0),
            PixelPoint(realWidth, 0.0),
            PixelPoint(realWidth, realHeight),
            PixelPoint(0.0, realHeight)
        )

        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)

        for (i in 0 until 4) {
            val x = pixelPoints[i].x
            val y = pixelPoints[i].y
            val u = realPoints[i].x
            val v = realPoints[i].y

            val rowU = i * 2
            a[rowU][0] = x
            a[rowU][1] = y
            a[rowU][2] = 1.0
            a[rowU][3] = 0.0
            a[rowU][4] = 0.0
            a[rowU][5] = 0.0
            a[rowU][6] = -u * x
            a[rowU][7] = -u * y
            b[rowU] = u

            val rowV = rowU + 1
            a[rowV][0] = 0.0
            a[rowV][1] = 0.0
            a[rowV][2] = 0.0
            a[rowV][3] = x
            a[rowV][4] = y
            a[rowV][5] = 1.0
            a[rowV][6] = -v * x
            a[rowV][7] = -v * y
            b[rowV] = v
        }

        val h = solveLinearSystem(a, b)
        return doubleArrayOf(
            h[0], h[1], h[2],
            h[3], h[4], h[5],
            h[6], h[7], 1.0
        )
    }

    fun pixelToReal(h: DoubleArray, px: Double, py: Double): Pair<Double, Double> {
        val x = h[0] * px + h[1] * py + h[2]
        val y = h[3] * px + h[4] * py + h[5]
        val z = h[6] * px + h[7] * py + h[8]
        require(abs(z) > 1e-9) { "Invalid homography (z≈0)" }
        return Pair(x / z, y / z)
    }

    fun realToPixel(h: DoubleArray, realX: Double, realY: Double): Pair<Double, Double> {
        val inv = invert3x3(h)
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

    private fun solveLinearSystem(aIn: Array<DoubleArray>, bIn: DoubleArray): DoubleArray {
        val n = bIn.size
        val a = Array(n) { r -> aIn[r].clone() }
        val b = bIn.clone()

        for (col in 0 until n) {
            var pivotRow = col
            for (r in col + 1 until n) {
                if (abs(a[r][col]) > abs(a[pivotRow][col])) pivotRow = r
            }
            require(abs(a[pivotRow][col]) > 1e-12) { "Calibration points are degenerate" }

            if (pivotRow != col) {
                val temp = a[col]
                a[col] = a[pivotRow]
                a[pivotRow] = temp
                val bTemp = b[col]
                b[col] = b[pivotRow]
                b[pivotRow] = bTemp
            }

            val pivot = a[col][col]
            for (c in col until n) a[col][c] /= pivot
            b[col] /= pivot

            for (r in 0 until n) {
                if (r == col) continue
                val factor = a[r][col]
                if (factor == 0.0) continue
                for (c in col until n) a[r][c] -= factor * a[col][c]
                b[r] -= factor * b[col]
            }
        }

        return b
    }

    private fun invert3x3(m: DoubleArray): DoubleArray {
        require(m.size == 9)
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]

        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(abs(det) > 1e-12) { "Homography not invertible" }

        val invDet = 1.0 / det
        return doubleArrayOf(
            (e * i - f * h) * invDet,
            (c * h - b * i) * invDet,
            (b * f - c * e) * invDet,
            (f * g - d * i) * invDet,
            (a * i - c * g) * invDet,
            (c * d - a * f) * invDet,
            (d * h - e * g) * invDet,
            (b * g - a * h) * invDet,
            (a * e - b * d) * invDet
        )
    }
}
