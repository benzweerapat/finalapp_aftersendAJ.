package com.example.myapplication2

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.telephony.SubscriptionManager
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import android.telephony.TelephonyManager
import android.telephony.ServiceState
import android.os.Handler
import android.os.Looper



class MainActivity : AppCompatActivity() {

    // ================== GLOBAL ==================
    var latestLocation: Location? = null
    var currentFilteredPressure: Float = 0f
    var referencePressure: Float = -1f
    var referenceGpsAltitude: Double? = null
    var floorHeightMeters: Float = 3.5f

    private lateinit var locationManager: LocationManager
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private val ALPHA = 0.1f

    private lateinit var prefs: SharedPreferences
    private var cellularSessionCounter = 1
    private var wifiSessionCounter = 1

    private val permissionRequestCode = 101
    private var reportCounter = 1

    // ================== CELLULAR CSV ==================
    var isRecordingCsv = false

    private val csvBuffer = mutableListOf<List<String>>()
    private fun requestLocationPermission() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                101
            )
        }
    }
    private fun requestPhonePermission() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                102
            )
        }
    }



    val csvHeader = listOf(
        "report","sys_time","sim_state","service_state","nr_state",
        "net_op_name","net_op_code","roaming","net_type","call_state",
        "data_state","data_act","data_rx","data_tx","gsm_neighbors",
        "umts_neighbors","lte_neighbors","nr_neighbors","cdma_neighbors","rssi_strongest",
        "nstrong","tech","mcc","mnc","mnc_master",
        "lac_tac_sid","long_cid","node_id_nid","cid_bid","psc_pci",
        "nrtac","nrnci","nrpci","nrarfcn","rssi",
        "rsrq","rssi_ev","ecio_ev","rssnr","nrssrsrp",
        "nrssrsrq","nrsssinr","nrcsirsrp","nrcsirsrq","nrcsisinr",
        "slev","ta","gps","accuracy","lat",
        "long","altitude","speed","bearing","band",
        "arfcn","bw","bwlist","thp_rx","thp_tx",
        "baro_pressure","baro_rel_alt","baro_floor","gps_rel_alt","gps_floor"
    )

    val neighborLteCsvHeader = listOf(
        "session_id",
        "sys_time",
        "report",

        "serving_tech",
        "serving_arfcn",
        "serving_pci",
        "serving_eci",

        "neighbor_index",
        "neighbor_tech",
        "neighbor_arfcn",
        "neighbor_pci",
        "neighbor_rsrp",
        "neighbor_rsrq",
        "neighbor_sinr",

        "lat",
        "long"
    )
    fun addNeighborLteCsvRow(
        sessionId: Int,
        report: Int,

        servingArfcn: Int?,
        servingPci: Int?,
        servingEci: Long?,

        neighborIndex: Int,
        neighborTech: String,
        neighborArfcn: Int?,
        neighborPci: Int?,
        neighborRsrp: Int?,
        neighborRsrq: Int?,
        neighborSinr: Float?
    ) {
        if (!isRecordingNeighborCsv) return

        val now = Date()
        val sysTime =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)

        val lat = latestLocation?.latitude?.toString() ?: ""
        val lon = latestLocation?.longitude?.toString() ?: ""

        val row = listOf(
            sessionId.toString(),
            sysTime,
            report.toString(),

            "LTE",
            servingArfcn?.toString() ?: "",
            servingPci?.toString() ?: "",
            servingEci?.toString() ?: "",

            neighborIndex.toString(),
            neighborTech,
            neighborArfcn?.toString() ?: "",
            neighborPci?.toString() ?: "",
            neighborRsrp?.toString() ?: "",
            neighborRsrq?.toString() ?: "",
            neighborSinr?.toString() ?: "",

            lat,
            lon
        )

        neighborLteCsvBuffer.add(row)
    }

    // ================== NEIGHBOR LTE CSV ==================
    var isRecordingNeighborCsv = false
    private val neighborLteCsvBuffer = mutableListOf<List<String>>()



    // ================== WIFI CSV ==================
    var isRecordingWifiCsv = false

    // ค้นหาส่วน Cellular CSV แล้วเพิ่มตัวแปรเหล่านี้เข้าไป


    private var servingTechForFileName = "UNKNOWN" // เก็บไว้ใช้ตอนตั้งชื่อไฟล์
    private val wifiCsvBuffer = mutableListOf<List<String>>()


    val wifiCsvHeader = listOf(
        "report","sys_time","lat","long","altitude",
        "ssid","freq","channel","bw","linkspeed",
        "security","mac","standard","signalqual","speed",
        "baro_pressure","baro_rel_alt","baro_floor",
        "gps_rel_alt","gps_floor"
    )
    // 1. Header สำหรับ Neighbor CSV
    val neighborCsvHeader = listOf(
        "report", "neighbor_index","sys_time",
        "serving_tech", "serving_arfcn", "serving_pci", "serving_cell_id",
        "neighbor_tech", "neighbor_arfcn", "neighbor_pci", "neighbor_rsrp",
        "neighbor_rsrq", "neighbor_sinr",
        "latitude", "longitude"
    )
    var currentWifiReportId: Int = 0

    // 2. Buffer สำหรับเก็บข้อมูลแถวของ Neighbor
    private val neighborCsvBuffer = mutableListOf<List<String>>()
    // ฟังก์ชันสำหรับขอเลข report ล่าสุด (เพื่อให้ serving และ neighbor ตรงกัน)
    fun getNextReportId(): Int = reportCounter
    fun incrementReportCounter() {
        reportCounter++
    }

    // 3. ฟังก์ชันสำหรับเพิ่มแถวข้อมูล (เรียกจาก Fragment)
    fun addNeighborCsvRow(row: List<String>) {
        if (isRecordingCsv) {
            neighborCsvBuffer.add(row)
        }
    }
    private fun saveNeighborCsv() {
        if (neighborCsvBuffer.isEmpty()) return

        val now = Date()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)

        // ใช้ค่า Session ล่าสุด (ลบ 1 เพราะ cellularSessionCounter มักจะถูกบวกเพิ่มไปแล้วใน saveCellularCsv)
        val session = cellularSessionCounter - 1
        val fileName = "Session_${session}_neighbor_${servingTechForFileName}_$timestamp.csv"

        // เรียกใช้ saveCsv ที่มีอยู่แล้วเพื่อความสะดวก
        saveCsv(fileName, neighborCsvHeader, neighborCsvBuffer, "Cellular")

        // เคลียร์ข้อมูลทิ้งหลังบันทึกเสร็จ
        neighborCsvBuffer.clear()
    }

// 4. แก้ไขฟังก์ชัน saveCellularCsv() ให้บันทึกไฟล์ Neighbor ด้วย
// โดยใช้ชื่อไฟล์ตามรูปแบบ: Session_<id>_neighbor_<TECH>_<timestamp>.csv


    // ================== ACTIVITY ==================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // -------------------------------------------------
        // โซนที่ 1: เตรียมข้อมูลและเครื่องมือ (Init Data & Services)
        // *ต้องทำก่อนสิ่งอื่นใด* เพื่อให้ตัวแปรพร้อมใช้งาน
        // -------------------------------------------------
        prefs = getSharedPreferences("drive_test", MODE_PRIVATE)
        cellularSessionCounter = prefs.getInt("cellular_session_counter", 1)
        wifiSessionCounter = prefs.getInt("wifi_session_counter", 1)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        // -------------------------------------------------
        // โซนที่ 2: สร้างหน้าจอ (Init UI & Fragments)
        // -------------------------------------------------
        setupButtons()

        if (savedInstanceState == null) {
            // วาง Fragment หลังจากตัวแปร (โซน 1) พร้อมแล้ว
            // เผื่อใน CellularFragment มีการดึงค่า SessionCounter ไปโชว์ จะได้ไม่ Error
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CellularFragment())
                .commit()
        }

        // -------------------------------------------------
        // โซนที่ 3: ตรวจสอบสถานะและขออนุญาต (Checks & Permissions)
        // ไว้ท้ายสุด เพื่อให้หน้าจอโหลดเสร็จก่อน Dialog จะเด้งขึ้นมา
        // -------------------------------------------------

        checkWifiAndLocationState() // เช็คว่าเปิด GPS/WiFi หรือยัง

        // แนะนำให้รวม request... ต่างๆ เป็นฟังก์ชันเดียวแล้วเรียกตรงนี้
        requestAllPermissions()
    }
    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Location (สำคัญที่สุดสำหรับ Network/Wifi)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 2. Phone State (สำหรับดูข้อมูล Cellular)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        // 3. Storage (เฉพาะ Android ต่ำกว่า 10 หรือ Q)
        // Android รุ่นใหม่ๆ ไม่ต้องขอ Write Storage แบบเก่าแล้ว
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // 4. Notification (สำหรับ Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 5. Wi-Fi Nearby Devices (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        // สั่งขอ Permission ทีเดียวรวด
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                1001 // Request Code ตั้งเป็นเลขอะไรก็ได้ (เช่น 1001)
            )
        }


    }
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun canWriteLegacyStorage(): Boolean {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.P || // Android 10+
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkStoragePermissionLegacy() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Android 8–9
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    200
                )
            }
        }
    }

    private fun checkWifiAndLocationState(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val wifiEnabled = wifiManager.isWifiEnabled
        val locationEnabled =
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!wifiEnabled) {
            toast("Wi-Fi is OFF")
            return false
        }

        if (!locationEnabled) {
            toast("Location is OFF")
            return false
        }

        return true
    }
    private fun hasWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }









    // ================== BUTTONS ==================
    private fun setupButtons() {

        val scanBtn = findViewById<Button>(R.id.saveCsvButton)
        findViewById<ImageView>(R.id.btnMenu).setOnClickListener { view ->

            if (isRecordingCsv || isRecordingWifiCsv) {
                toast("Stop recording before switching mode")
                return@setOnClickListener
            }

            val wrapper = ContextThemeWrapper(this, R.style.PopupMenuDark)
            val popup = PopupMenu(wrapper, view)

            popup.menuInflater.inflate(R.menu.scan_menu, popup.menu)

            val isRecordingNow = isRecordingCsv || isRecordingWifiCsv

            // 🔒 ถ้ากำลัง Recording → disable menu
            popup.menu.findItem(R.id.menu_cellular).isEnabled = !isRecordingNow
            popup.menu.findItem(R.id.menu_wifi).isEnabled = !isRecordingNow

            popup.setOnMenuItemClickListener { item ->

                // 🔴 Safety check (กันพลาด)
                if (isRecordingNow) {
                    toast("Stop recording before switching mode")
                    return@setOnMenuItemClickListener true
                }

                when (item.itemId) {
                    R.id.menu_cellular -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, CellularFragment())
                            .commit()
                        findViewById<TextView>(R.id.currentModeText).text = "Current: Cellular"
                        true
                    }

                    R.id.menu_wifi -> {

                        if (!hasWifiPermission()) {

                            toast("Please allow Wi-Fi permission first")
                            return@setOnMenuItemClickListener true
                        }

                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, WifiFragment())
                            .commit()

                        findViewById<TextView>(R.id.currentModeText).text = "Current: WiFi"
                        true
                    }


                    else -> false
                }
            }

            popup.show()
        }



        scanBtn.setOnClickListener {

            // 🔍 ดูว่าตอนนี้อยู่ Fragment ไหน
            val fragment = supportFragmentManager
                .findFragmentById(R.id.fragment_container)

            when (fragment) {

                // ================= CELLULAR =================
                // ค้นหาส่วน scanBtn.setOnClickListener ใน setupButtons()
                is CellularFragment -> {
                    if (!isRecordingCsv) {
                        calibrateAltitude()
                        csvBuffer.clear()
                        neighborCsvBuffer.clear() // ✅ เพิ่ม
                        reportCounter = 1         // ✅ เริ่มที่ 1
                        isRecordingCsv = true
                        scanBtn.text = "STOP"
                    } else {
                        isRecordingCsv = false
                        scanBtn.text = "START"

                        if (!canWriteLegacyStorage()) {
                            toast("Storage permission required")
                            return@setOnClickListener
                        }

                        saveCellularCsv()  // บันทึกไฟล์ Serving
                        saveNeighborCsv()  // ✅ บันทึกไฟล์ Neighbor ต่อท้ายทันที
                        toast("All CSVs saved")
                    }
                }

                // ================= WIFI =================
                is WifiFragment -> {

                    if (!isRecordingWifiCsv) {
                        calibrateAltitude()
                        wifiCsvBuffer.clear()
                        isRecordingWifiCsv = true

                        scanBtn.text = "STOP"
                        toast("WiFi logging started")

                    } else {
                        isRecordingWifiCsv = false
                        scanBtn.text = "START"
                        // ⬇️ วางตรงนี้
                        if (!canWriteLegacyStorage()) {
                            toast("Storage permission required to save CSV")
                            return@setOnClickListener
                        }

                        saveWifiCsv(fragment.getCurrentSsid())
                        saveWifiNeighborCsv()   // ✅ เพิ่มบรรทัดนี้
                        toast("WiFi CSV + Neighbor CSV saved")
                    }
                }
            }
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 200) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                toast("Storage permission granted")
            } else {
                toast("Storage permission denied (cannot save CSV on Android 8–9)")
            }
        }
    }



    // ================== CSV ADD ==================
    fun addCsvRow(row: List<String>) {
        if (isRecordingCsv) {
            csvBuffer.add(row)

            // ✅ col 21 = tech
            if (row.size > 21) {
                servingTechForFileName = row[21]
            }
        }
    }



    fun addWifiCsvRow(
        ssid: String,
        freq: Int?,
        channel: Int?,
        bw: String?,
        linkSpeed: Int?,
        security: String?,
        mac: String?,
        standard: String?,
        signalQual: Int?,
        speed: Float?
    ) {
        if (!isRecordingWifiCsv) return

        val now = Date()
        val sysTime = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(now)
        val loc = latestLocation

        // ===== Barometer =====
        val baroPressure = "%.2f".format(currentFilteredPressure)

        var baroRelAlt = ""
        var baroFloor = ""
        if (referencePressure != -1f && currentFilteredPressure > 0) {
            val h = 44330 * (1 - Math.pow(
                (currentFilteredPressure / referencePressure).toDouble(),
                1 / 5.255
            ))
            baroRelAlt = "%.2f".format(h)
            baroFloor = ((h / floorHeightMeters).roundToInt() + 1).toString()
        }

        // ===== GPS =====
        var gpsRelAlt = ""
        var gpsFloor = ""
        if (loc != null && referenceGpsAltitude != null && loc.hasAltitude()) {
            val rel = loc.altitude - referenceGpsAltitude!!
            gpsRelAlt = "%.2f".format(rel)
            gpsFloor = ((rel / floorHeightMeters).roundToInt() + 1).toString()
        }

        val row = listOf(
            wifiCsvBuffer.size.toString(),              // report
            sysTime,                                    // sys_time
            loc?.latitude?.toString() ?: "",            // lat
            loc?.longitude?.toString() ?: "",           // long
            loc?.altitude?.toString() ?: "",            // altitude

            ssid,                                       // ssid
            freq?.toString() ?: "",                     // freq
            channel?.toString() ?: "",                  // channel
            bw ?: "",                                   // bw
            linkSpeed?.toString() ?: "",                // linkspeed

            security ?: "",                             // security
            mac ?: "",                                  // mac
            standard ?: "",                             // standard
            signalQual?.toString() ?: "",               // signalqual
            speed?.toString() ?: "",                    // speed

            baroPressure,                               // baro_pressure
            baroRelAlt,                                 // baro_rel_alt
            baroFloor,                                  // baro_floor
            gpsRelAlt,                                  // gps_rel_alt
            gpsFloor                                   // gps_floor
        )

        wifiCsvBuffer.add(row)
    }
    override fun attachBaseContext(newBase: Context) {
        val config = newBase.resources.configuration
        config.fontScale = 0.8f   // 🔒 ล็อกขนาดตัวอักษร
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
    // ส่วน Header (แนะนำให้ประกาศเป็นตัวแปร global หรือในฟังก์ชัน save)
    val wifiNeighborHeader = listOf(
        "report", "neighbor_index","sys_time", "connected_ssid", "neighbor_ssid",
        "neighbor_bssid", "neighbor_level", "neighbor_freq",
        "capabilities", "lat", "long"
    )

    // เพิ่มใน MainActivity.kt
    private val wifiNeighborCsvBuffer = mutableListOf<List<String>>()

    fun addWifiNeighborCsvRow(row: List<String>) {
        if (isRecordingWifiCsv) {
            wifiNeighborCsvBuffer.add(row)
        }
    }

// ในส่วนที่สั่ง STOP Recording WiFi
// ให้เรียกฟังก์ชัน saveWifiNeighborCsv() ต่อท้าย saveWifiCsv()









    // ================== LOCATION / SENSOR ==================
    private val locListener = object : android.location.LocationListener {
        override fun onLocationChanged(loc: Location) { latestLocation = loc }
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
        @Deprecated("Deprecated") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    }
    @SuppressLint("MissingPermission")
    fun isReadyToStart(): Boolean {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val serviceState = tm.serviceState   // 👈 เก็บครั้งเดียว

        val radioOff =
            android.provider.Settings.Global.getInt(
                contentResolver,
                android.provider.Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1 ||
                    tm.serviceState?.state == ServiceState.STATE_POWER_OFF

        val inService = tm.serviceState?.state == ServiceState.STATE_IN_SERVICE
        val dataOk = tm.dataState == TelephonyManager.DATA_CONNECTED
        val gpsOk = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { false }

        return !radioOff && inService && dataOk && gpsOk
    }
    @SuppressLint("MissingPermission")
    fun isReadyToStartWifi(): Boolean {
        val wifiManager =
            applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val wifiOn = wifiManager.isWifiEnabled

        val gpsOn = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }

        // WiFi Scan ต้องเปิดทั้ง WiFi + Location
        return wifiOn && gpsOn
    }

    private fun updateStartButtonState() {
        val scanBtn = findViewById<Button>(R.id.saveCsvButton)

        // ถ้ากำลังอัด → ต้องกดได้เสมอ (เพื่อ STOP)
        if (isRecordingCsv || isRecordingWifiCsv) {
            scanBtn.isEnabled = true
            scanBtn.alpha = 1f
            return
        }

        val fragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container)

        val ready = when (fragment) {
            is CellularFragment -> isReadyToStart()
            is WifiFragment -> isReadyToStartWifi()
            else -> false
        }

        scanBtn.isEnabled = ready
        scanBtn.alpha = if (ready) 1f else 0.4f
    }


    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRunnable = object : Runnable {
        override fun run() {
            updateStartButtonState()
            uiHandler.postDelayed(this, 1000)
        }
    }




    private val barometerListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val raw = e.values[0]
            currentFilteredPressure =
                if (currentFilteredPressure == 0f) raw
                else ALPHA * raw + (1 - ALPHA) * currentFilteredPressure
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onResume() {
        super.onResume()


        startLocationUpdates()
        pressureSensor?.let {
            sensorManager?.registerListener(barometerListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        uiHandler.post(uiRunnable) // ✅ เพิ่ม
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locListener)
        sensorManager?.unregisterListener(barometerListener)
        uiHandler.removeCallbacks(uiRunnable)
    }

    // ================== SAVE CSV ==================
    private fun saveWifiCsv(ssid: String) {
        if (wifiCsvBuffer.isEmpty()) {
            toast("No WiFi data to save")
            return
        }


        val now = Date()
        val name = ssid.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val fileName = "Session_${wifiSessionCounter}_SSID${name}_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now) + "_CSV.csv"

        saveCsv(fileName, wifiCsvHeader, wifiCsvBuffer, "Wifi")

    }
    private fun saveWifiNeighborCsv() {
        if (wifiNeighborCsvBuffer.isEmpty()) {
            toast("No WiFi Neighbor data to save")
            return
        }

        val now = Date()
        val fileName =
            "Session_${wifiSessionCounter - 1}_WifiNeighbor_" +
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now) +
                    ".csv"

        saveCsv(
            fileName = fileName,
            header = wifiNeighborHeader,
            rows = wifiNeighborCsvBuffer,
            subDir = "Wifi"
        )

        wifiNeighborCsvBuffer.clear()
    }


    private fun saveCellularCsv() {
        val now = Date()
        val fileName = "Session_${cellularSessionCounter}_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now) + "_CSV.csv"

        saveCsv(fileName, csvHeader, csvBuffer, "Cellular")

    }

    private fun saveCsv(
        fileName: String,
        header: List<String>,
        rows: List<List<String>>,
        subDir: String
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ===== Android 10+ =====
                val resolver = contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Download/DriveTest/$subDir"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cv
                ) ?: return

                resolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    w.append(header.joinToString(";")).append("\n")
                    rows.forEach {
                        w.append(it.joinToString(";")).append("\n")
                    }
                }

                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    },
                    null,
                    null
                )

            } else {
                // ===== Android 9 ลงไป =====
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ),
                    "DriveTest/$subDir"
                )
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, fileName)
                file.bufferedWriter().use { w ->
                    w.append(header.joinToString(";")).append("\n")
                    rows.forEach {
                        w.append(it.joinToString(";")).append("\n")
                    }
                }
            }

            // ===== Update session counter =====
            if (subDir == "Cellular") {
                cellularSessionCounter++
                prefs.edit()
                    .putInt("cellular_session_counter", cellularSessionCounter)
                    .apply()
            } else if (subDir == "Wifi") {
                wifiSessionCounter++
                prefs.edit()
                    .putInt("wifi_session_counter", wifiSessionCounter)
                    .apply()
            }

            toast("Saved: Download/DriveTest/$subDir/$fileName")

        } catch (e: Exception) {
            e.printStackTrace()
            toast("Save error: ${e.message}")
        }
    }



    // ================== UTILS ==================
    fun calibrateAltitude() {
        if (currentFilteredPressure > 0) referencePressure = currentFilteredPressure
        if (latestLocation?.hasAltitude() == true)
            referenceGpsAltitude = latestLocation!!.altitude
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, locListener)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0f, locListener)
        }
    }

    private fun checkPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            ),
            permissionRequestCode
        )
    }

    fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
