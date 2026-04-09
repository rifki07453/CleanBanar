package com.example.cleanbanar.features.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cleanbanar.R
import com.example.cleanbanar.databinding.ItemHistoryTimelineBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryAdapter(private var items: List<Map<String, Any>> = emptyList()) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHistoryTimelineBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryTimelineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        val context = holder.itemView.context
        
        val action = entry["action"] as? String ?: ""
        val areaId = entry["areaId"] as? String ?: ""
        val fullName = entry["fullName"] as? String ?: ""
        val userId = entry["userId"] as? String ?: ""
        val timestamp = entry["timestamp"] as? Long ?: 0L

        // Bind data
        val title = when (action) {
            "emptied" -> "Pembersihan Area $areaId"
            "alert" -> "Peringatan Area $areaId"
            else -> "Aktivitas Sistem"
        }
        holder.binding.tvTitle.text = title
        
        // Time & Date
        holder.binding.tvTime.text = formatTime(timestamp)
        holder.binding.tvDate.text = getDayLabel(timestamp)

        // Status & Colors
        when (action) {
            "emptied" -> {
                holder.binding.tvStatusBadge.text = "DIKOSONGKAN"
                holder.binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_outlined_green)
                holder.binding.tvStatusBadge.setTextColor(context.getColor(R.color.badge_green_text))
                holder.binding.timelineDot.setBackgroundResource(R.drawable.ic_bg_circle_green)
                holder.binding.tvDetails.text = "Petugas: $fullName (ID: $userId)\nArea: $areaId"
            }
            "alert" -> {
                holder.binding.tvStatusBadge.text = "PENUH (100%)"
                holder.binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_outlined_red)
                holder.binding.tvStatusBadge.setTextColor(context.getColor(R.color.badge_red_text))
                holder.binding.timelineDot.setBackgroundResource(R.drawable.ic_bg_circle_red)
                holder.binding.tvDetails.text = "Kapasitas area $areaId telah mencapai batas maksimum. Pengosongan segera diperlukan."
            }

            else -> {
                holder.binding.tvStatusBadge.text = "SINKRON"
                holder.binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_outlined_blue)
                holder.binding.tvStatusBadge.setTextColor(context.getColor(R.color.badge_blue_text))
                holder.binding.timelineDot.setBackgroundResource(R.drawable.ic_bg_circle_blue)
                holder.binding.tvDetails.text = "Data sensor disinkronkan dengan sistem cloud."
            }
        }

        // Hide line for the last item (optional but looks better)
        // holder.binding.timelineLine.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Map<String, Any>>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun getDayLabel(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        cal.timeInMillis = timestamp

        return when {
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Hari ini, " + formatDate(timestamp)
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Kemarin, " + formatDate(timestamp)
            else -> formatDate(timestamp)
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM", Locale("id"))
        return sdf.format(Date(timestamp))
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val sdf = SimpleDateFormat("HH:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
