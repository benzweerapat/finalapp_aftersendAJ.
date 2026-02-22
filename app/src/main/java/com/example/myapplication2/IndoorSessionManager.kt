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
    val checkpoints: MutableList<IndoorCheckpoint> = mutableListOf()

    fun clearWalk() {
        checkpoints.clear()
    }
}
