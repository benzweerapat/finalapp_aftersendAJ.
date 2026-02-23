package com.example.myapplication2

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class IndoorSignalPanelFragment : Fragment(R.layout.fragment_indoor_signal_panel) {

    interface Listener {
        fun onEndSurveyClicked()
        fun onExpandDetailsRequested(
            mode: IndoorSessionManager.RadioMode,
            cellDetail: CellDetail?,
            wifiDetail: WifiDetail?
        )
    }

    data class CellDetail(
        val tech: String,
        val operatorName: String,
        val rsrp: Int,
        val rsrq: Int,
        val sinr: String,
        val arfcn: String,
        val freqBw: String,
        val pci: String,
        val tac: String,
        val cellId: String,
        val latitude: String,
        val longitude: String,
        val floor: String,
        val relHeight: String,
        val absAltitude: String,
        val pressure: String
    )

    data class WifiDetail(
        val ssid: String,
        val bssid: String,
        val rssi: Int,
        val signalQuality: String,
        val snr: String,
        val freq: String,
        val channel: String,
        val bw: String,
        val linkSpeed: String,
        val security: String,
        val latitude: String,
        val longitude: String,
        val floor: String,
        val relHeight: String,
        val absAltitude: String,
        val pressure: String
    )

    private var listener: Listener? = null
    private var textSignalMain: TextView? = null
    private var textSignalSub: TextView? = null
    private var textPointCount: TextView? = null
    private var btnToggle: Button? = null

    private var currentMode = IndoorSessionManager.RadioMode.CELLULAR
    private var currentCellDetail: CellDetail? = null
    private var currentWifiDetail: WifiDetail? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textSignalMain = view.findViewById(R.id.textSignalMain)
        textSignalSub = view.findViewById(R.id.textSignalSub)
        textPointCount = view.findViewById(R.id.textPointCount)
        btnToggle = view.findViewById(R.id.btnToggleDetails)

        view.findViewById<Button>(R.id.btnEndSurvey).setOnClickListener {
            listener?.onEndSurveyClicked()
        }
        btnToggle?.setOnClickListener {
            listener?.onExpandDetailsRequested(currentMode, currentCellDetail, currentWifiDetail)
        }
        btnToggle?.text = "ขยายข้อมูล"
    }

    fun updateSignal(main: String, sub: String) {
        textSignalMain?.text = main
        textSignalSub?.text = sub
    }

    fun updatePointCount(count: Int) {
        textPointCount?.text = "Points: $count"
    }

    fun setMode(mode: IndoorSessionManager.RadioMode) {
        currentMode = mode
    }

    fun updateCellDetail(detail: CellDetail) {
        currentCellDetail = detail
    }

    fun updateWifiDetail(detail: WifiDetail) {
        currentWifiDetail = detail
    }
}
