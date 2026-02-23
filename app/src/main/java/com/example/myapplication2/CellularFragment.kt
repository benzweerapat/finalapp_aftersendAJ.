package com.example.myapplication2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import kotlin.toString


open class CellularFragment : Fragment(R.layout.fragment_cellular) {

    private lateinit var mainActivity: MainActivity
    private lateinit var neighborAdapter: NeighborAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val scanIntervalMs = 1000L

    // UI Variables
    private var bandLabel: TextView? = null
    private var techLabel: TextView? = null
    private var operatorValue: TextView? = null
    private var rsrpValue: TextView? = null
    private var rsrqValue: TextView? = null
    private var arfcnValue: TextView? = null
    private var freqBwValue: TextView? = null
    private var cellIdBlock: TextView? = null
    private var latLngValue: TextView? = null

    // Icons
    private var iconSim: ImageView? = null
    private var iconCall: ImageView? = null
    private var iconData: ImageView? = null
    private var iconGps: ImageView? = null

    // Altitude UI
    private var textFloor: TextView? = null
    private var textAltitude: TextView? = null
    private var textPressure: TextView? = null
    private var textGpsFloor: TextView? = null
    private var textGpsRelHeight: TextView? = null
    private var textGpsAltitude: TextView? = null
    // estimated level labels
    private var textBaroEstimated: TextView? = null



    // Extra UI
    private var taValue: TextView? = null
    private var rssnrValue: TextView? = null
    private var bwValue: TextView? = null
    private var eciValue: TextView? = null
    private var enbValue: TextView? = null

    // Buttons
    private var btnCalibrate: View? = null
    private var btnReset: View? = null
    private var btnEditFloorHeight: View? = null
    private var textGpsEstimated: TextView? = null

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (isAdded) {
                val tm = requireContext()
                    .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                updateStatusIcons(tm)

                updateCellularInfo()
                updateLocationAndSensors()

                handler.postDelayed(this, scanIntervalMs)
            }
        }
    }

    //สร้าง Dialog ให้ผู้ใช้เลือกชั้น
    fun showStartFloorDialog(onConfirm: (Int) -> Unit) {

        val floors = arrayOf("1", "2", "3", "4", "5", "Custom")

        AlertDialog.Builder(requireContext())
            .setTitle("Select start floor")
            .setItems(floors) { _, which ->

                if (floors[which] != "Custom") {
                    onConfirm(floors[which].toInt())
                } else {
                    // Custom floor
                    val input = EditText(requireContext())
                    input.inputType =
                        android.text.InputType.TYPE_CLASS_NUMBER

                    AlertDialog.Builder(requireContext())
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


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity = requireActivity() as MainActivity

        // Bind UI Elements
        bandLabel = view.findViewById(R.id.bandLabel)
        techLabel = view.findViewById(R.id.techLabel)
        operatorValue = view.findViewById(R.id.operatorValue)
        rsrpValue = view.findViewById(R.id.rsrpValue)
        rsrqValue = view.findViewById(R.id.rsrqValue)
        arfcnValue = view.findViewById(R.id.arfcnValue)
        freqBwValue = view.findViewById(R.id.freqBwValue)
        cellIdBlock = view.findViewById(R.id.cellIdBlock)
        latLngValue = view.findViewById(R.id.latLngValue)

        iconSim = view.findViewById(R.id.iconSim)
        iconCall = view.findViewById(R.id.iconCall)
        iconData = view.findViewById(R.id.iconData)
        iconGps = view.findViewById(R.id.iconGps)

        textFloor = view.findViewById(R.id.textFloor)
        textAltitude = view.findViewById(R.id.textAltitude)
        textPressure = view.findViewById(R.id.textPressure)
        textGpsFloor = view.findViewById(R.id.textGpsFloor)
        textGpsRelHeight = view.findViewById(R.id.textGpsRelHeight)
        textGpsAltitude = view.findViewById(R.id.textGpsAltitude)

        textBaroEstimated = view.findViewById(R.id.textBaroEstimated)
        textGpsEstimated = view.findViewById(R.id.textGpsEstimated)


        taValue = view.findViewById(R.id.taValue)
        rssnrValue = view.findViewById(R.id.rssnrValue)
        bwValue = view.findViewById(R.id.bwValue)
        eciValue = view.findViewById(R.id.eciValue)
        enbValue = view.findViewById(R.id.enbValue)

        btnCalibrate = view.findViewById(R.id.btnCalibrate)
        btnReset = view.findViewById(R.id.btnReset)
        btnEditFloorHeight = view.findViewById(R.id.btnEditFloorHeight)
        updateEditHeightButtonLabel(mainActivity.getFloorHeightButtonLabel())

        btnCalibrate?.setOnClickListener {

            showStartFloorDialog { startFloor ->

                mainActivity.calibrateAltitude(startFloor)

                // Barometer
                textAltitude?.visibility = View.VISIBLE
                textFloor?.visibility = View.VISIBLE
                textBaroEstimated?.visibility = View.VISIBLE
                textGpsEstimated?.visibility = View.VISIBLE

                // GPS
                textGpsRelHeight?.visibility = View.VISIBLE
                textGpsFloor?.visibility = View.VISIBLE

                mainActivity.toast("Start floor = $startFloor")
            }
        }

        btnReset?.setOnClickListener {
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

        btnEditFloorHeight?.setOnClickListener {
            showEditDialog()
        }

        val rv = view.findViewById<RecyclerView>(R.id.neighborsRecycler)
        rv.layoutManager = LinearLayoutManager(context)
        neighborAdapter = NeighborAdapter()
        rv.adapter = neighborAdapter

        setButtonsState(mainActivity.isRecordingCsv)
    }

    fun setButtonsState(isRecording: Boolean) {
        val alpha = if (isRecording) 0.5f else 1.0f
        val enabled = !isRecording
        btnCalibrate?.isEnabled = enabled
        btnCalibrate?.alpha = alpha
        btnReset?.isEnabled = enabled
        btnReset?.alpha = alpha
        btnEditFloorHeight?.isEnabled = enabled
        btnEditFloorHeight?.alpha = alpha
    }

    fun setGroundButtonsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        btnCalibrate?.visibility = v
        btnReset?.visibility = v
        btnEditFloorHeight?.visibility = v
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


    private fun showEditDialog() {
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(mainActivity.floorHeightMeters.toString())
        AlertDialog.Builder(requireContext())
            .setTitle("Floor Height (m)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().toFloatOrNull()
                if (v != null && v > 0) {
                    mainActivity.onFloorHeightSelected(v)
                    mainActivity.toast("Saved: $v m")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun updateEditHeightButtonLabel(label: String) {
        (btnEditFloorHeight as? TextView)?.text = label
    }

    override fun onResume() {
        super.onResume()
        updateStatusIcons(
            requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        )
        handler.post(scanRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(scanRunnable)
    }


    private fun applyServingRsrpColor(rsrp: Int?) {
        rsrpValue?.setTextColor(
            when {
                rsrp == null -> Color.WHITE
                else -> colorForRsrp(rsrp)
            }
        )
    }

    private fun resetUi() {
        bandLabel?.text = "—"
        techLabel?.text = "—"
        operatorValue?.text = "—"
        rsrpValue?.text = "—"
        applyServingRsrpColor(null)
        rsrqValue?.text = "—"
        arfcnValue?.text = "—"
        freqBwValue?.text = "—"
        cellIdBlock?.text = "—"
        latLngValue?.text = "— / —"
        taValue?.text = "TA: -"
        rssnrValue?.text = "SINR: -"
        bwValue?.text = "BW: -"
        eciValue?.text = "ECI: -"
        enbValue?.text = "eNB CI: -"
        textPressure?.text = "Pressure: --"
        textAltitude?.text = "Rel. Height: -"
        textFloor?.text = "Floor: -"
        textGpsAltitude?.text = "Abs. Alt: -"
        textGpsRelHeight?.text = "Rel. Height: -"
        textGpsFloor?.text = "Floor: -"
        neighborAdapter.setData(emptyList())

    }

    private fun updateLocationAndSensors() {
        val loc = mainActivity.latestLocation
        latLngValue?.text = if (loc != null) "%.5f / %.5f".format(loc.latitude, loc.longitude) else "— / —"
        val press = mainActivity.currentFilteredPressure
        textPressure?.text = "Pressure: %.2f hPa".format(press)

        var baroRelStr = "—"; var baroFloorStr = "—"
        var gpsRelStr = "—"; var gpsFloorStr = "—"

        if (mainActivity.referencePressure != -1f && press > 0) {
            val pRatio = press / mainActivity.referencePressure
            val h = 44330 * (1 - Math.pow(pRatio.toDouble(), 1/5.255)).toFloat()
            val floor =
                mainActivity.startFloor + (h / mainActivity.floorHeightMeters).roundToInt()
            baroRelStr = "%.2f".format(h)
            baroFloorStr = floor.toString()
            textAltitude?.text = "Rel. Height: $baroRelStr m"
            textFloor?.text = "Floor: $baroFloorStr"
        } else {
            textAltitude?.text = "Rel. Height: -"; textFloor?.text = "Floor: -"
        }

        if (loc != null && loc.hasAltitude()) {
            textGpsAltitude?.text = "Abs. Alt: %.1f m".format(loc.altitude)
            if (mainActivity.referenceGpsAltitude != null) {
                val rel = loc.altitude - mainActivity.referenceGpsAltitude!!
                val floor =
                    mainActivity.startFloor +
                            (rel / mainActivity.floorHeightMeters).roundToInt()
                gpsRelStr = "%.2f".format(rel)
                gpsFloorStr = floor.toString()
                textGpsRelHeight?.text = "Rel. Height: $gpsRelStr m"
                textGpsFloor?.text = "Floor: $gpsFloorStr"
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun updateCellularInfo() {
        val tm = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val allInfo = try { tm.allCellInfo } catch (e: Exception) { null }
        if (allInfo.isNullOrEmpty()) return

        operatorValue?.text = "${tm.simOperatorName} / ${tm.networkOperatorName}"

        var techCsv = "—"; var bandCsv = "—"; var arfcnStr = "—"
        var rsrpStr = "—"; var rsrqStr = "—"; var rssnrStr = "—"
        var mcc = "—"; var mnc = "—"; var lacTac = "—"; var longCid = "—"; var nodeIdNid = "—"; var cidBid = "—"; var pscPci = "—"
        var nrTac = "—"; var nrNci = "—"; var nrPci = "—"; var nrArfcnStr = "—"
        var nrssRsrp = "—"; var nrssRsrq = "—"; var nrssSinr = "—"
        var taStr = "—"; var bwStr = "—"

        var servingFound = false
        val neighborList = ArrayList<NeighborItem>()
        var idx = 1

        for (ci in allInfo) {
            if (ci.isRegistered && !servingFound) {
                servingFound = true
                if (ci is CellInfoLte) {
                    val id = ci.cellIdentity
                    val ss = ci.cellSignalStrength
                    val earfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) id.earfcn else null

                    val isCa = mainActivity.isLteCaActiveCompat()

                    // 🔹 tech = ระบบ
                    techLabel?.text =
                        if (isCa) "LTE CA"
                        else "LTE"

                    // 🔹 band = คลื่น
                    bandLabel?.text =
                        if (isCa) "${lteBandLabel(earfcn)}"
                        else lteBandLabel(earfcn)


                    val servingRsrp = ss.rsrp.takeIf { it != Int.MAX_VALUE }
                    rsrpValue?.text = servingRsrp?.let { "$it dBm" } ?: "—"
                    applyServingRsrpColor(servingRsrp)
                    rsrqValue?.text = "${ss.rsrq} dB"
                    arfcnValue?.text = earfcn?.toString() ?: "—"

                    val ta = ss.timingAdvance.takeIf { it in 0..Int.MAX_VALUE }?.toString() ?: "—"
                    taValue?.text = "TA: $ta"
                    taStr = ta

                    val rssnr = ss.rssnr.takeIf { it != Int.MAX_VALUE }?.let { "%.1f".format(it / 10.0) } ?: "—"
                    rssnrValue?.text = "SINR: $rssnr dB"
                    rssnrStr = rssnr

                    val eci = id.ci.takeIf { it != Int.MAX_VALUE }
                    eciValue?.text = "ECI: ${eci ?: "—"}"
                    enbValue?.text = "eNB CI: ${if (eci != null) "${eci / 256} : ${eci % 256}" else "—"}"

                    val bwInt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.bandwidth else null
                    val bwStrVal = bwInt?.takeIf { it > 0 }?.let { "${it / 1000} MHz" } ?: "—"
                    bwValue?.text = "BW: $bwStrVal"
                    bwStr = bwStrVal

                    val freqMhz = earfcn?.let { lteDlFreqMhz(it) }
                    freqBwValue?.text = joinFreqBw(freqMhz, bwInt)

                    val (mccStr, mncStr) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        Pair(id.mccString ?: "—", id.mncString ?: "—") else fallbackMccMnc(tm)
                    cellIdBlock?.text = "MCC MNC: $mccStr $mncStr  •  TAC: ${id.tac}  •  PCI: ${id.pci}"

                    techCsv =
                        if (mainActivity.isLteCaActiveCompat()) "LTE_CA"
                        else "LTE"
                    bandCsv = lteBandNumber(earfcn)
                    rsrpStr = "${ss.rsrp}"; rsrqStr = "${ss.rsrq}"; arfcnStr = "${earfcn ?: ""}"
                    mcc = mccStr; mnc = mncStr
                    lacTac = "${id.tac}"; longCid = "${id.ci}"; pscPci = "${id.pci}"
                    if (eci != null) { nodeIdNid = "${eci/256}"; cidBid = "${eci%256}" }

                } else if (ci is CellInfoNr) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val id = ci.cellIdentity as CellIdentityNr
                        val ss = ci.cellSignalStrength as CellSignalStrengthNr
                        val nrarfcn = id.nrarfcn.takeIf { it != Int.MAX_VALUE }

                        techLabel?.text = nrBandLabel(nrarfcn)
                        bandLabel?.text = "NR"
                        val servingRsrp = ss.ssRsrp.takeIf { it != Int.MAX_VALUE }
                        rsrpValue?.text = servingRsrp?.let { "$it dBm" } ?: "—"
                        applyServingRsrpColor(servingRsrp)
                        rsrqValue?.text = "${ss.ssRsrq} dB"
                        arfcnValue?.text = nrarfcn?.toString() ?: "—"

                        val sinr = ss.ssSinr.takeIf { it != Int.MAX_VALUE }?.let { "%.1f".format(it / 1.0) } ?: "—"
                        rssnrValue?.text = "SINR: $sinr dB"
                        taValue?.text = "TA: —"; bwValue?.text = "BW: —"

                        val nci = id.nci.takeIf { it != Long.MAX_VALUE }
                        eciValue?.text = "NCI: ${nci ?: "—"}"
                        enbValue?.text = "gNB CI: ${if (nci != null) "${nci shr 8} : ${nci and 0xFF}" else "—"}"

                        val freqMhz = nrarfcn?.let { nrDlFreqMhz(it) }
                        freqBwValue?.text = joinFreqBw(freqMhz, null)

                        val (mccStr, mncStr) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            Pair(id.mccString ?: "—", id.mncString ?: "—") else fallbackMccMnc(tm)
                        cellIdBlock?.text = "MCC MNC: $mccStr $mncStr  •  TAC: ${id.tac}  •  PCI: ${id.pci}"

                        techCsv = "NR"; bandCsv = nrBandNumber(nrarfcn)
                        nrssRsrp = "${ss.ssRsrp}"; nrssRsrq = "${ss.ssRsrq}"; nrssSinr = sinr; nrArfcnStr = "${nrarfcn ?: ""}"
                        mcc = mccStr; mnc = mncStr
                        nrTac = "${id.tac}"; nrNci = "$nci"; nrPci = "${id.pci}"; pscPci = nrPci
                        if (nci != null) { nodeIdNid = "${nci shr 8}"; cidBid = "${nci and 0xFF}" }
                    }
                }
            }

            if (!ci.isRegistered || (ci.isRegistered && servingFound && ci != allInfo[0])) {
                when (ci) {
                    is CellInfoLte -> {
                        val id = ci.cellIdentity
                        val ss = ci.cellSignalStrength
                        val ear = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) id.earfcn else null
                        val rsrp = ss.rsrp.takeIf { it != Int.MAX_VALUE }
                        val pci = id.pci.takeIf { it != Int.MAX_VALUE }?.toString() ?: "—"
                        neighborList.add(NeighborItem(idx++, lteBandLabel(ear), ear?.toString() ?: "—", rsrp, pci))
                    }
                    is CellInfoNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val id = ci.cellIdentity as CellIdentityNr
                            val ss = ci.cellSignalStrength as CellSignalStrengthNr
                            val nrar = id.nrarfcn.takeIf { it != Int.MAX_VALUE }
                            val rsrp = ss.ssRsrp.takeIf { it != Int.MAX_VALUE }
                            val pci = id.pci.takeIf { it != Int.MAX_VALUE }?.toString() ?: "—"
                            neighborList.add(NeighborItem(idx++, nrBandLabel(nrar), nrar?.toString() ?: "—", rsrp, pci))
                        }
                    }
                }
            }
        }
        neighborAdapter.setData(neighborList)
        updateStatusIcons(tm)
        if (mainActivity.isRecordingCsv && servingFound) {

            val (reportId, sysTime) =
                mainActivity.allocateNextCellularServingReport()

            val loc = mainActivity.latestLocation
            val serviceState = try { tm.serviceState } catch (_: Exception) { null }

            val simState = simStateName(tm.simState)
            val svcState = svcStateName(serviceState?.state ?: -1)
            val nrState = nrStateNameCompat(serviceState)
            val roaming = if (serviceState?.roaming == true) "1" else "0"
            val callState = callStateName(tm.callState)
            val dataState = dataStateName(tm.dataState)
            val dataAct =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    netTypeName(tm.dataNetworkType)
                else netTypeName(tm.networkType)

            val row = mutableListOf<String>()

            row.add(reportId.toString())
            row.add(sysTime)

            row.add(simState)
            row.add(svcState)
            row.add(nrState)
            row.add(tm.networkOperatorName ?: "")
            row.add(tm.networkOperator ?: "")
            row.add(roaming)
            row.add(dataAct)
            row.add(callState)
            row.add(dataState)
            row.add(if(techCsv=="LTE") rsrpStr else if(techCsv=="NR") nrssRsrp else "")
            row.add(techCsv)
            row.add(mcc)
            row.add(mnc)
            row.add(mnc)
            row.add(lacTac)
            row.add(longCid)
            row.add(nodeIdNid)
            row.add(cidBid)
            row.add(pscPci)
            row.add(nrTac)
            row.add(nrNci)
            row.add(nrPci)
            row.add(nrArfcnStr)
            row.add(if(techCsv=="LTE") rsrpStr else "")
            row.add(if(techCsv=="LTE") rsrqStr else "")
            row.add(rssnrStr)
            row.add(nrssRsrp)
            row.add(nrssRsrq)
            row.add(nrssSinr)
            row.add(taStr)
            row.add(if (loc!=null) "1" else "0")
            row.add(loc?.accuracy?.toString() ?: "")
            row.add(loc?.latitude?.toString() ?: "")
            row.add(loc?.longitude?.toString() ?: "")
            row.add(loc?.altitude?.toString() ?: "")
            row.add(loc?.speed?.toString() ?: "")
            row.add(loc?.bearing?.toString() ?: "")
            row.add(bandCsv)
            row.add(arfcnStr)
            row.add(bwStr)
            row.add("%.2f".format(mainActivity.currentFilteredPressure))
            row.add(textAltitude?.text.toString().replace("Rel. Height: ","").replace(" m",""))
            row.add(textFloor?.text.toString().replace("Floor: ",""))
            row.add(textGpsRelHeight?.text.toString().replace("Rel. Height: ","").replace(" m",""))
            row.add(textGpsFloor?.text.toString().replace("Floor: ",""))

            mainActivity.addCsvRow(row)
            var nIdx = 1

            for (ci in allInfo) {
                if (!ci.isRegistered) {

                    val nRow = mutableListOf<String>()

                    nRow.add(nIdx.toString()) // neighbor_index
                    nRow.add(techCsv)
                    nRow.add(arfcnStr)
                    nRow.add(pscPci)
                    nRow.add(longCid)

                    when (ci) {

                        is CellInfoLte -> {
                            val id = ci.cellIdentity
                            val ss = ci.cellSignalStrength

                            nRow.add("LTE")
                            nRow.add(id.earfcn.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(id.pci.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(ss.rsrp.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(ss.rsrq.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(
                                ss.rssnr.takeIf { it != Int.MAX_VALUE }
                                    ?.let { "%.1f".format(it / 10.0) } ?: ""
                            )
                        }

                        is CellInfoNr -> {
                            val id = ci.cellIdentity as CellIdentityNr
                            val ss = ci.cellSignalStrength as CellSignalStrengthNr

                            nRow.add("NR")
                            nRow.add(id.nrarfcn.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(id.pci.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(ss.ssRsrp.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(ss.ssRsrq.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                            nRow.add(ss.ssSinr.takeIf { it != Int.MAX_VALUE }?.toString() ?: "")
                        }
                    }

                    nRow.add(loc?.latitude?.toString() ?: "")
                    nRow.add(loc?.longitude?.toString() ?: "")

                    mainActivity.addCellularNeighborRowSafe(nRow)

                    nIdx++
                }
            }

            mainActivity.flushPendingCellularNeighbors()
        }


    }

    // --- Helper Functions ---
    private val COL_RED    = Color.parseColor("#EF4444")
    private val COL_YELLOW = Color.parseColor("#FACC15")
    private val COL_GREEN  = Color.parseColor("#22C55E")
    private val COL_GRAY   = Color.parseColor("#9FB3C8")


    private fun setIcon(iv: ImageView?, @DrawableRes drawableRes: Int, color: Int) {
        iv?.setImageResource(drawableRes)
        iv?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun isAirplaneOn(): Boolean = try {
        android.provider.Settings.Global.getInt(requireContext().contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (_: Exception) { false }


    @SuppressLint("MissingPermission")
    private fun updateStatusIcons(tm: TelephonyManager) {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val radioOff = isAirplaneOn() || (try { tm.serviceState?.state == ServiceState.STATE_POWER_OFF } catch(_:Exception){false})
        val outOfService = (try { tm.serviceState?.state == ServiceState.STATE_OUT_OF_SERVICE } catch(_:Exception){false})

        val simColor = when {
            radioOff      -> COL_GRAY
            outOfService  -> COL_RED
            tm.simState == TelephonyManager.SIM_STATE_READY -> COL_GREEN
            else          -> COL_YELLOW
        }
        setIcon(iconSim, R.drawable.ic_sim, simColor)

        setIcon(iconCall, R.drawable.ic_call, if (radioOff) COL_GRAY else if (tm.callState == TelephonyManager.CALL_STATE_IDLE) COL_GREEN else COL_YELLOW)

        val dataColor = if (radioOff) COL_GRAY else when (tm.dataState) {
            TelephonyManager.DATA_CONNECTED -> COL_GREEN
            TelephonyManager.DATA_CONNECTING -> COL_YELLOW
            else -> COL_RED
        }
        setIcon(iconData, R.drawable.ic_data, dataColor)

        val gpsOn = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
        setIcon(iconGps, R.drawable.ic_gps, if (gpsOn) COL_GREEN else COL_RED)
    }

    private fun fallbackMccMnc(tm: TelephonyManager): Pair<String, String> {
        val op = tm.networkOperator ?: ""
        return if (op.length >= 5) op.substring(0,3) to op.substring(3) else "—" to "—"
    }

    private fun joinFreqBw(freqMhz: Double?, bwKhz: Int?): String {
        val f = freqMhz?.let { "%.1f MHz".format(it) }
        val b = bwKhz?.takeIf { it > 0 }?.let { "${it / 1000} MHz" }
        return if (f != null && b != null) "$f / $b" else f ?: b ?: "—"
    }

    private fun colorForRsrp(rsrp: Int): Int = when {
        rsrp >= -85  -> COL_GREEN
        rsrp >= -100 -> COL_YELLOW
        rsrp >= -110 -> Color.parseColor("#E67E22")
        else         -> COL_RED
    }

    private fun lteDlFreqMhz(earfcn: Int): Double? {
        return when(earfcn) {
            in 0..599 -> 2110.0 + 0.1 * (earfcn - 0)
            in 1200..1949 -> 1805.0 + 0.1 * (earfcn - 1200)
            in 2750..3449 -> 2620.0 + 0.1 * (earfcn - 2750)
            in 3450..3799 -> 925.0 + 0.1 * (earfcn - 3450) // [เพิ่ม] สูตรคำนวณ Band 8
            else -> null
        }
    }

    private fun nrDlFreqMhz(nrarfcn: Int): Double = nrarfcn * 0.005

    private fun lteBandLabel(earfcn: Int?): String = when (earfcn) {
        null -> "LTE"
        in 0..599 -> "L2100"
        in 1200..1949 -> "L1800"
        in 2750..3449 -> "L2600"
        in 3450..3799 -> "L900" // [เพิ่ม] Band 8
        in 38650..39649 -> "L2300" // (เผื่อไว้สำหรับ NT)
        in 39650..41589 -> "L2500" // (เผื่อไว้)
        else -> "LTE"
    }

    private fun nrBandLabel(nrarfcn: Int?): String = when (nrarfcn) {
        null -> "NR"
        in 499200..537999 -> "N2600"
        in 620000..680000 -> "N3500"
        else -> "NR"
    }

    private fun lteBandNumber(earfcn: Int?): String = when (earfcn) {
        null -> "—"
        in 0..599 -> "2100"
        in 1200..1949 -> "1800"
        in 2750..3449 -> "2600"
        in 3450..3799 -> "900" // [เพิ่ม] Band 8
        in 38650..39649 -> "2300"
        in 39650..41589 -> "2500"
        else -> "—"
    }

    private fun nrBandNumber(nrarfcn: Int?): String = when (nrarfcn) {
        null -> "—"
        in 499200..537999 -> "2600"
        in 620000..680000 -> "3500"
        else -> "—"
    }

    // Helper names (กู้คืนมาแล้ว)
    private fun simStateName(v: Int) = when (v) {
        TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
        TelephonyManager.SIM_STATE_READY -> "READY"
        else -> "OTHER"
    }
    private fun svcStateName(v: Int) = when (v) {
        ServiceState.STATE_IN_SERVICE -> "IN_SERVICE"
        ServiceState.STATE_OUT_OF_SERVICE -> "OUT"
        ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY"
        ServiceState.STATE_POWER_OFF -> "POWER_OFF"
        else -> "UNKNOWN"
    }
    private fun callStateName(v: Int) = when (v) {
        TelephonyManager.CALL_STATE_IDLE -> "IDLE"
        TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
        TelephonyManager.CALL_STATE_RINGING -> "RINGING"
        else -> "UNKNOWN"
    }
    private fun dataStateName(v: Int) = when (v) {
        TelephonyManager.DATA_CONNECTED -> "CONNECTED"
        TelephonyManager.DATA_CONNECTING -> "CONNECTING"
        TelephonyManager.DATA_DISCONNECTED -> "DISCONNECTED"
        TelephonyManager.DATA_SUSPENDED -> "SUSPENDED"
        else -> "UNKNOWN"
    }
    private fun netTypeName(v: Int) = when (v) {
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        else -> v.toString()
    }
    private fun nrStateNameCompat(ss: ServiceState?): String {
        if (ss == null) return "UNKNOWN"
        return try {
            val m = ServiceState::class.java.getMethod("getNrState")
            val state = (m.invoke(ss) as? Int) ?: -1
            if (state == 3) "CONNECTED" else "UNKNOWN"
        } catch (_: Exception) { "UNKNOWN" }
    }
}