package com.example.cleanbanar.features.notifications

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentNotificationBinding
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationFragment : BaseFragment<FragmentNotificationBinding>() {

    private var notifListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentNotificationBinding {
        return FragmentNotificationBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {}

    // ==========================================
    // Firebase Real-Time Listeners
    // ==========================================

    override fun observeData() {
        notifListener = FirebaseManager.listenNotifications { notifications ->
            if (!isAdded) return@listenNotifications
            binding.progressBar.visibility = android.view.View.GONE
            binding.notifListContainer.removeAllViews()

            if (notifications.isEmpty()) {
                addEmptyState()
                return@listenNotifications
            }

            binding.tvNotifSubtitle.text = "${notifications.size} notifikasi"

            for (notif in notifications) {
                addNotificationCard(
                    title = notif["title"] as String,
                    message = notif["message"] as String,
                    type = notif["type"] as String,
                    timestamp = notif["timestamp"] as Long
                )
            }
        }
    }

    // ==========================================
    // UI Builder - Notification Cards
    // ==========================================
    private fun addNotificationCard(title: String, message: String, type: String, timestamp: Long) {
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

        // Type-specific icon & color
        val (iconRes, bgRes, tintColor) = when (type) {
            "danger", "full" -> Triple(
                android.R.drawable.ic_dialog_alert,
                R.drawable.badge_red_bg,
                R.color.red_500
            )
            "warning" -> Triple(
                android.R.drawable.ic_dialog_alert,
                R.drawable.badge_amber_bg,
                R.color.amber_600
            )
            else -> Triple(
                android.R.drawable.ic_dialog_info,
                R.drawable.badge_green_bg,
                R.color.green_600
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

        // Status badge text
        val statusLabel = when (type) {
            "danger", "full" -> "Penuh"
            "warning" -> "Hampir Penuh"
            else -> "Info"
        }

        val tvTitle = TextView(requireContext()).apply {
            text = title
            setTextColor(resources.getColor(R.color.gray_800, null))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvMessage = TextView(requireContext()).apply {
            text = message
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 11f
        }
        info.addView(tvTitle)
        info.addView(tvMessage)

        // Right side: time + status
        val rightCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val tvTime = TextView(requireContext()).apply {
            text = formatTime(timestamp)
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 10f
            gravity = Gravity.END
        }
        val tvStatus = TextView(requireContext()).apply {
            text = statusLabel
            setTextColor(resources.getColor(tintColor, null))
            textSize = 9f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, 4.dpToPx(), 0, 0)
        }
        rightCol.addView(tvTime)
        rightCol.addView(tvStatus)

        row.addView(icon)
        row.addView(info)
        row.addView(rightCol)
        cardView.addView(row)
        binding.notifListContainer.addView(cardView)
    }

    // ==========================================
    // UI Builder - Empty State
    // ==========================================
    private fun addEmptyState() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 48.dpToPx(), 0, 48.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val icon = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(64.dpToPx(), 64.dpToPx())
            setImageResource(android.R.drawable.ic_popup_reminder)
            setColorFilter(resources.getColor(R.color.gray_400, null))
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        val tv = TextView(requireContext()).apply {
            text = "Belum ada notifikasi"
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 12.dpToPx(), 0, 4.dpToPx())
        }

        val tvSub = TextView(requireContext()).apply {
            text = "Belum ada aktivitas notifikasi"
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 11f
            gravity = Gravity.CENTER
        }

        container.addView(icon)
        container.addView(tv)
        container.addView(tvSub)
        binding.notifListContainer.addView(container)
    }

    // ==========================================
    // Utility / Helper Functions
    // ==========================================
    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        notifListener?.let { FirebaseManager.removeNotificationListener(it) }
        super.onDestroyView()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density
}
