package com.example.myapplication2

import android.net.Uri

data class IndoorConfig(
    val projectName: String,
    val floorName: String,
    val imageUri: Uri,
    val scaleMetersPerPixel: Double,
    val originNx: Double,
    val originNy: Double,
    val axisAngleRad: Double
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

object IndoorSessionManager {
    enum class RadioMode { CELLULAR, WIFI }

    var config: IndoorConfig? = null
    var radioMode: RadioMode = RadioMode.CELLULAR
    var importedFloorPlanUri: Uri? = null
    val plottedPointsNormalized: MutableList<Pair<Double, Double>> = mutableListOf()
    val checkpoints: MutableList<IndoorCheckpoint> = mutableListOf()

    fun addPlotPoint(nx: Double, ny: Double) {
        plottedPointsNormalized.add(Pair(nx, ny))
    }

    fun clearWalk() {
        checkpoints.clear()
    }
}
