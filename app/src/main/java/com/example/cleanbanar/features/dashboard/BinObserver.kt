package com.example.cleanbanar.features.dashboard

import android.util.Log
import com.example.cleanbanar.core.data.FirebaseManager
import com.google.firebase.database.ValueEventListener

/**
 * BinObserver — Pemicu notifikasi berdasarkan ambang batas (threshold) untuk banyak perangkat.
 */
object BinObserver {

    private const val TAG = "BinObserver"

    private val previousOrganikMap = mutableMapOf<String, Int>()
    private val previousNonOrganikMap = mutableMapOf<String, Int>()

    private var devicesListener: ValueEventListener? = null
    private val binListeners = mutableMapOf<String, Pair<ValueEventListener, ValueEventListener>>()
    
    private var settingsListener: ValueEventListener? = null

    private var isRunning = false
    var currentSettings = FirebaseManager.NotificationSettings()
    private var currentUserId: String = ""

    private var appContext: android.content.Context? = null

    fun start(context: android.content.Context, userId: String = "") {
        appContext = context.applicationContext
        if (isRunning) return
        isRunning = true
        currentUserId = userId

        if (userId.isNotEmpty()) {
            settingsListener = FirebaseManager.listenNotificationSettings(userId) { settings ->
                currentSettings = settings
            }
        }

        devicesListener = FirebaseManager.listenDevices { devices ->
            val currentDeviceIds = devices.map { it.id }.toSet()
            
            // Hapus listener untuk perangkat yang sudah dihapus
            val removedDevices = binListeners.keys - currentDeviceIds
            for (deviceId in removedDevices) {
                val listeners = binListeners[deviceId]
                if (listeners != null) {
                    FirebaseManager.removeBinListener(deviceId, "organik", listeners.first)
                    FirebaseManager.removeBinListener(deviceId, "nonOrganik", listeners.second)
                }
                binListeners.remove(deviceId)
                previousOrganikMap.remove(deviceId)
                previousNonOrganikMap.remove(deviceId)
            }
            
            // Tambahkan listener untuk perangkat baru
            for (device in devices) {
                if (!binListeners.containsKey(device.id)) {
                    previousOrganikMap[device.id] = -1
                    previousNonOrganikMap[device.id] = -1
                    
                    val orgListener = FirebaseManager.listenBinStatus(device.id, "organik") { persentase, _, _, terakhirDikosongkan ->
                        handleThreshold(device.id, device.nama, "organik", persentase, previousOrganikMap[device.id] ?: -1)
                        handleStaleWaste(device.id, "organik", terakhirDikosongkan, 3)
                        previousOrganikMap[device.id] = persentase
                    }
                    
                    val nonOrgListener = FirebaseManager.listenBinStatus(device.id, "nonOrganik") { persentase, _, _, terakhirDikosongkan ->
                        handleThreshold(device.id, device.nama, "nonOrganik", persentase, previousNonOrganikMap[device.id] ?: -1)
                        handleStaleWaste(device.id, "nonOrganik", terakhirDikosongkan, 7)
                        previousNonOrganikMap[device.id] = persentase
                    }
                    
                    if (orgListener != null && nonOrgListener != null) {
                        binListeners[device.id] = Pair(orgListener, nonOrgListener)
                    }
                }
            }
        }
    }

    fun stop() {
        devicesListener?.let { FirebaseManager.removeDeviceListener(it) }
        devicesListener = null
        
        for ((deviceId, listeners) in binListeners) {
            FirebaseManager.removeBinListener(deviceId, "organik", listeners.first)
            FirebaseManager.removeBinListener(deviceId, "nonOrganik", listeners.second)
        }
        binListeners.clear()
        
        settingsListener?.let {
            if (currentUserId.isNotEmpty()) {
                FirebaseManager.removeNotificationSettingsListener(currentUserId, it)
            }
        }
        settingsListener = null
        
        previousOrganikMap.clear()
        previousNonOrganikMap.clear()
        isRunning = false
        appContext = null
    }

    private fun handleThreshold(deviceId: String, deviceName: String, binType: String, currentPercent: Int, previousPercent: Int) {
        if (previousPercent == -1) return
        if (currentPercent <= previousPercent) return
        if (currentPercent < 0 || currentPercent > 100) return

        val binLabel = if (binType == "organik") "Organik" else "Non-Organik"

        if (currentPercent >= 95 && previousPercent < 95) {
            FirebaseManager.addHistoryEntry(
                aksi = "alert",
                tipeSampah = binType,
                idPengguna = "SYSTEM",
                namaLengkap = "Sistem Otomatis ($deviceName)"
            )

            if (currentSettings.penuh) {
                val judul = "$binLabel Penuh!"
                val pesan = "Kapasitas $binLabel di $deviceName telah mencapai $currentPercent%. Segera kosongkan."
                FirebaseManager.addNotification(
                    judul = judul,
                    pesan = pesan,
                    tipe = "danger"
                )
                appContext?.let {
                    com.example.cleanbanar.core.utils.NotificationHelper.showNotification(it, judul, pesan)
                }
            }
            return
        }

        if (currentPercent >= 80 && previousPercent < 80) {
            if (currentSettings.hampirPenuh) {
                val judul = "$binLabel Hampir Penuh"
                val pesan = "Kapasitas $binLabel di $deviceName di angka $currentPercent%. Segera perhatikan."
                FirebaseManager.addNotification(
                    judul = judul,
                    pesan = pesan,
                    tipe = "warning"
                )
                appContext?.let {
                    com.example.cleanbanar.core.utils.NotificationHelper.showNotification(it, judul, pesan)
                }
            }
        }
    }

    private fun handleStaleWaste(deviceId: String, binType: String, terakhirDikosongkan: Long, maxDays: Int) {
        // Cek toggle Notifikasi Sistem
        if (!currentSettings.sistem) return
        if (terakhirDikosongkan == 0L || appContext == null) return
        
        val diff = System.currentTimeMillis() - terakhirDikosongkan
        val days = diff / 86_400_000L
        
        if (days >= maxDays) {
            val prefs = appContext!!.getSharedPreferences("CleanBanarPrefs", android.content.Context.MODE_PRIVATE)
            val lastNotifKey = "stale_notif_${deviceId}_$binType"
            val lastNotifTime = prefs.getLong(lastNotifKey, 0L)
            
            // Throttle 24 jam (86_400_000 ms)
            if (System.currentTimeMillis() - lastNotifTime > 86_400_000L) {
                val judul = "Peringatan Waktu Inap"
                val pesan = if (binType == "organik") {
                    "Sampah Organik di alat ini sudah $days hari belum dikosongkan dan mungkin mulai berbau."
                } else {
                    "Sampah Non-Organik di alat ini sudah menumpuk selama $days hari."
                }
                
                FirebaseManager.addNotification(judul, pesan, "warning")
                com.example.cleanbanar.core.utils.NotificationHelper.showNotification(appContext!!, judul, pesan)
                
                prefs.edit().putLong(lastNotifKey, System.currentTimeMillis()).apply()
            }
        }
    }

    /**
     * Dipanggil setelah pengosongan bak sampah berhasil.
     * Menambahkan notifikasi ke Firebase dan push notification
     * hanya jika toggle "Selesai Dikosongkan" diaktifkan.
     */
    fun triggerSelesaiNotification(context: android.content.Context, binLabel: String, deviceId: String, userName: String) {
        if (!currentSettings.selesai) return
        val judul = "$binLabel Dikosongkan"
        val pesan = "Sampah $binLabel pada perangkat $deviceId telah dikosongkan oleh $userName."
        FirebaseManager.addNotification(judul, pesan, "success")
        com.example.cleanbanar.core.utils.NotificationHelper.showNotification(context, judul, pesan)
    }
}
