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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IndoorSetupFragment : Fragment(R.layout.fragment_indoor_setup) {

    private lateinit var imageView: ImageView
    private lateinit var overlayView: FloorPlanOverlayView
    private lateinit var projectInput: EditText
    private lateinit var floorInput: EditText
    private lateinit var originLatInput: EditText
    private lateinit var originLongInput: EditText
    private lateinit var calibrationSummary: TextView

    private var selectedImageUri: Uri? = null
    private var calibrationPoints = mutableListOf<Pair<Double, Double>>()
    private var calibrationSession: CalibrationSession? = null
    private var draggingPointIndex: Int? = null
    private var calibrationModeActive: Boolean = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            imageView.setImageURI(uri)
            calibrationPoints.clear()
            calibrationSession = null
            calibrationModeActive = false
            syncOverlay()
            updateSummary()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.setupFloorImage)
        overlayView = view.findViewById(R.id.setupFloorOverlay)
        projectInput = view.findViewById(R.id.inputProjectName)
        floorInput = view.findViewById(R.id.inputFloorName)
        originLatInput = view.findViewById(R.id.inputOriginLat)
        originLongInput = view.findViewById(R.id.inputOriginLong)
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
            calibrationSession = null
            calibrationModeActive = true
            syncOverlay()
            showCalibrationStartDialog()
            updateSummary("Calibration Mode พร้อมใช้งาน: เลือกจุด 1-4 (บนซ้าย → บนขวา → ล่างขวา → ล่างซ้าย)")
        }

        imageView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        view.findViewById<Button>(R.id.btnGoSurvey).setOnClickListener {
            goToSurvey()
        }

        updateSummary()
    }

    private fun handleTouch(event: MotionEvent) {
        if (selectedImageUri == null || !calibrationModeActive) return
        val normalized = NormalizedCoordinateMapper.viewToNormalized(imageView, event.x, event.y) ?: return
        val drawable = imageView.drawable ?: return
        val px = normalized.first * drawable.intrinsicWidth
        val py = normalized.second * drawable.intrinsicHeight

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingPointIndex = overlayView.findNearestCalibrationPoint(event.x, event.y)
                if (draggingPointIndex == null && calibrationPoints.size < 4) {
                    calibrationPoints.add(Pair(px, py))
                    syncOverlay()
                    if (calibrationPoints.size == 4) {
                        requestRectangleDimensionsAndSolve()
                    } else {
                        updateSummary("เลือกจุดลำดับถัดไป (${calibrationPoints.size + 1}/4)")
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dragIndex = draggingPointIndex ?: return
                calibrationPoints[dragIndex] = Pair(px, py)
                calibrationSession = null
                syncOverlay()
                updateSummary("กำลังปรับจุดที่ ${dragIndex + 1}")
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingPointIndex != null && calibrationPoints.size == 4) {
                    calibrationSession = null
                    requestRectangleDimensionsAndSolve()
                }
                draggingPointIndex = null
            }
        }
    }

    private fun requestRectangleDimensionsAndSolve() {
        val container = View.inflate(requireContext(), R.layout.dialog_calibration_dimensions, null)
        val widthInput = container.findViewById<EditText>(R.id.inputRealWidth)
        val heightInput = container.findViewById<EditText>(R.id.inputRealHeight)
        widthInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        heightInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle("Calibration Dimensions")
            .setMessage("กรอกความกว้าง/ยาวจริงของอาคาร (เมตร)")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val realWidth = widthInput.text.toString().toDoubleOrNull()
                val realHeight = heightInput.text.toString().toDoubleOrNull()
                if (realWidth == null || realHeight == null || realWidth <= 0.0 || realHeight <= 0.0) {
                    toast("Width/Height ต้องมากกว่า 0")
                    return@setPositiveButton
                }
                solveHomography(realWidth, realHeight)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun solveHomography(realWidth: Double, realHeight: Double) {
        val drawable = imageView.drawable ?: return
        if (calibrationPoints.size != 4) return

        val points = calibrationPoints.map { PixelPoint(it.first, it.second) }
        val matrix = try {
            IndoorCoordinateTransformer.solveHomography(points, realWidth, realHeight)
        } catch (e: IllegalArgumentException) {
            toast(e.message ?: "Calibration failed")
            return
        }

        calibrationSession = CalibrationSession(
            sessionId = "cal-${System.currentTimeMillis()}",
            floorplanId = floorInput.text.toString().trim().ifBlank { "floorplan-default" },
            imageWidth = drawable.intrinsicWidth,
            imageHeight = drawable.intrinsicHeight,
            p1 = points[0],
            p2 = points[1],
            p3 = points[2],
            p4 = points[3],
            realWidth = realWidth,
            realHeight = realHeight,
            homographyMatrix = matrix.toList(),
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
        )
        calibrationModeActive = false
        updateSummary("Calibration complete")
    }


    private fun showCalibrationStartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Calibration Mode")
            .setMessage(
                """เริ่มเลือก 4 จุดตามลำดับ:
1) บนซ้าย
2) บนขวา
3) ล่างขวา
4) ล่างซ้าย

สามารถลาก marker เพื่อปรับตำแหน่งได้"""
            )
            .setPositiveButton("เริ่ม") { _, _ -> }
            .show()
    }

    private fun goToSurvey() {
        val project = projectInput.text.toString().trim().ifBlank { "IndoorProject" }
        val floor = floorInput.text.toString().trim().ifBlank { "Floor-1" }
        val uri = selectedImageUri
        val session = calibrationSession

        if (uri == null || session == null) {
            toast("ต้องเลือกภาพและทำ calibration 4 จุดก่อน")
            return
        }

        val originLat = originLatInput.text.toString().trim().toDoubleOrNull()
        val originLong = originLongInput.text.toString().trim().toDoubleOrNull()

        IndoorSessionManager.config = IndoorConfig(
            projectName = project,
            floorName = floor,
            imageUri = uri,
            scaleMetersPerPixel = 1.0,
            originNx = 0.0,
            originNy = 0.0,
            axisAngleRad = 0.0,
            calibrationSession = session,
            originLatitude = originLat,
            originLongitude = originLong
        )
        IndoorSessionManager.clearWalk()

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, IndoorWalkFragment())
            .commit()
    }

    private fun syncOverlay() {
        overlayView.setCalibrationPoints(calibrationPoints.map { Pair(it.first.toFloat(), it.second.toFloat()) })
    }

    private fun updateSummary(extra: String? = null) {
        val base = calibrationSession?.let {
            val lonScale = IndoorCoordinateTransformer.longitudeDegreesPerMeter(originLatInput.text.toString().toDoubleOrNull() ?: 0.0)
            "Calibration: ready, ${it.realWidth}m x ${it.realHeight}m\nH=[${it.homographyMatrix.joinToString(",") { v -> "%.4f".format(v) }}]\nlatScale=%.10f, lonScale=%.10f deg/m".format(
                0.00000899,
                lonScale
            )
        } ?: if (calibrationModeActive) "Calibration: กำลังเลือกจุดอ้างอิง (ต้องครบ 4 จุด)" else "Calibration: ยังไม่พร้อม (กด Start Calibration)"
        calibrationSummary.text = listOfNotNull(base, extra).joinToString("\n")
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
