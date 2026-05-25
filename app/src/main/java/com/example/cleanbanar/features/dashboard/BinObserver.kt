package com.example.cleanbanar.features.dashboard

import android.util.Log
import com.example.cleanbanar.core.data.FirebaseManager
import com.google.firebase.database.ValueEventListener

/**
 * BinObserver — Pemicu notifikasi berdasarkan ambang batas (threshold).
 */
object BinObserver {

    private const val TAG = "BinObserver"

    private var previousOrganik: Int = -1
    private var previousNonOrganik: Int = -1

    private var organikListener: ValueEventListener? = null
    private var nonOrganikListener: ValueEventListener? = null
    private var settingsListener: ValueEventListener? = null

    private var isRunning = false
    private var currentSettings = FirebaseManager.NotificationSettings()
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

        organikListener = FirebaseManager.listenBinStatus("organik") { persentase, _, _, terakhirDikosongkan ->
            handleThreshold("organik", persentase, previousOrganik)
            handleStaleWaste("organik", terakhirDikosongkan, 3)
            previousOrganik = persentase
        }

        nonOrganikListener = FirebaseManager.listenBinStatus("nonOrganik") { persentase, _, _, terakhirDikosongkan ->
            handleThreshold("nonOrganik", persentase, previousNonOrganik)
            handleStaleWaste("nonOrganik", terakhirDikosongkan, 7)
            previousNonOrganik = persentase
        }
    }

    fun stop() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        settingsListener?.let {
            if (currentUserId.isNotEmpty()) {
                FirebaseManager.removeNotificationSettingsListener(currentUserId, it)
            }
        }
        organikListener = null
        nonOrganikListener = null
        settingsListener = null
        previousOrganik = -1
        previousNonOrganik = -1
        isRunning = false
        appContext = null
    }

    private fun handleThreshold(binType: String, currentPercent: Int, previousPercent: Int) {
        if (previousPercent == -1) return
        if (currentPercent <= previousPercent) return
        if (currentPercent < 0 || currentPercent > 100) return

        val binLabel = if (binType == "organik") "Organik" else "Non-Organik"

        if (currentPercent >= 95 && previousPercent < 95) {
            FirebaseManager.addHistoryEntry(
                aksi = "alert",
                tipeSampah = binType,
                idPengguna = "SYSTEM",
                namaLengkap = "Sistem Otomatis"
            )

            if (currentSettings.penuh) {
                val judul = "$binLabel Penuh!"
                val pesan = "Kapasitas $binLabel telah mencapai $currentPercent%. Segera kosongkan."
                FirebaseManager.addNotification(
                    judul = judul,
                    pesan = pesan,
                    tipe = "danger"
                )
                appContext?.let {
                    com.example.cleanbanar.core.utils.NotificationHelper.showNotification(it, judul, pesan)
                }
            }

            FirebaseManager.updateDailyStats(binType, currentPercent)
            return
        }

        if (currentPercent >= 80 && previousPercent < 80) {
            if (currentSettings.hampirPenuh) {
                val judul = "$binLabel Hampir Penuh"
                val pesan = "Kapasitas $binLabel di angka $currentPercent%. Segera perhatikan."
                FirebaseManager.addNotification(
                    judul = judul,
                    pesan = pesan,
                    tipe = "warning"
                )
                appContext?.let {
                    com.example.cleanbanar.core.utils.NotificationHelper.showNotification(it, judul, pesan)
                }
            }

            FirebaseManager.updateDailyStats(binType, currentPercent)
        }
    }

    private fun handleStaleWaste(binType: String, terakhirDikosongkan: Long, maxDays: Int) {
        if (terakhirDikosongkan == 0L || appContext == null) return
        
        val diff = System.currentTimeMillis() - terakhirDikosongkan
        val days = diff / 86_400_000L
        
        if (days >= maxDays) {
            val prefs = appContext!!.getSharedPreferences("CleanBanarPrefs", android.content.Context.MODE_PRIVATE)
            val lastNotifKey = "stale_notif_$binType"
            val lastNotifTime = prefs.getLong(lastNotifKey, 0L)
            
            // Throttle 24 jam (86_400_000 ms)
            if (System.currentTimeMillis() - lastNotifTime > 86_400_000L) {
                val judul = "Peringatan Waktu Inap"
                val pesan = if (binType == "organik") {
                    "Sampah Organik sudah $days hari belum dikosongkan dan mungkin mulai berbau."
                } else {
                    "Sampah Non-Organik sudah menumpuk selama $days hari."
                }
                
                FirebaseManager.addNotification(judul, pesan, "warning")
                com.example.cleanbanar.core.utils.NotificationHelper.showNotification(appContext!!, judul, pesan)
                
                prefs.edit().putLong(lastNotifKey, System.currentTimeMillis()).apply()
            }
        }
    }
}
