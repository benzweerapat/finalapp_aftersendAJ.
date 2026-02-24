package com.example.myapplication2

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

class WifiDetailsFragment : WifiFragment(R.layout.fragment_wifi_inline_details) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideLocationSection(view)
    }

    private fun hideLocationSection(root: View) {
        val latLng = root.findViewById<View>(R.id.latLngValue) ?: return
        latLng.visibility = View.GONE
        var parent = latLng.parent as? View
        while (parent != null) {
            if (parent.javaClass.name.contains("CardView")) {
                parent.visibility = View.GONE
                break
            }
            parent = parent.parent as? View
        }
    }
}
