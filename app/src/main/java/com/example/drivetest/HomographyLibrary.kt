package com.example.drivetest

import kotlin.math.abs

object HomographyLibrary {
    fun solveFromQuad(pixelPoints: List<PixelPoint>, realWidth: Double, realHeight: Double): DoubleArray {
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

    fun transformPoint(h: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
        val tx = h[0] * x + h[1] * y + h[2]
        val ty = h[3] * x + h[4] * y + h[5]
        val tz = h[6] * x + h[7] * y + h[8]
        require(abs(tz) > 1e-9) { "Invalid homography (z≈0)" }
        return Pair(tx / tz, ty / tz)
    }

    fun invert3x3(m: DoubleArray): DoubleArray {
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
}
