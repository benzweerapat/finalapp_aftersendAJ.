package com.example.myapplication2

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import kotlin.math.atan2
import kotlin.math.hypot

class IndoorSetupFragment : Fragment(R.layout.fragment_indoor_setup) {

    private lateinit var imageView: ImageView
    private lateinit var projectInput: EditText
    private lateinit var floorInput: EditText
    private lateinit var calibrationSummary: TextView

    private var selectedImageUri: Uri? = null
    private var calibrationPoints = mutableListOf<Pair<Double, Double>>()
    private var calibratedScaleMetersPerPixel: Double? = null
    private var axisAngleRad: Double? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            imageView.setImageURI(uri)
            calibrationPoints.clear()
            calibratedScaleMetersPerPixel = null
            axisAngleRad = null
            updateSummary()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.setupFloorImage)
        projectInput = view.findViewById(R.id.inputProjectName)
        floorInput = view.findViewById(R.id.inputFloorName)
        calibrationSummary = view.findViewById(R.id.textCalibrationSummary)

        view.findViewById<Button>(R.id.btnPickFloorImage).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        view.findViewById<Button>(R.id.btnStartCalibrationLine).setOnClickListener {
            if (selectedImageUri == null) {
                toast("กรุณาเลือก Floor Plan ก่อน")
                return@setOnClickListener
            }
            calibrationPoints.clear()
            calibratedScaleMetersPerPixel = null
            axisAngleRad = null
            updateSummary("แตะ 2 จุดเพื่อสร้าง calibration line")
        }

        imageView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val normalized = NormalizedCoordinateMapper.viewToNormalized(imageView, event.x, event.y) ?: return@setOnTouchListener true
                onCalibrationTap(normalized)
            }
            true
        }

        view.findViewById<Button>(R.id.btnGoSurvey).setOnClickListener {
            goToSurvey()
        }

        updateSummary()
    }

    private fun onCalibrationTap(normalized: Pair<Double, Double>) {
        if (selectedImageUri == null) return
        if (calibrationPoints.size >= 2) return

        calibrationPoints.add(normalized)
        if (calibrationPoints.size == 2) {
            requestDistanceAndSolve()
        } else {
            updateSummary("เลือกจุดที่ 2 เพื่อจบ calibration")
        }
    }

    private fun requestDistanceAndSolve() {
        val dialogInput = EditText(requireContext()).apply {
            hint = "Distance (meters)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Calibration Distance")
            .setMessage("กรอกระยะจริงระหว่าง 2 จุด (เมตร)")
            .setView(dialogInput)
            .setPositiveButton("Apply") { _, _ ->
                val meters = dialogInput.text.toString().toDoubleOrNull()
                if (meters == null || meters <= 0.0) {
                    toast("ระยะต้องมากกว่า 0")
                    return@setPositiveButton
                }
                solveScaleAndAngle(meters)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun solveScaleAndAngle(realDistanceMeters: Double) {
        val drawable = imageView.drawable ?: return
        val p1 = calibrationPoints[0]
        val p2 = calibrationPoints[1]

        val x1 = p1.first * drawable.intrinsicWidth
        val y1 = p1.second * drawable.intrinsicHeight
        val x2 = p2.first * drawable.intrinsicWidth
        val y2 = p2.second * drawable.intrinsicHeight

        val pixelDistance = hypot(x2 - x1, y2 - y1)
        if (pixelDistance <= 0.0) {
            toast("จุด calibration ซ้ำกัน")
            return
        }

        calibratedScaleMetersPerPixel = realDistanceMeters / pixelDistance
        axisAngleRad = atan2(y2 - y1, x2 - x1)
        updateSummary()
    }

    private fun goToSurvey() {
        val project = projectInput.text.toString().trim().ifBlank { "IndoorProject" }
        val floor = floorInput.text.toString().trim().ifBlank { "Floor-1" }
        val uri = selectedImageUri
        val scale = calibratedScaleMetersPerPixel
        val angle = axisAngleRad

        if (uri == null || scale == null || angle == null || calibrationPoints.size < 2) {
            toast("ต้องเลือกภาพและทำ calibration line ก่อน")
            return
        }

        IndoorSessionManager.config = IndoorConfig(
            projectName = project,
            floorName = floor,
            imageUri = uri,
            scaleMetersPerPixel = scale,
            originNx = calibrationPoints[0].first,
            originNy = calibrationPoints[0].second,
            axisAngleRad = angle
        )
        IndoorSessionManager.clearWalk()

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, IndoorWalkFragment())
            .commit()
    }

    private fun updateSummary(extra: String? = null) {
        val base = if (calibratedScaleMetersPerPixel == null || axisAngleRad == null) {
            "Calibration: ยังไม่พร้อม"
        } else {
            "Calibration: scale=%.6f m/px, angle=%.2f°".format(
                calibratedScaleMetersPerPixel,
                Math.toDegrees(axisAngleRad!!)
            )
        }
        calibrationSummary.text = listOfNotNull(base, extra).joinToString("\n")
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
