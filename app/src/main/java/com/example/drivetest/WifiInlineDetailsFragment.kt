package com.example.drivetest

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

class WifiInlineDetailsFragment : Fragment(R.layout.fragment_wifi_inline_details) {

    companion object {
        private const val ARG_DATA = "arg_wifi_inline_data"

        fun newInstance(data: WifiInlineDetailData): WifiInlineDetailsFragment {
            return WifiInlineDetailsFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_DATA, data) }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnReset)?.visibility = View.GONE
        val btn = view.findViewById<View>(R.id.btnCalibrate)

        view.findViewById<View>(R.id.btnCalibrate)?.visibility = View.GONE
        val data = arguments?.getSerializable(ARG_DATA) as? WifiInlineDetailData ?: WifiInlineDetailData()

        view.findViewById<TextView>(R.id.wifiSsid)?.text = data.ssid
        view.findViewById<TextView>(R.id.wifiRssi)?.text = "${data.rssi} dBm"
        view.findViewById<TextView>(R.id.wifiFreq)?.text = data.frequency
        view.findViewById<TextView>(R.id.wifiChannel)?.text = data.channel
        view.findViewById<TextView>(R.id.wifiLinkSpeed)?.text = data.linkSpeed
        view.findViewById<TextView>(R.id.wifiSecurity)?.text = data.security
        view.findViewById<TextView>(R.id.wifiMac)?.text = data.bssid
        view.findViewById<TextView>(R.id.latLngValue)?.text = "${data.latitude} / ${data.longitude}"
    }
}
