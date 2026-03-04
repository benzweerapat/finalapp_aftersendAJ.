package com.example.drivetest

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView

data class InlineSignalItem(
    val itemId: Long,
    val title: String,
    val mode: IndoorSessionManager.RadioMode,
    val wifiData: WifiInlineDetailData? = null,
    val cellularData: CellularInlineDetailData? = null
)

class InlineExpandableSignalAdapter(
    private val fragmentManager: FragmentManager,
    private val hostLifecycleOwnerTag: String,
    private val items: MutableList<InlineSignalItem> = mutableListOf()
) : RecyclerView.Adapter<InlineExpandableSignalAdapter.VH>() {

    private val transition = AutoTransition().apply { duration = 180 }
    private var expandedId: Long? = null

    init {
        setHasStableIds(true)
    }

    fun submit(newItems: List<InlineSignalItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].itemId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_inline_expandable_signal, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item, item.itemId == expandedId)

        holder.itemView.setOnClickListener {
            val oldExpanded = expandedId
            expandedId = if (oldExpanded == item.itemId) null else item.itemId

            oldExpanded?.let { oldId ->
                val oldPos = items.indexOfFirst { it.itemId == oldId }
                if (oldPos >= 0) notifyItemChanged(oldPos)
            }
            notifyItemChanged(holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.detachFragmentIfNeeded()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.textTitle)
        private val chevron = view.findViewById<ImageView>(R.id.imageChevron)
        private val container = view.findViewById<FrameLayout>(R.id.detailsContainer)

        fun bind(item: InlineSignalItem, expanded: Boolean) {
            title.text = item.title
            if (container.id == View.NO_ID) container.id = View.generateViewId()

            TransitionManager.beginDelayedTransition(itemView as ViewGroup, transition)
            container.isVisible = expanded
            chevron.setImageResource(
                if (expanded) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )

            if (!expanded) {
                detachFragmentIfNeeded()
                return
            }

            val tag = fragmentTag(item)
            val existing = fragmentManager.findFragmentByTag(tag)
            val fragment = existing ?: createFragment(item)

            if (!fragment.isAdded) {
                fragmentManager.beginTransaction()
                    .replace(container.id, fragment, tag)
                    .commitNowAllowingStateLoss()
            } else if (fragment.id != container.id) {
                fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss()
                fragmentManager.beginTransaction()
                    .replace(container.id, createFragment(item), tag)
                    .commitNowAllowingStateLoss()
            }
        }

        fun detachFragmentIfNeeded() {
            val id = container.id
            if (id == View.NO_ID) return
            fragmentManager.findFragmentById(id)?.let {
                fragmentManager.beginTransaction()
                    .remove(it)
                    .commitNowAllowingStateLoss()
            }
        }

        private fun createFragment(item: InlineSignalItem): Fragment {
            return when (item.mode) {
                IndoorSessionManager.RadioMode.WIFI -> {
                    WifiInlineDetailsFragment.newInstance(item.wifiData ?: WifiInlineDetailData())
                }
                IndoorSessionManager.RadioMode.CELLULAR -> {
                    CellularInlineDetailsFragment.newInstance(item.cellularData ?: CellularInlineDetailData())
                }
            }
        }

        private fun fragmentTag(item: InlineSignalItem): String {
            return "${hostLifecycleOwnerTag}_inline_${item.itemId}"
        }
    }
}
