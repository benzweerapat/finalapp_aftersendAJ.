package com.example.drivetest

import android.graphics.Matrix
import android.widget.ImageView

object NormalizedCoordinateMapper {
    fun viewToNormalized(imageView: ImageView, viewX: Float, viewY: Float): Pair<Double, Double>? {
        val drawable = imageView.drawable ?: return null
        val inverse = Matrix()
        if (!imageView.imageMatrix.invert(inverse)) return null

        val points = floatArrayOf(viewX, viewY)
        inverse.mapPoints(points)

        val px = points[0]
        val py = points[1]
        val w = drawable.intrinsicWidth.toFloat()
        val h = drawable.intrinsicHeight.toFloat()

        if (px < 0f || py < 0f || px > w || py > h) return null

        return Pair((px / w).toDouble(), (py / h).toDouble())
    }
}
