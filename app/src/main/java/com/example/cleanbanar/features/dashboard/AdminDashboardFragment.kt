package com.example.cleanbanar.features.dashboard

import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.Manifest
import android.content.pm.PackageManager
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.DeviceModel
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.data.PinConfig
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.core.utils.BluetoothHelper
import com.example.cleanbanar.databinding.FragmentAdminDashboardBinding
import com.example.cleanbanar.features.admin.StaffManagementFragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.ValueEventListener
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {
    
    private val bluetoothHelper = BluetoothHelper()
    private lateinit var authManager: AuthManager
    
    private var devicesListener: ValueEventListener? = null
    private var notifListener: ValueEventListener? = null
    private var statsListener: ValueEventListener? = null
    
    private var cachedDevices = listOf<DeviceModel>()
    private var lastKnownHistory: List<Map<String, Any>> = emptyList()

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
            // TODO: Navigasi ke fragment history penuh (opsional)
        }

        binding.btnExportPdf.setOnClickListener {
            exportHistoryToPdf()
        }

        setupChart()
    }

    private fun setupChart() {
        binding.barChartStatistik.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            axisRight.isEnabled = false
            
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                setDrawGridLines(true)
                gridColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.bg_main)
                textColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary)
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary)
            }

            legend.textColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary)
            legend.textSize = 12f
            setNoDataText("Memuat data statistik...")
            setNoDataTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_tertiary))
        }
    }

    override fun observeData() {
        devicesListener = FirebaseManager.listenDevices { devices ->
            if (!isAdded) return@listenDevices
            cachedDevices = devices
            
            val isOnline = devices.any { it.statusKoneksi == "ONLINE" }
            
            if (isOnline) {
                binding.tvDeviceStatusOverview.text = "ONLINE"
                binding.tvDeviceStatusOverview.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.green_600))
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_green)
                (binding.dotStatus.parent as android.widget.LinearLayout).setBackgroundResource(R.drawable.bg_badge_green)
            } else {
                binding.tvDeviceStatusOverview.text = "OFFLINE"
                binding.tvDeviceStatusOverview.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.red_600))
                binding.dotStatus.setBackgroundResource(R.drawable.dot_timeline_red)
                (binding.dotStatus.parent as android.widget.LinearLayout).setBackgroundResource(R.drawable.badge_red_bg)
            }
        }

        statsListener = FirebaseManager.listenDailyStats { stats ->
            if (isAdded && isVisible) {
                updateChart(stats)
            }
        }

        notifListener = FirebaseManager.listenNotifications { notifList ->
            if (!isAdded) return@listenNotifications
            lastKnownHistory = notifList
            
            binding.recentActivityContainer.removeAllViews()
            val latest = notifList.take(3)
            
            if (latest.isEmpty()) {
                addEmptyActivity()
            } else {
                hideEmptyActivity()
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

    private fun updateChart(stats: List<Map<String, Any>>) {
        if (stats.isEmpty()) {
            binding.barChartStatistik.clear()
            return
        }

        val recentStats = stats.takeLast(7)
        val entriesOrganik = mutableListOf<BarEntry>()
        val entriesNonOrganik = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfOutput = SimpleDateFormat("dd/MM", Locale.getDefault())

        recentStats.forEachIndexed { index, stat ->
            val tglStr = stat["tanggal"] as? String ?: ""
            val label = try {
                val date = sdfInput.parse(tglStr)
                if (date != null) sdfOutput.format(date) else tglStr
            } catch (e: Exception) { tglStr }

            labels.add(label)

            val orgCount = (stat["organikEmptyCount"] as? Number)?.toFloat() ?: 0f
            val nonOrgCount = (stat["nonOrganikEmptyCount"] as? Number)?.toFloat() ?: 0f

            entriesOrganik.add(BarEntry(index.toFloat(), orgCount))
            entriesNonOrganik.add(BarEntry(index.toFloat(), nonOrgCount))
        }

        val setOrganik = BarDataSet(entriesOrganik, "Organik")
        setOrganik.color = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.primary)
        setOrganik.valueTextColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary)
        setOrganik.valueTextSize = 10f

        val setNonOrganik = BarDataSet(entriesNonOrganik, "Non-Organik")
        setNonOrganik.color = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.amber_500)
        setNonOrganik.valueTextColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary)
        setNonOrganik.valueTextSize = 10f

        val data = BarData(setOrganik, setNonOrganik)
        data.barWidth = 0.35f

        binding.barChartStatistik.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChartStatistik.data = data
        
        val groupSpace = 0.2f
        val barSpace = 0.05f
        binding.barChartStatistik.groupBars(-0.5f, groupSpace, barSpace)
        binding.barChartStatistik.xAxis.axisMinimum = -0.5f
        binding.barChartStatistik.xAxis.axisMaximum = labels.size - 0.5f

        binding.barChartStatistik.animateY(1000)
        binding.barChartStatistik.invalidate()
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
        val etTrigLuarOrg = view.findViewById<TextInputEditText>(R.id.etTrigLuarOrg)
        val etEchoLuarOrg = view.findViewById<TextInputEditText>(R.id.etEchoLuarOrg)
        val etServoOrg = view.findViewById<TextInputEditText>(R.id.etServoOrg)
        val etTrigNonOrg = view.findViewById<TextInputEditText>(R.id.etTrigNonOrg)
        val etEchoNonOrg = view.findViewById<TextInputEditText>(R.id.etEchoNonOrg)
        val etTrigLuarNonOrg = view.findViewById<TextInputEditText>(R.id.etTrigLuarNonOrg)
        val etEchoLuarNonOrg = view.findViewById<TextInputEditText>(R.id.etEchoLuarNonOrg)
        val etServoNonOrg = view.findViewById<TextInputEditText>(R.id.etServoNonOrg)
        val etTinggiTong = view.findViewById<TextInputEditText>(R.id.etTinggiTong)
        val etBatasPenuh = view.findViewById<TextInputEditText>(R.id.etBatasPenuh)

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
                trigOrganik = etTrigOrg.text.toString().toIntOrNull() ?: 5,
                echoOrganik = etEchoOrg.text.toString().toIntOrNull() ?: 18,
                trigNonOrganik = etTrigNonOrg.text.toString().toIntOrNull() ?: 16,
                echoNonOrganik = etEchoNonOrg.text.toString().toIntOrNull() ?: 17,
                trigLuarOrganik = etTrigLuarOrg.text.toString().toIntOrNull() ?: 22,
                echoLuarOrganik = etEchoLuarOrg.text.toString().toIntOrNull() ?: 23,
                trigLuarNonOrganik = etTrigLuarNonOrg.text.toString().toIntOrNull() ?: 19,
                echoLuarNonOrganik = etEchoLuarNonOrg.text.toString().toIntOrNull() ?: 21,
                servoOrganik = etServoOrg.text.toString().toIntOrNull() ?: 4,
                servoNonOrganik = etServoNonOrg.text.toString().toIntOrNull() ?: 15
            )
            
            val tinggi = etTinggiTong.text.toString().toDoubleOrNull() ?: 50.0
            val batas = etBatasPenuh.text.toString().toDoubleOrNull() ?: 5.0

            FirebaseManager.addDevice(id, nama, pins)
            FirebaseManager.updateDeviceConfig(id, tinggi, batas)
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
        val ivDeviceQrCode = view.findViewById<android.widget.ImageView>(R.id.ivDeviceQrCode)
        val tvDeviceId = view.findViewById<android.widget.TextView>(R.id.tvDeviceId)
        
        tvDetailDeviceName.text = device.nama
        tvDeviceId.text = "ID: ${device.id}"

        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(device.id, BarcodeFormat.QR_CODE, 400, 400)
            ivDeviceQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (device.statusKoneksi == "ONLINE") {
            tvConnectionStatus.text = "ONLINE"
            tvConnectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.green_600))
        } else {
            tvConnectionStatus.text = "OFFLINE"
            tvConnectionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.red_600))
        }

        val etDetailTrigOrg = view.findViewById<TextInputEditText>(R.id.etDetailTrigOrg)
        val etDetailEchoOrg = view.findViewById<TextInputEditText>(R.id.etDetailEchoOrg)
        val etDetailTrigLuarOrg = view.findViewById<TextInputEditText>(R.id.etDetailTrigLuarOrg)
        val etDetailEchoLuarOrg = view.findViewById<TextInputEditText>(R.id.etDetailEchoLuarOrg)
        val etDetailServoOrg = view.findViewById<TextInputEditText>(R.id.etDetailServoOrg)
        val etDetailTrigNonOrg = view.findViewById<TextInputEditText>(R.id.etDetailTrigNonOrg)
        val etDetailEchoNonOrg = view.findViewById<TextInputEditText>(R.id.etDetailEchoNonOrg)
        val etDetailTrigLuarNonOrg = view.findViewById<TextInputEditText>(R.id.etDetailTrigLuarNonOrg)
        val etDetailEchoLuarNonOrg = view.findViewById<TextInputEditText>(R.id.etDetailEchoLuarNonOrg)
        val etDetailServoNonOrg = view.findViewById<TextInputEditText>(R.id.etDetailServoNonOrg)
        val etDetailTinggiTong = view.findViewById<TextInputEditText>(R.id.etDetailTinggiTong)
        val etDetailBatasPenuh = view.findViewById<TextInputEditText>(R.id.etDetailBatasPenuh)

        etDetailTrigOrg.setText(device.config.pins.trigOrganik.toString())
        etDetailEchoOrg.setText(device.config.pins.echoOrganik.toString())
        etDetailTrigLuarOrg.setText(device.config.pins.trigLuarOrganik.toString())
        etDetailEchoLuarOrg.setText(device.config.pins.echoLuarOrganik.toString())
        etDetailServoOrg.setText(device.config.pins.servoOrganik.toString())
        etDetailTrigNonOrg.setText(device.config.pins.trigNonOrganik.toString())
        etDetailEchoNonOrg.setText(device.config.pins.echoNonOrganik.toString())
        etDetailTrigLuarNonOrg.setText(device.config.pins.trigLuarNonOrganik.toString())
        etDetailEchoLuarNonOrg.setText(device.config.pins.echoLuarNonOrganik.toString())
        etDetailServoNonOrg.setText(device.config.pins.servoNonOrganik.toString())
        etDetailTinggiTong.setText(device.config.tinggiTong.toString())
        etDetailBatasPenuh.setText(device.config.batasPenuh.toString())

        view.findViewById<MaterialButton>(R.id.btnUpdatePins).setOnClickListener {
            val pins = PinConfig(
                trigOrganik = etDetailTrigOrg.text.toString().toIntOrNull() ?: 5,
                echoOrganik = etDetailEchoOrg.text.toString().toIntOrNull() ?: 18,
                trigNonOrganik = etDetailTrigNonOrg.text.toString().toIntOrNull() ?: 16,
                echoNonOrganik = etDetailEchoNonOrg.text.toString().toIntOrNull() ?: 17,
                trigLuarOrganik = etDetailTrigLuarOrg.text.toString().toIntOrNull() ?: 22,
                echoLuarOrganik = etDetailEchoLuarOrg.text.toString().toIntOrNull() ?: 23,
                trigLuarNonOrganik = etDetailTrigLuarNonOrg.text.toString().toIntOrNull() ?: 19,
                echoLuarNonOrganik = etDetailEchoLuarNonOrg.text.toString().toIntOrNull() ?: 21,
                servoOrganik = etDetailServoOrg.text.toString().toIntOrNull() ?: 4,
                servoNonOrganik = etDetailServoNonOrg.text.toString().toIntOrNull() ?: 15
            )
            val tinggi = etDetailTinggiTong.text.toString().toDoubleOrNull() ?: 50.0
            val batas = etDetailBatasPenuh.text.toString().toDoubleOrNull() ?: 5.0
            
            FirebaseManager.updateDevicePins(device.id, pins)
            FirebaseManager.updateDeviceConfig(device.id, tinggi, batas)
            Toast.makeText(requireContext(), "Konfigurasi berhasil diperbarui", Toast.LENGTH_SHORT).show()
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

    @Suppress("DEPRECATION")
    private fun checkBluetoothAndSend(ssid: String, pass: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        val devices = bluetoothHelper.getPairedDevices()

        if (devices.isEmpty()) {
            Toast.makeText(context, "Tidak ada perangkat Bluetooth yang terpasang (paired). Silakan pasangkan dulu di Pengaturan HP Anda.", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = devices.map { it.name ?: "Unknown Device (${it.address})" }.toTypedArray()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Pilih Perangkat (Sudah Paired)")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = devices[which]
                Toast.makeText(context, "Menghubungkan ke ${selectedDevice.name}...", Toast.LENGTH_SHORT).show()
                
                bluetoothHelper.connect(selectedDevice) { success, message ->
                    activity?.runOnUiThread {
                        if (success) {
                            val configStr = "SET_WIFI:$ssid,$pass\n"
                            if (bluetoothHelper.sendData(configStr)) {
                                Toast.makeText(context, "Konfigurasi terkirim!", Toast.LENGTH_LONG).show()
                                // Beri jeda sedikit agar ESP32 sempat membaca seluruh data sebelum koneksi ditutup
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    bluetoothHelper.close()
                                }, 1500)
                            } else {
                                Toast.makeText(context, "Gagal mengirim data", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
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
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 12f
        }

        val tvMsg = android.widget.TextView(context).apply {
            text = message
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
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
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_tertiary))
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
        binding.lottieEmptyState.visibility = android.view.View.VISIBLE
        binding.tvEmptyStateText.visibility = android.view.View.VISIBLE
    }

    private fun hideEmptyActivity() {
        binding.lottieEmptyState.visibility = android.view.View.GONE
        binding.tvEmptyStateText.visibility = android.view.View.GONE
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

    // ==========================================
    // Ekspor PDF (Laporan Aktivitas)
    // ==========================================
    private fun exportHistoryToPdf() {
        if (lastKnownHistory.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data riwayat untuk diekspor", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Menyiapkan PDF...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val titlePaint = android.graphics.Paint().apply {
                    color = Color.BLACK
                    textSize = 20f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }

                val textPaint = android.graphics.Paint().apply {
                    color = Color.BLACK
                    textSize = 12f
                }

                val borderPaint = android.graphics.Paint().apply {
                    color = Color.BLACK
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1f
                }

                var yPosition = 50f
                canvas.drawText("Laporan Aktivitas CleanBanar", 50f, yPosition, titlePaint)
                
                yPosition += 30f
                val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault())
                canvas.drawText("Dicetak pada: ${sdf.format(Date())}", 50f, yPosition, textPaint)
                
                yPosition += 50f
                
                // Draw Table Header
                val colWaktu = 50f
                val colPetugas = 200f
                val colAksi = 350f
                val colTipe = 450f

                canvas.drawText("Waktu", colWaktu, yPosition, titlePaint)
                canvas.drawText("Judul", colPetugas, yPosition, titlePaint)
                canvas.drawText("Pesan", colAksi, yPosition, titlePaint)
                
                yPosition += 10f
                canvas.drawLine(50f, yPosition, 545f, yPosition, borderPaint)
                yPosition += 20f

                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                // Draw Table Content (Max 20 for one page simplicity)
                for (item in lastKnownHistory.take(20)) {
                    val waktu = item["waktu"] as? Long ?: 0L
                    val judul = item["judul"] as? String ?: "-"
                    val pesan = item["pesan"] as? String ?: "-"

                    canvas.drawText(dateFormat.format(Date(waktu)), colWaktu, yPosition, textPaint)
                    canvas.drawText(if (judul.length > 20) judul.substring(0, 20) + "..." else judul, colPetugas, yPosition, textPaint)
                    canvas.drawText(if (pesan.length > 20) pesan.substring(0, 20) + "..." else pesan, colAksi, yPosition, textPaint)

                    yPosition += 25f

                    // Jika penuh, hentikan
                    if (yPosition > 800f) {
                        canvas.drawText("... dan seterusnya", 50f, yPosition, textPaint)
                        break
                    }
                }

                pdfDocument.finishPage(page)

                val fileName = "Laporan_Aktivitas_CleanBanar_${System.currentTimeMillis()}.pdf"
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = requireContext().contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                    }
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(dir, fileName)
                    pdfDocument.writeTo(FileOutputStream(file))
                }

                pdfDocument.close()

                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "PDF berhasil disimpan di folder Download", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Gagal membuat PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
