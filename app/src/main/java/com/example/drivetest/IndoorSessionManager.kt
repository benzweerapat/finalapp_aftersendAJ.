package com.example.drivetest

import android.net.Uri

data class IndoorConfig(
    val projectName: String,
    val floorName: String,
    val imageUri: Uri,
    val scaleMetersPerPixel: Double,
    val originNx: Double,
    val originNy: Double,
    val axisAngleRad: Double,
    val calibrationSession: CalibrationSession? = null,
    val originLatitude: Double? = null,
    val originLongitude: Double? = null
)

data class PixelPoint(
    val x: Double,
    val y: Double
)

data class CalibrationSession(
    val sessionId: String,
    val floorplanId: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val p1: PixelPoint,
    val p2: PixelPoint,
    val p3: PixelPoint,
    val p4: PixelPoint,
    val realWidth: Double,
    val realHeight: Double,
    val homographyMatrix: List<Double>,
    val createdAt: String
)

data class IndoorCheckpoint(
    val index: Int,
    val timestamp: String,
    val normalizedX: Double,
    val normalizedY: Double,
    val localX: Double,
    val localY: Double,
    val source: String
)

data class IndoorTestPoint(
    val timestamp: String,
    val floorLabel: String,
    val pointNo: Int,
    val mapX: Float,
    val mapY: Float,
    val networkType: String,
    val cellIdBssid: String,
    val rsrpRssi: Int,
    val rsrqSinr: Int
)

object IndoorSessionManager {
    enum class RadioMode { CELLULAR, WIFI }

    var config: IndoorConfig? = null
    var radioMode: RadioMode = RadioMode.CELLULAR
    var importedFloorPlanUri: Uri? = null
    var surveyRunning: Boolean = false
    val plottedPointsNormalized: MutableList<Pair<Double, Double>> = mutableListOf()
    val points: MutableList<IndoorTestPoint> = mutableListOf()
    val checkpoints: MutableList<IndoorCheckpoint> = mutableListOf()

    fun addPlotPoint(nx: Double, ny: Double) {
        plottedPointsNormalized.add(Pair(nx, ny))
    }

    fun clearWalk() {
        surveyRunning = false
        checkpoints.clear()
        points.clear()
        plottedPointsNormalized.clear()
    }
}
