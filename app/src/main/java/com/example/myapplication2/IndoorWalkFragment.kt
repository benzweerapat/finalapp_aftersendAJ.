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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IndoorWalkFragment : Fragment(R.layout.fragment_indoor_walk) {

    private lateinit var mapView: IndoorPlotImageView
    private lateinit var textSignalMain: TextView
    private lateinit var textSignalSub: TextView
    private lateinit var textPointCount: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private var surveyActive = true

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.indoorMapView)
        textSignalMain = view.findViewById(R.id.textSignalMain)
        textSignalSub = view.findViewById(R.id.textSignalSub)
        textPointCount = view.findViewById(R.id.textPointCount)

        ensureDefaultConfigIfMissing()
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
            if (IndoorSessionManager.points.isNotEmpty()) {
                IndoorSessionManager.points.removeLast()
                mapView.setPointsNormalized(IndoorSessionManager.points.map { Pair(it.mapX.toDouble(), it.mapY.toDouble()) })
                updatePointCount()
            }
        }

        view.findViewById<Button>(R.id.btnExportCsv).setOnClickListener {
            exportCsv(showToast = true)
        }

        view.findViewById<Button>(R.id.btnEndSurvey).setOnClickListener {
            surveyActive = false
            exportCsv(showToast = true)
            Toast.makeText(requireContext(), "End Survey completed", Toast.LENGTH_SHORT).show()
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
        val rsrqSinr: Int
    )

    private fun getWifiSnapshot(): SignalSnapshot {
        return try {
            val wm = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            SignalSnapshot(
                networkType = "WIFI",
                cellIdBssid = info?.bssid ?: "-",
                rsrpRssi = info?.rssi ?: -999,
                rsrqSinr = 0
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
                return SignalSnapshot(
                    networkType = "LTE",
                    cellIdBssid = "PCI:${lte.cellIdentity.pci},CI:${lte.cellIdentity.ci}",
                    rsrpRssi = lte.cellSignalStrength.rsrp,
                    rsrqSinr = lte.cellSignalStrength.rsrq
                )
            }
            val nr = cellInfos.filterIsInstance<CellInfoNr>().firstOrNull()
            if (nr != null) {
                val pci = invokeIntGetter(nr.cellIdentity, "getPci")
                val nci = invokeLongGetter(nr.cellIdentity, "getNci")
                val ssRsrp = invokeIntGetter(nr.cellSignalStrength, "getSsRsrp")
                val ssRsrq = invokeIntGetter(nr.cellSignalStrength, "getSsRsrq")
                SignalSnapshot(
                    networkType = "NR",
                    cellIdBssid = "PCI:${pci ?: -1},NCI:${nci ?: -1}",
                    rsrpRssi = ssRsrp ?: -999,
                    rsrqSinr = ssRsrq ?: -999
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
        textSignalMain.text = when (snapshot.networkType) {
            "WIFI" -> "RSSI ${snapshot.rsrpRssi} dBm"
            else -> "RSRP ${snapshot.rsrpRssi} dBm"
        }
        textSignalSub.text = "${snapshot.networkType} • ${snapshot.cellIdBssid} • RSRQ/SINR ${snapshot.rsrqSinr}"
    }

    private fun updatePointCount() {
        textPointCount.text = "Points: ${IndoorSessionManager.points.size}"
    }

    private fun exportCsv(showToast: Boolean) {
        val config = IndoorSessionManager.config ?: return
        val rows = mutableListOf<List<String>>()
        rows.add(
            listOf(
                "timestamp",
                "floor_label",
                "point_no",
                "map_x",
                "map_y",
                "network_type",
                "cellid_bssid",
                "rsrp_rssi",
                "rsrq_sinr"
            )
        )
        IndoorSessionManager.points.forEach {
            rows.add(
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
            )
        }

        val fileName = "${config.projectName}_${config.floorName}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_indoor_walk.csv"
        val ok = saveCsvToIndoor(fileName, rows)
        if (showToast) {
            Toast.makeText(
                requireContext(),
                if (ok) "Exported: Download/DriveTest/Indoor/$fileName" else "Export failed",
                Toast.LENGTH_LONG
            ).show()
        }
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
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DriveTest/Indoor"
                )
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeText(csv)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
