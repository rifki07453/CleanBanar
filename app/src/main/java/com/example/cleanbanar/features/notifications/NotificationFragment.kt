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
        binding.progressBar.visibility = android.view.View.GONE
        binding.notifListContainer.removeAllViews()
        
        // Hide the subtitle or update it if needed. The design doesn't show subtitle vividly.
        binding.tvNotifSubtitle.visibility = android.view.View.GONE
        
        val dummyNotifs = listOf(
            mapOf(
                "title" to "Organik Penuh!",
                "message" to "Kapasitas tong sampah organik di\nlingkungan A telah mencapai 100%. Harap\nsegera dikosongkan.",
                "type" to "danger",
                "timeText" to "Baru saja",
                "isUnread" to true
            ),
            mapOf(
                "title" to "Non-Org Hampir Penuh",
                "message" to "Kapasitas tong sampah non-organik di\nangka 85%. Bersiap untuk persiapan\npengosongan rutin.",
                "type" to "warning",
                "timeText" to "15 menit yang lalu",
                "isUnread" to true
            ),
            mapOf(
                "title" to "Pengosongan Selesai",
                "message" to "Sampah organik telah berhasil\ndikosongkan secara manual oleh Petugas\nKebersihan Ahmad.",
                "type" to "success",
                "timeText" to "Kemarin, 14:30",
                "isUnread" to false
            ),
            mapOf(
                "title" to "Sistem Online",
                "message" to "Smart Bin berhasil terkoneksi kembali ke\nserver monitoring utama setelah restart\nnode.",
                "type" to "info",
                "timeText" to "2 Hari lalu",
                "isUnread" to false
            )
        )

        for (notif in dummyNotifs) {
            addNotificationCard(
                title = notif["title"] as String,
                message = notif["message"] as String,
                type = notif["type"] as String,
                timeText = notif["timeText"] as String,
                isUnread = notif["isUnread"] as Boolean
            )
        }
    }

    // ==========================================
    // UI Builder - Notification Cards
    // ==========================================
    private fun addNotificationCard(title: String, message: String, type: String, timeText: String, isUnread: Boolean) {
        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dpToPx() }
            radius = 24f.dpToPxF()
            cardElevation = 0f.dpToPxF()
            setCardBackgroundColor(resources.getColor(R.color.white, null))
            
            val strokeCol = when (type) {
                "danger" -> android.graphics.Color.parseColor("#FCA5A5") // red 300
                "warning" -> android.graphics.Color.parseColor("#FDE047") // yellow 300
                "success" -> android.graphics.Color.parseColor("#60A5FA") // blue 400
                else -> android.graphics.Color.parseColor("#FEF08A") // faint yellow/white outline
            }
            // For info we can just use light gray
            if (type == "info") {
                 setStrokeColor(android.graphics.Color.parseColor("#F3F4F6"))
            } else {
                 setStrokeColor(strokeCol)
            }
            strokeWidth = 1.dpToPx()
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        val (iconRes, iconBgColor, iconTintColor) = when (type) {
            "danger" -> Triple(
                android.R.drawable.ic_dialog_alert,
                "#FEF2F2", // red 50
                resources.getColor(R.color.red_500, null)
            )
            "warning" -> Triple(
                android.R.drawable.ic_popup_reminder, // generic bell
                "#FEFCE8", // yellow 50
                resources.getColor(R.color.amber_600, null)
            )
            "success" -> Triple(
                android.R.drawable.ic_input_add, // will be rotated to look like checkmark
                "#F9FAFB", // gray 50
                android.graphics.Color.parseColor("#111827")
            )
            else -> Triple(
                android.R.drawable.ic_dialog_dialer, // wifi substitute
                "#EFF6FF", // blue 50
                android.graphics.Color.parseColor("#1E3A8A")
            )
        }

        val iconFrame = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx()).apply {
                marginEnd = 16.dpToPx()
            }
            background = getCircleDrawable(android.graphics.Color.parseColor(iconBgColor))
        }

        val icon = ImageView(requireContext()).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(iconRes)
            if (iconRes == android.R.drawable.ic_input_add) rotation = 45f
            setColorFilter(iconTintColor)
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }
        iconFrame.addView(icon)

        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(requireContext()).apply {
            text = title
            setTextColor(resources.getColor(R.color.gray_800, null))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvMessage = TextView(requireContext()).apply {
            text = message
            setTextColor(resources.getColor(R.color.gray_500, null))
            textSize = 12f
            setPadding(0, 8.dpToPx(), 0, 16.dpToPx())
            setLineSpacing(2f.dpToPxF(), 1f)
        }
        val timeColor = when (type) {
            "danger" -> android.graphics.Color.parseColor("#EF4444")
            "warning" -> android.graphics.Color.parseColor("#EAB308")
            else -> resources.getColor(R.color.gray_400, null)
        }
        val tvTime = TextView(requireContext()).apply {
            text = timeText
            setTextColor(timeColor)
            textSize = 10f
        }
        info.addView(tvTitle)
        info.addView(tvMessage)
        info.addView(tvTime)

        val rightCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        if (isUnread) {
            val dot = android.view.View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 8.dpToPx()).apply {
                    topMargin = 4.dpToPx()
                }
                background = getCircleDrawable(iconTintColor)
            }
            rightCol.addView(dot)
        }

        row.addView(iconFrame)
        row.addView(info)
        row.addView(rightCol)
        cardView.addView(row)
        binding.notifListContainer.addView(cardView)
    }

    private fun getCircleDrawable(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
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
            text = "Tidak ada notifikasi saat ini"
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 12.dpToPx(), 0, 4.dpToPx())
        }

        // Removed tvSub secondary text to keep it minimal as per reference

        container.addView(icon)
        container.addView(tv)
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
