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

    @Test
    fun homography_pixelToReal_and_reverse() {
        val h = IndoorCoordinateTransformer.solveHomography(
            pixelPoints = listOf(
                PixelPoint(100.0, 100.0),
                PixelPoint(500.0, 120.0),
                PixelPoint(520.0, 420.0),
                PixelPoint(90.0, 410.0)
            ),
            realWidth = 32.5,
            realHeight = 18.2
        )

        val real = IndoorCoordinateTransformer.pixelToReal(h, 100.0, 100.0)
        assertEquals(0.0, real.first, 1e-4)
        assertEquals(0.0, real.second, 1e-4)

        val px = IndoorCoordinateTransformer.realToPixel(h, 32.5, 18.2)
        assertEquals(520.0, px.first, 1e-2)
        assertEquals(420.0, px.second, 1e-2)
    }

    @Test
    fun realToLatLong_usesLatitudeDependentLongitudeScale() {
        val result = IndoorCoordinateTransformer.realToLatLong(
            realX = 10.0,
            realY = 5.0,
            originLatitude = 13.7563,
            originLongitude = 100.5018
        )

        assertEquals(13.75634495, result.first, 1e-8)
        assertEquals(100.5018925, result.second, 1e-6)
    }
}
