package com.example.myapplication2

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment

class IndoorDetailsPopupDialogFragment : DialogFragment(R.layout.fragment_indoor_details_popup) {

    companion object {
        private const val ARG_MODE = "arg_mode"
        private const val TAG_NAME = "indoor_details_popup"

        fun newInstance(mode: IndoorSessionManager.RadioMode): IndoorDetailsPopupDialogFragment {
            return IndoorDetailsPopupDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, mode.name)
                }
            }
        }

        fun tagName(): String = TAG_NAME
    }

    private val mode: IndoorSessionManager.RadioMode by lazy {
        val value = arguments?.getString(ARG_MODE)
        IndoorSessionManager.RadioMode.valueOf(value ?: IndoorSessionManager.RadioMode.CELLULAR.name)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            val width = (resources.displayMetrics.widthPixels * 0.95f).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.92f).toInt()
            setLayout(width, height)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnClosePopup).setOnClickListener {
            dismiss()
        }

        if (childFragmentManager.findFragmentById(R.id.indoorDetailsContainer) == null) {
            val detailFragment: Fragment = when (mode) {
                IndoorSessionManager.RadioMode.CELLULAR -> IndoorCellularFragment()
                IndoorSessionManager.RadioMode.WIFI -> IndoorWifiFragment()
            }

            childFragmentManager.beginTransaction()
                .replace(R.id.indoorDetailsContainer, detailFragment)
                .commitNow()
        }
    }
}
