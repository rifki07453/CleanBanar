package com.example.cleanbanar.features.dashboard

import android.content.Intent

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.print.PrintHelper
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.ValueEventListener
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {
    
    private val bluetoothHelper = BluetoothHelper()
    private lateinit var authManager: AuthManager
    
    private var devicesListener: ValueEventListener? = null
    private var notifListener: ValueEventListener? = null

    
    private var cachedDevices = listOf<DeviceModel>()
    private var lastKnownHistory: List<Map<String, Any>> = emptyList()

    // Untuk retry aksi setelah izin diberikan
    private var pendingWifiSsidView: com.google.android.material.textfield.MaterialAutoCompleteTextView? = null
    private var pendingBtSsid: String? = null
    private var pendingBtPass: String? = null
    private var pendingBtDeviceId: String? = null

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

        // Terapkan animasi bernapas pada background hijau header
        val headerBlock = view?.findViewById<android.widget.FrameLayout>(R.id.headerBlock)
        if (headerBlock != null) {
            com.example.cleanbanar.core.utils.AnimationUtils.applyHeaderBreathingEffect(headerBlock)
        }
    }



    override fun observeData() {
        devicesListener = FirebaseManager.listenDevices { devices ->
            if (!isAdded) return@listenDevices
            cachedDevices = devices
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
        val etBatasJarakTangan = view.findViewById<TextInputEditText>(R.id.etBatasJarakTangan)

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
            val jarakTangan = etBatasJarakTangan.text.toString().toDoubleOrNull() ?: 15.0

            FirebaseManager.addDevice(id, nama, pins)
            FirebaseManager.updateDeviceConfig(id, tinggi, batas, jarakTangan)
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
        val btnPrintQr = view.findViewById<MaterialButton>(R.id.btnPrintQr)
        val btnViewOverview = view.findViewById<MaterialButton>(R.id.btnViewOverview)
        
        tvDetailDeviceName.text = device.nama
        tvDeviceId.text = "ID: ${device.id}"

        var generatedBitmap: Bitmap? = null
        try {
            val barcodeEncoder = BarcodeEncoder()
            generatedBitmap = barcodeEncoder.encodeBitmap(device.id, BarcodeFormat.QR_CODE, 400, 400)
            ivDeviceQrCode.setImageBitmap(generatedBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        btnPrintQr.setOnClickListener {
            generatedBitmap?.let { bmp ->
                printQrCode(device, bmp)
            } ?: Toast.makeText(requireContext(), "QR Code belum tersedia", Toast.LENGTH_SHORT).show()
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
        val etDetailBatasJarakTangan = view.findViewById<TextInputEditText>(R.id.etDetailBatasJarakTangan)

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
        etDetailBatasJarakTangan.setText(device.config.batasJarakTangan.toString())

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
            val jarakTangan = etDetailBatasJarakTangan.text.toString().toDoubleOrNull() ?: 15.0
            
            FirebaseManager.updateDevicePins(device.id, pins)
            FirebaseManager.updateDeviceConfig(device.id, tinggi, batas, jarakTangan)
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

        val etSsid = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.etSsid)
        val etPassword = view.findViewById<TextInputEditText>(R.id.etPassword)
        val btnSendConfig = view.findViewById<MaterialButton>(R.id.btnSendConfig)

        btnViewOverview.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), DeviceProvisionSuccessActivity::class.java)
            intent.putExtra("device_id", device.id)
            intent.putExtra("ssid", device.ssid)
            intent.putExtra("is_provisioning", false)
            startActivity(intent)
        }

        setupWifiDropdown(etSsid)

        btnSendConfig.setOnClickListener {
            val ssid = etSsid.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (ssid.isEmpty()) {
                Toast.makeText(context, "Nama WiFi tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkBluetoothAndSend(ssid, pass, device.id)
        }

        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun checkBluetoothAndSend(ssid: String, pass: String, deviceId: String) {
        pendingBtSsid = ssid
        pendingBtPass = pass
        pendingBtDeviceId = deviceId

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        performBluetoothConnectAndSend(ssid, pass, deviceId)
    }

    private fun performBluetoothConnectAndSend(ssid: String, pass: String, deviceId: String) {
        val devices = bluetoothHelper.getPairedDevices()
        if (devices.isEmpty()) {
            Toast.makeText(context, "Tidak ada perangkat Bluetooth yang terpasang (paired).", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = devices.map { it.name ?: it.address }.toTypedArray()
        val pairedList = devices

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pilih Perangkat Bluetooth")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = pairedList[which]
                val configStr = "SET_WIFI:$ssid,$pass,$deviceId\n"
                
                val intent = Intent(requireContext(), BluetoothProgressActivity::class.java)
                intent.putExtra("bluetooth_device", selectedDevice)
                intent.putExtra("config_str", configStr)
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupWifiDropdown(etSsid: com.google.android.material.textfield.MaterialAutoCompleteTextView) {
        pendingWifiSsidView = etSsid
        
        val locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        if (!isGpsEnabled) {
            Toast.makeText(requireContext(), "Harap nyalakan GPS/Lokasi di HP Anda untuk memindai daftar WiFi", Toast.LENGTH_LONG).show()
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE), 1002)
        } else {
            val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            // 1. Ambil WiFi yang sedang terhubung
            val info = wifiManager.connectionInfo
            var currentSsid = info.ssid ?: ""
            if (currentSsid.startsWith("\"") && currentSsid.endsWith("\"")) {
                currentSsid = currentSsid.substring(1, currentSsid.length - 1)
            }
            if (currentSsid != "<unknown ssid>" && currentSsid.isNotEmpty()) {
                etSsid.setText(currentSsid)
            }
        }
        
        etSsid.isFocusable = false
        etSsid.isClickable = true
        etSsid.isCursorVisible = false
        
        etSsid.setOnClickListener {
            showWifiListDialog(etSsid)
        }
    }

    private fun getScannedWifiList(): List<String> {
        val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val ssidList = mutableListOf<String>()
        
        try {
            val results = wifiManager.scanResults
            val scannedList = results.mapNotNull { it.SSID }.filter { it.isNotEmpty() }.distinct()
            ssidList.addAll(scannedList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ssidList
    }

    private fun showWifiListDialog(etSsid: com.google.android.material.textfield.MaterialAutoCompleteTextView) {
        val dialogView = android.view.LayoutInflater.from(requireContext()).inflate(R.layout.dialog_wifi_list, null)
        val btnRefreshWifi = dialogView.findViewById<android.widget.ImageView>(R.id.btnRefreshWifi)
        val pbWifiLoading = dialogView.findViewById<android.widget.ProgressBar>(R.id.pbWifiLoading)
        val lvWifi = dialogView.findViewById<android.widget.ListView>(R.id.lvWifi)
        
        val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .show()
            
        fun updateWifiList() {
            pbWifiLoading.visibility = android.view.View.VISIBLE
            lvWifi.visibility = android.view.View.GONE
            
            // Lakukan scan
            try {
                wifiManager.startScan()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isAdded) return@postDelayed
                
                pbWifiLoading.visibility = android.view.View.GONE
                lvWifi.visibility = android.view.View.VISIBLE
                
                val list = getScannedWifiList()
                if (list.isEmpty()) {
                    val locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                    if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                        Toast.makeText(requireContext(), "GPS mati. Nyalakan GPS agar daftar WiFi muncul.", Toast.LENGTH_SHORT).show()
                    }
                }
                
                val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list)
                lvWifi.adapter = adapter
            }, 1500) // Delay sedikit agar loading terlihat dan scan selesai
        }
        
        btnRefreshWifi.setOnClickListener {
            updateWifiList()
        }
        
        lvWifi.setOnItemClickListener { _, _, position, _ ->
            val selectedSsid = lvWifi.adapter.getItem(position) as String
            etSsid.setText(selectedSsid)
            dialog.dismiss()
        }
        
        // Initial load
        updateWifiList()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) { // Bluetooth
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingBtSsid != null && pendingBtPass != null && pendingBtDeviceId != null) {
                    performBluetoothConnectAndSend(pendingBtSsid!!, pendingBtPass!!, pendingBtDeviceId!!)
                }
            } else {
                Toast.makeText(requireContext(), "Izin Bluetooth ditolak", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 1002) { // WiFi / Location
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingWifiSsidView?.let { 
                    val wifiManager = requireContext().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    try {
                        wifiManager.startScan()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

            } else {
                Toast.makeText(requireContext(), "Izin Lokasi ditolak, daftar WiFi tidak dapat dimuat otomatis", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun printQrCode(device: DeviceModel, qrBitmap: Bitmap) {
        val printHelper = PrintHelper(requireContext())
        printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
        
        val width = 600
        val height = 800
        val printBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(printBitmap)
        canvas.drawColor(Color.WHITE)
        
        val paint = Paint().apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        paint.textSize = 40f
        paint.isFakeBoldText = true
        canvas.drawText("CleanBanar", width / 2f, 120f, paint)
        
        paint.textSize = 28f
        paint.isFakeBoldText = false
        canvas.drawText(device.nama, width / 2f, 180f, paint)
        
        val qrSize = 400
        val scaledQr = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, false)
        canvas.drawBitmap(scaledQr, (width - qrSize) / 2f, 220f, null)
        
        paint.textSize = 24f
        paint.color = Color.DKGRAY
        paint.isFakeBoldText = true
        canvas.drawText("ID: ${device.id}", width / 2f, 660f, paint)
        
        paint.textSize = 22f
        paint.color = Color.BLACK
        paint.isFakeBoldText = false
        canvas.drawText("Pindai QR ini di aplikasi untuk", width / 2f, 720f, paint)
        canvas.drawText("memeriksa status tempat sampah", width / 2f, 755f, paint)
        
        printHelper.printBitmap("QR Code - ${device.nama}", printBitmap)
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
        com.example.cleanbanar.core.utils.AnimationUtils.applyFloatingEffect(binding.lottieEmptyState)
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

}
