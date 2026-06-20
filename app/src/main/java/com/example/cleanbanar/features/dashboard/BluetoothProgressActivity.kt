package com.example.cleanbanar.features.dashboard

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cleanbanar.R
import com.example.cleanbanar.core.utils.BluetoothHelper
import com.google.android.material.button.MaterialButton

class BluetoothProgressActivity : AppCompatActivity() {

    private lateinit var ivStatusIcon: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusMessage: TextView
    private lateinit var btnKembali: MaterialButton

    private val bluetoothHelper = BluetoothHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_progress)

        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        progressBar = findViewById(R.id.progressBar)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusMessage = findViewById(R.id.tvStatusMessage)
        btnKembali = findViewById(R.id.btnKembali)

        btnKembali.setOnClickListener {
            finish()
        }

        val device = intent.getParcelableExtra<BluetoothDevice>("bluetooth_device")
        val configStr = intent.getStringExtra("config_str")

        if (device == null || configStr == null) {
            showError("Data Tidak Valid", "Perangkat atau konfigurasi tidak ditemukan.")
            return
        }

        startConnectionProcess(device, configStr)
    }

    private fun startConnectionProcess(device: BluetoothDevice, configStr: String) {
        tvStatusTitle.text = "Menghubungkan..."
        tvStatusMessage.text = "Sedang menghubungkan ke ${device.name}, mohon tunggu..."
        
        bluetoothHelper.connect(device) { success, message ->
            runOnUiThread {
                if (success) {
                    tvStatusTitle.text = "Mengirim Data..."
                    tvStatusMessage.text = "Berhasil terhubung. Sedang mengirim konfigurasi WiFi ke alat..."
                    
                    // Jeda sebentar agar UI update terlihat
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (bluetoothHelper.sendData(configStr)) {
                            showSuccess(configStr)
                        } else {
                            showError("Pengiriman Gagal", "Koneksi terputus saat mengirim data.")
                        }
                    }, 1000)
                } else {
                    showError("Koneksi Gagal", "Gagal terhubung ke alat. Pastikan alat menyala dan dalam jarak dekat. ($message)")
                }
            }
        }
    }

    private fun showSuccess(configStr: String) {
        progressBar.visibility = View.GONE
        ivStatusIcon.setImageResource(R.drawable.ic_check_circle_24dp)
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.emerald_600))
        
        tvStatusTitle.text = "Konfigurasi Berhasil Dikirim!"
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.emerald_600))
        tvStatusMessage.text = "Membuka halaman pemantauan..."
        
        btnKembali.visibility = View.GONE
        
        // Ekstrak SSID dan Device ID dari configStr ("SET_WIFI:ssid,pass,deviceId")
        var ssid = "-"
        var deviceId = ""
        try {
            val parts = configStr.substringAfter("SET_WIFI:").split(",")
            if (parts.size >= 3) {
                ssid = parts[0]
                deviceId = parts[2].trim()
            }
        } catch (e: Exception) {}

        Handler(Looper.getMainLooper()).postDelayed({
            bluetoothHelper.close()
            
            // Perbarui data WiFi di Firebase agar bersih & tidak menampilkan SSID/IP lama
            if (deviceId.isNotEmpty()) {
                val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("cleanbanar/devices/$deviceId")
                val updates = mapOf(
                    "statusKoneksi" to "OFFLINE",
                    "ipAddress" to "-",
                    "ssid" to ssid
                )
                dbRef.updateChildren(updates)
            }

            val intent = Intent(this, DeviceProvisionSuccessActivity::class.java)
            intent.putExtra("device_id", deviceId)
            intent.putExtra("ssid", ssid)
            startActivity(intent)
            finish()
        }, 1500)
    }

    private fun showError(title: String, message: String) {
        progressBar.visibility = View.GONE
        ivStatusIcon.setImageResource(R.drawable.ic_error_outline_24dp) // Pastikan drawable ini ada
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.red_600))
        
        tvStatusTitle.text = title
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.red_600))
        tvStatusMessage.text = message
        
        btnKembali.visibility = View.VISIBLE
        btnKembali.text = "Tutup Halaman"
        
        bluetoothHelper.close()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothHelper.close()
    }
}
