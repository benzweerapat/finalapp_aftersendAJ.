package com.example.myapplication2

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
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
import android.widget.Toast
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
        val pressure: String
    )

    private data class FloorPlanNeighborRecord(
        val pointNo: Int,
        val timestamp: String,
        val lat: String,
        val long: String,
        val servingNetworkType: String,
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
        IndoorSessionManager.surveyRunning = true
        setSurveyRunning(true)
        Toast.makeText(requireContext(), "Floor plan survey started", Toast.LENGTH_SHORT).show()
    }

    fun stopSurvey() {
        if (!IndoorSessionManager.surveyRunning) return
        setSurveyRunning(false)
        exportCsv(showToast = true)
        IndoorSessionManager.points.clear()
        IndoorSessionManager.plottedPointsNormalized.clear()
        pointRecords.clear()
        neighborRecords.clear()
        refreshMapPoints()
        updatePointCount()
        Toast.makeText(requireContext(), "Floor plan survey stopped", Toast.LENGTH_SHORT).show()
    }

    fun isSurveyRunning(): Boolean = IndoorSessionManager.surveyRunning

    fun setSurveyRunning(running: Boolean) {
        IndoorSessionManager.surveyRunning = running
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

        mapView.setImageFromUri(IndoorSessionManager.importedFloorPlanUri ?: IndoorSessionManager.config?.imageUri)
        mapView.setPlotEnabled(true)
        refreshMapPoints()

        view.findViewById<Button>(R.id.btnResetView).setOnClickListener { mapView.resetViewFitScreen() }

        view.findViewById<Button>(R.id.btnBrowseFloorPlan).setOnClickListener {
            pickFloorPlanLauncher.launch("image/*")
        }

        updatePointCount()
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

    private fun updateAddPointButtonUi() {
        val enabled = startPrerequisitesReady
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.setAddPointEnabled(enabled)
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
        val floor = IndoorSessionManager.config?.floorName ?: "Floor-1"
        val pointNo = IndoorSessionManager.points.size + 1
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

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

        val mapLat = String.format(Locale.US, "%.6f", nx)
        val mapLong = String.format(Locale.US, "%.6f", ny)
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
                pressure = snapshot.pressure
            )
        )

        val neighbors = when (IndoorSessionManager.radioMode) {
            IndoorSessionManager.RadioMode.WIFI -> collectWifiNeighbors(pointNo, ts, mapLat, mapLong)
            IndoorSessionManager.RadioMode.CELLULAR -> collectCellularNeighbors(pointNo, ts, mapLat, mapLong)
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
        val snr: String = "-"
    )

    private fun getWifiSnapshot(): SignalSnapshot {
        return try {
            val wm = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info?.frequency?.toString() ?: "-" else "-"
            val channel = if (freq != "-") ((freq.toIntOrNull()?.minus(2407))?.div(5))?.toString() ?: "-" else "-"
            val quality = info?.rssi?.let { ((it + 100).coerceIn(0, 100)).toString() + "%" } ?: "-"
            val location = (activity as? MainActivity)?.latestLocation
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
                bw = "-",
                linkSpeed = "${info?.linkSpeed ?: 0} Mbps",
                security = "-",
                mac = info?.bssid ?: "-",
                signalQuality = quality,
                snr = "N/A"
            )
        } catch (_: Exception) {
            SignalSnapshot("WIFI", "-", -999, 0)
        }
    }

    private fun getCellularSnapshot(): SignalSnapshot {
        return try {
            val tm = requireContext().getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
            val cellInfos = tm.allCellInfo.orEmpty()
            val lte = cellInfos.filterIsInstance<CellInfoLte>().firstOrNull()
            if (lte != null) {
                val location = (activity as? MainActivity)?.latestLocation
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
                    pressure = (activity as? MainActivity)?.currentFilteredPressure?.toString() ?: "-"
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
                    pci = (pci ?: -1).takeIf { it >= 0 }?.toString() ?: "-"
                )
            } else {
                SignalSnapshot("CELL", "-", -999, -999)
            }
        } catch (_: Exception) {
            SignalSnapshot("CELL", "-", -999, -999)
        }
    }

    private fun collectWifiNeighbors(pointNo: Int, timestamp: String, lat: String, long: String): List<FloorPlanNeighborRecord> {
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
    private fun collectCellularNeighbors(pointNo: Int, timestamp: String, lat: String, long: String): List<FloorPlanNeighborRecord> {
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
                "",
                it.rsrpRssi,
                it.signalQuality.replace("%", ""),
                "",
                it.pressure,
                "",
                "",
                "",
                ""
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
                "",
                "IN_SERVICE",
                if (isNr) "CONNECTED" else "",
                it.operatorName,
                "",
                "",
                it.networkType,
                "",
                "CONNECTED",
                it.rsrpRssi,
                it.networkType,
                "",
                "",
                "",
                it.tac,
                it.cellIdBssid,
                "",
                "",
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
                "",
                it.lat,
                it.long,
                it.gpsAltitude,
                "",
                "",
                "",
                it.arfcn,
                it.bw,
                it.pressure,
                "",
                "",
                "",
                ""
            )
        }
    }

    private fun floorPlanWifiNeighborRows(): List<List<String>> {
        return neighborRecords.map {
            listOf(
                it.pointNo.toString(),
                it.neighborIndex.toString(),
                formatSysTime(it.timestamp),
                "",
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
                "",
                "",
                "",
                "",
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
        val sessionId = mainActivity.getNextSessionIdMaxPlusOne("FloorPlan")
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

        val okServing = saveCsvToFloorPlan(servingFileName, servingHeader, servingRows)
        val okNeighbor = saveCsvToFloorPlan(neighborFileName, neighborHeader, neighborRows)
        val ok = okServing && okNeighbor
        if (showToast) {
            Toast.makeText(
                requireContext(),
                if (ok) "Saved: Download/DriveTest/FloorPlan/FloorPlan/$servingFileName (+ neighbor)" else "Export failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveCsvToFloorPlan(fileName: String, header: List<String>, rows: List<List<String>>): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DriveTest/FloorPlan/FloorPlan")
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
                    "DriveTest/FloorPlan/FloorPlan"
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
