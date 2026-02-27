package com.example.myapplication2

import android.os.Bundle
import android.text.TextUtils
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

    private var textSignalMain: TextView? = null
    private var textSignalSub: TextView? = null
    private var textPointCount: TextView? = null
    private var btnToggle: Button? = null
    private var btnAddPoint: Button? = null
    private var btnUndo: Button? = null
    private var btnClear: Button? = null
    private var detailsContainer: FrameLayout? = null

    private var onAddPointClick: (() -> Unit)? = null
    private var onUndoClick: (() -> Unit)? = null
    private var onClearClick: (() -> Unit)? = null

    private var currentMode = IndoorSessionManager.RadioMode.CELLULAR
    private var expandedItemId: Long? = null
    private val transition = AutoTransition().apply { duration = 180 }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textSignalMain = view.findViewById(R.id.textSignalMain)
        textSignalSub = view.findViewById(R.id.textSignalSub)
        textPointCount = view.findViewById(R.id.textPointCount)
        btnToggle = view.findViewById(R.id.btnToggleDetails)
        btnAddPoint = view.findViewById(R.id.btnAddPoint)
        btnUndo = view.findViewById(R.id.btnUndo)
        btnClear = view.findViewById(R.id.btnClear)
        detailsContainer = view.findViewById(R.id.detailsContainer)
        detailsContainer?.id = View.generateViewId()

        textSignalSub?.apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        btnToggle?.setOnClickListener { toggleInlineDetails() }
        btnAddPoint?.setOnClickListener { onAddPointClick?.invoke() }
        btnUndo?.setOnClickListener { onUndoClick?.invoke() }
        btnClear?.setOnClickListener { onClearClick?.invoke() }
        refreshToggleUi()
    }

    fun updateSignal(main: String, sub: String) {
        textSignalMain?.text = main
        textSignalSub?.text = sub
    }

    fun updatePointCount(count: Int) {
        textPointCount?.text = "Points: $count"
    }


    fun setOnAddPointClickListener(listener: (() -> Unit)?) {
        onAddPointClick = listener
    }

    fun setOnUndoClickListener(listener: (() -> Unit)?) {
        onUndoClick = listener
    }

    fun setOnClearClickListener(listener: (() -> Unit)?) {
        onClearClick = listener
    }

    fun setAddPointStartRequiredHint(visible: Boolean) {
        // hint now shown via MainActivity.startHintText for consistent position
    }

    fun setAddPointEnabled(enabled: Boolean) {
        btnAddPoint?.isEnabled = enabled
        btnAddPoint?.alpha = if (enabled) 1f else 0.75f
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
            IndoorSessionManager.RadioMode.CELLULAR -> CellularDetailsFragment()
            IndoorSessionManager.RadioMode.WIFI -> WifiDetailsFragment()
        }

        val maxHeight = (resources.displayMetrics.heightPixels * 0.25f).toInt()
        container.layoutParams = container.layoutParams.apply { height = maxHeight }

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
        container.layoutParams = container.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    }

    private fun refreshToggleUi() {
        val expanded = expandedItemId == currentMode.ordinal.toLong()
        btnToggle?.text = if (expanded) "Hide Details ▲" else "Show Details ▼"
    }
}
