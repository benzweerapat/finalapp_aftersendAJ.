package com.example.myapplication2

import org.junit.Assert.assertEquals
import org.junit.Test

class IndoorCoordinateTransformerTest {

    @Test
    fun normalizedToLocalMeters_axisAligned() {
        val out = IndoorCoordinateTransformer.normalizedToLocalMeters(
            normalizedX = 0.60,
            normalizedY = 0.50,
            imageWidth = 1000,
            imageHeight = 1000,
            originNx = 0.50,
            originNy = 0.50,
            axisAngleRad = 0.0,
            scaleMetersPerPixel = 0.02
        )

        assertEquals(2.0, out.first, 1e-6)
        assertEquals(0.0, out.second, 1e-6)
    }

    @Test
    fun normalizedToLocalMeters_rotated90deg() {
        val out = IndoorCoordinateTransformer.normalizedToLocalMeters(
            normalizedX = 0.50,
            normalizedY = 0.60,
            imageWidth = 1000,
            imageHeight = 1000,
            originNx = 0.50,
            originNy = 0.50,
            axisAngleRad = Math.PI / 2,
            scaleMetersPerPixel = 0.02
        )

        assertEquals(2.0, out.first, 1e-6)
        assertEquals(0.0, out.second, 1e-6)
    }
}
