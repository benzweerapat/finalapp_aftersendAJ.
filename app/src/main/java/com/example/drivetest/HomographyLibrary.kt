package com.example.drivetest

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.LUDecomposition
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
        val matrix = Array2DRowRealMatrix(
            arrayOf(
                doubleArrayOf(m[0], m[1], m[2]),
                doubleArrayOf(m[3], m[4], m[5]),
                doubleArrayOf(m[6], m[7], m[8])
            ),
            false
        )
        val solver = LUDecomposition(matrix, 1e-12).solver
        require(solver.isNonSingular) { "Homography not invertible" }
        val inv = solver.inverse
        return doubleArrayOf(
            inv.getEntry(0, 0), inv.getEntry(0, 1), inv.getEntry(0, 2),
            inv.getEntry(1, 0), inv.getEntry(1, 1), inv.getEntry(1, 2),
            inv.getEntry(2, 0), inv.getEntry(2, 1), inv.getEntry(2, 2)
        )
    }

    private fun solveLinearSystem(aIn: Array<DoubleArray>, bIn: DoubleArray): DoubleArray {
        val matrix = Array2DRowRealMatrix(aIn, true)
        val vector = ArrayRealVector(bIn, true)
        val solver = LUDecomposition(matrix, 1e-12).solver
        require(solver.isNonSingular) { "Calibration points are degenerate" }
        return solver.solve(vector).toArray()
    }
}
