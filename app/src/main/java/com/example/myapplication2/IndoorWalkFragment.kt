package com.example.myapplication2

import android.content.ContentValues
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
import androidx.fragment.app.Fragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IndoorWalkFragment : Fragment(R.layout.fragment_indoor_walk) {

    private lateinit var mapView: IndoorPlotImageView
    private val uiHandler = Handler(Looper.getMainLooper())
    private var surveyActive = false

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
        surveyActive = true
        Toast.makeText(requireContext(), "Indoor survey started", Toast.LENGTH_SHORT).show()
    }

    fun stopSurvey() {
        if (!surveyActive) return
        surveyActive = false
        exportCsv(showToast = true)
        IndoorSessionManager.points.clear()
        IndoorSessionManager.plottedPointsNormalized.clear()
        mapView.setPointsNormalized(emptyList())
        updatePointCount()
        Toast.makeText(requireContext(), "Indoor survey stopped", Toast.LENGTH_SHORT).show()
    }

    fun isSurveyRunning(): Boolean = surveyActive


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.indoorMapView)
        ensureDefaultConfigIfMissing()

        if (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.indoorSignalPanelContainer, IndoorSignalPanelFragment())
                .commitNow()
        }

        mapView.setImageFromUri(IndoorSessionManager.importedFloorPlanUri ?: IndoorSessionManager.config?.imageUri)
        mapView.setPlotEnabled(true)
        mapView.setPointsNormalized(IndoorSessionManager.points.map { Pair(it.mapX.toDouble(), it.mapY.toDouble()) })

        mapView.onPointAdded = { nx, ny ->
            if (!surveyActive) {
                Toast.makeText(requireContext(), "Survey ended", Toast.LENGTH_SHORT).show()
            } else {
                recordPoint(nx.toFloat(), ny.toFloat())
                mapView.setPointsNormalized(IndoorSessionManager.points.map { Pair(it.mapX.toDouble(), it.mapY.toDouble()) })
                updatePointCount()
            }
        }

        view.findViewById<Button>(R.id.btnBrowseFloorPlan).setOnClickListener {
            pickFloorPlanLauncher.launch("image/*")
        }

        view.findViewById<Button>(R.id.btnUndo).setOnClickListener {
            if (IndoorSessionManager.points.isEmpty()) {
                Toast.makeText(requireContext(), "No point to undo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                IndoorSessionManager.points.removeAt(IndoorSessionManager.points.lastIndex)
                mapView.setPointsNormalized(IndoorSessionManager.points.map { Pair(it.mapX.toDouble(), it.mapY.toDouble()) })
                updatePointCount()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Undo failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        updatePointCount()
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

    private fun updatePointCount() {
        (childFragmentManager.findFragmentById(R.id.indoorSignalPanelContainer) as? IndoorSignalPanelFragment)
            ?.updatePointCount(IndoorSessionManager.points.size)
    }

    private val indoorCsvHeader = listOf(
        "timestamp", "floor_label", "point_no", "map_x", "map_y",
        "network_type", "cellid_bssid", "rsrp_rssi", "rsrq_sinr"
    )

    private fun exportCsv(showToast: Boolean) {
        val config = IndoorSessionManager.config ?: return
        if (IndoorSessionManager.points.isEmpty()) {
            if (showToast) Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        // outdoor-like naming/session strategy
        val mainActivity = activity as? MainActivity
        val sessionId = mainActivity?.getNextSessionIdMaxPlusOne("Indoor") ?: 1
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Session_${sessionId}_INDOOR_SURV_${IndoorSessionManager.radioMode.name}_$timestamp.csv"

        val rows = IndoorSessionManager.points.map {
            listOf(
                it.timestamp,
                it.floorLabel,
                it.pointNo.toString(),
                "%.6f".format(it.mapX),
                "%.6f".format(it.mapY),
                it.networkType,
                it.cellIdBssid,
                it.rsrpRssi.toString(),
                it.rsrqSinr.toString()
            )
        }

        val ok = saveCsvToIndoor(fileName, indoorCsvHeader, rows)
        if (showToast) {
            Toast.makeText(
                requireContext(),
                if (ok) "Saved: Download/DriveTest/Indoor/Indoor/$fileName" else "Export failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveCsvToIndoor(fileName: String, header: List<String>, rows: List<List<String>>): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/DriveTest/Indoor/Indoor")
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
                    "DriveTest/Indoor/Indoor"
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
