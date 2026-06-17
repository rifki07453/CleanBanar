package com.example.cleanbanar.features.dashboard

import android.bluetooth.BluetoothDevice
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
                            showSuccess()
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

    private fun showSuccess() {
        progressBar.visibility = View.GONE
        ivStatusIcon.setImageResource(R.drawable.ic_check_circle_24dp) // Pastikan drawable ini ada atau gunakan built-in
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.emerald_600))
        
        tvStatusTitle.text = "Konfigurasi Berhasil Dikirim!"
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.emerald_600))
        tvStatusMessage.text = "Alat akan otomatis me-restart dan menghubungkan ke WiFi baru."
        
        btnKembali.visibility = View.VISIBLE
        btnKembali.text = "Kembali ke Dashboard"
        
        // Tutup koneksi setelah berhasil
        Handler(Looper.getMainLooper()).postDelayed({
            bluetoothHelper.close()
        }, 1000)
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
