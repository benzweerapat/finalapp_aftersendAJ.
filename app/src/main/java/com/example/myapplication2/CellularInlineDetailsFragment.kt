package com.example.myapplication2

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

class CellularInlineDetailsFragment : Fragment(R.layout.fragment_cellular_inline_details) {

    companion object {
        private const val ARG_DATA = "arg_cell_inline_data"

        fun newInstance(data: CellularInlineDetailData): CellularInlineDetailsFragment {
            return CellularInlineDetailsFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_DATA, data) }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnReset)?.visibility = View.GONE
        val data = arguments?.getSerializable(ARG_DATA) as? CellularInlineDetailData ?: CellularInlineDetailData()

        view.findViewById<TextView>(R.id.techLabel)?.text = data.tech
        view.findViewById<TextView>(R.id.operatorValue)?.text = data.operatorName
        view.findViewById<TextView>(R.id.rsrpValue)?.text = "${data.rsrp} dBm"
        view.findViewById<TextView>(R.id.rsrqValue)?.text = "${data.rsrq} dB"
        view.findViewById<TextView>(R.id.arfcnValue)?.text = data.arfcn
        view.findViewById<TextView>(R.id.freqBwValue)?.text = data.freqBw
        view.findViewById<TextView>(R.id.eciValue)?.text = "PCI: ${data.pci}"
        view.findViewById<TextView>(R.id.taValue)?.text = "TAC: ${data.tac}"
        view.findViewById<TextView>(R.id.enbValue)?.text = "Cell ID: ${data.cellId}"
        view.findViewById<TextView>(R.id.latLngValue)?.text = "${data.latitude} / ${data.longitude}"
    }
}
