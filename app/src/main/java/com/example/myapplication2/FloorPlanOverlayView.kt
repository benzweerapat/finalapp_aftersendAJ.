package com.example.myapplication2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import kotlin.math.hypot

class FloorPlanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var calibrationPoints: List<Pair<Float, Float>> = emptyList()
        private set

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.FILL
    }
    private val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
    }
    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun setCalibrationPoints(points: List<Pair<Float, Float>>) {
        calibrationPoints = points
        invalidate()
    }

    fun findNearestCalibrationPoint(viewX: Float, viewY: Float, thresholdPx: Float = 36f): Int? {
        val imageView = resolveSiblingImageView() ?: return null
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        calibrationPoints.forEachIndexed { index, point ->
            val mapped = mapBitmapToView(imageView, point.first, point.second)
            val distance = hypot(mapped.first - viewX, mapped.second - viewY)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return if (bestIndex >= 0 && bestDistance <= thresholdPx) bestIndex else null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val imageView = resolveSiblingImageView() ?: return

        calibrationPoints.forEachIndexed { index, point ->
            val mapped = mapBitmapToView(imageView, point.first, point.second)
            canvas.drawCircle(mapped.first, mapped.second, 13f, markerPaint)
            canvas.drawCircle(mapped.first, mapped.second, 13f, markerStrokePaint)
            canvas.drawText((index + 1).toString(), mapped.first + 16f, mapped.second - 16f, markerTextPaint)
        }
    }

    private fun resolveSiblingImageView(): ImageView? {
        val viewGroup = parent as? ViewGroup ?: return null
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ImageView) return child
        }
        return null
    }

    private fun mapBitmapToView(imageView: ImageView, x: Float, y: Float): Pair<Float, Float> {
        val pts = floatArrayOf(x, y)
        imageView.imageMatrix.mapPoints(pts)
        return Pair(pts[0], pts[1])
    }
}
