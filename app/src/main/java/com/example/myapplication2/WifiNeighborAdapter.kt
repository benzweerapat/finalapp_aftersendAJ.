package com.example.myapplication2

import android.net.wifi.ScanResult
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color


class WifiNeighborAdapter : RecyclerView.Adapter<WifiNeighborAdapter.VH>() {

    private var list: List<ScanResult> = emptyList()

    fun setData(newList: List<ScanResult>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi_scan_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.bind(item, position + 1)
    }

    override fun getItemCount(): Int = list.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIndex: TextView = itemView.findViewById(R.id.tvIndex)
        private val tvFreq: TextView = itemView.findViewById(R.id.tvFreq)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)
        private val tvBw: TextView = itemView.findViewById(R.id.tvBw)
        private val tvMac: TextView = itemView.findViewById(R.id.tvMac)

        private val tvSsid: TextView = itemView.findViewById(R.id.tv_ssid)


        fun bind(item: ScanResult, index: Int) {
            tvIndex.text = index.toString()
            tvFreq.text = item.frequency.toString()
            tvRssi.text = item.level.toString()
            tvMac.text = item.BSSID ?: "-"   // MAC Address

            tvSsid.text = if (item.SSID.isNullOrBlank()) "<hidden>" else item.SSID
            // ===== RSSI box (heatmap style) =====
            val rssi = item.level
            tvRssi.text = "$rssi dBm"

            val bgColor = when {
                rssi >= -60        -> "#7CF3A1" // เขียว (แรงมาก)
                rssi in -70..-61   -> "#FFD66E" // เหลือง
                rssi in -80..-71   -> "#FFB27C" // ส้ม
                else               -> "#FF8A8A" // แดง
            }

            try {
                tvRssi.setBackgroundColor(android.graphics.Color.parseColor(bgColor))
                tvRssi.setTextColor(android.graphics.Color.BLACK)
            } catch (_: Exception) {}


            // Bandwidth Logic
            val bwStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                when (item.channelWidth) {
                    0 -> "20"
                    1 -> "40"
                    2 -> "80"
                    3 -> "160"
                    4 -> "80+"
                    else -> "-"
                }
            } else {
                "-"
            }
            tvBw.text = bwStr



        }
    }
}