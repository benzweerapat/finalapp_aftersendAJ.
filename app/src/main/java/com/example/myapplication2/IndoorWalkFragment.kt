package com.example.myapplication2

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
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
import java.io.File
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

    private val pickFloorPlanLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                IndoorSessionManager.importedFloorPlanUri = uri
                mapView.setImageFromUri(uri)
            }
        }

    private val signalTicker = object : Runnable {
        override fun run() {
            if (!isAdded) return
            refreshSignalPanel()
            uiHandler.postDelayed(this, 1000)
        }
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
            ?.setOnAddPointClickListener {
                if (!IndoorSessionManager.surveyRunning) {
                    (activity as? MainActivity)?.showStartHint("Please press START first")
                    return@setOnAddPointClickListener
                }
                if (IndoorSessionManager.config?.calibrationSession == null) {
                    Toast.makeText(requireContext(), "Please complete Calibration (4 flags) before Add Point", Toast.LENGTH_SHORT).show()
                    return@setOnAddPointClickListener
                }
                val pinTip = mapView.getPinTipNormalized()
                if (pinTip == null) {
                    Toast.makeText(requireContext(), "Move map so pin is over floor plan", Toast.LENGTH_SHORT).show()
                    return@setOnAddPointClickListener
                }
                // Use the bottom tip of the fixed pin, not the visual center, for saved point coordinates
                recordPoint(pinTip.first.toFloat(), pinTip.second.toFloat())
                refreshMapPoints()
                updatePointCount()
            }
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setOnUndoClickListener {
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
                Toast.makeText(requireContext(), "Cleared all points", Toast.LENGTH_SHORT).show()
            }
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setOnCalibrateClickListener {
                if (IndoorSessionManager.surveyRunning) {
                    Toast.makeText(requireContext(), "Stop recording before changing floor", Toast.LENGTH_SHORT).show()
                    return@setOnCalibrateClickListener
                }
                (activity as? MainActivity)?.showStartFloorDialog { selectedFloor ->
                    (activity as? MainActivity)?.onIndoorStartFloorSelected(selectedFloor)
                    updateGroundControlLabels()
                    Toast.makeText(requireContext(), "Selected floor: $selectedFloor", Toast.LENGTH_SHORT).show()
                }
            }
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setOnEditFloorHeightClickListener {
                if (IndoorSessionManager.surveyRunning) {
                    Toast.makeText(requireContext(), "Stop recording before editing floor height", Toast.LENGTH_SHORT).show()
                    return@setOnEditFloorHeightClickListener
                }
                showEditFloorHeightDialog()
            }

        mapView.setImageFromUri(IndoorSessionManager.importedFloorPlanUri ?: IndoorSessionManager.config?.imageUri)
        mapView.setPlotEnabled(true)
        refreshMapPoints()

        view.findViewById<Button>(R.id.btnResetView).setOnClickListener { mapView.resetViewFitScreen() }

        view.findViewById<Button>(R.id.btnCalibration).setOnClickListener {
            if (!IndoorSessionManager.surveyRunning) {
                Toast.makeText(requireContext(), "Please press START before calibration", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            captureCalibrationFlagAtPin()
        }

        view.findViewById<Button>(R.id.btnBrowseFloorPlan).setOnClickListener {
            pickFloorPlanLauncher.launch("image/*")
        }

        updatePointCount()
        updateGroundControlLabels()
        updateGroundButtonsEnabledState()
        updateAddPointButtonUi()
        refreshSignalPanel()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(signalTicker)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(signalTicker)
    }

    private fun showCalibrationRequiredDialog() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Calibration Required")
            .setMessage("ก่อน Add Point ต้องกด Calibration และปักธงให้ครบ 4 จุด แล้วกรอกความยาว 2 ด้าน")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateAddPointButtonUi() {
        val calibrationReady = IndoorSessionManager.config?.calibrationSession != null
        val enabled = startPrerequisitesReady && calibrationReady && IndoorSessionManager.surveyRunning
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setAddPointEnabled(enabled)
    }

    private fun captureCalibrationFlagAtPin() {
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
            ?.setGroundButtonsEnabled(!IndoorSessionManager.surveyRunning)
    }

    private fun showEditFloorHeightDialog() {
        val main = activity as? MainActivity ?: return
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(main.floorHeightMeters.toString())
        AlertDialog.Builder(requireContext())
            .setTitle("Floor Height (m)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().toFloatOrNull()
                if (v != null && v > 0f) {
                    main.onFloorHeightSelected(v)
                    updateGroundControlLabels()
                    Toast.makeText(requireContext(), "Saved: $v m", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        val config = IndoorSessionManager.config
        val floor = config?.floorName ?: "Floor-1"
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
        } else {
            null
        }

        IndoorSessionManager.points.add(
            IndoorTestPoint(
                timestamp = ts,
                floorLabel = floor,
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
                floorLabel = floor,
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
                baroFloor = snapshot.baroFloor,
                gpsRelAlt = snapshot.gpsRelAlt,
                gpsFloor = snapshot.gpsFloor,
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
            baroFloor = (startFloor + (h / floorHeight).toInt()).toString()
        }
        var gpsRelAlt = "0.00"
        var gpsFloor = startFloor.toString()
        val refGps = main?.referenceGpsAltitude
        if (location != null && refGps != null && location.hasAltitude()) {
            val rel = location.altitude - refGps
            gpsRelAlt = String.format(Locale.US, "%.2f", rel)
            gpsFloor = (startFloor + (rel / floorHeight).toInt()).toString()
        }
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
            if (lte != null) {
                return SignalSnapshot(
                    networkType = "LTE",
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
            val scan = wm.scanResults.orEmpty()
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

    private fun updatePointCount() {
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.updatePointCount(IndoorSessionManager.points.size)
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
            val isLte = it.networkType.equals("LTE", ignoreCase = true)
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
            IndoorSessionManager.RadioMode.WIFI -> "Session_${sessionId}_FLOOR_PLAN_SURV_WIFI_$timestamp.csv"
            IndoorSessionManager.RadioMode.CELLULAR -> "Session_${sessionId}_FLOOR_PLAN_SURV_CELLULAR_$timestamp.csv"
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

        val okServing = saveCsvToFloorPlan(servingFileName, servingHeader, servingRows, exportSubDir)
        val okNeighbor = saveCsvToFloorPlan(neighborFileName, neighborHeader, neighborRows, exportSubDir)
        val imageFileName = servingFileName.removeSuffix(".csv") + ".png"
        val okImage = saveFloorPlanImage(imageFileName, exportSubDir)
        val ok = okServing && okNeighbor && okImage
        if (showToast) {
            Toast.makeText(
                requireContext(),
                if (ok) "Saved: Download/DriveTest/FloorPlan/$exportSubDir/$servingFileName (+ neighbor + image)" else "Export failed",
                Toast.LENGTH_LONG
            ).show()
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
