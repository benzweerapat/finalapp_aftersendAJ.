package com.example.myapplication2

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class IndoorSignalPanelFragment : Fragment(R.layout.fragment_indoor_signal_panel) {

    interface Listener {
        fun onEndSurveyClicked()
    }

    data class CellDetail(
        val tech: String,
        val operatorName: String,
        val rsrp: Int,
        val rsrq: Int,
        val arfcn: String,
        val freqBw: String,
        val cellId: String
    )

    data class WifiDetail(
        val ssid: String,
        val rssi: Int,
        val freq: String,
        val channel: String,
        val bw: String,
        val linkSpeed: String,
        val security: String,
        val mac: String
    )

    private var listener: Listener? = null
    private var textSignalMain: TextView? = null
    private var textSignalSub: TextView? = null
    private var textPointCount: TextView? = null
    private var btnToggle: Button? = null

    private var cellContainer: LinearLayout? = null
    private var wifiContainer: LinearLayout? = null
    private var isExpanded = false
    private var currentMode = IndoorSessionManager.RadioMode.CELLULAR

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

        cellContainer = view.findViewById(R.id.cellDetailContainer)
        wifiContainer = view.findViewById(R.id.wifiDetailContainer)

        view.findViewById<Button>(R.id.btnEndSurvey).setOnClickListener {
            listener?.onEndSurveyClicked()
        }
        btnToggle?.setOnClickListener {
            isExpanded = !isExpanded
            updateExpandUi()
        }
        updateExpandUi()
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
        val showCell = mode == IndoorSessionManager.RadioMode.CELLULAR
        cellContainer?.visibility = if (isExpanded && showCell) View.VISIBLE else View.GONE
        wifiContainer?.visibility = if (isExpanded && !showCell) View.VISIBLE else View.GONE
    }

    fun updateCellDetail(detail: CellDetail) {
        view?.findViewById<TextView>(R.id.cellTechLabel)?.text = detail.tech
        view?.findViewById<TextView>(R.id.cellOperatorValue)?.text = detail.operatorName
        view?.findViewById<TextView>(R.id.cellRsrpValue)?.text = "${detail.rsrp} dBm"
        view?.findViewById<TextView>(R.id.cellRsrqValue)?.text = "${detail.rsrq} dB"
        view?.findViewById<TextView>(R.id.cellArfcnValue)?.text = detail.arfcn
        view?.findViewById<TextView>(R.id.cellFreqBwValue)?.text = detail.freqBw
        view?.findViewById<TextView>(R.id.cellIdBlock)?.text = detail.cellId
    }

    fun updateWifiDetail(detail: WifiDetail) {
        view?.findViewById<TextView>(R.id.wifiSsidValue)?.text = detail.ssid
        view?.findViewById<TextView>(R.id.wifiRssiValue)?.text = "${detail.rssi} dBm"
        view?.findViewById<TextView>(R.id.wifiFreqValue)?.text = detail.freq
        view?.findViewById<TextView>(R.id.wifiChannelValue)?.text = detail.channel
        view?.findViewById<TextView>(R.id.wifiBwValue)?.text = detail.bw
        view?.findViewById<TextView>(R.id.wifiLinkSpeedValue)?.text = detail.linkSpeed
        view?.findViewById<TextView>(R.id.wifiSecurityValue)?.text = detail.security
        view?.findViewById<TextView>(R.id.wifiMacValue)?.text = detail.mac
    }

    private fun updateExpandUi() {
        btnToggle?.text = if (isExpanded) "ย่อข้อมูล" else "ขยายข้อมูล"
        setMode(currentMode)
    }
}
