package com.example.myapplication2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class IndoorPlotImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.FILL
    }

    private val matrixValues = FloatArray(9)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var plotEnabled = false

    private val points = mutableListOf<Pair<Double, Double>>()
    var onPointAdded: ((Double, Double) -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setPlotEnabled(enabled: Boolean) {
        plotEnabled = enabled
    }

    fun setImageFromUri(uri: Uri?) {
        if (uri != null) {
            setImageURI(uri)
        } else {
            setImageResource(R.drawable.floor_plan_placeholder)
        }
        fitCenterLikeMatrix()
    }

    fun setPointsNormalized(newPoints: List<Pair<Double, Double>>) {
        points.clear()
        points.addAll(newPoints)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitCenterLikeMatrix()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!plotEnabled) return false
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (kotlin.math.abs(dx) > 2 || kotlin.math.abs(dy) > 2) {
                        imageMatrix.postTranslate(dx, dy)
                        this.imageMatrix = imageMatrix
                        dragging = true
                        invalidate()
                    }
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && !scaleDetector.isInProgress) {
                    val normalized = mapViewToNormalized(event.x, event.y)
                    if (normalized != null) {
                        points.add(normalized)
                        onPointAdded?.invoke(normalized.first, normalized.second)
                        invalidate()
                    }
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = drawable ?: return
        points.forEach {
            val px = (it.first * d.intrinsicWidth).toFloat()
            val py = (it.second * d.intrinsicHeight).toFloat()
            val mapped = floatArrayOf(px, py)
            imageMatrix.mapPoints(mapped)
            canvas.drawCircle(mapped[0], mapped[1], 8f, drawPaint)
        }
    }

    private fun mapViewToNormalized(x: Float, y: Float): Pair<Double, Double>? {
        val d = drawable ?: return null
        val inv = Matrix()
        if (!imageMatrix.invert(inv)) return null
        val pts = floatArrayOf(x, y)
        inv.mapPoints(pts)
        val px = pts[0]
        val py = pts[1]
        if (px < 0f || py < 0f || px > d.intrinsicWidth || py > d.intrinsicHeight) return null
        return Pair(px / d.intrinsicWidth.toDouble(), py / d.intrinsicHeight.toDouble())
    }

    private fun fitCenterLikeMatrix() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return

        val m = Matrix()
        val scale = minOf(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        val dx = (width - d.intrinsicWidth * scale) / 2f
        val dy = (height - d.intrinsicHeight * scale) / 2f
        m.postScale(scale, scale)
        m.postTranslate(dx, dy)
        imageMatrix = m
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor.coerceIn(0.8f, 1.25f)
            imageMatrix.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]
            val targetScale = (currentScale * scaleFactor).coerceIn(0.5f, 8f)
            val relative = targetScale / currentScale
            imageMatrix.postScale(relative, relative, detector.focusX, detector.focusY)
            this@IndoorPlotImageView.imageMatrix = imageMatrix
            invalidate()
            return true
        }
    }
}
