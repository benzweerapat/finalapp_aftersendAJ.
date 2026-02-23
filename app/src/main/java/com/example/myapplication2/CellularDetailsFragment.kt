package com.example.myapplication2

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class CellularDetailsFragment : Fragment(R.layout.fragment_cellular) {

    companion object {
        private const val ARG_DETAIL = "arg_cellular_detail"

        fun newInstance(detail: CellularDetailArgs): CellularDetailsFragment {
            return CellularDetailsFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_DETAIL, detail)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val detail = arguments?.getSerializable(ARG_DETAIL) as? CellularDetailArgs ?: return

        view.findViewById<TextView>(R.id.techLabel)?.text = detail.tech
        view.findViewById<TextView>(R.id.bandLabel)?.text = "L2100"
        view.findViewById<TextView>(R.id.operatorValue)?.text = detail.operatorName

        view.findViewById<TextView>(R.id.rsrpValue)?.text = "${detail.rsrp} dBm"
        view.findViewById<TextView>(R.id.rsrqValue)?.text = "${detail.rsrq} dB"
        view.findViewById<TextView>(R.id.rssnrValue)?.text = "SINR: ${detail.sinr}"
        view.findViewById<TextView>(R.id.taValue)?.text = "TAC: ${detail.tac}"

        view.findViewById<TextView>(R.id.arfcnValue)?.text = detail.arfcn
        view.findViewById<TextView>(R.id.freqBwValue)?.text = detail.freqBw
        view.findViewById<TextView>(R.id.eciValue)?.text = "PCI: ${detail.pci}"
        view.findViewById<TextView>(R.id.enbValue)?.text = "Cell ID: ${detail.cellId}"
        view.findViewById<TextView>(R.id.cellIdBlock)?.text = "MCC/MNC/TAC/PCI: ${detail.tac} • ${detail.pci}"

        view.findViewById<TextView>(R.id.latLngValue)?.text = "${detail.latitude} / ${detail.longitude}"

        view.findViewById<TextView>(R.id.textFloor)?.text = "Floor: ${detail.floor}"
        view.findViewById<TextView>(R.id.textAltitude)?.text = "Rel. Height: ${detail.relHeight}"
        view.findViewById<TextView>(R.id.textPressure)?.text = "Pressure: ${detail.pressure}"
        view.findViewById<TextView>(R.id.textGpsFloor)?.text = "Floor: ${detail.floor}"
        view.findViewById<TextView>(R.id.textGpsRelHeight)?.text = "Rel. Height: ${detail.relHeight}"
        view.findViewById<TextView>(R.id.textGpsAltitude)?.text = "Abs. Alt: ${detail.absAltitude}"

        view.findViewById<View>(R.id.neighborsRecycler)?.visibility = View.GONE

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
