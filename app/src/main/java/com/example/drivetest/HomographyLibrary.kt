package com.example.drivetest

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

object HomographyLibrary {
    private const val EPS = 1e-12

    init {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException("OpenCV native library is not available", e)
        }
    }

    fun solveFromQuad(pixelPoints: List<PixelPoint>, realWidth: Double, realHeight: Double): DoubleArray {
        require(pixelPoints.size == 4) { "Need exactly 4 pixel points" }

        val src = MatOfPoint2f(
            Point(pixelPoints[0].x, pixelPoints[0].y),
            Point(pixelPoints[1].x, pixelPoints[1].y),
            Point(pixelPoints[2].x, pixelPoints[2].y),
            Point(pixelPoints[3].x, pixelPoints[3].y)
        )
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(realWidth, 0.0),
            Point(realWidth, realHeight),
            Point(0.0, realHeight)
        )

        val h = Imgproc.getPerspectiveTransform(src, dst)
        require(!h.empty()) { "Calibration points are degenerate" }
        return mat3x3ToArray(h)
    }

    fun transformPoint(h: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
        require(h.size == 9) { "Homography must have 9 elements" }
        val src = MatOfPoint2f(Point(x, y))
        val dst = MatOfPoint2f()
        Core.perspectiveTransform(src, dst, arrayToMat3x3(h))
        val out = dst.toArray().firstOrNull() ?: throw IllegalArgumentException("Transform failed")
        return Pair(out.x, out.y)
    }

    fun invert3x3(m: DoubleArray): DoubleArray {
        require(m.size == 9)
        val src = arrayToMat3x3(m)
        val inv = Mat(3, 3, CvType.CV_64F)
        val det = Core.invert(src, inv)
        require(abs(det) > EPS) { "Homography not invertible" }
        return mat3x3ToArray(inv)
    }

    private fun arrayToMat3x3(values: DoubleArray): Mat {
        require(values.size == 9) { "Homography must have 9 elements" }
        val mat = Mat(3, 3, CvType.CV_64F)
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                mat.put(r, c, values[r * 3 + c])
            }
        }
        return mat
    }

    private fun mat3x3ToArray(mat: Mat): DoubleArray {
        require(mat.rows() == 3 && mat.cols() == 3) { "Homography must be 3x3" }
        val out = DoubleArray(9)
        val cell = DoubleArray(1)
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                mat.get(r, c, cell)
                out[r * 3 + c] = cell[0]
            }
        }
        return out
    }
}
