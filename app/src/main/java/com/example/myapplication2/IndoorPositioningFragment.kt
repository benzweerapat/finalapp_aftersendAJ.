package com.example.myapplication2

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment

class IndoorPositioningFragment : Fragment(R.layout.fragment_indoor_positioning) {

    private data class CalibrationPoint(
        val pixelX: Double,
        val pixelY: Double,
        val meterX: Double,
        val meterY: Double
    )

    private lateinit var floorSpinner: Spinner
    private lateinit var floorPlanImage: ImageView
    private lateinit var overlayView: FloorPlanOverlayView
    private lateinit var modeLabel: TextView
    private lateinit var calibrationSummary: TextView
    private lateinit var transformSummary: TextView
    private lateinit var tapLog: TextView

    private val calibrationPoints = mutableListOf<CalibrationPoint>()
    private val recordedTaps = mutableListOf<String>()
    private var transform: SimilarityTransform? = null
    private var tapIndex = 1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        floorSpinner = view.findViewById(R.id.floorSpinner)
        floorPlanImage = view.findViewById(R.id.floorPlanImage)
        overlayView = view.findViewById(R.id.floorPlanOverlay)
        modeLabel = view.findViewById(R.id.modeLabel)
        calibrationSummary = view.findViewById(R.id.calibrationSummary)
        transformSummary = view.findViewById(R.id.transformSummary)
        tapLog = view.findViewById(R.id.tapLog)

        val floors = listOf("Floor 1", "Floor 2", "Floor 3", "Floor 4")
        floorSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, floors)
        floorSpinner.setSelection(0)

        view.findViewById<View>(R.id.btnStartCalibration).setOnClickListener {
            modeLabel.text = "Mode: Tap เพื่อเพิ่ม Calibration Point (${calibrationPoints.size}/4)"
        }
        view.findViewById<View>(R.id.btnSolveTransform).setOnClickListener { solveTransform() }
        view.findViewById<View>(R.id.btnClearAll).setOnClickListener { clearAll() }

        floorPlanImage.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val pixel = FloorPlanCoordinateMapper.viewToBitmap(floorPlanImage, event.x, event.y)
                if (pixel != null) {
                    handleTap(pixel.first, pixel.second)
                }
            }
            true
        }

        updateUi()
    }

    private fun handleTap(pixelX: Double, pixelY: Double) {
        if (calibrationPoints.size < 4 && transform == null) {
            requestMeterCoordinate(pixelX, pixelY)
            return
        }

        val solved = transform
        if (solved == null) {
            showMessage("เพิ่ม calibration อย่างน้อย 2 จุด แล้วกด Solve")
            return
        }

        val local = solved.map(pixelX, pixelY)
        recordedTaps.add(
            "#${tapIndex++}: pixel(%.1f, %.1f) -> local(%.2f m, %.2f m)".format(
                pixelX,
                pixelY,
                local.first,
                local.second
            )
        )
        overlayView.setTapPoints(overlayView.tapPoints + Pair(pixelX.toFloat(), pixelY.toFloat()))
        updateUi()
    }

    private fun requestMeterCoordinate(pixelX: Double, pixelY: Double) {
        val container = View.inflate(requireContext(), R.layout.dialog_calibration_point, null)
        val inputX = container.findViewById<EditText>(R.id.inputMeterX)
        val inputY = container.findViewById<EditText>(R.id.inputMeterY)
        inputX.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        inputY.inputType = inputX.inputType

        AlertDialog.Builder(requireContext())
            .setTitle("Calibration Point ${calibrationPoints.size + 1}")
            .setMessage("กำหนดพิกัดจริงหน่วยเมตรสำหรับจุดที่แตะ")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val meterX = inputX.text.toString().toDoubleOrNull()
                val meterY = inputY.text.toString().toDoubleOrNull()
                if (meterX == null || meterY == null) {
                    showMessage("กรอกตัวเลข x,y ให้ถูกต้อง")
                    return@setPositiveButton
                }
                calibrationPoints.add(CalibrationPoint(pixelX, pixelY, meterX, meterY))
                overlayView.setCalibrationPoints(calibrationPoints.map { Pair(it.pixelX.toFloat(), it.pixelY.toFloat()) })
                transform = null
                updateUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun solveTransform() {
        transform = SimilarityTransformEstimator.estimate(
            calibrationPoints.map {
                SimilarityTransformEstimator.Correspondence(
                    sourceX = it.pixelX,
                    sourceY = it.pixelY,
                    targetX = it.meterX,
                    targetY = it.meterY
                )
            }
        )

        if (transform == null) {
            showMessage("ต้องมีอย่างน้อย 2 จุดที่ไม่ซ้ำกัน")
        }
        updateUi()
    }

    private fun clearAll() {
        calibrationPoints.clear()
        recordedTaps.clear()
        transform = null
        tapIndex = 1
        overlayView.setCalibrationPoints(emptyList())
        overlayView.setTapPoints(emptyList())
        updateUi()
    }

    private fun updateUi() {
        calibrationSummary.text = if (calibrationPoints.isEmpty()) {
            "Calibration: ยังไม่มีจุด"
        } else {
            calibrationPoints.mapIndexed { index, point ->
                "P${index + 1} pixel(%.1f, %.1f) -> meter(%.2f, %.2f)".format(
                    point.pixelX,
                    point.pixelY,
                    point.meterX,
                    point.meterY
                )
            }.joinToString("\n")
        }

        val solved = transform
        transformSummary.text = if (solved == null) {
            "Transform: not solved"
        } else {
            "Transform solved | scale=%.5f m/px | rotation=%.2f°".format(solved.scale, solved.rotationDegrees)
        }

        tapLog.text = if (recordedTaps.isEmpty()) "Tap log: -" else recordedTaps.joinToString("\n")
        modeLabel.isVisible = true
    }

    private fun showMessage(text: String) {
        android.widget.Toast.makeText(requireContext(), text, android.widget.Toast.LENGTH_SHORT).show()
    }
}
