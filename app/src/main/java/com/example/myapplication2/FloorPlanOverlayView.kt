package com.example.myapplication2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

class FloorPlanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var calibrationPoints: List<Pair<Float, Float>> = emptyList()
        private set
    var tapPoints: List<Pair<Float, Float>> = emptyList()
        private set

    private val calibrationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.FILL
    }

    private val tapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.FILL
    }

    fun setCalibrationPoints(points: List<Pair<Float, Float>>) {
        calibrationPoints = points
        invalidate()
    }

    fun setTapPoints(points: List<Pair<Float, Float>>) {
        tapPoints = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val imageView = resolveSiblingImageView() ?: return

        calibrationPoints.forEach { point ->
            val mapped = mapBitmapToView(imageView, point.first, point.second)
            canvas.drawCircle(mapped.first, mapped.second, 10f, calibrationPaint)
        }

        tapPoints.forEach { point ->
            val mapped = mapBitmapToView(imageView, point.first, point.second)
            canvas.drawCircle(mapped.first, mapped.second, 7f, tapPaint)
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
