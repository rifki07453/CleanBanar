package com.example.cleanbanar.features.history

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentHistoryBinding
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryFragment : BaseFragment<FragmentHistoryBinding>() {

    private var historyListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHistoryBinding {
        return FragmentHistoryBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {}

    override fun observeData() {
        historyListener = FirebaseManager.listenHistory { historyList ->
            if (!isAdded) return@listenHistory
            binding.historyListContainer.removeAllViews()

            if (historyList.isEmpty()) {
                addEmptyState()
                return@listenHistory
            }

            // Group by day
            var lastDayLabel = ""
            for (entry in historyList) {
                val timestamp = entry["timestamp"] as Long
                val dayLabel = getDayLabel(timestamp)

                if (dayLabel != lastDayLabel) {
                    addDayHeader(dayLabel)
                    lastDayLabel = dayLabel
                }

                addHistoryCard(
                    action = entry["action"] as String,
                    bin = entry["bin"] as String,
                    actor = entry["actor"] as String,
                    timestamp = timestamp
                )
            }
        }
    }

    private fun addDayHeader(label: String) {
        val tv = TextView(requireContext()).apply {
            text = label
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.gray_500, null))
            setPadding(4.dpToPx(), 8.dpToPx(), 0, 8.dpToPx())
        }
        binding.historyListContainer.addView(tv)
    }

    private fun addHistoryCard(action: String, bin: String, actor: String, timestamp: Long) {
        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dpToPx() }
            radius = 16f.dpToPxF()
            cardElevation = 1f.dpToPxF()
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(14.dpToPx(), 14.dpToPx(), 14.dpToPx(), 14.dpToPx())
            gravity = Gravity.CENTER_VERTICAL
        }

        // Icon based on action type
        val (iconRes, bgRes, tintColor) = when (action) {
            "emptied" -> Triple(
                android.R.drawable.ic_menu_revert,
                R.drawable.leaf_bg,
                R.color.emerald_600
            )
            "alert" -> Triple(
                android.R.drawable.ic_dialog_alert,
                R.drawable.badge_amber_bg,
                R.color.amber_600
            )
            else -> Triple(
                android.R.drawable.ic_popup_sync,
                R.drawable.badge_blue_bg,
                R.color.secondary
            )
        }

        val icon = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(36.dpToPx(), 36.dpToPx())
            setImageResource(iconRes)
            setBackgroundResource(bgRes)
            setColorFilter(resources.getColor(tintColor, null))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }

        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12.dpToPx(), 0, 0, 0)
        }

        // Title
        val binLabel = if (bin == "organik") "Organik" else "Non-Organik"
        val actionLabel = when (action) {
            "emptied" -> "$binLabel dikosongkan"
            "alert" -> "$binLabel hampir penuh"
            else -> "Data sensor disinkronkan"
        }

        val tvTitle = TextView(requireContext()).apply {
            text = actionLabel
            setTextColor(resources.getColor(R.color.gray_800, null))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvSubtitle = TextView(requireContext()).apply {
            text = if (action == "emptied") "Oleh: $actor" else actor
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 11f
        }
        info.addView(tvTitle)
        info.addView(tvSubtitle)

        val tvTime = TextView(requireContext()).apply {
            text = formatTime(timestamp)
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 11f
        }

        row.addView(icon)
        row.addView(info)
        row.addView(tvTime)
        cardView.addView(row)
        binding.historyListContainer.addView(cardView)
    }

    private fun addEmptyState() {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 48.dpToPx(), 0, 48.dpToPx())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val icon = android.widget.ImageView(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(64.dpToPx(), 64.dpToPx())
            setImageResource(android.R.drawable.ic_menu_recent_history)
            setColorFilter(resources.getColor(R.color.gray_400, null))
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        val tv = android.widget.TextView(requireContext()).apply {
            text = "Belum ada riwayat aktivitas"
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 12.dpToPx(), 0, 4.dpToPx())
        }

        val tvSub = android.widget.TextView(requireContext()).apply {
            text = "Aktivitas akan muncul saat tong sampah dikosongkan"
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 11f
            gravity = Gravity.CENTER
        }

        container.addView(icon)
        container.addView(tv)
        container.addView(tvSub)
        binding.historyListContainer.addView(container)
    }

    private fun getDayLabel(timestamp: Long): String {
        if (timestamp == 0L) return "Tidak diketahui"
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        cal.timeInMillis = timestamp

        return when {
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Hari Ini"
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Kemarin"
            else -> {
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("id"))
                sdf.format(Date(timestamp))
            }
        }
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        historyListener?.let { FirebaseManager.removeHistoryListener(it) }
        super.onDestroyView()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density
}
