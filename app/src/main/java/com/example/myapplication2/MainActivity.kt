package com.example.myapplication2
import android.view.View

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
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr



class MainActivity : AppCompatActivity() {

    enum class DriveMode(val label: String, val folderName: String) {
        OUTDOOR("Outdoor", "Outdoor"),
        INDOOR("Indoor", "Indoor")
    }

    enum class CurrentTech { WIFI, CELL }
    enum class CurrentEnv { INDOOR, OUTDOOR }
    enum class SurveyState { IDLE, RUNNING }

    // ================== GLOBAL ==================
    var latestLocation: Location? = null
    var currentFilteredPressure: Float = 0f
    var referencePressure: Float = -1f
    var referenceGpsAltitude: Double? = null
    var floorHeightMeters: Float = 3.5f
    var hasSelectedFloorHeight: Boolean = false
    var hasSelectedStartFloor: Boolean = false

    var startFloor: Int = 1   // ชั้นเริ่มต้น (ผู้ใช้เลือก)

    private lateinit var locationManager: LocationManager
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private val ALPHA = 0.1f
//
    private lateinit var prefs: SharedPreferences

    private val permissionRequestCode = 101
    private var reportCounter = 1

    // ================== CELLULAR CSV ==================
    var isRecordingCsv = false
    var isGroundSet = false
    private var currentDriveMode = DriveMode.OUTDOOR
    private var currentTech = CurrentTech.CELL
    private var currentEnv = CurrentEnv.OUTDOOR
    private var indoorSurveyState = SurveyState.IDLE

    // 🔒 ล็อก session ต่อ 1 การอัด
    private var currentCellularSessionId: Int? = null
    private var currentWifiSessionId: Int? = null
    // ================== CELLULAR SNAPSHOT SYSTEM ==================

    private var cellularReportCounter: Int = 1

    private var pendingCellularReportId: Int? = null
    private var pendingCellularSysTime: String? = null

    private var isCellularSnapshotActive = false

    // buffer กัน async timing
    private val pendingCellularNeighborBuffer = mutableListOf<List<String>>()



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
        "data_state","rssi_strongest","tech","mcc","mnc","mnc_master",
        "lac_tac_sid","long_cid","node_id_nid","cid_bid","psc_pci",
        "nrtac","nrnci","nrpci","nrarfcn","rssi",
        "rsrq","rssnr","nrssrsrp","nrssrsrq","nrsssinr",
        "ta","gps","accuracy","lat","long","altitude","speed","bearing",
        "band","arfcn","bw",
        "baro_pressure","baro_rel_alt","baro_floor","gps_rel_alt","gps_floor"
    )





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
    private var wifiReportCounter: Int = 1
    private var pendingWifiNeighborReportId: Int = 0
    private var pendingWifiNeighborSysTime: String? = null

    // 2. Buffer สำหรับเก็บข้อมูลแถวของ Neighbor
    private val neighborCsvBuffer = mutableListOf<List<String>>()
    // ฟังก์ชันสำหรับขอเลข report ล่าสุด (เพื่อให้ serving และ neighbor ตรงกัน)
    fun getNextReportId(): Int = reportCounter
    fun incrementReportCounter() {
        reportCounter++
    }

    fun beginWifiReportSession() {
        wifiReportCounter = 1
        currentWifiReportId = 0
        pendingWifiNeighborReportId = 0
        pendingWifiNeighborSysTime = null
    }

    fun allocateNextWifiServingReportId(): Int {
        val id = wifiReportCounter
        wifiReportCounter++
        currentWifiReportId = id
        pendingWifiNeighborReportId = id
        pendingWifiNeighborSysTime = null
        return id
    }
    fun allocateNextCellularServingReport(): Pair<Int, String> {

        val reportId = cellularReportCounter++
        val sysTime = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            .format(Date())

        pendingCellularReportId = reportId
        pendingCellularSysTime = sysTime
        isCellularSnapshotActive = true

        return Pair(reportId, sysTime)
    }
    fun addCellularNeighborRowSafe(rowWithoutReport: List<String>) {

        if (!isRecordingCsv) return

        val reportId = pendingCellularReportId
        val sysTime = pendingCellularSysTime

        if (!isCellularSnapshotActive || reportId == null || sysTime == null) {
            pendingCellularNeighborBuffer.add(rowWithoutReport)
            return
        }

        val finalRow = listOf(
            reportId.toString(),
            rowWithoutReport[0], // neighbor_index
            sysTime
        ) + rowWithoutReport.drop(1)

        neighborCsvBuffer.add(finalRow)
    }
    fun flushPendingCellularNeighbors() {

        val reportId = pendingCellularReportId ?: return
        val sysTime = pendingCellularSysTime ?: return

        pendingCellularNeighborBuffer.forEach { rowWithoutReport ->

            val finalRow = listOf(
                reportId.toString(),
                rowWithoutReport[0],
                sysTime
            ) + rowWithoutReport.drop(1)

            neighborCsvBuffer.add(finalRow)
        }

        pendingCellularNeighborBuffer.clear()
    }
    fun clearPendingCellularSnapshot() {
        pendingCellularReportId = null
        pendingCellularSysTime = null
        isCellularSnapshotActive = false
        pendingCellularNeighborBuffer.clear()
    }
    fun beginCellularReportSession() {
        cellularReportCounter = 1
        pendingCellularReportId = null
        pendingCellularSysTime = null
        isCellularSnapshotActive = false
        pendingCellularNeighborBuffer.clear()
    }


    fun getPendingWifiNeighborReportId(): Int? {
        return pendingWifiNeighborReportId.takeIf { it > 0 }
    }

    fun getPendingWifiNeighborSysTime(): String? {
        return pendingWifiNeighborSysTime
    }

    fun clearPendingWifiNeighborReportId() {
        pendingWifiNeighborReportId = 0
        pendingWifiNeighborSysTime = null
    }

    // 3. ฟังก์ชันสำหรับเพิ่มแถวข้อมูล (เรียกจาก Fragment)
    fun addNeighborCsvRow(row: List<String>) {
        if (isRecordingCsv) {
            neighborCsvBuffer.add(row)
        }
    }
    private fun saveNeighborCsv() {
        if (neighborCsvBuffer.isEmpty()) return


        val sessionId = currentCellularSessionId ?: return
        val timestamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // ใช้ค่า Session ล่าสุด (ลบ 1 เพราะ cellularSessionCounter มักจะถูกบวกเพิ่มไปแล้วใน saveCellularCsv)

        val fileName = "Session_${sessionId }_CELL_NEI_$timestamp.csv"

        // ✅ แก้ตรงนี้บรรทัดเดียว
        saveCsv(
            fileName,
            neighborCsvHeader,   // ✅ ใช้ header ตัวนี้
            neighborCsvBuffer,   // ✅ ใช้ buffer ตัวนี้
            "Cellular"
        )
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


        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        // -------------------------------------------------
        // โซนที่ 2: สร้างหน้าจอ (Init UI & Fragments)
        // -------------------------------------------------
        setupButtons()

        if (savedInstanceState == null) {
            renderCurrentScreen()
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










    private fun showStartFloorDialog(onConfirm: (Int) -> Unit) {

        val floors = arrayOf("1", "2", "3", "4", "5", "Custom")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select start floor")
            .setItems(floors) { _, which ->
                if (floors[which] != "Custom") {
                    onConfirm(floors[which].toInt())
                } else {
                    val input = android.widget.EditText(this)
                    input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Enter floor number")
                        .setView(input)
                        .setPositiveButton("OK") { _, _ ->
                            val v = input.text.toString().toIntOrNull()
                            if (v != null) onConfirm(v)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .show()
    }

    private fun updateFloorButtonLabel() {
        val label = if (hasSelectedStartFloor) "Floor $startFloor" else "Floor --"
        findViewById<Button>(R.id.btnSelectFloor)?.text = label
    }

    fun getFloorHeightButtonLabel(): String {
        return if (hasSelectedFloorHeight) {
            "Height = ${"%.1f".format(Locale.US, floorHeightMeters)} m"
        } else {
            "Edit Height"
        }
    }

    fun refreshFloorHeightButtonLabel() {
        when (val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)) {
            is CellularFragment -> fragment.updateEditHeightButtonLabel(getFloorHeightButtonLabel())
            is WifiFragment -> fragment.updateEditHeightButtonLabel(getFloorHeightButtonLabel())
        }
    }

    fun onFloorHeightSelected(heightMeters: Float) {
        floorHeightMeters = heightMeters
        hasSelectedFloorHeight = true
        refreshFloorHeightButtonLabel()
    }

    private fun validateSelectionsBeforeStart(): Boolean {
        if (!hasSelectedStartFloor && !hasSelectedFloorHeight) {
            toast("Please select Floor and Edit Height before START")
            return false
        }
        if (!hasSelectedStartFloor) {
            toast("Please select Floor before START")
            return false
        }
        if (!hasSelectedFloorHeight) {
            toast("Please select Edit Height before START")
            return false
        }
        return true
    }


    private fun updateDriveModeButtonUi() {
        val btn = findViewById<Button>(R.id.btnDriveMode) ?: return
        btn.text = currentDriveMode.label

        val colorRes =
            if (currentDriveMode == DriveMode.OUTDOOR) android.R.color.holo_green_light
            else android.R.color.holo_orange_light
        val color = ContextCompat.getColor(this, colorRes)

        btn.setTextColor(color)
    }

    private fun updateCurrentModeLabel(fragmentLabel: String) {
        findViewById<TextView>(R.id.currentModeText).text =
            "Current: $fragmentLabel • ${currentDriveMode.label}"
    }

    fun isIndoorDriveMode(): Boolean = currentDriveMode == DriveMode.INDOOR

    private fun renderCurrentScreen() {
        if (currentEnv == CurrentEnv.INDOOR) {
            indoorSurveyState = if (IndoorSessionManager.surveyRunning) SurveyState.RUNNING else SurveyState.IDLE
        }

        val fragment = if (currentEnv == CurrentEnv.INDOOR) {
            IndoorSessionManager.radioMode = if (currentTech == CurrentTech.WIFI) IndoorSessionManager.RadioMode.WIFI else IndoorSessionManager.RadioMode.CELLULAR
            IndoorWalkFragment()
        } else {
            if (currentTech == CurrentTech.WIFI) WifiFragment() else CellularFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        if (currentEnv == CurrentEnv.INDOOR) {
            supportFragmentManager.executePendingTransactions()
            (supportFragmentManager.findFragmentById(R.id.fragment_container) as? IndoorWalkFragment)
                ?.setSurveyRunning(IndoorSessionManager.surveyRunning)
        }

        updateCurrentModeLabel("${if (currentTech == CurrentTech.WIFI) "WiFi" else "Cellular"}${if (currentEnv == CurrentEnv.INDOOR) " (Indoor Walk Test)" else ""}")
        updateUnifiedSurveyButtonUi()
    }

    private fun updateUnifiedSurveyButtonUi() {
        val scanBtn = findViewById<Button>(R.id.saveCsvButton)
        scanBtn.isEnabled = true
        val running = if (currentEnv == CurrentEnv.INDOOR) {
            IndoorSessionManager.surveyRunning
        } else {
            isRecordingCsv || isRecordingWifiCsv
        }
        applySurveyButtonState(scanBtn, running)
    }

    private fun applySurveyButtonState(button: Button, running: Boolean) {
        button.text = if (running) "Stop" else "Start"
        val icon = if (running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        button.compoundDrawablePadding = 8
    }

    // ================== BUTTONS ==================
    private fun setupButtons() {

        val scanBtn = findViewById<Button>(R.id.saveCsvButton)
        val btnDriveMode = findViewById<Button>(R.id.btnDriveMode)
        val btnSelectFloor =
            findViewById<Button>(R.id.btnSelectFloor)
        updateFloorButtonLabel()
        refreshFloorHeightButtonLabel()
        updateDriveModeButtonUi()
        updateCurrentModeLabel("Cellular")
        updateUnifiedSurveyButtonUi()

        btnDriveMode.setOnClickListener {
            if (isRecordingCsv || isRecordingWifiCsv || indoorSurveyState == SurveyState.RUNNING) {
                toast("Stop recording before changing drive mode")
                return@setOnClickListener
            }

            currentDriveMode =
                if (currentDriveMode == DriveMode.OUTDOOR) DriveMode.INDOOR else DriveMode.OUTDOOR
            currentEnv = if (currentDriveMode == DriveMode.INDOOR) CurrentEnv.INDOOR else CurrentEnv.OUTDOOR

            updateDriveModeButtonUi()
            renderCurrentScreen()
            toast("Drive mode: ${currentDriveMode.label}")
        }
        btnSelectFloor.setOnClickListener {
            if (isRecordingCsv || isRecordingWifiCsv || indoorSurveyState == SurveyState.RUNNING) {
                toast("Stop recording before changing floor")
                return@setOnClickListener
            }

            showStartFloorDialog { floor ->
                calibrateAltitude(floor)
                toast("Selected floor: $floor")
            }
        }
        findViewById<ImageView>(R.id.btnMenu).setOnClickListener { view ->

            if (isRecordingCsv || isRecordingWifiCsv || indoorSurveyState == SurveyState.RUNNING) {
                toast("Stop recording before changing mode")
                return@setOnClickListener
            }

            val wrapper = ContextThemeWrapper(this, R.style.PopupMenuDark)
            val popup = PopupMenu(wrapper, view)

            popup.menuInflater.inflate(R.menu.scan_menu, popup.menu)

            val isRecordingNow = isRecordingCsv || isRecordingWifiCsv || indoorSurveyState == SurveyState.RUNNING

            // 🔒 ถ้ากำลัง Recording → disable menu
            popup.menu.findItem(R.id.menu_cellular).isEnabled = !isRecordingNow
            popup.menu.findItem(R.id.menu_wifi).isEnabled = !isRecordingNow

            popup.setOnMenuItemClickListener { item ->

                // 🔴 Safety check (กันพลาด)
                if (isRecordingNow) {
                    toast("Stop recording before changing mode")
                    return@setOnMenuItemClickListener true
                }

                when (item.itemId) {
                    R.id.menu_cellular -> {
                        if (currentDriveMode == DriveMode.INDOOR) {
                            currentTech = CurrentTech.CELL
                            (supportFragmentManager.findFragmentById(R.id.fragment_container) as? IndoorWalkFragment)
                                ?.setRadioMode(IndoorSessionManager.RadioMode.CELLULAR)
                            renderCurrentScreen()
                            true
                        } else {
                            currentTech = CurrentTech.CELL
                            renderCurrentScreen()
                            true
                        }
                    }

                    R.id.menu_wifi -> {

                        if (!hasWifiPermission()) {

                            toast("Please allow Wi-Fi permission first")
                            return@setOnMenuItemClickListener true
                        }

                        if (currentDriveMode == DriveMode.INDOOR) {
                            currentTech = CurrentTech.WIFI
                            (supportFragmentManager.findFragmentById(R.id.fragment_container) as? IndoorWalkFragment)
                                ?.setRadioMode(IndoorSessionManager.RadioMode.WIFI)
                            renderCurrentScreen()
                            true
                        } else {
                            currentTech = CurrentTech.WIFI
                            renderCurrentScreen()
                            true
                        }
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

                    val cellFrag = fragment as CellularFragment

                    if (!isRecordingCsv) {
                        if (!validateSelectionsBeforeStart()) return@setOnClickListener

                        cellFrag.setGroundButtonsVisible(false)
                        cellFrag.showGroundUiAfterStart()

                        currentCellularSessionId =
                            getNextSessionIdMaxPlusOne("Cellular")

                        csvBuffer.clear()
                        neighborCsvBuffer.clear()
                        beginCellularReportSession()
                        isRecordingCsv = true
                        updateUnifiedSurveyButtonUi()

                    } else {

                        // STOP (เหมือนเดิม)
                        isRecordingCsv = false
                        updateUnifiedSurveyButtonUi()

                        saveCellularCsv()
                        saveNeighborCsv()
                        currentCellularSessionId = null
                        clearPendingCellularSnapshot()
                        cellFrag.setGroundButtonsVisible(true)
                        toast("All CSVs saved")
                    }
                }



                is IndoorWalkFragment -> {
                    if (indoorSurveyState == SurveyState.IDLE) {
                        calibrateAltitude(startFloor)
                        fragment.startSurvey()
                        indoorSurveyState = SurveyState.RUNNING
                        IndoorSessionManager.surveyRunning = true
                    } else {
                        fragment.stopSurvey()
                        indoorSurveyState = SurveyState.IDLE
                        IndoorSessionManager.surveyRunning = false
                    }
                    updateUnifiedSurveyButtonUi()
                }

                // ================= WIFI =================
                is WifiFragment -> {

                    if (!isRecordingWifiCsv) {
                        if (!validateSelectionsBeforeStart()) return@setOnClickListener

                        val fragment = fragment as WifiFragment

                        fragment.setGroundButtonsVisible(false)
                        fragment.showGroundUiAfterStart()

                        currentWifiSessionId =
                            getNextSessionIdMaxPlusOne("Wifi")

                        wifiCsvBuffer.clear()
                        wifiNeighborCsvBuffer.clear()
                        isRecordingWifiCsv = true
                        beginWifiReportSession()
                        updateUnifiedSurveyButtonUi()

                    } else {
                        isRecordingWifiCsv = false
                        updateUnifiedSurveyButtonUi()

                        if (!canWriteLegacyStorage()) {
                            toast("Storage permission required")
                            return@setOnClickListener
                        }

                        saveWifiCsv(fragment.getCurrentSsid())
                        saveWifiNeighborCsv()
                        currentWifiSessionId = null

                        // 👁️ โชว์ปุ่มกลับ
                        (fragment as? WifiFragment)?.setGroundButtonsVisible(true)

                        toast("All CSVs saved")
                    }
                }


            }
        }
    }
    fun setGroundButtonsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE

        // ❗ ID ต้องตรงกับปุ่มจริงใน layout
        findViewById<View>(R.id.btnCalibrate)?.visibility = v
        findViewById<View>(R.id.btnReset)?.visibility = v
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

            val techIndex = csvHeader.indexOf("tech")
            if (techIndex >= 0 && row.size > techIndex) {
                servingTechForFileName = row[techIndex]
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
            baroFloor = (startFloor + (h / floorHeightMeters).roundToInt()).toString()
        }

        // ===== GPS =====
        var gpsRelAlt = ""
        var gpsFloor = ""
        if (loc != null && referenceGpsAltitude != null && loc.hasAltitude()) {
            val rel = loc.altitude - referenceGpsAltitude!!
            gpsRelAlt = "%.2f".format(rel)
            gpsFloor = (startFloor + (rel / floorHeightMeters).roundToInt()).toString()
        }

        val reportId = allocateNextWifiServingReportId()
        pendingWifiNeighborSysTime = sysTime

        val row = listOf(
            reportId.toString(),                        // report (sync with WiFi Neighbor)
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

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

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
        } catch (_: Exception) {
            false
        }

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
        val hint = findViewById<TextView>(R.id.startHintText)

        // ถ้ากำลังอัด → START ต้องกดได้ และไม่ต้องมีข้อความเตือน
        if (isRecordingCsv || isRecordingWifiCsv || indoorSurveyState == SurveyState.RUNNING) {
            scanBtn.isEnabled = true
            scanBtn.alpha = 1f
            hint.visibility = View.GONE
            return
        }

        val fragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (fragment) {

            is CellularFragment -> {
                val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

                val gpsOn = try {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                } catch (_: Exception) { false }

                val inService =
                    tm.serviceState?.state == ServiceState.STATE_IN_SERVICE

                when {
                    !gpsOn -> {
                        scanBtn.isEnabled = false
                        scanBtn.alpha = 0.4f
                        hint.text = "Please turn on GPS"
                        hint.visibility = View.VISIBLE
                    }

                    !inService -> {
                        scanBtn.isEnabled = false
                        scanBtn.alpha = 0.4f
                        hint.text = "Cellular service not ready"
                        hint.visibility = View.VISIBLE
                    }

                    else -> {
                        scanBtn.isEnabled = true
                        scanBtn.alpha = 1f
                        hint.visibility = View.GONE
                    }
                }
            }

            is WifiFragment -> {
                val wifiManager =
                    applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

                val wifiOn = wifiManager.isWifiEnabled
                val gpsOn = try {
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                } catch (_: Exception) { false }
                val info = wifiManager.connectionInfo
                val isConnected = info != null && info.networkId != -1

                when {
                    !wifiOn -> {
                        scanBtn.isEnabled = false
                        scanBtn.alpha = 0.4f
                        hint.text = "Please turn on Wi-Fi"
                        hint.visibility = View.VISIBLE
                    }

                    !gpsOn -> {
                        scanBtn.isEnabled = false
                        scanBtn.alpha = 0.4f
                        hint.text = "Please turn on GPS"
                        hint.visibility = View.VISIBLE
                    }
                    !isConnected -> {
                        scanBtn.isEnabled = false
                        scanBtn.alpha = 0.4f
                        hint.text = "Please connect to Wi-Fi"
                        hint.visibility = View.VISIBLE
                    }


                    else -> {
                        scanBtn.isEnabled = true
                        scanBtn.alpha = 1f
                        hint.visibility = View.GONE
                    }
                }
            }

            else -> {
                scanBtn.isEnabled = false
                scanBtn.alpha = 0.4f
                hint.visibility = View.GONE
            }
        }
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

        if (wifiCsvBuffer.isEmpty()) return

        val sessionId = currentWifiSessionId ?: return
        val timestamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val safeSsid = ssid.replace(Regex("[^a-zA-Z0-9_]"), "_")

        val fileName =
            "Session_${sessionId}_WIFI_SERV_${safeSsid}_$timestamp.csv"

        saveCsv(fileName, wifiCsvHeader, wifiCsvBuffer, "Wifi")
    }
    private fun saveWifiNeighborCsv() {
        if (wifiNeighborCsvBuffer.isEmpty()) {
            toast("No WiFi Neighbor data to save")
            return
        }

        val sessionId = currentWifiSessionId ?: return
        val timestamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName =
            "Session_${sessionId}_WIFI_NEI_$timestamp.csv"
        saveCsv(
            fileName = fileName,
            header = wifiNeighborHeader,
            rows = wifiNeighborCsvBuffer,
            subDir = "Wifi"
        )

        wifiNeighborCsvBuffer.clear()
    }


    private fun saveCellularCsv() {
        val sessionId = currentCellularSessionId ?: return
        val timestamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName =
            "Session_${sessionId}_CELL_SERV_$timestamp.csv"
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
                        "Download/DriveTest/${currentDriveMode.folderName}/$subDir"
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
                    "DriveTest/${currentDriveMode.folderName}/$subDir"
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



            toast("Saved: Download/DriveTest/${currentDriveMode.folderName}/$subDir/$fileName")

        } catch (e: Exception) {
            e.printStackTrace()
            toast("Save error: ${e.message}")
        }
    }



    // ================== UTILS ==================
    fun calibrateAltitude(selectedStartFloor: Int) {
        if (currentFilteredPressure > 0) {
            referencePressure = currentFilteredPressure
        }
        if (latestLocation?.hasAltitude() == true) {
            referenceGpsAltitude = latestLocation!!.altitude
        }
        startFloor = selectedStartFloor
        hasSelectedStartFloor = true
        isGroundSet = true
        updateFloorButtonLabel()
    }
    //เพิ่มฟังก์ชันตรวจ LTE CA
    @SuppressLint("MissingPermission")
    fun isLteCaActiveCompat(): Boolean {
        // 1. ดึง TelephonyManager เพื่อเข้าถึงข้อมูลเครือข่าย
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        // 2. ดึง ServiceState (สถานะการบริการปัจจุบัน) ถ้าไม่มีค่า (null) ให้จบและตอบ false
        val ss = tm.serviceState ?: return false

        return try {
            // --- ส่วนที่ 3: ตรวจสอบตามเวอร์ชัน Android ---
            // กรณี Android 10 (Q) ขึ้นไป (Android 10, 11, 12, 13, 14+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 3.1 ค้นหาฟังก์ชันชื่อ "isUsingCarrierAggregation" ในคลาส ServiceState
                val m = ServiceState::class.java.getMethod("isUsingCarrierAggregation")
                // 3.2 สั่งรันฟังก์ชันนั้น แล้วแปลงผลลัพธ์เป็น Boolean
                m.invoke(ss) as Boolean
            }
            // กรณี Android 8 (O) ถึง 9 (P)
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 4.1 ค้นหาตัวแปร (Field) ที่ซ่อนอยู่ชื่อ "mIsUsingCarrierAggregation"
                val f = ss.javaClass.getDeclaredField("mIsUsingCarrierAggregation")
                // 4.2 อนุญาตให้เข้าถึงตัวแปรนี้ได้ (แม้ว่ามันจะเป็น private)
                f.isAccessible = true
                // 4.3 ดึงค่า boolean ออกมาจากตัวแปรนั้น
                f.getBoolean(ss)
            }
            // กรณี Android 7 ลงไป (ไม่รองรับการเช็คด้วยวิธีนี้
            else {
                false
            }
        } catch (e: Exception) {
            // หากเกิด error (เช่น หาชื่อฟังก์ชันไม่เจอใน ROM บางยี่ห้อ) ให้ตอบ false
            false
        }
    }
    @SuppressLint("MissingPermission")
    fun getLteCaCount(): Int {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.allCellInfo
            ?.count { it is android.telephony.CellInfoLte && it.isRegistered }
            ?: 0
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
//เพิ่มฟังก์ชันหา Session ใหม่
    fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
    fun getNextSessionIdMaxPlusOne(subDir: String): Int {

        val baseDir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ),
            "DriveTest/${currentDriveMode.folderName}/$subDir"
        )

        if (!baseDir.exists()) return 1

        var maxSession = 0

        baseDir.listFiles()?.forEach { file ->
            val regex = Regex("""Session_(\d+)_""")
            val match = regex.find(file.name)
            match?.groupValues?.get(1)?.toIntOrNull()?.let { session ->
                if (session > maxSession) {
                    maxSession = session
                }
            }
        }

        return maxSession + 1
    }

}
