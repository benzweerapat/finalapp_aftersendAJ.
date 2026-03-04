package com.example.drivetest

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IndoorWalkFragment : Fragment(R.layout.fragment_indoor_walk) {

    private lateinit var mapView: IndoorPlotImageView
    private var startPrerequisitesReady: Boolean = true
    private val uiHandler = Handler(Looper.getMainLooper())
    private val calibrationPointsNormalized = mutableListOf<Pair<Double, Double>>()
    private var calibrationLocked: Boolean = false

    private data class FloorPlanPointRecord(
        val timestamp: String,
        val floorLabel: String,
        val pointNo: Int,
        val lat: String,
        val long: String,
        val mapX: String,
        val mapY: String,
        val networkType: String,
        val operatorName: String,
        val cellIdBssid: String,
        val pci: String,
        val tac: String,
        val arfcn: String,
        val freq: String,
        val bw: String,
        val rsrpRssi: String,
        val rsrqSinr: String,
        val sinr: String,
        val ssid: String,
        val mac: String,
        val channel: String,
        val linkSpeed: String,
        val security: String,
        val signalQuality: String,
        val snr: String,
        val gpsLat: String,
        val gpsLong: String,
        val gpsAltitude: String,
        val pressure: String,
        val standard: String,
        val speed: String,
        val bearing: String,
        val accuracy: String,
        val baroRelAlt: String,
        val baroFloor: String,
        val gpsRelAlt: String,
        val gpsFloor: String,
        val simState: String,
        val nrState: String,
        val netOpCode: String,
        val roaming: String,
        val callState: String,
        val mcc: String,
        val mnc: String,
        val mncMaster: String,
        val longCid: String,
        val nodeIdNid: String,
        val cidBid: String,
        val band: String,
        val dataState: String
    )

    private data class FloorPlanNeighborRecord(
        val pointNo: Int,
        val timestamp: String,
        val lat: String,
        val long: String,
        val servingNetworkType: String,
        val servingTech: String,
        val servingArfcn: String,
        val servingPci: String,
        val servingCellId: String,
        val connectedSsid: String,
        val neighborIndex: Int,
        val neighborType: String,
        val neighborId: String,
        val neighborPci: String,
        val neighborArfcn: String,
        val neighborFreqMhz: String,
        val neighborRsrp: String,
        val neighborRsrq: String,
        val neighborSinr: String,
        val neighborSsid: String,
        val neighborBssid: String,
        val neighborRssi: String,
        val neighborChannel: String,
        val neighborSecurity: String
    )

    private data class Quad<A,B,C,D>(val a:A,val b:B,val c:C,val d:D)

    private val pointRecords = mutableListOf<FloorPlanPointRecord>()
    private val neighborRecords = mutableListOf<FloorPlanNeighborRecord>()
    private var latestWifiScanResults: List<ScanResult> = emptyList()
    private var wifiScanReceiverRegistered = false
    private var lastWifiScanRequestAtMs: Long = 0L

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isAdded) return
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            val wm = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            latestWifiScanResults = wm.scanResults.orEmpty()
        }
    }

    private val pickFloorPlanLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {

                // A) เซ็ต floor plan ใหม่
                IndoorSessionManager.importedFloorPlanUri = uri
                mapView.setImageFromUri(uri)

                // B) เรียก reset floor/height ที่ MainActivity
                (activity as? MainActivity)?.resetIndoorSelectionsForNewFloorPlan()

                // C) รีเซ็ต calibration ของรูปเดิม
                IndoorSessionManager.config = IndoorSessionManager.config?.copy(calibrationSession = null)
                calibrationPointsNormalized.clear()
                calibrationLocked = false
                mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)
                mapView.setCalibrationCursorEnabled(true)

                // D) เคลียร์จุดเก่า (กัน plot ค้างบนรูปใหม่)
                IndoorSessionManager.points.clear()
                IndoorSessionManager.plottedPointsNormalized.clear()
                IndoorSessionManager.checkpoints.clear()
                pointRecords.clear()
                neighborRecords.clear()
                refreshMapPoints()
                updatePointCount()
                updateAddPointButtonUi()
                updatePrimaryActionButtonState()
                (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
                    ?.setSurveyUiVisible(false)

                (activity as? MainActivity)?.showStartFloorDialog { selectedFloor ->

                    val main = activity as? MainActivity ?: return@showStartFloorDialog

                    // ตั้งค่า floor
                    main.onIndoorStartFloorSelected(selectedFloor)

                    // อัปเดต label ปุ่มใน panel
                    updateGroundControlLabels()

                    Toast.makeText(
                        requireContext(),
                        "Selected floor: $selectedFloor",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val signalTicker = object : Runnable {
        override fun run() {
            if (!isAdded) return
            refreshSignalPanel()
            requestWifiScanIfNeeded()
            uiHandler.postDelayed(this, 1000)
        }
    }
    private fun updatePointCount() {
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.updatePointCount(IndoorSessionManager.points.size)
        (activity as? MainActivity)?.setIndoorSaveButtonVisible(pointRecords.isNotEmpty())
    }

    fun setRadioMode(mode: IndoorSessionManager.RadioMode) {
        IndoorSessionManager.radioMode = mode
        if (isAdded) refreshSignalPanel()
    }

    fun startSurvey() {
        IndoorSessionManager.config = IndoorSessionManager.config?.copy(calibrationSession = null)
        calibrationPointsNormalized.clear()
        calibrationLocked = false
        mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)
        mapView.setCalibrationCursorEnabled(true)

        IndoorSessionManager.surveyRunning = true
        setSurveyRunning(true)
        showCalibrationRequiredDialog()
        (activity as? MainActivity)?.showStartHint("พร้อมเริ่มคาลิเบรต: วางธง 4 จุดและกรอกขนาดจริง")
        Toast.makeText(requireContext(), "Survey started: press Calibration and pin 4 points", Toast.LENGTH_LONG).show()
    }

    fun stopSurvey() {
        if (!IndoorSessionManager.surveyRunning) return
        setSurveyRunning(false)
        exportCsv(showToast = true)
        IndoorSessionManager.points.clear()
        IndoorSessionManager.plottedPointsNormalized.clear()
        IndoorSessionManager.config = IndoorSessionManager.config?.copy(calibrationSession = null)
        calibrationPointsNormalized.clear()
        calibrationLocked = false
        mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)
        mapView.setCalibrationCursorEnabled(false)
        pointRecords.clear()
        neighborRecords.clear()
        refreshMapPoints()
        updatePointCount()
        (activity as? MainActivity)?.showStartHint("พร้อมเริ่มคาลิเบรต: วางธง 4 จุดและกรอกขนาดจริง")
        Toast.makeText(requireContext(), "Floor plan survey stopped", Toast.LENGTH_SHORT).show()
    }

    fun isSurveyRunning(): Boolean = IndoorSessionManager.surveyRunning


    fun setSurveyRunning(running: Boolean) {
        IndoorSessionManager.surveyRunning = running
        updateGroundButtonsEnabledState()
        updateAddPointButtonUi()
    }



    fun setStartPrerequisitesReady(ready: Boolean) {
        startPrerequisitesReady = ready
        updateAddPointButtonUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.indoorMapView)
        ensureDefaultConfigIfMissing()

        if (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.indoorSignalPanelContainer, IndoorSignalPanelFragment())
                .commitNow()
        }

        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setSurveyUiVisible(IndoorSessionManager.config?.calibrationSession != null)

        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setOnAddPointClickListener {
                if (IndoorSessionManager.config?.calibrationSession == null) {
                    captureCalibrationFlagAtPin()
                    updatePrimaryActionButtonState()
                    return@setOnAddPointClickListener
                }

                val pinTip = mapView.getPinTipNormalized()
                if (pinTip == null) {
                    Toast.makeText(requireContext(), "Move map so pin is over floor plan", Toast.LENGTH_SHORT).show()
                    return@setOnAddPointClickListener
                }

                val calibration = IndoorSessionManager.config?.calibrationSession
                val drawable = mapView.drawable
                if (calibration != null && drawable != null) {
                    val px = pinTip.first * drawable.intrinsicWidth
                    val py = pinTip.second * drawable.intrinsicHeight
                    val inside = IndoorCoordinateTransformer.isPointInsideCalibrationQuad(
                        px = px,
                        py = py,
                        p1 = calibration.p1,
                        p2 = calibration.p2,
                        p3 = calibration.p3,
                        p4 = calibration.p4
                    )
                    if (!inside) {
                        Toast.makeText(requireContext(), "Point is outside calibration area; lat/long may be inaccurate", Toast.LENGTH_LONG).show()
                        return@setOnAddPointClickListener
                    }
                }

                // Use the bottom tip of the fixed pin, not the visual center, for saved point coordinates
                recordPoint(pinTip.first.toFloat(), pinTip.second.toFloat())
                refreshMapPoints()
                updatePointCount()
                updatePrimaryActionButtonState()
            }
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setOnUndoClickListener {
                if (!calibrationLocked && calibrationPointsNormalized.isNotEmpty()) {
                    calibrationPointsNormalized.removeLastOrNull()
                    mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)
                    Toast.makeText(requireContext(), "Undo calibration point", Toast.LENGTH_SHORT).show()
                    return@setOnUndoClickListener
                }

                if (pointRecords.isEmpty()) {
                    Toast.makeText(requireContext(), "No point to undo", Toast.LENGTH_SHORT).show()
                    return@setOnUndoClickListener
                }
                try {
                    val removedPoint = IndoorSessionManager.points.removeAt(IndoorSessionManager.points.lastIndex)
                    pointRecords.removeAll { it.pointNo == removedPoint.pointNo }
                    neighborRecords.removeAll { it.pointNo == removedPoint.pointNo }
                    refreshMapPoints()
                    updatePointCount()
                    updatePrimaryActionButtonState()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Undo failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setOnClearClickListener {
                IndoorSessionManager.points.clear()
                IndoorSessionManager.plottedPointsNormalized.clear()
                pointRecords.clear()
                neighborRecords.clear()
                refreshMapPoints()
                updatePointCount()
                updatePrimaryActionButtonState()
                Toast.makeText(requireContext(), "Cleared all points", Toast.LENGTH_SHORT).show()
            }





        mapView.setImageFromUri(IndoorSessionManager.importedFloorPlanUri ?: IndoorSessionManager.config?.imageUri)
        mapView.setPlotEnabled(true)
        refreshMapPoints()

        view.findViewById<Button>(R.id.btnResetView).setOnClickListener { mapView.resetViewFitScreen() }

        view.findViewById<Button>(R.id.btnCalibration).visibility = View.GONE

        view.findViewById<Button>(R.id.btnBrowseFloorPlan).setOnClickListener {
            if (IndoorSessionManager.surveyRunning) {
                Toast.makeText(requireContext(), "Stop survey before changing floor plan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickFloorPlanLauncher.launch("image/*")
        }

        updatePointCount()
        updateGroundControlLabels()
        updatePrimaryActionButtonState()
        updateGroundButtonsEnabledState()
        updateAddPointButtonUi()
        refreshSignalPanel()
        if (!IndoorSessionManager.surveyRunning) {
            (activity as? MainActivity)?.showStartHint("พร้อมเริ่มคาลิเบรต: วางธง 4 จุดและกรอกขนาดจริง")
        }
    }

    override fun onResume() {
        super.onResume()
        registerWifiScanReceiverIfNeeded()
        primeLatestWifiScanResults()
        requestWifiScanIfNeeded(force = true)
        uiHandler.post(signalTicker)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(signalTicker)
        unregisterWifiScanReceiverIfNeeded()
    }

    private fun registerWifiScanReceiverIfNeeded() {
        if (wifiScanReceiverRegistered || !isAdded) return
        try {
            requireContext().registerReceiver(
                wifiScanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            )
            wifiScanReceiverRegistered = true
        } catch (_: Exception) {
            wifiScanReceiverRegistered = false
        }
    }

    private fun unregisterWifiScanReceiverIfNeeded() {
        if (!wifiScanReceiverRegistered || !isAdded) return
        try {
            requireContext().unregisterReceiver(wifiScanReceiver)
        } catch (_: Exception) {
        } finally {
            wifiScanReceiverRegistered = false
        }
    }

    private fun primeLatestWifiScanResults() {
        if (!isAdded) return
        try {
            val wm = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            latestWifiScanResults = wm.scanResults.orEmpty()
        } catch (_: Exception) {
            latestWifiScanResults = emptyList()
        }
    }

    private fun requestWifiScanIfNeeded(force: Boolean = false) {
        if (!isAdded || IndoorSessionManager.radioMode != IndoorSessionManager.RadioMode.WIFI) return
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val now = SystemClock.elapsedRealtime()
        val enoughTimePassed = now - lastWifiScanRequestAtMs >= 2000L
        if (!force && !enoughTimePassed) return

        try {
            val wm = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wm.isWifiEnabled) {
                wm.startScan()
                lastWifiScanRequestAtMs = now
            }
        } catch (_: Exception) {
        }
    }



    private fun showCalibrationRequiredDialog() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Calibrated Required")
            .setMessage(
                """step 1 : เลื่อนรูปภาพไปยังมุมตึกด้านซ้ายบนที่ต้องการให้ calibrated โดยให้ตรงกับ ธง
step 2 : กดปุ่ม calibrated
step 3 : ย้อนกลับไป step 1 แต่เลื่อนไปมุมขวาบน  ล่างขวา ล่างซ้าย
**ซ้ายบน ขวาบน ขวาล่าง ซ้ายล่าง** เท่านั้น
step 4 : กรอกความยาวจริง 2 ด้าน"""
            )
            .setPositiveButton("OK", null)
            .show()
    }
    private fun updateAddPointButtonUi() {
        val calibrationReady = IndoorSessionManager.config?.calibrationSession != null
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setAddPointEnabled(calibrationReady)
    }

    private fun captureCalibrationFlagAtPin() {
        val main = activity as? MainActivity
        if (main != null && !main.hasSelectedStartFloor) {
            Toast.makeText(requireContext(), "Please select Floor before calibration", Toast.LENGTH_SHORT).show()
            return
        }
        if (calibrationLocked) {
            Toast.makeText(requireContext(), "Calibration already saved for this run", Toast.LENGTH_SHORT).show()
            return
        }
        val pinTip = mapView.getPinTipNormalized()
        if (pinTip == null) {
            Toast.makeText(requireContext(), "Move map so flag is over floor plan", Toast.LENGTH_SHORT).show()
            return
        }
        if (calibrationPointsNormalized.size >= 4) {
            Toast.makeText(requireContext(), "Calibration pointsครบแล้ว", Toast.LENGTH_SHORT).show()
            return
        }

        calibrationPointsNormalized.add(pinTip)
        mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)

        if (calibrationPointsNormalized.size < 4) {
            Toast.makeText(requireContext(), "Calibration point ${calibrationPointsNormalized.size}/4 saved", Toast.LENGTH_SHORT).show()
            return
        }
        showCalibrationDimensionsDialog()
    }

    private fun showCalibrationDimensionsDialog() {
        val container = View.inflate(requireContext(), R.layout.dialog_calibration_dimensions, null)
        val widthInput = container.findViewById<EditText>(R.id.inputRealWidth)
        val heightInput = container.findViewById<EditText>(R.id.inputRealHeight)
        widthInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        heightInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle("Calibration Dimensions")
            .setMessage("Enter real width/height in meters")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val realWidth = widthInput.text.toString().toDoubleOrNull()
                val realHeight = heightInput.text.toString().toDoubleOrNull()
                if (realWidth == null || realHeight == null || realWidth <= 0.0 || realHeight <= 0.0) {
                    Toast.makeText(requireContext(), "Width/Height must be > 0", Toast.LENGTH_SHORT).show()
                    calibrationPointsNormalized.removeLastOrNull()
                    mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)
                    return@setPositiveButton
                }
                saveCalibrationSession(realWidth, realHeight)
            }
            .setNeutralButton("Undo last calibrated") { _, _ ->
                calibrationPointsNormalized.removeLastOrNull()
                mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)
            }
            .setNegativeButton("Cancel") { _, _ ->
                calibrationPointsNormalized.removeLastOrNull()
                mapView.setCalibrationFlagsNormalized(calibrationPointsNormalized)
            }
            .show()
    }

    private fun saveCalibrationSession(realWidth: Double, realHeight: Double) {
        val drawable = mapView.drawable ?: return
        if (calibrationPointsNormalized.size != 4) return
        val pixels = calibrationPointsNormalized.map {
            PixelPoint(it.first * drawable.intrinsicWidth, it.second * drawable.intrinsicHeight)
        }
        val matrix = try {
            IndoorCoordinateTransformer.solveHomography(pixels, realWidth, realHeight)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(requireContext(), e.message ?: "Calibration failed", Toast.LENGTH_SHORT).show()
            return
        }

        val config = IndoorSessionManager.config ?: return
        IndoorSessionManager.config = config.copy(
            calibrationSession = CalibrationSession(
                sessionId = "cal-${System.currentTimeMillis()}",
                floorplanId = config.floorName,
                imageWidth = drawable.intrinsicWidth,
                imageHeight = drawable.intrinsicHeight,
                p1 = pixels[0],
                p2 = pixels[1],
                p3 = pixels[2],
                p4 = pixels[3],
                realWidth = realWidth,
                realHeight = realHeight,
                homographyMatrix = matrix.toList(),
                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
            )
        )
        calibrationLocked = true
        mapView.setCalibrationCursorEnabled(false)
        updateAddPointButtonUi()
        updateGroundControlLabels()
        updatePrimaryActionButtonState()
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setSurveyUiVisible(true)
        (activity as? MainActivity)?.showStartHint("Calibration saved. You can add report points now")
        Toast.makeText(requireContext(), "Calibration saved", Toast.LENGTH_SHORT).show()
    }

    fun updateGroundControlLabels() {
        val main = activity as? MainActivity ?: return
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)?.apply {
            updateCalibrateButtonLabel(main.getStartFloorButtonLabel())
            updateEditFloorHeightButtonLabel(main.getFloorHeightButtonLabel())
        }
    }

    private fun updateGroundButtonsEnabledState() {
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)

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

    private fun recordPoint(nx: Float, ny: Float) {
        val snapshot = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> getWifiSnapshot()
            IndoorSessionManager.RadioMode.CELLULAR -> getCellularSnapshot()
        }

        // ✅ floor ที่ผู้ใช้เลือกจาก Select start floor
        val selectedFloor = (activity as? MainActivity)?.startFloor?.toString() ?: "1"

        val config = IndoorSessionManager.config
        val pointNo = IndoorSessionManager.points.size + 1
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        val drawable = mapView.drawable
        val pixelX = nx.toDouble() * (drawable?.intrinsicWidth ?: 1)
        val pixelY = ny.toDouble() * (drawable?.intrinsicHeight ?: 1)

        val calibration = config?.calibrationSession
        val realXY = if (calibration != null) {
            IndoorCoordinateTransformer.pixelToReal(calibration.homographyMatrix.toDoubleArray(), pixelX, pixelY)
        } else {
            Pair(nx.toDouble(), ny.toDouble())
        }

        val latLong = if (config?.originLatitude != null && config.originLongitude != null) {
            IndoorCoordinateTransformer.realToLatLong(
                realX = realXY.first,
                realY = realXY.second,
                originLatitude = config.originLatitude,
                originLongitude = config.originLongitude
            )
        } else null

        // ✅ ให้ floorLabel เป็นชั้นที่เลือก
        IndoorSessionManager.points.add(
            IndoorTestPoint(
                timestamp = ts,
                floorLabel = selectedFloor,
                pointNo = pointNo,
                mapX = nx,
                mapY = ny,
                networkType = snapshot.networkType,
                cellIdBssid = snapshot.cellIdBssid,
                rsrpRssi = snapshot.rsrpRssi,
                rsrqSinr = snapshot.rsrqSinr
            )
        )

        IndoorSessionManager.checkpoints.add(
            IndoorCheckpoint(
                index = pointNo,
                timestamp = ts,
                normalizedX = nx.toDouble(),
                normalizedY = ny.toDouble(),
                localX = realXY.first,
                localY = realXY.second,
                source = "homography"
            )
        )

        val mapLat = String.format(Locale.US, "%.6f", latLong?.first ?: realXY.second)
        val mapLong = String.format(Locale.US, "%.6f", latLong?.second ?: realXY.first)

        pointRecords.add(
            FloorPlanPointRecord(
                timestamp = ts,
                floorLabel = selectedFloor,   // ✅ ใช้ค่าที่เลือก
                pointNo = pointNo,
                lat = mapLat,
                long = mapLong,
                mapX = mapLat,
                mapY = mapLong,
                networkType = snapshot.networkType,
                operatorName = snapshot.operatorName,
                cellIdBssid = snapshot.cellIdBssid,
                pci = snapshot.pci,
                tac = snapshot.tac,
                arfcn = snapshot.arfcn,
                freq = snapshot.freq,
                bw = snapshot.bw,
                rsrpRssi = snapshot.rsrpRssi.toString(),
                rsrqSinr = snapshot.rsrqSinr.toString(),
                sinr = snapshot.sinr,
                ssid = snapshot.ssid,
                mac = snapshot.mac,
                channel = snapshot.channel,
                linkSpeed = snapshot.linkSpeed,
                security = snapshot.security,
                signalQuality = snapshot.signalQuality,
                snr = snapshot.snr,
                gpsLat = snapshot.latitude,
                gpsLong = snapshot.longitude,
                gpsAltitude = snapshot.absAltitude,
                pressure = snapshot.pressure,
                standard = snapshot.standard,
                speed = snapshot.speed,
                bearing = snapshot.bearing,
                accuracy = snapshot.accuracy,
                baroRelAlt = snapshot.baroRelAlt,
                baroFloor = selectedFloor,    // ✅ ตาม Select start floor
                gpsRelAlt = snapshot.gpsRelAlt,
                gpsFloor = selectedFloor,     // ✅ ตาม Select start floor
                simState = snapshot.simState,
                nrState = snapshot.nrState,
                netOpCode = snapshot.netOpCode,
                roaming = snapshot.roaming,
                callState = snapshot.callState,
                mcc = snapshot.mcc,
                mnc = snapshot.mnc,
                mncMaster = snapshot.mncMaster,
                longCid = snapshot.longCid,
                nodeIdNid = snapshot.nodeIdNid,
                cidBid = snapshot.cidBid,
                band = snapshot.band,
                dataState = snapshot.dataState
            )
        )

        val neighbors = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> collectWifiNeighbors(pointNo, ts, mapLat, mapLong, snapshot)
            IndoorSessionManager.RadioMode.CELLULAR -> collectCellularNeighbors(pointNo, ts, mapLat, mapLong, snapshot)
        }
        neighborRecords.addAll(neighbors)
    }

    private data class SignalSnapshot(
        val networkType: String,
        val cellIdBssid: String,
        val rsrpRssi: Int,
        val rsrqSinr: Int,
        val sinr: String = "-",
        val operatorName: String = "-",
        val arfcn: String = "-",
        val freqBw: String = "-",
        val pci: String = "-",
        val tac: String = "-",
        val latitude: String = "-",
        val longitude: String = "-",
        val floor: String = "-",
        val relHeight: String = "-",
        val absAltitude: String = "-",
        val pressure: String = "-",
        val ssid: String = "-",
        val freq: String = "-",
        val channel: String = "-",
        val bw: String = "-",
        val linkSpeed: String = "-",
        val security: String = "-",
        val mac: String = "-",
        val signalQuality: String = "-",
        val snr: String = "-",
        val standard: String = "-",
        val speed: String = "-",
        val bearing: String = "-",
        val accuracy: String = "-",
        val baroRelAlt: String = "",
        val baroFloor: String = "",
        val gpsRelAlt: String = "",
        val gpsFloor: String = "",
        val simState: String = "",
        val nrState: String = "",
        val netOpCode: String = "",
        val roaming: String = "",
        val callState: String = "",
        val mcc: String = "",
        val mnc: String = "",
        val mncMaster: String = "",
        val longCid: String = "",
        val nodeIdNid: String = "",
        val cidBid: String = "",
        val band: String = "",
        val dataState: String = ""
    )

    private fun computeRelativeHeights(location: android.location.Location?): Quad<String, String, String, String> {
        val main = activity as? MainActivity
        val pressure = main?.currentFilteredPressure ?: 0f
        val refPressure = main?.referencePressure ?: -1f
        val floorHeight = main?.floorHeightMeters ?: 3.5f
        val startFloor = main?.startFloor ?: 1
        var baroRelAlt = "0.00"
        var baroFloor = startFloor.toString()
        if (refPressure > 0f && pressure > 0f) {
            val h = 44330 * (1 - Math.pow((pressure / refPressure).toDouble(), 1 / 5.255))
            baroRelAlt = String.format(Locale.US, "%.2f", h)
            baroFloor = startFloor.toString()        }
        var gpsRelAlt = "0.00"
        var gpsFloor = startFloor.toString()
        val refGps = main?.referenceGpsAltitude
        if (location != null && refGps != null && location.hasAltitude()) {
            val rel = location.altitude - refGps
            gpsRelAlt = String.format(Locale.US, "%.2f", rel)
            gpsFloor = startFloor.toString()        }
        return Quad(baroRelAlt, baroFloor, gpsRelAlt, gpsFloor)
    }

    private fun getWifiSnapshot(): SignalSnapshot {
        return try {
            val wm = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info?.frequency?.toString() ?: "-" else "-"
            val channel = if (freq != "-") ((freq.toIntOrNull()?.minus(2407))?.div(5))?.toString() ?: "-" else "-"
            val quality = info?.rssi?.let { ((it + 100).coerceIn(0, 100)).toString() + "%" } ?: "-"
            val location = (activity as? MainActivity)?.latestLocation
            val heights = computeRelativeHeights(location)
            val speed = location?.speed?.toString() ?: ""
            val bearing = location?.bearing?.toString() ?: ""
            val accuracy = location?.accuracy?.toString() ?: ""
            val connected = wm.scanResults.orEmpty().firstOrNull { it.BSSID == info?.bssid }
            val security = connected?.capabilities ?: "-"
            val bw = when (connected?.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
                ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
                ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
                else -> "-"
            }
            val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (connected?.wifiStandard) {
                    ScanResult.WIFI_STANDARD_11N -> "802.11n"
                    ScanResult.WIFI_STANDARD_11AC -> "802.11ac"
                    ScanResult.WIFI_STANDARD_11AX -> "802.11ax"
                    ScanResult.WIFI_STANDARD_11BE -> "802.11be"
                    else -> "802.11a/b/g"
                }
            } else ""
            SignalSnapshot(
                networkType = "WIFI",
                cellIdBssid = info?.bssid ?: "-",
                rsrpRssi = info?.rssi ?: -999,
                rsrqSinr = 0,
                latitude = location?.latitude?.toString() ?: "-",
                longitude = location?.longitude?.toString() ?: "-",
                absAltitude = location?.altitude?.toString() ?: "-",
                pressure = (activity as? MainActivity)?.currentFilteredPressure?.toString() ?: "-",
                ssid = info?.ssid ?: "-",
                freq = freq,
                channel = channel,
                bw = bw,
                linkSpeed = "${info?.linkSpeed ?: 0}",
                security = security,
                mac = info?.bssid ?: "-",
                signalQuality = quality,
                snr = "N/A",
                standard = standard,
                speed = speed,
                bearing = bearing,
                accuracy = accuracy,
                baroRelAlt = heights.a,
                baroFloor = heights.b,
                gpsRelAlt = heights.c,
                gpsFloor = heights.d
            )
        } catch (_: Exception) {
            SignalSnapshot("WIFI", "-", -999, 0)
        }
    }

    private fun getCellularSnapshot(): SignalSnapshot {
        return try {
            val tm = requireContext().getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
            val location = (activity as? MainActivity)?.latestLocation
            val heights = computeRelativeHeights(location)
            val netOpCode = tm.networkOperator ?: ""
            val mcc = netOpCode.take(3)
            val mnc = netOpCode.drop(3)
            val cellInfos = tm.allCellInfo.orEmpty()
            val lte = cellInfos.filterIsInstance<CellInfoLte>().firstOrNull()
            val main = activity as? MainActivity
            val isCa = main?.isLteCaActiveCompat() == true
            val lteNetworkType = if (isCa) "LTE_CA" else "LTE"

            if (lte != null) {
                return SignalSnapshot(
                    networkType = lteNetworkType,
                    cellIdBssid = "PCI:${lte.cellIdentity.pci},CI:${lte.cellIdentity.ci}",
                    rsrpRssi = lte.cellSignalStrength.rsrp,
                    rsrqSinr = lte.cellSignalStrength.rsrq,
                    sinr = lte.cellSignalStrength.rssnr.toString(),
                    operatorName = tm.networkOperatorName ?: "-",
                    arfcn = lte.cellIdentity.earfcn.toString(),
                    freqBw = "EARFCN ${lte.cellIdentity.earfcn}",
                    pci = lte.cellIdentity.pci.toString(),
                    tac = lte.cellIdentity.tac.toString(),
                    latitude = location?.latitude?.toString() ?: "-",
                    longitude = location?.longitude?.toString() ?: "-",
                    absAltitude = location?.altitude?.toString() ?: "-",
                    pressure = (activity as? MainActivity)?.currentFilteredPressure?.toString() ?: "-",
                    speed = location?.speed?.toString() ?: "",
                    bearing = location?.bearing?.toString() ?: "",
                    accuracy = location?.accuracy?.toString() ?: "",
                    baroRelAlt = heights.a,
                    baroFloor = heights.b,
                    gpsRelAlt = heights.c,
                    gpsFloor = heights.d,
                    simState = if (tm.simState == TelephonyManager.SIM_STATE_READY) "READY" else tm.simState.toString(),
                    nrState = "UNKNOWN",
                    netOpCode = netOpCode,
                    roaming = if (tm.isNetworkRoaming) "1" else "0",
                    callState = "IDLE",
                    mcc = mcc,
                    mnc = mnc,
                    mncMaster = mnc,
                    longCid = lte.cellIdentity.ci.toString(),
                    nodeIdNid = (lte.cellIdentity.ci / 256).toString(),
                    cidBid = (lte.cellIdentity.ci % 256).toString(),
                    band = lteDlFreqMhz(lte.cellIdentity.earfcn)?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    dataState = "CONNECTED"
                )
            }
            val nr = cellInfos.filterIsInstance<CellInfoNr>().firstOrNull()
            if (nr != null) {
                val pci = invokeIntGetter(nr.cellIdentity, "getPci")
                val nci = invokeLongGetter(nr.cellIdentity, "getNci")
                val ssRsrp = invokeIntGetter(nr.cellSignalStrength, "getSsRsrp")
                val ssRsrq = invokeIntGetter(nr.cellSignalStrength, "getSsRsrq")
                val nrarfcn = invokeIntGetter(nr.cellIdentity, "getNrarfcn")
                SignalSnapshot(
                    networkType = "NR",
                    cellIdBssid = "PCI:${pci ?: -1},NCI:${nci ?: -1}",
                    rsrpRssi = ssRsrp ?: -999,
                    rsrqSinr = ssRsrq ?: -999,
                    operatorName = tm.networkOperatorName ?: "-",
                    arfcn = (nrarfcn ?: -1).takeIf { it >= 0 }?.toString() ?: "-",
                    freqBw = "NR",
                    pci = (pci ?: -1).takeIf { it >= 0 }?.toString() ?: "-",
                    latitude = location?.latitude?.toString() ?: "-",
                    longitude = location?.longitude?.toString() ?: "-",
                    absAltitude = location?.altitude?.toString() ?: "-",
                    pressure = (activity as? MainActivity)?.currentFilteredPressure?.toString() ?: "-",
                    speed = location?.speed?.toString() ?: "",
                    bearing = location?.bearing?.toString() ?: "",
                    accuracy = location?.accuracy?.toString() ?: "",
                    baroRelAlt = heights.a,
                    baroFloor = heights.b,
                    gpsRelAlt = heights.c,
                    gpsFloor = heights.d,
                    simState = if (tm.simState == TelephonyManager.SIM_STATE_READY) "READY" else tm.simState.toString(),
                    nrState = "CONNECTED",
                    netOpCode = netOpCode,
                    roaming = if (tm.isNetworkRoaming) "1" else "0",
                    callState = "IDLE",
                    mcc = mcc,
                    mnc = mnc,
                    mncMaster = mnc,
                    longCid = (nci ?: "").toString(),
                    nodeIdNid = nci?.let { (it / 4096).toString() } ?: "",
                    cidBid = nci?.let { (it % 4096).toString() } ?: "",
                    band = nrarfcn?.let { String.format(Locale.US, "%.1f", nrDlFreqMhz(it)) } ?: "",
                    dataState = "CONNECTED"
                )
            } else {
                SignalSnapshot("CELL", "-", -999, -999)
            }
        } catch (_: Exception) {
            SignalSnapshot("CELL", "-", -999, -999)
        }
    }

    private fun collectWifiNeighbors(pointNo: Int, timestamp: String, lat: String, long: String, snapshot: SignalSnapshot): List<FloorPlanNeighborRecord> {
        return try {
            val wm = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager

            val scan = latestWifiScanResults.takeIf { it.isNotEmpty() } ?: wm.scanResults.orEmpty()
            scan.mapIndexed { idx, ap ->
                val channel = if (ap.frequency > 0) ((ap.frequency - 2407) / 5).toString() else "-"
                FloorPlanNeighborRecord(
                    pointNo = pointNo,
                    timestamp = timestamp,
                    lat = lat,
                    long = long,
                    servingNetworkType = "WIFI",
                    servingTech = snapshot.networkType,
                    servingArfcn = snapshot.arfcn,
                    servingPci = snapshot.pci,
                    servingCellId = snapshot.cellIdBssid,
                    connectedSsid = snapshot.ssid,
                    neighborIndex = idx + 1,
                    neighborType = "WIFI",
                    neighborId = ap.SSID ?: "-",
                    neighborPci = "-",
                    neighborArfcn = "-",
                    neighborFreqMhz = ap.frequency.toString(),
                    neighborRsrp = "-",
                    neighborRsrq = "-",
                    neighborSinr = "-",
                    neighborSsid = ap.SSID ?: "-",
                    neighborBssid = ap.BSSID ?: "-",
                    neighborRssi = ap.level.toString(),
                    neighborChannel = channel,
                    neighborSecurity = ap.capabilities ?: "-"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun collectCellularNeighbors(pointNo: Int, timestamp: String, lat: String, long: String, snapshot: SignalSnapshot): List<FloorPlanNeighborRecord> {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return try {
            val tm = requireContext().getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
            val allInfo = tm.allCellInfo.orEmpty()
            val out = mutableListOf<FloorPlanNeighborRecord>()
            var idx = 1
            for (ci in allInfo) {
                if (ci.isRegistered) continue
                when (ci) {
                    is CellInfoLte -> {
                        val earfcn = ci.cellIdentity.earfcn.takeIf { it != Int.MAX_VALUE }
                        val freq = earfcn?.let { lteDlFreqMhz(it) }?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
                        out.add(
                            FloorPlanNeighborRecord(
                                pointNo = pointNo,
                                timestamp = timestamp,
                                lat = lat,
                                long = long,
                                servingNetworkType = "CELLULAR",
                                servingTech = snapshot.networkType,
                                servingArfcn = snapshot.arfcn,
                                servingPci = snapshot.pci,
                                servingCellId = snapshot.longCid,
                                connectedSsid = "",
                                neighborIndex = idx++,
                                neighborType = "LTE",
                                neighborId = ci.cellIdentity.ci.takeIf { it != Int.MAX_VALUE }?.toString() ?: "-",
                                neighborPci = ci.cellIdentity.pci.takeIf { it != Int.MAX_VALUE }?.toString() ?: "-",
                                neighborArfcn = earfcn?.toString() ?: "-",
                                neighborFreqMhz = freq,
                                neighborRsrp = ci.cellSignalStrength.rsrp.takeIf { it != Int.MAX_VALUE }?.toString() ?: "-",
                                neighborRsrq = ci.cellSignalStrength.rsrq.takeIf { it != Int.MAX_VALUE }?.toString() ?: "-",
                                neighborSinr = ci.cellSignalStrength.rssnr.takeIf { it != Int.MAX_VALUE }?.toString() ?: "-",
                                neighborSsid = "-",
                                neighborBssid = "-",
                                neighborRssi = "-",
                                neighborChannel = "-",
                                neighborSecurity = "-"
                            )
                        )
                    }
                    is CellInfoNr -> {
                        val nrarfcn = invokeIntGetter(ci.cellIdentity, "getNrarfcn")
                        val freq = nrarfcn?.let { String.format(Locale.US, "%.1f", nrDlFreqMhz(it)) } ?: "-"
                        out.add(
                            FloorPlanNeighborRecord(
                                pointNo = pointNo,
                                timestamp = timestamp,
                                lat = lat,
                                long = long,
                                servingNetworkType = "CELLULAR",
                                servingTech = snapshot.networkType,
                                servingArfcn = snapshot.arfcn,
                                servingPci = snapshot.pci,
                                servingCellId = snapshot.longCid,
                                connectedSsid = "",
                                neighborIndex = idx++,
                                neighborType = "NR",
                                neighborId = invokeLongGetter(ci.cellIdentity, "getNci")?.toString() ?: "-",
                                neighborPci = invokeIntGetter(ci.cellIdentity, "getPci")?.toString() ?: "-",
                                neighborArfcn = nrarfcn?.toString() ?: "-",
                                neighborFreqMhz = freq,
                                neighborRsrp = invokeIntGetter(ci.cellSignalStrength, "getSsRsrp")?.toString() ?: "-",
                                neighborRsrq = invokeIntGetter(ci.cellSignalStrength, "getSsRsrq")?.toString() ?: "-",
                                neighborSinr = invokeIntGetter(ci.cellSignalStrength, "getSsSinr")?.toString() ?: "-",
                                neighborSsid = "-",
                                neighborBssid = "-",
                                neighborRssi = "-",
                                neighborChannel = "-",
                                neighborSecurity = "-"
                            )
                        )
                    }
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun lteDlFreqMhz(earfcn: Int): Double? {
        return when (earfcn) {
            in 0..599 -> 2110.0 + 0.1 * earfcn
            in 1200..1949 -> 1805.0 + 0.1 * (earfcn - 1200)
            in 2750..3449 -> 2620.0 + 0.1 * (earfcn - 2750)
            in 3450..3799 -> 925.0 + 0.1 * (earfcn - 3450)
            else -> null
        }
    }

    private fun nrDlFreqMhz(nrarfcn: Int): Double = nrarfcn * 0.005

    private fun invokeIntGetter(target: Any?, methodName: String): Int? {
        if (target == null) return null
        return try {
            (target.javaClass.getMethod(methodName).invoke(target) as? Number)?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun invokeLongGetter(target: Any?, methodName: String): Long? {
        if (target == null) return null
        return try {
            (target.javaClass.getMethod(methodName).invoke(target) as? Number)?.toLong()
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshSignalPanel() {
        val snapshot = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> getWifiSnapshot()
            IndoorSessionManager.RadioMode.CELLULAR -> getCellularSnapshot()
        }
        val main = if (IndoorSessionManager.radioMode == IndoorSessionManager.RadioMode.WIFI) {
            "RSSI ${snapshot.rsrpRssi} dBm"
        } else {
            "RSRP ${snapshot.rsrpRssi} dBm"
        }
        val sub = if (IndoorSessionManager.radioMode == IndoorSessionManager.RadioMode.WIFI) {
            buildWifiCollapsedSummary(snapshot)
        } else {
            buildCellCollapsedSummary(snapshot)
        }
        val panel = childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment
        panel?.updateSignal(main, sub)
        panel?.setMode(IndoorSessionManager.radioMode)
    }



    private fun buildWifiCollapsedSummary(snapshot: SignalSnapshot): String {
        val ssid = snapshot.ssid.takeIf { !it.isNullOrBlank() && it != "<unknown ssid>" && it != "-" } ?: "Hidden SSID"
        val freqInt = snapshot.freq.toIntOrNull()
        val band = when {
            freqInt == null -> "-"
            freqInt < 3000 -> "2.4GHz"
            freqInt in 4900..5900 -> "5GHz"
            freqInt in 5925..7125 -> "6GHz"
            else -> "${freqInt}MHz"
        }
        val channel = snapshot.channel.takeIf { !it.isNullOrBlank() && it != "-" } ?: "-"
        val link = snapshot.linkSpeed.takeIf { !it.isNullOrBlank() && it != "-" } ?: "— Mbps"
        val security = simplifySecurity(snapshot.security)
        val bssid = snapshot.mac.takeIf { !it.isNullOrBlank() && it != "-" } ?: "-"

        return listOf(
            ssid,
            "$band CH$channel",
            link,
            security,
            bssid
        ).joinToString(" · ")
    }

    private fun buildCellCollapsedSummary(snapshot: SignalSnapshot): String {
        val techBand = when (snapshot.networkType) {
            "NR" -> "NR ${nrBandFromArfcn(snapshot.arfcn)}".trim()
            "LTE_CA" -> "LTE CA ${lteBandFromEarfcn(snapshot.arfcn)}".trim()
            else -> "LTE ${lteBandFromEarfcn(snapshot.arfcn)}".trim()
        }
        val sinrVal = snapshot.sinr.toDoubleOrNull()
        val qualityPart = if (sinrVal != null) {
            "SINR ${snapshot.sinr}"
        } else {
            "RSRQ ${snapshot.rsrqSinr}"
        }
        val pci = snapshot.pci.takeIf { it != "-" } ?: "-"
        val arfcnLabel = if (snapshot.networkType == "NR") "NRARFCN" else "EARFCN"
        val arfcn = snapshot.arfcn.takeIf { it != "-" } ?: "-"

        return listOf(
            techBand,
            qualityPart,
            "PCI $pci",
            "$arfcnLabel $arfcn"
        ).joinToString(" · ")
    }

    private fun simplifySecurity(raw: String): String {
        val v = raw.uppercase(Locale.US)
        return when {
            v.contains("WPA3") -> "WPA3"
            v.contains("WPA2") -> "WPA2"
            v.contains("WPA") -> "WPA"
            v.contains("OPEN") -> "OPEN"
            raw.isBlank() || raw == "-" -> "—"
            else -> raw
        }
    }

    private fun lteBandFromEarfcn(arfcn: String): String {
        val v = arfcn.toIntOrNull() ?: return "B?"
        return when (v) {
            in 0..599 -> "B1"
            in 1200..1949 -> "B3"
            in 2750..3449 -> "B7"
            in 3450..3799 -> "B8"
            else -> "B?"
        }
    }

    private fun nrBandFromArfcn(arfcn: String): String {
        val v = arfcn.toIntOrNull() ?: return "n?"
        return when (v) {
            in 620000..653333 -> "n78"
            in 151600..160600 -> "n28"
            else -> "n?"
        }
    }


    private fun refreshMapPoints() {
        mapView.setPlotPoints(
            IndoorSessionManager.points.map {
                IndoorPlotImageView.PlotPoint(
                    nx = it.mapX.toDouble(),
                    ny = it.mapY.toDouble(),
                    color = signalColorForPoint(it)
                )
            }
        )
    }

    private fun signalColorForPoint(point: IndoorTestPoint): Int {
        return if (point.networkType.equals("WIFI", ignoreCase = true)) {
            when {
                point.rsrpRssi > -65 -> android.graphics.Color.parseColor("#7CF3A1")
                point.rsrpRssi >= -75 -> android.graphics.Color.parseColor("#FFD66E")
                point.rsrpRssi >= -85 -> android.graphics.Color.parseColor("#FFB27C")
                point.rsrpRssi >= -95 -> android.graphics.Color.parseColor("#FF6B6B")
                else -> android.graphics.Color.parseColor("#9B59B6")
            }
        } else {
            when {
                point.rsrpRssi > -85 -> android.graphics.Color.parseColor("#7CF3A1")
                point.rsrpRssi >= -95 -> android.graphics.Color.parseColor("#FFD66E")
                point.rsrpRssi >= -100 -> android.graphics.Color.parseColor("#FFB27C")
                else -> android.graphics.Color.parseColor("#FF6B6B")
            }
        }
    }



    private fun formatSysTime(pointTimestamp: String): String {
        return try {
            val inFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val outFmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            outFmt.format(inFmt.parse(pointTimestamp) ?: Date())
        } catch (_: Exception) {
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        }
    }

    private fun floorPlanWifiServingRows(): List<List<String>> {
        return pointRecords.map {
            listOf(
                it.pointNo.toString(),
                formatSysTime(it.timestamp),
                it.lat,
                it.long,
                it.gpsAltitude,
                it.ssid,
                it.freq,
                it.channel,
                it.bw,
                it.linkSpeed,
                it.security,
                it.mac,
                it.standard,
                it.rsrpRssi,
                it.signalQuality.replace("%", ""),
                it.speed,
                it.pressure,
                it.baroRelAlt,
                it.baroFloor,
                it.gpsRelAlt,
                it.gpsFloor
            )
        }
    }

    private fun floorPlanCellServingRows(): List<List<String>> {
        return pointRecords.map {
            val isNr = it.networkType.equals("NR", ignoreCase = true)
            val isLte = it.networkType.equals("LTE", ignoreCase = true) ||
                    it.networkType.equals("LTE_CA", ignoreCase = true)
            listOf(
                it.pointNo.toString(),
                formatSysTime(it.timestamp),
                it.simState,
                "IN_SERVICE",
                it.nrState,
                it.operatorName,
                it.netOpCode,
                it.roaming,
                it.networkType,
                it.callState,
                it.dataState,
                it.rsrpRssi,
                it.networkType,
                it.mcc,
                it.mnc,
                it.mncMaster,
                it.tac,
                it.longCid,
                it.nodeIdNid,
                it.cidBid,
                it.pci,
                if (isNr) it.tac else "",
                if (isNr) it.cellIdBssid else "",
                if (isNr) it.pci else "",
                if (isNr) it.arfcn else "",
                if (isLte) it.rsrpRssi else "",
                if (isLte) it.rsrqSinr else "",
                if (isLte) it.sinr else "",
                if (isNr) it.rsrpRssi else "",
                if (isNr) it.rsrqSinr else "",
                if (isNr) it.sinr else "",
                "",
                if (it.gpsLat != "-" && it.gpsLong != "-") "1" else "0",
                it.accuracy,
                it.lat,
                it.long,
                it.gpsAltitude,
                it.speed,
                it.bearing,
                it.band,
                it.arfcn,
                it.bw,
                it.pressure,
                it.baroRelAlt,
                it.baroFloor,
                it.gpsRelAlt,
                it.gpsFloor
            )
        }
    }

    private fun floorPlanWifiNeighborRows(): List<List<String>> {
        return neighborRecords.map {
            listOf(
                it.pointNo.toString(),
                it.neighborIndex.toString(),
                formatSysTime(it.timestamp),
                it.connectedSsid,
                it.neighborSsid,
                it.neighborBssid,
                it.neighborRssi,
                it.neighborFreqMhz,
                it.neighborSecurity,
                it.lat,
                it.long
            )
        }
    }

    private fun floorPlanCellNeighborRows(): List<List<String>> {
        return neighborRecords.map {
            listOf(
                it.pointNo.toString(),
                it.neighborIndex.toString(),
                formatSysTime(it.timestamp),
                it.servingTech,
                it.servingArfcn,
                it.servingPci,
                it.servingCellId,
                it.neighborType,
                it.neighborArfcn,
                it.neighborFreqMhz,
                it.neighborPci,
                it.neighborRsrp,
                it.neighborRsrq,
                it.neighborSinr,
                it.lat,
                it.long
            )
        }
    }

    fun saveAndClearFloorPlanPoints() {
        if (pointRecords.isEmpty()) {
            Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show()
            return
        }
        exportCsv(showToast = true)
        IndoorSessionManager.points.clear()
        IndoorSessionManager.plottedPointsNormalized.clear()
        pointRecords.clear()
        neighborRecords.clear()
        refreshMapPoints()
        updatePointCount()
        updatePrimaryActionButtonState()
        Toast.makeText(requireContext(), "Saved and cleared points", Toast.LENGTH_SHORT).show()
    }

    private fun updatePrimaryActionButtonState() {
        val calibrated = IndoorSessionManager.config?.calibrationSession != null
        val panel = childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment
        panel?.setPrimaryActionLabel(if (calibrated) "📍 Add Point" else "🎯 Calibration")
        panel?.setAddPointEnabled(true)
        (activity as? MainActivity)?.setIndoorSaveButtonVisible(pointRecords.isNotEmpty())
    }

    private fun exportCsv(showToast: Boolean) {
        if (pointRecords.isEmpty()) {
            if (showToast) Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        val mainActivity = activity as? MainActivity ?: return
        val exportSubDir = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> "Wifi"
            IndoorSessionManager.RadioMode.CELLULAR -> "Cellular"
        }
        val sessionId = mainActivity.getNextSessionIdMaxPlusOne(exportSubDir)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val servingFileName = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> "Session_${sessionId}_FLOOR_PLAN_SERV_WIFI_$timestamp.csv"
            IndoorSessionManager.RadioMode.CELLULAR -> "Session_${sessionId}_FLOOR_PLAN_SERV_CELLULAR_$timestamp.csv"
        }
        val neighborFileName = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> "Session_${sessionId}_FLOOR_PLAN_NEI_WIFI_$timestamp.csv"
            IndoorSessionManager.RadioMode.CELLULAR -> "Session_${sessionId}_FLOOR_PLAN_NEI_CELLULAR_$timestamp.csv"
        }

        val servingHeader = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> mainActivity.wifiCsvHeader
            IndoorSessionManager.RadioMode.CELLULAR -> mainActivity.csvHeader
        }
        val servingRows = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> floorPlanWifiServingRows()
            IndoorSessionManager.RadioMode.CELLULAR -> floorPlanCellServingRows()
        }

        val neighborHeader = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> mainActivity.wifiNeighborHeader
            IndoorSessionManager.RadioMode.CELLULAR -> mainActivity.neighborCsvHeader
        }
        val neighborRows = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> floorPlanWifiNeighborRows()
            IndoorSessionManager.RadioMode.CELLULAR -> floorPlanCellNeighborRows()
        }

        val imageFileName = servingFileName.removeSuffix(".csv") + ".png"
        val zipFileName = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> "Session_${sessionId}_FLOOR_PLAN_WiFi_$timestamp.zip"
            IndoorSessionManager.RadioMode.CELLULAR -> "Session_${sessionId}_FLOOR_PLAN_CELL_$timestamp.zip"
        }

        val ok = saveFloorPlanZip(
            zipFileName = zipFileName,
            subDir = exportSubDir,
            servingFileName = servingFileName,
            servingHeader = servingHeader,
            servingRows = servingRows,
            neighborFileName = neighborFileName,
            neighborHeader = neighborHeader,
            neighborRows = neighborRows,
            imageFileName = imageFileName
        )

        if (showToast) {
            Toast.makeText(
                requireContext(),
                if (ok) "Saved ZIP: Download/DriveTest/FloorPlan/$exportSubDir/$zipFileName" else "Export failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun csvContent(header: List<String>, rows: List<List<String>>): ByteArray {
        val content = buildString {
            append(header.joinToString(";")).append("\n")
            rows.forEach { row -> append(row.joinToString(";")).append("\n") }
        }
        return content.toByteArray(Charsets.UTF_8)
    }

    private fun floorPlanImageBytesPng(): ByteArray? {
        if (!::mapView.isInitialized) return null
        val bitmap = mapView.createExportBitmapWithPoints() ?: return null
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    private fun saveFloorPlanZip(
        zipFileName: String,
        subDir: String,
        servingFileName: String,
        servingHeader: List<String>,
        servingRows: List<List<String>>,
        neighborFileName: String,
        neighborHeader: List<String>,
        neighborRows: List<List<String>>,
        imageFileName: String
    ): Boolean {
        return try {
            val imageBytes = floorPlanImageBytesPng() ?: return false
            val servingBytes = csvContent(servingHeader, servingRows)
            val neighborBytes = csvContent(neighborHeader, neighborRows)

            val zipBytes = ByteArrayOutputStream().use { bos ->
                ZipOutputStream(bos).use { zos ->
                    fun addEntry(name: String, bytes: ByteArray) {
                        val entry = ZipEntry(name)
                        zos.putNextEntry(entry)
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                    addEntry(servingFileName, servingBytes)
                    addEntry(neighborFileName, neighborBytes)
                    addEntry(imageFileName, imageBytes)
                }
                bos.toByteArray()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, zipFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DriveTest_GNSS_Floorplan/FloorPlan/$subDir")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> out.write(zipBytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DriveTest_GNSS_Floorplan/FloorPlan/$subDir"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, zipFileName)
                file.outputStream().use { out -> out.write(zipBytes) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun saveFloorPlanImage(fileName: String, subDir: String): Boolean {
        return try {
            if (!::mapView.isInitialized) return false
            val bitmap = mapView.createExportBitmapWithPoints() ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DriveTest/FloorPlan/$subDir")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DriveTest/FloorPlan/$subDir"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveCsvToFloorPlan(fileName: String, header: List<String>, rows: List<List<String>>, subDir: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DriveTest/FloorPlan/$subDir")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    w.append(header.joinToString(";")).append("\n")
                    rows.forEach { row ->
                        w.append(row.joinToString(";")).append("\n")
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DriveTest/FloorPlan/$subDir"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.bufferedWriter().use { w ->
                    w.append(header.joinToString(";")).append("\n")
                    rows.forEach { row ->
                        w.append(row.joinToString(";")).append("\n")
                    }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
