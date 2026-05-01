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
                binding.tvDeviceStatusOverview.setTextColor(android.graphics.Color.parseColor("#059669"))
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
    }

    /**
     * Menampilkan dialog Bottom Sheet untuk detail status perangkat dan konfigurasi WiFi.
     */
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

        val isOnline = currentConnectionStatus == "ONLINE"
        
        fun updateUIMode(mode: String) {
            tvLastSync.text = formatLastUpdate(lastSeenTimestamp)
            
            if (mode == "WIFI") {
                btnModeWifi.strokeWidth = 2
                btnModeWifi.strokeColor = android.graphics.Color.parseColor("#10B981")
                btnModeWifi.setCardBackgroundColor(android.graphics.Color.parseColor("#ECFDF5"))
                
                btnModeBluetooth.strokeWidth = 1
                btnModeBluetooth.strokeColor = android.graphics.Color.parseColor("#E5E7EB")
                btnModeBluetooth.setCardBackgroundColor(android.graphics.Color.WHITE)

                tvNetworkMode.text = "Terhubung via WiFi"
                ivConnectionIcon.setImageResource(R.drawable.ic_wifi_status)
                
                if (isOnline) {
                    tvConnectionStatus.text = "ONLINE"
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
                btnModeBluetooth.strokeWidth = 2
                btnModeBluetooth.strokeColor = android.graphics.Color.parseColor("#3B82F6")
                btnModeBluetooth.setCardBackgroundColor(android.graphics.Color.parseColor("#EFF6FF"))
                
                btnModeWifi.strokeWidth = 1
                btnModeWifi.strokeColor = android.graphics.Color.parseColor("#E5E7EB")
                btnModeWifi.setCardBackgroundColor(android.graphics.Color.WHITE)

                tvNetworkMode.text = "Terhubung via Bluetooth"
                ivConnectionIcon.setImageResource(R.drawable.ic_bluetooth_status)
                
                if (isOnline) {
                    tvConnectionStatus.text = "ONLINE"
                    tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#2563EB"))
                    flConnectionIconBg.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DBEAFE"))
                    ivConnectionIcon.setColorFilter(android.graphics.Color.parseColor("#2563EB"))
                } else {
                    tvConnectionStatus.text = "OFFLINE"
                    tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    flConnectionIconBg.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FEE2E2"))
                    ivConnectionIcon.setColorFilter(android.graphics.Color.parseColor("#DC2626"))
                }
            }
        }

        updateUIMode(currentNetworkType)

        btnModeWifi.setOnClickListener {
            FirebaseManager.updateDeviceNetworkType("WIFI")
            updateUIMode("WIFI")
            android.widget.Toast.makeText(context, "Beralih ke mode WiFi", android.widget.Toast.LENGTH_SHORT).show()
        }

        btnModeBluetooth.setOnClickListener {
            FirebaseManager.updateDeviceNetworkType("BLUETOOTH")
            updateUIMode("BLUETOOTH")
            android.widget.Toast.makeText(context, "Beralih ke mode Bluetooth", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Logika Provisioning (Pengaturan WiFi via Bluetooth)
        val etSsid = view.findViewById<TextInputEditText>(R.id.etSsid)
        val etPassword = view.findViewById<TextInputEditText>(R.id.etPassword)
        val btnSendConfig = view.findViewById<MaterialButton>(R.id.btnSendConfig)

        btnSendConfig.setOnClickListener {
            val ssid = etSsid.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (ssid.isEmpty()) {
                Toast.makeText(context, "Nama WiFi tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkBluetoothAndSend(ssid, pass)
        }

        bottomSheetDialog.show()
    }

    /**
     * Memeriksa izin Bluetooth dan mengirimkan kredensial WiFi ke ESP32.
     */
    private fun checkBluetoothAndSend(ssid: String, pass: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        val devices = bluetoothHelper.getPairedDevices()
        val espDevice = devices.find { it.name?.contains("CleanBanar", ignoreCase = true) == true }

        if (espDevice == null) {
            Toast.makeText(context, "ESP32 (CleanBanar) tidak ditemukan di daftar Bluetooth terpasang", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Menghubungkan ke ${espDevice.name}...", Toast.LENGTH_SHORT).show()
        
        bluetoothHelper.connect(espDevice) { success, message ->
            activity?.runOnUiThread {
                if (success) {
                    val configStr = "SET_WIFI:$ssid,$pass\n"
                    if (bluetoothHelper.sendData(configStr)) {
                        Toast.makeText(context, "Konfigurasi terkirim! ESP32 akan segera terhubung ke WiFi.", Toast.LENGTH_LONG).show()
                        bluetoothHelper.close()
                    } else {
                        Toast.makeText(context, "Gagal mengirim data via Bluetooth", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        deviceListener?.let { FirebaseManager.removeDeviceListener(it) }
        super.onDestroyView()
    }
}
