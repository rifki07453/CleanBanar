package com.example.cleanbanar.features.history

import android.view.LayoutInflater
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
        
        val type = entry["type"] as? String ?: entry["action"] as? String ?: ""
        val binTypeRaw = entry["bin_type"] as? String ?: entry["areaId"] as? String ?: ""
        val capacity = (entry["capacity"] as? Number)?.toInt() ?: 0
        val petugas = entry["petugas"] as? String ?: entry["fullName"] as? String ?: ""
        val timestamp = entry["timestamp"] as? Long ?: 0L

        // Format binType: organik -> Sampah Organik
        val titleFormat = "Sampah ${binTypeRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}"
        holder.binding.tvTitle.text = titleFormat
        
        // Time & Date
        holder.binding.tvTime.text = formatTime(timestamp)
        holder.binding.tvDate.text = getDayLabel(timestamp)

        // Status & Colors based on type
        when (type) {
            "dikosongkan", "emptied" -> {
                holder.binding.tvStatusBadge.text = "DIKOSONGKAN"
                holder.binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_outlined_green)
                holder.binding.tvStatusBadge.setTextColor(context.getColor(R.color.badge_green_text))
                holder.binding.timelineDot.setBackgroundResource(R.drawable.ic_bg_circle_green)
                
                holder.binding.tvDetails.text = "Petugas: $petugas\nKapasitas akhir: $capacity%"
            }
            "penuh", "alert" -> {
                holder.binding.tvStatusBadge.text = "PENUH (100%)"
                holder.binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_outlined_red)
                holder.binding.tvStatusBadge.setTextColor(context.getColor(R.color.badge_red_text))
                holder.binding.timelineDot.setBackgroundResource(R.drawable.ic_bg_circle_red)
                
                holder.binding.tvDetails.text = "Notifikasi dikirim ke petugas kebersihan untuk pengangkutan."
            }
            else -> {
                // For any other status such as hampir penuh
                holder.binding.tvStatusBadge.text = "HAMPIR PENUH"
                holder.binding.tvStatusBadge.setBackgroundResource(R.drawable.badge_outlined_blue)
                holder.binding.tvStatusBadge.setTextColor(context.getColor(R.color.badge_blue_text))
                holder.binding.timelineDot.setBackgroundResource(R.drawable.ic_bg_circle_blue)
                
                holder.binding.tvDetails.text = "Kapasitas saat ini: $capacity%"
            }
        }
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
