package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentAdminDashboardBinding
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.cleanbanar.features.admin.StaffManagementFragment
import com.example.cleanbanar.core.utils.BluetoothHelper
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * Fragment untuk Dashboard Admin.
 * Menampilkan ringkasan status sistem, manajemen petugas, dan status perangkat IoT.
 */
class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {
    
    private val bluetoothHelper = BluetoothHelper()
    private lateinit var authManager: AuthManager
    private var organikListener: ValueEventListener? = null
    private var nonOrganikListener: ValueEventListener? = null
    private var deviceListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAdminDashboardBinding {
        return FragmentAdminDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())
        binding.tvAdminTitle.text = "Halo, ${authManager.getUserName()}"

        // Navigasi ke Manajemen Petugas
        binding.cardManajemenPetugas.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StaffManagementFragment())
                .addToBackStack(null)
                .commit()
        }

        // Tampilkan Bottom Sheet Status Perangkat
        binding.cardStatusPerangkat.setOnClickListener {
            showDeviceStatusBottomSheet()
        }

        // Navigasi ke Riwayat
        binding.tvLihatSemua.setOnClickListener {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_history
        }
    }

    private var currentConnectionStatus: String = "OFFLINE"
    private var currentNetworkType: String = "WIFI"
    private var lastSeenTimestamp: Long = 0L

    private var notifListener: ValueEventListener? = null
    
    override fun observeData() {
        // Pantau status koneksi perangkat IoT
        deviceListener = FirebaseManager.listenDeviceStatus { connectionStatus, lastSeen, networkType ->
            if (!isAdded) return@listenDeviceStatus
            
            currentConnectionStatus = connectionStatus ?: "OFFLINE"
            currentNetworkType = networkType ?: "WIFI"
            lastSeenTimestamp = lastSeen
            val isOnline = currentConnectionStatus == "ONLINE"
            
            // Perbarui indikator status di dashboard
            if (isOnline) {
                binding.tvDeviceStatusOverview.text = "ONLINE"
                binding.tvDeviceStatusOverview.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_green)
                binding.dotStatus.parent.let {
                    if (it is android.widget.LinearLayout) {
                        it.setBackgroundResource(R.drawable.bg_badge_green)
                    }
                }
            } else {
                binding.tvDeviceStatusOverview.text = "OFFLINE"
                binding.tvDeviceStatusOverview.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_red)
                binding.dotStatus.parent.let {
                    if (it is android.widget.LinearLayout) {
                        it.setBackgroundResource(R.drawable.bg_badge_red)
                    }
                }
            }
        }

        // Pantau aktivitas terbaru (notifikasi)
        notifListener = FirebaseManager.listenNotifications { notifList ->
            if (!isAdded) return@listenNotifications
            
            binding.recentActivityContainer.removeAllViews()
            
            // Ambil maksimal 3 aktivitas terbaru
            val latest = notifList.take(3)
            
            if (latest.isEmpty()) {
                addEmptyActivity()
            } else {
                for (notif in latest) {
                    val title = notif["judul"] as? String ?: "Aktivitas"
                    val message = notif["pesan"] as? String ?: ""
                    val time = notif["waktu"] as? Long ?: 0L
                    val type = notif["tipe"] as? String ?: "info"
                    
                    addActivityItem(title, message, time, type)
                }
            }
        }
    }

    private fun addActivityItem(title: String, message: String, timestamp: Long, type: String) {
        val context = requireContext()
        val itemLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dpToPx() }
            gravity = android.view.Gravity.TOP
        }

        val isNonOrganik = title.lowercase().contains("non")
        val (iconRes, iconBg, iconColor) = when (type) {
            "danger" -> Triple(R.drawable.ic_trash_modern, R.drawable.badge_red_bg, "#EF4444")
            "success" -> {
                if (isNonOrganik) Triple(R.drawable.ic_trash_modern, R.drawable.bg_rounded_light_blue, "#2563EB")
                else Triple(R.drawable.ic_trash_modern, R.drawable.bg_badge_green, "#16A34A")
            }
            else -> Triple(android.R.drawable.ic_popup_reminder, R.drawable.bg_rounded_light_blue, "#3B82F6")
        }

        val frame = android.widget.FrameLayout(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(36.dpToPx(), 36.dpToPx())
            setBackgroundResource(iconBg)
        }

        val icon = android.widget.ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(iconRes)
            setColorFilter(android.graphics.Color.parseColor(iconColor))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }
        frame.addView(icon)

        val info = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dpToPx()
            }
        }

        val tvTitle = android.widget.TextView(context).apply {
            text = title
            setTextColor(android.graphics.Color.parseColor("#111827"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 12f
        }

        val tvMsg = android.widget.TextView(context).apply {
            text = message
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            textSize = 10f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2.dpToPx() }
            setLineSpacing(2f.dpToPx().toFloat(), 1f)
        }

        info.addView(tvTitle)
        info.addView(tvMsg)

        val tvTime = android.widget.TextView(context).apply {
            text = formatLastUpdate(timestamp)
            setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            textSize = 9f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8.dpToPx() }
        }

        itemLayout.addView(frame)
        itemLayout.addView(info)
        itemLayout.addView(tvTime)

        binding.recentActivityContainer.addView(itemLayout)
    }

    private fun addEmptyActivity() {
        val tv = android.widget.TextView(requireContext()).apply {
            text = "Belum ada aktivitas terbaru"
            setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
        }
        binding.recentActivityContainer.addView(tv)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        deviceListener?.let { FirebaseManager.removeDeviceListener(it) }
        notifListener?.let { FirebaseManager.removeNotificationListener(it) }
        super.onDestroyView()
    }
}
