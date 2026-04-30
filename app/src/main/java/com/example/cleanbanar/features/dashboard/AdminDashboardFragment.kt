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

class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {

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

        // Navigate to Staff Management when the card is clicked
        binding.cardManajemenPetugas.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StaffManagementFragment())
                .addToBackStack(null)
                .commit()
        }

        // Show Device Status Bottom Sheet
        binding.cardStatusPerangkat.setOnClickListener {
            showDeviceStatusBottomSheet()
        }

        // Navigate to History when 'Lihat Semua' is clicked
        binding.tvLihatSemua.setOnClickListener {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_history
        }
    }

    private var currentConnectionStatus: String = "OFFLINE"
    private var lastSeenTimestamp: Long = 0L

    override fun observeData() {
        // Listen to device status for the Status Perangkat card
        deviceListener = FirebaseManager.listenDeviceStatus { connectionStatus, lastSeen ->
            if (!isAdded) return@listenDeviceStatus
            
            currentConnectionStatus = connectionStatus ?: "OFFLINE"
            lastSeenTimestamp = lastSeen
            val isOnline = currentConnectionStatus == "ONLINE"
            
            // Only update the minimal status badge in the new UI
            if (isOnline) {
                binding.tvDeviceStatusOverview.text = "ONLINE"
                binding.tvDeviceStatusOverview.setTextColor(android.graphics.Color.parseColor("#059669")) // Emerald 600
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_green)
                binding.dotStatus.parent.let {
                    if (it is android.widget.LinearLayout) {
                        it.setBackgroundResource(R.drawable.bg_badge_green)
                    }
                }
            } else {
                binding.tvDeviceStatusOverview.text = "OFFLINE"
                binding.tvDeviceStatusOverview.setTextColor(android.graphics.Color.parseColor("#DC2626")) // Red 600
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_red)
                binding.dotStatus.parent.let {
                    if (it is android.widget.LinearLayout) {
                        it.setBackgroundResource(R.drawable.bg_badge_red)
                    }
                }
            }
        }
    }

    private fun showDeviceStatusBottomSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_device_status, null)
        bottomSheetDialog.setContentView(view)

        val tvConnectionStatus = view.findViewById<android.widget.TextView>(R.id.tvConnectionStatus)
        val tvNetworkMode = view.findViewById<android.widget.TextView>(R.id.tvNetworkMode)
        val tvLastSync = view.findViewById<android.widget.TextView>(R.id.tvLastSync)
        val ivConnectionIcon = view.findViewById<android.widget.ImageView>(R.id.ivConnectionIcon)
        val flConnectionIconBg = view.findViewById<android.widget.FrameLayout>(R.id.flConnectionIconBg)
        
        val btnModeWifi = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnModeWifi)
        val btnModeBluetooth = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnModeBluetooth)

        // Initial update based on current Firebase status
        val isOnline = currentConnectionStatus == "ONLINE"
        
        fun updateUIMode(mode: String) {
            tvLastSync.text = formatLastUpdate(lastSeenTimestamp)
            
            if (mode == "WIFI") {
                // Style for WiFi Mode
                btnModeWifi.strokeWidth = 2
                btnModeWifi.strokeColor = android.graphics.Color.parseColor("#10B981")
                btnModeWifi.setCardBackgroundColor(android.graphics.Color.parseColor("#ECFDF5"))
                
                btnModeBluetooth.strokeWidth = 1
                btnModeBluetooth.strokeColor = android.graphics.Color.parseColor("#E5E7EB")
                btnModeBluetooth.setCardBackgroundColor(android.graphics.Color.WHITE)

                tvNetworkMode.text = "Terhubung via WiFi"
                ivConnectionIcon.setImageResource(R.drawable.ic_wifi_status)
                
                if (isOnline) {
                    tvConnectionStatus.text = "ONLINE (WiFi)"
                    tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#059669"))
                    flConnectionIconBg.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D1FAE5"))
                    ivConnectionIcon.setColorFilter(android.graphics.Color.parseColor("#059669"))
                } else {
                    tvConnectionStatus.text = "OFFLINE"
                    tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    flConnectionIconBg.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FEE2E2"))
                    ivConnectionIcon.setColorFilter(android.graphics.Color.parseColor("#DC2626"))
                }
            } else {
                // Style for Bluetooth Mode
                btnModeBluetooth.strokeWidth = 2
                btnModeBluetooth.strokeColor = android.graphics.Color.parseColor("#3B82F6")
                btnModeBluetooth.setCardBackgroundColor(android.graphics.Color.parseColor("#EFF6FF"))
                
                btnModeWifi.strokeWidth = 1
                btnModeWifi.strokeColor = android.graphics.Color.parseColor("#E5E7EB")
                btnModeWifi.setCardBackgroundColor(android.graphics.Color.WHITE)

                tvNetworkMode.text = "Terhubung via Bluetooth"
                ivConnectionIcon.setImageResource(R.drawable.ic_bluetooth_status)
                
                // Assuming Bluetooth is directly connected when selected (Simulation)
                tvConnectionStatus.text = "TERHUBUNG (Bluetooth)"
                tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#2563EB"))
                flConnectionIconBg.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DBEAFE"))
                ivConnectionIcon.setColorFilter(android.graphics.Color.parseColor("#2563EB"))
            }
        }

        // Initialize with WiFi mode
        updateUIMode("WIFI")

        btnModeWifi.setOnClickListener {
            updateUIMode("WIFI")
            android.widget.Toast.makeText(context, "Beralih ke mode WiFi", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnModeBluetooth.setOnClickListener {
            updateUIMode("BLUETOOTH")
            android.widget.Toast.makeText(context, "Mencari perangkat Bluetooth...", android.widget.Toast.LENGTH_SHORT).show()
        }

        bottomSheetDialog.show()
    }

    private fun formatLastUpdate(timestamp: Long): String {
        if (timestamp == 0L) return "Menunggu data..."
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "$minutes menit lalu"
            else -> "${minutes / 60} jam lalu"
        }
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "—"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        deviceListener?.let { FirebaseManager.removeDeviceListener(it) }
        super.onDestroyView()
    }
}
