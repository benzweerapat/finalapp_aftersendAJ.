package com.example.myapplication2

import android.content.Context
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
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
    private var detailsContainer: FrameLayout? = null

    private var currentMode = IndoorSessionManager.RadioMode.CELLULAR
    private var currentCellDetail: CellDetail? = null
    private var currentWifiDetail: WifiDetail? = null
    private var expandedItemId: Long? = null
    private val transition = AutoTransition().apply { duration = 180 }

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
        detailsContainer = view.findViewById(R.id.detailsContainer)
        detailsContainer?.id = View.generateViewId()

        view.findViewById<Button>(R.id.btnEndSurvey).setOnClickListener {
            listener?.onEndSurveyClicked()
        }
        btnToggle?.setOnClickListener {
            toggleInlineDetails()
        }
        refreshToggleUi()
    }

    fun updateSignal(main: String, sub: String) {
        textSignalMain?.text = main
        textSignalSub?.text = sub
    }

    fun updatePointCount(count: Int) {
        textPointCount?.text = "Points: $count"
    }

    fun setMode(mode: IndoorSessionManager.RadioMode) {
        if (currentMode == mode) return
        currentMode = mode
        if (expandedItemId != null) {
            expandedItemId = null
            removeInlineDetails()
            refreshToggleUi()
        }
    }

    fun updateCellDetail(detail: CellDetail) {
        currentCellDetail = detail
    }

    fun updateWifiDetail(detail: WifiDetail) {
        currentWifiDetail = detail
    }

    private fun toggleInlineDetails() {
        val itemId = currentMode.ordinal.toLong()
        if (expandedItemId == itemId) {
            expandedItemId = null
            removeInlineDetails()
        } else {
            expandedItemId = itemId
            showInlineDetails()
        }
        refreshToggleUi()
    }

    private fun showInlineDetails() {
        val container = detailsContainer ?: return
        val containerId = container.id
        if (containerId == View.NO_ID) return

        val fragment = when (currentMode) {
            IndoorSessionManager.RadioMode.CELLULAR -> {
                val c = currentCellDetail
                CellularInlineDetailsFragment.newInstance(
                    CellularInlineDetailData(
                        tech = c?.tech ?: "-",
                        operatorName = c?.operatorName ?: "-",
                        rsrp = c?.rsrp ?: -999,
                        rsrq = c?.rsrq ?: -999,
                        arfcn = c?.arfcn ?: "-",
                        freqBw = c?.freqBw ?: "-",
                        pci = c?.pci ?: "-",
                        tac = c?.tac ?: "-",
                        cellId = c?.cellId ?: "-",
                        latitude = c?.latitude ?: "-",
                        longitude = c?.longitude ?: "-"
                    )
                )
            }
            IndoorSessionManager.RadioMode.WIFI -> {
                val w = currentWifiDetail
                WifiInlineDetailsFragment.newInstance(
                    WifiInlineDetailData(
                        ssid = w?.ssid ?: "-",
                        rssi = w?.rssi ?: -999,
                        frequency = w?.freq ?: "-",
                        channel = w?.channel ?: "-",
                        linkSpeed = w?.linkSpeed ?: "-",
                        security = w?.security ?: "-",
                        bssid = w?.bssid ?: "-",
                        latitude = w?.latitude ?: "-",
                        longitude = w?.longitude ?: "-"
                    )
                )
            }
        }

        TransitionManager.beginDelayedTransition(view as? ViewGroup ?: return, transition)
        container.isVisible = true
        childFragmentManager.beginTransaction()
            .replace(containerId, fragment, "inline_detail_${currentMode.name}")
            .commitNowAllowingStateLoss()
    }

    private fun removeInlineDetails() {
        val container = detailsContainer ?: return
        val id = container.id
        if (id == View.NO_ID) return

        childFragmentManager.findFragmentById(id)?.let {
            childFragmentManager.beginTransaction()
                .remove(it)
                .commitNowAllowingStateLoss()
        }
        TransitionManager.beginDelayedTransition(view as? ViewGroup ?: return, transition)
        container.isVisible = false
    }

    private fun refreshToggleUi() {
        val expanded = expandedItemId == currentMode.ordinal.toLong()
        btnToggle?.text = if (expanded) "ย่อข้อมูล ▲" else "ขยายข้อมูล ▼"
    }
}
