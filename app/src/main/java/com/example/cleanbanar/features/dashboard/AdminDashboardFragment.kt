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

        // Navigate to History when 'Lihat Semua' is clicked
        binding.tvLihatSemua.setOnClickListener {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_history
        }
    }

    override fun observeData() {
        // Listen to device status for the Status Perangkat card
        deviceListener = FirebaseManager.listenDeviceStatus { connectionStatus, lastSeen ->
            if (!isAdded) return@listenDeviceStatus
            val isOnline = connectionStatus == "ONLINE"
            
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
