package com.example.myapplication2

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NeighborAdapter : RecyclerView.Adapter<NeighborAdapter.VH>() {

    private val items = mutableListOf<NeighborItem>()

    fun setData(list: List<NeighborItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_neighbor, parent, false) as ViewGroup
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.tvIndex.text = it.index.toString()
        holder.tvTech.text = it.tech
        holder.tvArfcn.text = it.arfcn
        holder.tvId.text = it.idText

        // RSRP box
        val rsrpText = it.rsrp?.let { v -> "$v dBm" } ?: "—"
        holder.tvRsrp.text = rsrpText

        // heatmap สีตามค่า RSRP (ประมาณ)
        val bg = when {
            it.rsrp == null            -> "#7A7A7A" // เทา
            it.rsrp >= -85             -> "#7CF3A1" // เขียว
            it.rsrp in -95..-86        -> "#FFD66E" // เหลือง
            it.rsrp in -105..-96       -> "#FFB27C" // ส้ม
            else                       -> "#FF8A8A" // แดง
        }
        try { holder.tvRsrp.setBackgroundColor(Color.parseColor(bg)) } catch (_: Exception) {}
    }

    override fun getItemCount(): Int = items.size

    class VH(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val tvIndex: TextView = root.findViewById(R.id.tvIndex)
        val tvTech: TextView = root.findViewById(R.id.tvTech)
        val tvArfcn: TextView = root.findViewById(R.id.tvArfcn)
        val tvRsrp: TextView = root.findViewById(R.id.tvRsrp)
        val tvId: TextView = root.findViewById(R.id.tvId)
    }
}
