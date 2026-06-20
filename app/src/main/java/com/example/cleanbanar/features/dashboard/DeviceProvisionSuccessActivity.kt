package com.example.cleanbanar.features.dashboard

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cleanbanar.R
import com.example.cleanbanar.core.utils.BluetoothHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DeviceProvisionSuccessActivity : AppCompatActivity() {

    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var tvLastSeen: TextView
    private lateinit var tvSSID: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvSignal: TextView
    private lateinit var tvCloudConnection: TextView
    private lateinit var tvOnlineStatus: TextView
    private lateinit var dotOnlineStatus: View

    // Timeline elements
    private lateinit var dotRestart: View
    private lateinit var tvTitleRestart: TextView
    private lateinit var tvDescRestart: TextView
    private lateinit var dotOnline: View
    private lateinit var tvTitleOnline: TextView
    private lateinit var tvDescOnline: TextView

    private lateinit var btnSelesai: MaterialButton
    private lateinit var btnReconfigure: MaterialCardView
    private lateinit var btnRestart: MaterialCardView
    private lateinit var pbRestartLoading: android.widget.ProgressBar
    private lateinit var tvRestartLabel: TextView

    private var deviceId: String = ""
    private var ssid: String = ""
    private var isRestartLoading = false

    private val bluetoothHelper = BluetoothHelper()
    private var pendingBtSsid: String? = null
    private var pendingBtPass: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_provision_success)

        deviceId = intent.getStringExtra("device_id") ?: ""
        ssid = intent.getStringExtra("ssid") ?: "-"

        // Fix: padding atas agar tidak tertutup status bar HP
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        val isProvisioning = intent.getBooleanExtra("is_provisioning", true)
        if (!isProvisioning) {
            findViewById<View>(R.id.llSuccessHeader)?.visibility = View.GONE
            val contentContainer = findViewById<View>(R.id.llContentContainer)
            val params = contentContainer?.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                val marginInPx = (16 * resources.displayMetrics.density).toInt()
                params.topMargin = marginInPx
                contentContainer.layoutParams = params
            }
        }

        initViews()
        setupListeners()
        startMonitoring()
    }

    private fun initViews() {
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        tvLastSeen = findViewById(R.id.tvLastSeen)
        tvSSID = findViewById(R.id.tvSSID)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvSignal = findViewById(R.id.tvSignal)
        tvCloudConnection = findViewById(R.id.tvCloudConnection)
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus)
        dotOnlineStatus = findViewById(R.id.dotOnlineStatus)

        dotRestart = findViewById(R.id.dotRestart)
        tvTitleRestart = findViewById(R.id.tvTitleRestart)
        tvDescRestart = findViewById(R.id.tvDescRestart)
        dotOnline = findViewById(R.id.dotOnline)
        tvTitleOnline = findViewById(R.id.tvTitleOnline)
        tvDescOnline = findViewById(R.id.tvDescOnline)

        btnSelesai = findViewById(R.id.btnSelesai)
        btnReconfigure = findViewById(R.id.btnReconfigure)
        btnRestart = findViewById(R.id.btnRestart)
        pbRestartLoading = findViewById(R.id.pbRestart)
        tvRestartLabel = btnRestart.findViewById(R.id.tvRestartLabel)

        tvDeviceId.text = deviceId
        tvSSID.text = ssid
    }

    private fun setupListeners() {
        btnSelesai.setOnClickListener {
            finish()
        }

        // === CONFIG ULANG: buka dialog provisioning WiFi via Bluetooth ===
        btnReconfigure.setOnClickListener {
            showReconfigureDialog()
        }

        // === RESTART: kirim perintah Firebase + loading + riwayat ===
        btnRestart.setOnClickListener {
            if (isRestartLoading) return@setOnClickListener
            if (deviceId.isEmpty()) {
                showSnackbar("ID Perangkat tidak ditemukan", isError = true)
                return@setOnClickListener
            }
            performRestart()
        }
    }

    // ---------------------------------------------------------------
    // RESTART
    // ---------------------------------------------------------------
    private fun performRestart() {
        isRestartLoading = true
        setRestartButtonLoading(true)

        val ref = FirebaseDatabase.getInstance()
            .getReference("cleanbanar/devices/$deviceId/perintah/restart")

        ref.setValue(true)
            .addOnSuccessListener {
                // Catat ke Riwayat Aktivitas (Firebase notifications)
                logActivity(
                    judul = "Restart Alat",
                    pesan = "Perintah restart berhasil dikirim ke perangkat $deviceId",
                    tipe = "info"
                )

                // Update timeline
                dotRestart.setBackgroundResource(R.drawable.dot_timeline_green)
                tvTitleRestart.setTextColor(ContextCompat.getColor(this, R.color.gray_900))
                dotOnline.setBackgroundResource(R.drawable.dot_timeline_gray)
                tvTitleOnline.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
                tvOnlineStatus.text = "Restarting..."
                dotOnlineStatus.setBackgroundResource(R.drawable.dot_timeline_gray)

                setRestartButtonLoading(false)
                isRestartLoading = false
                showSnackbar("✓ Perintah restart berhasil dikirim!", isError = false)

                // Auto-reset flag restart di Firebase setelah 30 detik agar tidak loop
                Handler(Looper.getMainLooper()).postDelayed({
                    FirebaseDatabase.getInstance()
                        .getReference("cleanbanar/devices/$deviceId/perintah/restart")
                        .setValue(false)
                }, 30_000)
            }
            .addOnFailureListener { e ->
                setRestartButtonLoading(false)
                isRestartLoading = false
                showSnackbar("✗ Gagal mengirim restart: ${e.message}", isError = true)
            }
    }

    private fun setRestartButtonLoading(loading: Boolean) {
        if (loading) {
            tvRestartLabel.text = "Mengirim..."
            pbRestartLoading.visibility = View.VISIBLE
            btnRestart.alpha = 0.7f
            btnRestart.isClickable = false
        } else {
            tvRestartLabel.text = "Restart Alat"
            pbRestartLoading.visibility = View.GONE
            btnRestart.alpha = 1.0f
            btnRestart.isClickable = true
        }
    }

    // ---------------------------------------------------------------
    // CONFIG ULANG
    // ---------------------------------------------------------------
    private fun showReconfigureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reconfigure_wifi, null)
        val etSsid = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.etReconfigSsid)
        val etPass = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReconfigPassword)
        val btnSend = dialogView.findViewById<MaterialButton>(R.id.btnReconfigSend)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnReconfigCancel)

        // Isi SSID awal dari data yang sudah ada
        if (ssid.isNotEmpty() && ssid != "-") etSsid.setText(ssid)

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { alertDialog.dismiss() }

        btnSend.setOnClickListener {
            val inputSsid = etSsid.text.toString().trim()
            val inputPass = etPass.text.toString().trim()
            if (inputSsid.isEmpty()) {
                etSsid.error = "SSID tidak boleh kosong"
                return@setOnClickListener
            }
            alertDialog.dismiss()
            pendingBtSsid = inputSsid
            pendingBtPass = inputPass
            checkBluetoothAndSend(inputSsid, inputPass)
        }

        alertDialog.show()
    }

    private fun checkBluetoothAndSend(ssidVal: String, passVal: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            @Suppress("DEPRECATION")
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                2001
            )
            return
        }
        performBluetoothSend(ssidVal, passVal)
    }

    private fun performBluetoothSend(ssidVal: String, passVal: String) {
        val pairedDevices = bluetoothHelper.getPairedDevices()
        if (pairedDevices.isEmpty()) {
            showSnackbar("Tidak ada perangkat Bluetooth yang terpasang", isError = true)
            return
        }
        val deviceNames = pairedDevices.map { it.name ?: it.address }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Pilih Perangkat Bluetooth")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = pairedDevices[which]
                val configStr = "SET_WIFI:$ssidVal,$passVal,$deviceId\n"

                // Catat ke Riwayat Aktivitas
                logActivity(
                    judul = "Config Ulang WiFi",
                    pesan = "Mengirim konfigurasi WiFi baru ($ssidVal) ke perangkat $deviceId via Bluetooth",
                    tipe = "info"
                )

                val intent = Intent(this, BluetoothProgressActivity::class.java)
                intent.putExtra("bluetooth_device", selectedDevice)
                intent.putExtra("config_str", configStr)
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val s = pendingBtSsid ?: return
                val p = pendingBtPass ?: ""
                performBluetoothSend(s, p)
            } else {
                showSnackbar("Izin Bluetooth ditolak", isError = true)
            }
        }
    }

    // ---------------------------------------------------------------
    // MONITORING FIREBASE
    // ---------------------------------------------------------------
    private fun startMonitoring() {
        if (deviceId.isEmpty()) return

        dotRestart.setBackgroundResource(R.drawable.dot_timeline_green)
        tvTitleRestart.setTextColor(ContextCompat.getColor(this, R.color.gray_900))
        tvDescRestart.text = "ESP32 sedang mencoba terhubung ke WiFi..."

        val ref = FirebaseDatabase.getInstance().getReference("cleanbanar/devices/$deviceId")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                tvDeviceName.text = snapshot.child("nama").getValue(String::class.java) ?: "-"

                val ipAddr = snapshot.child("ipAddress").getValue(String::class.java) ?: "-"
                if (ipAddr != "-") tvIpAddress.text = ipAddr

                val fbSsid = snapshot.child("ssid").getValue(String::class.java)
                if (!fbSsid.isNullOrEmpty() && fbSsid != "-") tvSSID.text = fbSsid

                val signal = snapshot.child("kekuatanSinyal").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 0
                if (signal < 0) tvSignal.text = "$signal dBm"

                val statusKoneksi = snapshot.child("statusKoneksi").getValue(String::class.java) ?: "OFFLINE"
                val isOnline = statusKoneksi == "ONLINE"

                if (isOnline) {
                    tvCloudConnection.text = "Online"
                    tvCloudConnection.setTextColor(ContextCompat.getColor(this@DeviceProvisionSuccessActivity, R.color.primary))
                    tvOnlineStatus.text = "Online"
                    tvOnlineStatus.setTextColor(ContextCompat.getColor(this@DeviceProvisionSuccessActivity, R.color.primary))
                    dotOnlineStatus.setBackgroundResource(R.drawable.dot_timeline_green)
                    dotOnline.setBackgroundResource(R.drawable.dot_timeline_green)
                    tvTitleOnline.setTextColor(ContextCompat.getColor(this@DeviceProvisionSuccessActivity, R.color.gray_900))
                    tvDescOnline.text = "Berhasil terhubung ke Cloud"
                } else {
                    tvCloudConnection.text = "Offline"
                    tvCloudConnection.setTextColor(ContextCompat.getColor(this@DeviceProvisionSuccessActivity, R.color.red_600))
                    tvOnlineStatus.text = "Offline"
                    tvOnlineStatus.setTextColor(ContextCompat.getColor(this@DeviceProvisionSuccessActivity, R.color.red_600))
                    dotOnlineStatus.setBackgroundResource(R.drawable.dot_timeline_red)
                }

                val terakhirTerlihat = snapshot.child("terakhirTerlihat").getValue(Any::class.java)?.toString()?.toLongOrNull() ?: 0L
                tvLastSeen.text = formatTime(terakhirTerlihat)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ---------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------
    private fun logActivity(judul: String, pesan: String, tipe: String) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("cleanbanar/notifications")
            .push()
        ref.setValue(mapOf(
            "judul" to judul,
            "pesan" to pesan,
            "tipe" to tipe,
            "waktu" to System.currentTimeMillis(),
            "sudahDibaca" to false
        ))
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        val rootView = window.decorView.rootView
        val snack = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
        val bgColor = if (isError) ContextCompat.getColor(this, R.color.red_600)
                      else ContextCompat.getColor(this, R.color.emerald_600)
        snack.setBackgroundTint(bgColor)
        snack.setTextColor(ContextCompat.getColor(this, R.color.white))
        snack.show()
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "Belum sinkron"
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
        return sdf.format(java.util.Date(millis))
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothHelper.close()
    }
}
