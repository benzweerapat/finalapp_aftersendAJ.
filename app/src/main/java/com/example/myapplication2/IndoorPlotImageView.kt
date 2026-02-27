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

    data class PlotPoint(
        val nx: Double,
        val ny: Double,
        val color: Int
    )

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#22D3EE")
    }
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#35FFFFFF")
        strokeWidth = 1f
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EF4444")
    }
    private val pinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#EF4444")
    }

    private val pinHeadCenterYOffset = -40f
    private val pinStemTopYOffset = -34f
    private val pinStemBottomYOffset = 20f

    private val matrixValues = FloatArray(9)
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastX = 0f
    private var lastY = 0f
    private var plotEnabled = false

    private val points = mutableListOf<PlotPoint>()

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

    fun setPlotPoints(newPoints: List<PlotPoint>) {
        points.clear()
        points.addAll(newPoints)
        invalidate()
    }

    fun setPointsNormalized(newPoints: List<Pair<Double, Double>>) {
        setPlotPoints(newPoints.map { PlotPoint(it.first, it.second, Color.parseColor("#22D3EE")) })
    }

    fun zoomIn() {
        zoomBy(1.2f)
    }

    fun zoomOut() {
        zoomBy(0.83f)
    }

    fun resetViewFitScreen() {
        fitCenterLikeMatrix()
    }

    fun getCenterNormalized(): Pair<Double, Double>? {
        return mapViewToNormalized(width / 2f, height / 2f)
    }

    fun getPinTipNormalized(): Pair<Double, Double>? {
        val cx = width / 2f
        val pinTipY = height / 2f + pinStemBottomYOffset
        return mapViewToNormalized(cx, pinTipY)
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
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (kotlin.math.abs(dx) > 1 || kotlin.math.abs(dy) > 1) {
                        imageMatrix.postTranslate(dx, dy)
                        this.imageMatrix = imageMatrix
                        invalidate()
                    }
                    lastX = event.x
                    lastY = event.y
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawGrid(canvas)

        val d = drawable ?: return
        points.forEach {
            val px = (it.nx * d.intrinsicWidth).toFloat()
            val py = (it.ny * d.intrinsicHeight).toFloat()
            val mapped = floatArrayOf(px, py)
            imageMatrix.mapPoints(mapped)
            pointPaint.color = it.color
            canvas.drawCircle(mapped[0], mapped[1], 9f, pointPaint)
            canvas.drawCircle(mapped[0], mapped[1], 9f, pointStrokePaint)
        }

        drawFixedCenterPin(canvas)
    }

    private fun drawGrid(canvas: Canvas) {
        val stepPx = 48f
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += stepPx
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += stepPx
        }
    }

    private fun drawFixedCenterPin(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx, cy + pinStemTopYOffset, cx, cy + pinStemBottomYOffset, pinLinePaint)
        canvas.drawCircle(cx, cy + pinHeadCenterYOffset, 10f, pinPaint)
        canvas.drawCircle(cx, cy + pinHeadCenterYOffset, 10f, pointStrokePaint)
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
        invalidate()
    }

    private fun zoomBy(scaleFactor: Float) {
        imageMatrix.getValues(matrixValues)
        val currentScale = matrixValues[Matrix.MSCALE_X]
        val targetScale = (currentScale * scaleFactor).coerceIn(0.5f, 8f)
        val relative = targetScale / currentScale
        imageMatrix.postScale(relative, relative, width / 2f, height / 2f)
        this.imageMatrix = imageMatrix
        invalidate()
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
