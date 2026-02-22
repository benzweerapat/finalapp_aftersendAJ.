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
    }

    private var listener: Listener? = null
    private var textSignalMain: TextView? = null
    private var textSignalSub: TextView? = null
    private var textPointCount: TextView? = null

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

        view.findViewById<Button>(R.id.btnEndSurvey).setOnClickListener {
            listener?.onEndSurveyClicked()
        }
    }

    fun updateSignal(main: String, sub: String) {
        textSignalMain?.text = main
        textSignalSub?.text = sub
    }

    fun updatePointCount(count: Int) {
        textPointCount?.text = "Points: $count"
    }
}
