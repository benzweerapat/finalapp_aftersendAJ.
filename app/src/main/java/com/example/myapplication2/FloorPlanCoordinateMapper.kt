package com.example.myapplication2

import android.graphics.Matrix
import android.widget.ImageView

object FloorPlanCoordinateMapper {
    fun viewToBitmap(imageView: ImageView, viewX: Float, viewY: Float): Pair<Double, Double>? {
        val drawable = imageView.drawable ?: return null
        val inverse = Matrix()
        if (!imageView.imageMatrix.invert(inverse)) return null
        val points = floatArrayOf(viewX, viewY)
        inverse.mapPoints(points)

        if (points[0] < 0f || points[1] < 0f || points[0] > drawable.intrinsicWidth || points[1] > drawable.intrinsicHeight) {
            return null
        }
        return Pair(points[0].toDouble(), points[1].toDouble())
    }
}
