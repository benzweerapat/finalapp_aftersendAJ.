package com.example.myapplication2

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import android.widget.ImageView
import android.animation.ObjectAnimator
import android.animation.ValueAnimator



class WifiFragment : Fragment(R.layout.fragment_wifi) {

    companion object {
        private const val NA = "-"
    }

    private var textGpsEstimated: TextView? = null


    private lateinit var mainActivity: MainActivity
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiScanReceiver: BroadcastReceiver
    private val handler = Handler(Looper.getMainLooper())

    /* ================= UI ================= */

    private var wifiSsid: TextView? = null
    private var wifiMac: TextView? = null

    private var wifiRssi: TextView? = null
    private var wifiFreq: TextView? = null
    private var wifiLinkSpeed: TextView? = null
    private var wifiChannel: TextView? = null
    private var wifiBw: TextView? = null
    private var wifiStandard: TextView? = null
    private var wifiSecurity: TextView? = null
    private var wifiTxRx: TextView? = null
    private var wifiSignalQual: TextView? = null
    private var wifiSnr: TextView? = null
    private var wifiSpeed: TextView? = null
    private var latLngValue: TextView? = null

    // Altitude
    private var textFloor: TextView? = null
    private var textAltitude: TextView? = null
    private var textPressure: TextView? = null
    private var textGpsFloor: TextView? = null
    private var textGpsRelHeight: TextView? = null
    private var textGpsAltitude: TextView? = null
    // estimated level labels
    private var textBaroEstimated: TextView? = null


    // Buttons
    private lateinit var btnCalibrate: View
    private lateinit var btnReset: View
    private lateinit var btnEditFloorHeight: View

    // RecyclerView
    private lateinit var wifiAdapter: WifiNeighborAdapter

    private val WIFI_PERMISSION_REQ = 1001
    private var iconGps: ImageView? = null
    private var iconWifi: ImageView? = null
    private var wifiBlinkAnimator: ObjectAnimator? = null
    private var currentSsid: String = "Unknown"

    private fun startWifiBlink() {
        if (wifiBlinkAnimator != null) return

        wifiBlinkAnimator = ObjectAnimator.ofFloat(iconWifi, "alpha", 1f, 0.3f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }


    fun setGroundButtonsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        btnCalibrate?.visibility = v
        btnReset?.visibility = v
        btnEditFloorHeight?.visibility = v
    }


    private fun stopWifiBlink() {
        wifiBlinkAnimator?.cancel()
        wifiBlinkAnimator = null
        iconWifi?.alpha = 1f
    }
    fun showGroundUiAfterStart() {
        // Barometer
        textAltitude?.visibility = View.VISIBLE
        textFloor?.visibility = View.VISIBLE
        textBaroEstimated?.visibility = View.VISIBLE

        // GPS
        textGpsRelHeight?.visibility = View.VISIBLE
        textGpsFloor?.visibility = View.VISIBLE
        textGpsEstimated?.visibility = View.VISIBLE
    }








    private fun updateStatusIcons(isConnected: Boolean) {

        // ===== GPS =====
        val gpsOn = isLocationEnabled()
        iconGps?.setColorFilter(
            if (gpsOn)
                android.graphics.Color.parseColor("#4CAF50")
            else
                android.graphics.Color.parseColor("#F44336")
        )

        // ===== Wi-Fi =====
        if (!wifiManager.isWifiEnabled) {
            // Wi-Fi OFF → แดง นิ่ง
            stopWifiBlink()
            iconWifi?.setColorFilter(android.graphics.Color.parseColor("#F44336"))
            return
        }

        // Wi-Fi ON
        iconWifi?.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))

        if (!isConnected) {
            // 🟢 Non-connected → เขียว + กระพริบ
            startWifiBlink()
        } else {
            // 🟢 Connected → เขียว นิ่ง
            stopWifiBlink()
        }
    }




    /* ================= Lifecycle ================= */

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        mainActivity = requireActivity() as MainActivity
        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Bind WiFi UI
        wifiSsid = view.findViewById(R.id.wifiSsid)
        wifiMac = view.findViewById(R.id.wifiMac)

        wifiRssi = view.findViewById(R.id.wifiRssi)
        wifiFreq = view.findViewById(R.id.wifiFreq)
        wifiLinkSpeed = view.findViewById(R.id.wifiLinkSpeed)
        wifiChannel = view.findViewById(R.id.wifiChannel)
        wifiBw = view.findViewById(R.id.wifiBw)
        wifiStandard = view.findViewById(R.id.wifiStandard)
        wifiSecurity = view.findViewById(R.id.wifiSecurity)
        wifiTxRx = view.findViewById(R.id.wifiTxRx)
        wifiSignalQual = view.findViewById(R.id.wifiSignalQual)
        wifiSnr = view.findViewById(R.id.wifiSnr)
        wifiSpeed = view.findViewById(R.id.wifiSpeed)
        latLngValue = view.findViewById(R.id.latLngValue)
        iconGps = view.findViewById(R.id.iconGps)
        iconWifi = view.findViewById(R.id.iconWifi)


        // Altitude
        textFloor = view.findViewById(R.id.textFloor)
        textAltitude = view.findViewById(R.id.textAltitude)
        textPressure = view.findViewById(R.id.textPressure)
        textGpsFloor = view.findViewById(R.id.textGpsFloor)
        textGpsRelHeight = view.findViewById(R.id.textGpsRelHeight)
        textGpsAltitude = view.findViewById(R.id.textGpsAltitude)

        textBaroEstimated = view.findViewById(R.id.textBaroEstimated)
        textGpsEstimated = view.findViewById(R.id.textGpsEstimated)


        // Buttons
        btnCalibrate = view.findViewById(R.id.btnCalibrate)
        btnReset = view.findViewById(R.id.btnReset)
        btnEditFloorHeight = view.findViewById(R.id.btnEditFloorHeight)

        // RecyclerView
        view.findViewById<RecyclerView>(R.id.wifiNeighborsRecycler).apply {
            layoutManager = LinearLayoutManager(context)
            wifiAdapter = WifiNeighborAdapter()
            adapter = wifiAdapter
        }

        wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    displayWifiScanResults()
                }
            }
        }

        btnCalibrate.setOnClickListener {
            mainActivity.calibrateAltitude()

            // Barometer
            textAltitude?.visibility = View.VISIBLE
            textFloor?.visibility = View.VISIBLE

            // GPS
            textGpsRelHeight?.visibility = View.VISIBLE
            textGpsFloor?.visibility = View.VISIBLE
            textBaroEstimated?.visibility = View.VISIBLE
            textGpsEstimated?.visibility = View.VISIBLE


            mainActivity.toast("Ground Set")
        }

        btnReset.setOnClickListener {
            // 1️⃣ ล้าง reference
            mainActivity.referencePressure = -1f
            mainActivity.referenceGpsAltitude = null

            // =====================
            // 2️⃣ Barometer UI
            // =====================
            // แสดง Pressure อย่างเดียว
            textPressure?.visibility = View.VISIBLE
            textPressure?.text =
                "Pressure: %.2f hPa".format(mainActivity.currentFilteredPressure)

            // ❌ ซ่อน Rel / Floor
            textAltitude?.visibility = View.GONE
            textFloor?.visibility = View.GONE
            // ซ่อน estimated level
            textBaroEstimated?.visibility = View.GONE
            textGpsEstimated?.visibility = View.GONE


            // =====================
            // 3️⃣ GPS UI
            // =====================
            // แสดง Abs. Alt อย่างเดียว
            val loc = mainActivity.latestLocation
            textGpsAltitude?.visibility = View.VISIBLE
            textGpsAltitude?.text =
                if (loc != null && loc.hasAltitude())
                    "Abs. Alt: %.1f m".format(loc.altitude)
                else
                    "Abs. Alt: -"

            // ❌ ซ่อน Rel / Floor
            textGpsRelHeight?.visibility = View.GONE
            textGpsFloor?.visibility = View.GONE

            mainActivity.toast("Reset Height")
        }
    }

    override fun onResume() {
        super.onResume()

        if (hasWifiPermission()) {
            requireContext().registerReceiver(
                wifiScanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            )
        }

        handler.post(wifiRunnable)
    }


    override fun onPause() {
        super.onPause()
        stopWifiBlink()
        try { requireContext().unregisterReceiver(wifiScanReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(wifiRunnable)
    }


    /* ================= Runnable ================= */

    private val wifiRunnable = object : Runnable {
        override fun run() {

            // ✅ เพิ่มบรรทัดนี้
            if (!hasWifiPermission()) {
                wifiSsid?.text = "Wi-Fi permission required"
                handler.postDelayed(this, 1000)
                return
            }



            val info = wifiManager.connectionInfo
            val isConnected = info != null && info.networkId != -1
            updateStatusIcons(isConnected)

            when {
                !isLocationEnabled() -> {
                    clearWifiUi()
                    wifiSsid?.text = "Location OFF"
                }
                else -> {
                    scanWifi()
                    updateAltitudeInfo()
                }
            }


            handler.postDelayed(this, 1000)
        }
    }
    private fun recordWifiNeighbors(results: List<ScanResult>) {
        if (mainActivity.isRecordingWifiCsv) {
            // ใช้เลข Report ล่าสุดจาก MainActivity
            val reportId = mainActivity.currentWifiReportId

            val now = Date()
            val sysTime =
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
            val loc = mainActivity.latestLocation

            // ข้อมูลตัวที่เชื่อมต่ออยู่ปัจจุบัน
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val currentSsid = info.ssid.replace("\"", "")
            val currentBssid = info.bssid

            var nIdx = 1
            for (res in results) {
                // ไม่บันทึกตัวที่ซ้ำกับตัวที่เชื่อมต่ออยู่ (ถ้าต้องการเก็บเฉพาะ Neighbor)
                if (res.BSSID != currentBssid) {
                    val nRow = mutableListOf<String>()
                    nRow.add(reportId.toString()) // ✅
                    nRow.add(nIdx.toString())
                    nRow.add(sysTime)
                    nRow.add(currentSsid)
                    nRow.add(res.SSID)
                    nRow.add(res.BSSID)
                    nRow.add(res.level.toString())
                    nRow.add(res.frequency.toString())
                    nRow.add(res.capabilities)
                    nRow.add(loc?.latitude?.toString() ?: "")
                    nRow.add(loc?.longitude?.toString() ?: "")


                    mainActivity.addWifiNeighborCsvRow(nRow)
                    nIdx++
                }
            }
        }
    }
    // วางไว้ภายใน class WifiFragment
    private fun updateWifiNeighborsCsv(results: List<ScanResult>) {
        if (mainActivity.isRecordingWifiCsv) {
            // 1. ดึงเลข Report ปัจจุบัน (ใช้ร่วมกับไฟล์ WiFi หลัก)
            val reportId = mainActivity.getNextReportId()
            val now = Date()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
            val loc = mainActivity.latestLocation

            // 2. ข้อมูล WiFi ตัวที่เชื่อมต่ออยู่ (Serving)
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val sSsid = info.ssid.replace("\"", "")
            val sBssid = info.bssid ?: "00:00:00:00:00:00"

            var nIdx = 1
            for (res in results) {
                // กรอง: ไม่บันทึกตัวที่ซ้ำกับตัวที่เชื่อมต่ออยู่ เพื่อแยกเป็น Neighbor จริงๆ
                if (res.BSSID != sBssid) {
                    val nRow = mutableListOf<String>()

                    // [Column 1-2] Indexing
                    nRow.add(reportId.toString()) // ✅
                    nRow.add(nIdx.toString())

                    // [Column 3-4] Serving Info
                    nRow.add(sSsid)
                    nRow.add(sBssid)

                    // [Column 5-9] Neighbor Info
                    nRow.add(res.SSID)          // neighbor_ssid
                    nRow.add(res.BSSID)         // neighbor_bssid
                    nRow.add(res.level.toString()) // neighbor_level (dBm)
                    nRow.add(res.frequency.toString()) // neighbor_freq (MHz)
                    nRow.add(res.capabilities)  // neighbor_capabilities (Security)

                    // [Column 10-12] Location & Time
                    nRow.add(loc?.latitude?.toString() ?: "")
                    nRow.add(loc?.longitude?.toString() ?: "")
                    nRow.add(timestamp)

                    // 3. ส่งข้อมูลไปเก็บที่ MainActivity
                    mainActivity.addWifiNeighborCsvRow(nRow)
                    nIdx++
                }
            }
        }
    }
    /* ================= Core ================= */
    @SuppressLint("MissingPermission")
    private fun scanWifi() {

        // ❗ กันไว้ตั้งแต่บรรทัดแรก
        if (!hasWifiPermission()) {
            wifiSsid?.text = "Wi-Fi permission required"
            return
        }

        // ================= GPS =================
        val loc = mainActivity.latestLocation
        latLngValue?.text =
            loc?.let { "%.5f / %.5f".format(it.latitude, it.longitude) }
                ?: "- / -"

        // ================= Wi-Fi OFF =================
        if (!wifiManager.isWifiEnabled) {
            clearWifiUi()
            wifiSsid?.text = "WiFi Off"
            currentSsid = "WiFi_Off"
            return
        }

        // ✅ เรียกได้แล้ว เพราะ permission ผ่าน
        wifiManager.startScan()

        val info = wifiManager.connectionInfo
        val isConnected = info != null && info.networkId != -1


        if (!isConnected) {
            clearConnectedUiOnly()
            wifiSsid?.text = "Non-connected"
            currentSsid = "Non-connected"   // ✅ วางตรงนี้
            return
        }



        // ================= Connected Wi-Fi =================
        val ssid = info.ssid.replace("\"", "")
        val rssi = info.rssi
        val freq = info.frequency

        wifiSsid?.text = ssid
        currentSsid = ssid



        wifiMac?.text = info.bssid   // MAC Address

        wifiRssi?.text = "$rssi dBm"
        wifiFreq?.text = "$freq MHz"
        wifiLinkSpeed?.text = "${info.linkSpeed} Mbps"
        wifiChannel?.text = "CH: ${freqToChannel(freq)}"
        wifiSignalQual?.text = "Qual: ${rssiToQuality(rssi)}%"
        wifiSnr?.text = "SNR: N/A"

        wifiTxRx?.text =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                "Tx/Rx: ${info.txLinkSpeedMbps}/${info.rxLinkSpeedMbps}"
            else NA


        // ================= Extra info จาก ScanResult =================
        if (
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val scan = wifiManager.scanResults.firstOrNull { it.BSSID == info.bssid }
            if (scan != null) {

                wifiStandard?.text =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        when (scan.wifiStandard) {
                            ScanResult.WIFI_STANDARD_11N  -> "802.11n"
                            ScanResult.WIFI_STANDARD_11AC -> "802.11ac"
                            ScanResult.WIFI_STANDARD_11AX -> "802.11ax"
                            ScanResult.WIFI_STANDARD_11BE -> "802.11be"
                            else -> "802.11a/b/g"
                        }
                    else NA

                wifiBw?.text =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        "${getBandwidthString(scan.channelWidth)} MHz"
                    else NA

                wifiSecurity?.text = scan.capabilities
            }
        }
        // 🔒 กำหนด report ครั้งนี้
        mainActivity.currentWifiReportId = mainActivity.getNextReportId()


        mainActivity.addWifiCsvRow(
            ssid = currentSsid,
            freq = freq,
            channel = freqToChannel(freq),
            bw = wifiBw?.text?.toString(),
            linkSpeed = info.linkSpeed,
            security = wifiSecurity?.text?.toString(),
            mac = info.bssid,
            standard = wifiStandard?.text?.toString(),
            signalQual = rssiToQuality(rssi),
            speed = mainActivity.latestLocation?.speed
        )




    }
    private fun hasWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }



    /* ================= Helpers ================= */

    private fun clearWifiUi() {
        listOf(
            wifiSsid, wifiMac, wifiRssi, wifiFreq, wifiLinkSpeed,
            wifiChannel, wifiBw, wifiStandard, wifiSecurity,
            wifiTxRx, wifiSignalQual, wifiSnr, wifiSpeed
        ).forEach { it?.text = NA }

        latLngValue?.text = "$NA / $NA"
        if (::wifiAdapter.isInitialized) wifiAdapter.setData(emptyList())
    }
    private fun clearConnectedUiOnly() {
        wifiSsid?.text = NA
        wifiMac?.text = NA

        wifiRssi?.text = NA
        wifiFreq?.text = NA
        wifiLinkSpeed?.text = NA
        wifiChannel?.text = NA
        wifiBw?.text = NA
        wifiStandard?.text = NA
        wifiSecurity?.text = NA
        wifiTxRx?.text = NA
        wifiSignalQual?.text = NA
        wifiSnr?.text = NA
        wifiSpeed?.text = NA

    }


    private fun updateAltitudeInfo() {
        val press = mainActivity.currentFilteredPressure
        textPressure?.text = "Pressure: %.2f hPa".format(press)

        if (mainActivity.referencePressure != -1f && press > 0) {
            val h = 44330 * (1 - Math.pow((press / mainActivity.referencePressure).toDouble(), 1 / 5.255))
            textAltitude?.text = "Rel. Height: %.2f m".format(h)
            textFloor?.text = "Floor: ${(h / mainActivity.floorHeightMeters).roundToInt() + 1}"
        } else {
            textAltitude?.text = "Rel. Height: $NA"
            textFloor?.text = "Floor: $NA"
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun freqToChannel(freq: Int) = when {
        freq in 2412..2484 -> (freq - 2407) / 5
        freq in 5170..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> -1
    }

    private fun rssiToQuality(rssi: Int) =
        when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> 2 * (rssi + 100)
        }

    private fun getBandwidthString(w: Int) =
        when (w) { 0 -> "20"; 1 -> "40"; 2 -> "80"; 3 -> "160"; 4 -> "80+80"; else -> NA }

    private fun saveCsv(ssid: String, rssi: Int) {
        if (!mainActivity.isRecordingCsv) return
        // (เหมือนเดิม ใช้ของคุณได้เลย)
    }

    @SuppressLint("MissingPermission")
    private fun displayWifiScanResults() {

        if (!hasWifiPermission()) return
        val results = wifiManager.scanResults

        wifiAdapter.setData(
            wifiManager.scanResults
                .sortedByDescending { it.level }
                .take(50)
        )
        // 1️⃣ เขียน Neighbor (ใช้ report เดียวกับ Serving)
        recordWifiNeighbors(results)

        // 2️⃣ ปิดรอบ → ค่อย increment
        if (mainActivity.isRecordingWifiCsv) {
            mainActivity.incrementReportCounter()
        }
    }


    fun getCurrentSsid(): String {
        return currentSsid
    }

}
