package com.example.cleanbanar.features.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.DeviceModel
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.data.PinConfig
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.core.utils.BluetoothHelper
import com.example.cleanbanar.databinding.FragmentAdminDashboardBinding
import com.example.cleanbanar.features.admin.StaffManagementFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.ValueEventListener

class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {
    
    private val bluetoothHelper = BluetoothHelper()
    private lateinit var authManager: AuthManager
    
    private var devicesListener: ValueEventListener? = null
    private var notifListener: ValueEventListener? = null
    
    private var cachedDevices = listOf<DeviceModel>()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAdminDashboardBinding {
        return FragmentAdminDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())
        binding.tvAdminTitle.text = "Halo, ${authManager.getUserName()}"

        binding.cardManajemenPetugas.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StaffManagementFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.cardStatusPerangkat.setOnClickListener {
            showDeviceListBottomSheet()
        }

        binding.tvLihatSemua.setOnClickListener {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
            bottomNav.selectedItemId = R.id.nav_history
        }
    }

    override fun observeData() {
        devicesListener = FirebaseManager.listenDevices { devices ->
            if (!isAdded) return@listenDevices
            cachedDevices = devices
            
            val isOnline = devices.any { it.statusKoneksi == "ONLINE" }
            
            if (isOnline) {
                binding.tvDeviceStatusOverview.text = "ONLINE"
                binding.tvDeviceStatusOverview.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_green)
                (binding.dotStatus.parent as android.widget.LinearLayout).setBackgroundResource(R.drawable.bg_badge_green)
            } else {
                binding.tvDeviceStatusOverview.text = "OFFLINE"
                binding.tvDeviceStatusOverview.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_red)
                (binding.dotStatus.parent as android.widget.LinearLayout).setBackgroundResource(R.drawable.badge_red_bg)
            }
        }

        notifListener = FirebaseManager.listenNotifications { notifList ->
            if (!isAdded) return@listenNotifications
            
            binding.recentActivityContainer.removeAllViews()
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

    private fun showDeviceListBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_device_list, null)
        bottomSheetDialog.setContentView(view)

        val rvDevices = view.findViewById<RecyclerView>(R.id.rvDevices)
        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        
        val adapter = DeviceAdapter(cachedDevices) { device ->
            bottomSheetDialog.dismiss()
            showDeviceDetailBottomSheet(device)
        }
        rvDevices.adapter = adapter

        val tempListener = FirebaseManager.listenDevices { devices ->
            adapter.updateData(devices)
        }

        view.findViewById<MaterialButton>(R.id.btnAddDevice).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAddDeviceDialog()
        }

        bottomSheetDialog.setOnDismissListener {
            tempListener?.let { FirebaseManager.removeDeviceListener(it) }
        }

        bottomSheetDialog.show()
    }

    private fun showAddDeviceDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_device, null)
        dialog.setContentView(view)

        val etDeviceId = view.findViewById<TextInputEditText>(R.id.etDeviceId)
        val etDeviceName = view.findViewById<TextInputEditText>(R.id.etDeviceName)
        
        val etTrigOrg = view.findViewById<TextInputEditText>(R.id.etTrigOrg)
        val etEchoOrg = view.findViewById<TextInputEditText>(R.id.etEchoOrg)
        val etTrigNonOrg = view.findViewById<TextInputEditText>(R.id.etTrigNonOrg)
        val etEchoNonOrg = view.findViewById<TextInputEditText>(R.id.etEchoNonOrg)

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val id = etDeviceId.text.toString().trim()
            val nama = etDeviceName.text.toString().trim()
            
            if (id.isEmpty() || nama.isEmpty()) {
                Toast.makeText(requireContext(), "ID dan Nama Perangkat harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pins = PinConfig(
                trigOrganik = etTrigOrg.text.toString().toIntOrNull() ?: 12,
                echoOrganik = etEchoOrg.text.toString().toIntOrNull() ?: 13,
                trigNonOrganik = etTrigNonOrg.text.toString().toIntOrNull() ?: 14,
                echoNonOrganik = etEchoNonOrg.text.toString().toIntOrNull() ?: 15
            )

            FirebaseManager.addDevice(id, nama, pins)
            Toast.makeText(requireContext(), "Perangkat ditambahkan", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            showDeviceListBottomSheet()
        }

        dialog.show()
    }

    private fun showDeviceDetailBottomSheet(device: DeviceModel) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_device_detail, null)
        dialog.setContentView(view)

        val tvDetailDeviceName = view.findViewById<android.widget.TextView>(R.id.tvDetailDeviceName)
        val tvConnectionStatus = view.findViewById<android.widget.TextView>(R.id.tvConnectionStatus)
        val btnDeleteDevice = view.findViewById<android.widget.ImageView>(R.id.btnDeleteDevice)
        
        tvDetailDeviceName.text = device.nama
        if (device.statusKoneksi == "ONLINE") {
            tvConnectionStatus.text = "ONLINE"
            tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
        } else {
            tvConnectionStatus.text = "OFFLINE"
            tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
        }

        val etDetailTrigOrg = view.findViewById<TextInputEditText>(R.id.etDetailTrigOrg)
        val etDetailEchoOrg = view.findViewById<TextInputEditText>(R.id.etDetailEchoOrg)
        val etDetailTrigNonOrg = view.findViewById<TextInputEditText>(R.id.etDetailTrigNonOrg)
        val etDetailEchoNonOrg = view.findViewById<TextInputEditText>(R.id.etDetailEchoNonOrg)

        etDetailTrigOrg.setText(device.config.pins.trigOrganik.toString())
        etDetailEchoOrg.setText(device.config.pins.echoOrganik.toString())
        etDetailTrigNonOrg.setText(device.config.pins.trigNonOrganik.toString())
        etDetailEchoNonOrg.setText(device.config.pins.echoNonOrganik.toString())

        view.findViewById<MaterialButton>(R.id.btnUpdatePins).setOnClickListener {
            val pins = PinConfig(
                trigOrganik = etDetailTrigOrg.text.toString().toIntOrNull() ?: 12,
                echoOrganik = etDetailEchoOrg.text.toString().toIntOrNull() ?: 13,
                trigNonOrganik = etDetailTrigNonOrg.text.toString().toIntOrNull() ?: 14,
                echoNonOrganik = etDetailEchoNonOrg.text.toString().toIntOrNull() ?: 15
            )
            FirebaseManager.updateDevicePins(device.id, pins)
            Toast.makeText(requireContext(), "PIN berhasil diperbarui", Toast.LENGTH_SHORT).show()
        }

        btnDeleteDevice.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Hapus Perangkat")
                .setMessage("Yakin ingin menghapus perangkat ini?")
                .setPositiveButton("Hapus") { _, _ ->
                    FirebaseManager.deleteDevice(device.id)
                    Toast.makeText(requireContext(), "Perangkat dihapus", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    showDeviceListBottomSheet()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

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

        dialog.show()
    }

    private fun checkBluetoothAndSend(ssid: String, pass: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        val devices = bluetoothHelper.getPairedDevices()
        val espDevice = devices.find { it.name?.contains("CleanBanar", ignoreCase = true) == true }

        if (espDevice == null) {
            Toast.makeText(context, "Perangkat CleanBanar tidak ditemukan di daftar Bluetooth terpasang", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Menghubungkan ke ${espDevice.name}...", Toast.LENGTH_SHORT).show()
        
        bluetoothHelper.connect(espDevice) { success, message ->
            activity?.runOnUiThread {
                if (success) {
                    val configStr = "SET_WIFI:$ssid,$pass\n"
                    if (bluetoothHelper.sendData(configStr)) {
                        Toast.makeText(context, "Konfigurasi terkirim!", Toast.LENGTH_LONG).show()
                        bluetoothHelper.close()
                    } else {
                        Toast.makeText(context, "Gagal mengirim data", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
            setLineSpacing(2.dpToPx().toFloat(), 1f)
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
    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density

    private fun formatLastUpdate(timestamp: Long): String {
        if (timestamp == 0L) return "Menunggu data..."
        val diff = System.currentTimeMillis() - timestamp
        val menit  = diff / 60_000L
        val jam    = diff / 3_600_000L
        val hari   = diff / 86_400_000L
        val minggu = diff / 604_800_000L
        val bulan  = diff / 2_592_000_000L
        return when {
            menit  < 1  -> "Baru saja"
            menit  < 60 -> "$menit menit lalu"
            jam    < 24 -> "$jam jam lalu"
            hari   < 7  -> "$hari hari lalu"
            minggu < 4  -> "$minggu minggu lalu"
            else        -> "$bulan bulan lalu"
        }
    }

    override fun onDestroyView() {
        devicesListener?.let { FirebaseManager.removeDeviceListener(it) }
        notifListener?.let { FirebaseManager.removeNotificationListener(it) }
        super.onDestroyView()
    }
}
