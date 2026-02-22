package com.example.myapplication2

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

class IndoorWalkFragment : Fragment(R.layout.fragment_indoor_walk) {

    private lateinit var mapImage: ImageView
    private lateinit var overlay: FloorPlanOverlayView
    private lateinit var textRealtime: TextView
    private lateinit var textStats: TextView

    private val rawTaps = mutableListOf<Pair<Double, Double>>()
    private var stepMeters = 1.0
    private var pendingTap: Pair<Double, Double>? = null
    private var surveyActive: Boolean = false

    fun isSurveyActive(): Boolean = surveyActive

    fun startSurvey() {
        surveyActive = true
        Toast.makeText(requireContext(), "Indoor survey started", Toast.LENGTH_SHORT).show()
        refreshUi()
    }

    fun stopSurveyAndExport() {
        if (!surveyActive) return
        surveyActive = false
        exportCsv()
        Toast.makeText(requireContext(), "Indoor survey stopped", Toast.LENGTH_SHORT).show()
        refreshUi()
    }

    fun setRadioMode(mode: IndoorSessionManager.RadioMode) {
        IndoorSessionManager.radioMode = mode
        refreshSignalLabel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapImage = view.findViewById(R.id.walkFloorImage)
        overlay = view.findViewById(R.id.walkOverlay)
        textRealtime = view.findViewById(R.id.textRealtimeSignal)
        textStats = view.findViewById(R.id.textWalkStats)

        ensureDefaultConfigIfMissing()
        mapImage.setImageURI(IndoorSessionManager.config?.imageUri)
        refreshSignalLabel()

        mapImage.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (!surveyActive) {
                    Toast.makeText(requireContext(), "กด START ก่อนเริ่มบันทึก Indoor", Toast.LENGTH_SHORT).show()
                    return@setOnTouchListener true
                }
                val n = NormalizedCoordinateMapper.viewToNormalized(mapImage, event.x, event.y) ?: return@setOnTouchListener true
                pendingTap = n
                Toast.makeText(requireContext(), "เลือกตำแหน่งแล้ว กด Add Pin/Checkpoint เพื่อบันทึก", Toast.LENGTH_SHORT).show()
            }
            true
        }

        view.findViewById<Button>(R.id.btnUndoPin).setOnClickListener { undoPin() }
        view.findViewById<Button>(R.id.btnCheckpoint).setOnClickListener { saveCheckpoint() }
        view.findViewById<Button>(R.id.btnStopAndExport).apply {
            visibility = View.GONE
            setOnClickListener { stopSurveyAndExport() }
        }

        refreshUi()
    }

    private fun ensureDefaultConfigIfMissing() {
        if (IndoorSessionManager.config != null) return
        val uri = Uri.parse("android.resource://${requireContext().packageName}/${R.drawable.floor_plan_placeholder}")
        IndoorSessionManager.config = IndoorConfig(
            projectName = "IndoorProject",
            floorName = "Floor-1",
            imageUri = uri,
            scaleMetersPerPixel = 1.0,
            originNx = 0.5,
            originNy = 0.5,
            axisAngleRad = 0.0
        )
    }

    private fun addPin(nx: Double, ny: Double) {
        val config = IndoorSessionManager.config ?: return
        val drawable = mapImage.drawable ?: return

        if (rawTaps.isNotEmpty()) {
            val prev = rawTaps.last()
            val pxDist = hypot(
                (nx - prev.first) * drawable.intrinsicWidth,
                (ny - prev.second) * drawable.intrinsicHeight
            )
            val meterDist = pxDist * config.scaleMetersPerPixel
            val count = (meterDist / stepMeters).toInt()
            for (i in 1 until count) {
                val t = i.toDouble() / count
                val ix = prev.first + (nx - prev.first) * t
                val iy = prev.second + (ny - prev.second) * t
                addCheckpoint(ix, iy, "interpolated")
            }
        }

        rawTaps.add(Pair(nx, ny))
        addCheckpoint(nx, ny, "manual_pin")
        refreshUi()
    }

    private fun addCheckpoint(nx: Double, ny: Double, source: String) {
        val config = IndoorSessionManager.config ?: return
        val drawable = mapImage.drawable ?: return

        val local = IndoorCoordinateTransformer.normalizedToLocalMeters(
            normalizedX = nx,
            normalizedY = ny,
            imageWidth = drawable.intrinsicWidth,
            imageHeight = drawable.intrinsicHeight,
            originNx = config.originNx,
            originNy = config.originNy,
            axisAngleRad = config.axisAngleRad,
            scaleMetersPerPixel = config.scaleMetersPerPixel
        )

        val idx = IndoorSessionManager.checkpoints.size + 1
        val ts = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

        IndoorSessionManager.checkpoints.add(
            IndoorCheckpoint(
                index = idx,
                timestamp = ts,
                normalizedX = nx,
                normalizedY = ny,
                localX = local.first,
                localY = local.second,
                source = source
            )
        )

        val all = IndoorSessionManager.checkpoints.map {
            Pair(
                (it.normalizedX * drawable.intrinsicWidth).toFloat(),
                (it.normalizedY * drawable.intrinsicHeight).toFloat()
            )
        }
        overlay.setTapPoints(all)
    }

    private fun undoPin() {
        if (!surveyActive) return
        if (IndoorSessionManager.checkpoints.isNotEmpty()) {
            IndoorSessionManager.checkpoints.removeLast()
        }
        if (rawTaps.isNotEmpty()) rawTaps.removeLast()
        refreshUi()
    }

    private fun saveCheckpoint() {
        if (!surveyActive) {
            Toast.makeText(requireContext(), "กด START ก่อน", Toast.LENGTH_SHORT).show()
            return
        }

        val pending = pendingTap
        if (pending == null) {
            Toast.makeText(requireContext(), "แตะตำแหน่งบนแผนที่ก่อน", Toast.LENGTH_SHORT).show()
            return
        }
        addPin(pending.first, pending.second)
        pendingTap = null
        Toast.makeText(requireContext(), "Checkpoint saved (${IndoorSessionManager.checkpoints.size})", Toast.LENGTH_SHORT).show()
    }

    private fun exportCsv() {
        val config = IndoorSessionManager.config ?: return
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("project", "floor", "radio_mode", "index", "timestamp", "norm_x", "norm_y", "local_x_m", "local_y_m", "source"))

        IndoorSessionManager.checkpoints.forEach {
            rows.add(
                listOf(
                    config.projectName,
                    config.floorName,
                    IndoorSessionManager.radioMode.name,
                    it.index.toString(),
                    it.timestamp,
                    "%.6f".format(it.normalizedX),
                    "%.6f".format(it.normalizedY),
                    "%.3f".format(it.localX),
                    "%.3f".format(it.localY),
                    it.source
                )
            )
        }

        val fileName = "${config.projectName}_${config.floorName}_${IndoorSessionManager.radioMode.name}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_indoor_walk.csv"
        val ok = saveCsvToIndoor(fileName, rows)
        if (ok) Toast.makeText(requireContext(), "Exported to Download/DriveTest/Indoor", Toast.LENGTH_LONG).show()
    }

    private fun saveCsvToIndoor(fileName: String, rows: List<List<String>>): Boolean {
        val csv = rows.joinToString("\n") { it.joinToString(",") }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DriveTest/Indoor")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DriveTest/Indoor")
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeText(csv)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshSignalLabel() {
        val mode = if (IndoorSessionManager.radioMode == IndoorSessionManager.RadioMode.WIFI) "WiFi" else "Cellular"
        textRealtime.text = "Indoor Walk Test • Signal Mode: $mode"
    }

    private fun refreshUi() {
        val total = IndoorSessionManager.checkpoints.size
        val state = if (surveyActive) "RUNNING" else "IDLE"
        textStats.text = "Survey: $state | Pins/Checkpoints: $total"
    }
}
