package com.example.cleanbanar.features.dashboard

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.DeviceModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

    private var deviceId: String = ""
    private var ssid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_provision_success)

        deviceId = intent.getStringExtra("device_id") ?: ""
        ssid = intent.getStringExtra("ssid") ?: "-"

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

        tvDeviceId.text = deviceId
        tvSSID.text = ssid
    }

    private fun setupListeners() {
        btnSelesai.setOnClickListener {
            finish()
        }

        btnReconfigure.setOnClickListener {
            // Kembali ke dashboard untuk memulai ulang proses
            finish()
        }

        btnRestart.setOnClickListener {
            if (deviceId.isNotEmpty()) {
                val ref = FirebaseDatabase.getInstance().getReference("cleanbanar/devices/$deviceId/perintah/restart")
                ref.setValue(true).addOnSuccessListener {
                    Toast.makeText(this, "Perintah restart dikirim ke alat!", Toast.LENGTH_SHORT).show()
                    // Reset timeline
                    dotRestart.setBackgroundResource(R.drawable.dot_timeline_green)
                    tvTitleRestart.setTextColor(ContextCompat.getColor(this, R.color.gray_900))
                    dotOnline.setBackgroundResource(R.drawable.dot_timeline_gray)
                    tvTitleOnline.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
                    tvOnlineStatus.text = "Restarting..."
                    dotOnlineStatus.setBackgroundResource(R.drawable.dot_timeline_gray)
                }
            }
        }
    }

    private fun startMonitoring() {
        if (deviceId.isEmpty()) return

        // Ubah timeline ke step 2: Menunggu Restart
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

                    // Update Timeline Step 3
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

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "Belum sinkron"
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
        return sdf.format(java.util.Date(millis))
    }
}
