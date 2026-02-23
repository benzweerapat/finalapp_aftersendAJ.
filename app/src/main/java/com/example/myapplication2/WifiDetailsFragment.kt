package com.example.myapplication2

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class WifiDetailsFragment : Fragment(R.layout.fragment_wifi) {

    companion object {
        private const val ARG_DETAIL = "arg_wifi_detail"

        fun newInstance(detail: WifiDetailArgs): WifiDetailsFragment {
            return WifiDetailsFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_DETAIL, detail)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val detail = arguments?.getSerializable(ARG_DETAIL) as? WifiDetailArgs ?: return

        view.findViewById<TextView>(R.id.wifiSsid)?.text = detail.ssid
        view.findViewById<TextView>(R.id.wifiRssi)?.text = "${detail.rssi} dBm"
        view.findViewById<TextView>(R.id.wifiFreq)?.text = detail.freq
        view.findViewById<TextView>(R.id.wifiChannel)?.text = "CH ${detail.channel}"
        view.findViewById<TextView>(R.id.wifiBw)?.text = detail.bw
        view.findViewById<TextView>(R.id.wifiLinkSpeed)?.text = detail.linkSpeed
        view.findViewById<TextView>(R.id.wifiSecurity)?.text = detail.security
        view.findViewById<TextView>(R.id.wifiMac)?.text = detail.bssid
        view.findViewById<TextView>(R.id.wifiSignalQual)?.text = detail.signalQuality
        view.findViewById<TextView>(R.id.wifiSnr)?.text = detail.snr

        view.findViewById<TextView>(R.id.latLngValue)?.text = "${detail.latitude} / ${detail.longitude}"

        view.findViewById<TextView>(R.id.textFloor)?.text = "Floor: ${detail.floor}"
        view.findViewById<TextView>(R.id.textAltitude)?.text = "Rel. Height: ${detail.relHeight}"
        view.findViewById<TextView>(R.id.textPressure)?.text = "Pressure: ${detail.pressure}"
        view.findViewById<TextView>(R.id.textGpsFloor)?.text = "Floor: ${detail.floor}"
        view.findViewById<TextView>(R.id.textGpsRelHeight)?.text = "Rel. Height: ${detail.relHeight}"
        view.findViewById<TextView>(R.id.textGpsAltitude)?.text = "Abs. Alt: ${detail.absAltitude}"

        view.findViewById<View>(R.id.wifiNeighborsRecycler)?.visibility = View.GONE

        view.findViewById<View>(R.id.btnCalibrate)?.setOnClickListener {
            // TODO: connect Set Ground for indoor detail context
            Toast.makeText(requireContext(), "TODO: Set Ground", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.btnReset)?.setOnClickListener {
            // TODO: connect Reset for indoor detail context
            Toast.makeText(requireContext(), "TODO: Reset", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.btnEditFloorHeight)?.setOnClickListener {
            // TODO: connect Edit Height for indoor detail context
            Toast.makeText(requireContext(), "TODO: Edit Height", Toast.LENGTH_SHORT).show()
        }
    }
}
