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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fragment untuk menampilkan daftar notifikasi sistem secara real-time.
 */
class NotificationFragment : BaseFragment<FragmentNotificationBinding>() {

    private var notifListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentNotificationBinding {
        return FragmentNotificationBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        binding.tvClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Hapus Semua Notifikasi")
                .setMessage("Apakah Anda yakin ingin menghapus semua notifikasi? Tindakan ini tidak dapat dibatalkan.")
                .setPositiveButton("Hapus") { _, _ ->
                    FirebaseManager.clearAllNotifications()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    override fun observeData() {
        // Mendengarkan data notifikasi dari Firebase
        notifListener = FirebaseManager.listenNotifications { notifData ->
            if (isAdded) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.tvNotifSubtitle.visibility = android.view.View.GONE
                binding.notifListContainer.removeAllViews()

                if (notifData.isEmpty()) {
                    binding.tvClearAll.visibility = android.view.View.GONE
                    addEmptyState()
                } else {
                    binding.tvClearAll.visibility = android.view.View.VISIBLE
                    for (notif in notifData) {
                        val waktu = notif["waktu"] as? Long ?: 0L
                        addNotificationCard(
                            title = notif["judul"] as? String ?: "Notifikasi",
                            message = notif["pesan"] as? String ?: "",
                            type = notif["tipe"] as? String ?: "info",
                            timeText = formatTimestamp(waktu),
                            isUnread = !(notif["sudahDibaca"] as? Boolean ?: true)
                        )
                    }
                }
            }
        }
    }

    /**
     * Membangun kartu notifikasi secara dinamis.
     */
    private fun addNotificationCard(title: String, message: String, type: String, timeText: String, isUnread: Boolean) {
        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dpToPx() }
            radius = 24f.dpToPxF()
            cardElevation = 0f.dpToPxF()
            setCardBackgroundColor(resources.getColor(R.color.white, null))
            
            val isNonOrganik = title.lowercase().contains("non")
            val strokeCol = when (type) {
                "danger" -> androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.red_500)
                "warning" -> androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.amber_500)
                "success" -> if (isNonOrganik) androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.blue_600) else androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.emerald_500)
                else -> androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.bg_main)
            }
            setStrokeColor(strokeCol)
            strokeWidth = 1.dpToPx()
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        val isNonOrganik = title.lowercase().contains("non")
        val isRestart = title.lowercase().contains("restart")
        val isConfig = title.lowercase().contains("config")

        val (iconRes, _, iconTintColor) = when {
            isRestart -> Triple(
                R.drawable.ic_power_settings_new_24dp,
                "#FEF2F2",
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.red_500)
            )
            isConfig -> Triple(
                R.drawable.ic_bluetooth_24dp,
                "#EFF6FF",
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.blue_600)
            )
            else -> when (type) {
                "danger" -> Triple(R.drawable.ic_trash_modern, "#FEF2F2", resources.getColor(R.color.red_500, null))
                "warning" -> Triple(R.drawable.ic_trash_modern, "#FEFCE8", resources.getColor(R.color.amber_600, null))
                "success" -> {
                    if (isNonOrganik) {
                        Triple(R.drawable.ic_trash_modern, "#EFF6FF", androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.blue_600))
                    } else {
                        Triple(R.drawable.ic_trash_modern, "#ECFDF5", androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.green_600))
                    }
                }
                else -> Triple(R.drawable.ic_trash_modern, "#F9FAFB", androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
            }
        }

        val iconFrame = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx()).apply {
                marginEnd = 16.dpToPx()
            }
        }

        val icon = ImageView(requireContext()).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(iconRes)
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
        val tvTime = TextView(requireContext()).apply {
            text = timeText
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 10f
        }
        info.addView(tvTitle)
        info.addView(tvMessage)
        info.addView(tvTime)

        val rightCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        row.addView(iconFrame)
        row.addView(info)
        row.addView(rightCol)
        cardView.addView(row)
        binding.notifListContainer.addView(cardView)
    }

    private fun addEmptyState() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Gunakan weight atau margins agar ke tengah
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                // Menambahkan margin atas agak besar agar turun ke tengah
                topMargin = 120.dpToPx()
            }
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

        container.addView(icon)
        container.addView(tv)
        binding.notifListContainer.addView(container)
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "$minutes menit lalu"
            minutes < 1440 -> "${minutes / 60} jam lalu"
            else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    override fun onDestroyView() {
        notifListener?.let { FirebaseManager.removeNotificationListener(it) }
        super.onDestroyView()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density
}
