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

    fun start(userId: String = "") {
        if (isRunning) return
        isRunning = true
        currentUserId = userId

        if (userId.isNotEmpty()) {
            settingsListener = FirebaseManager.listenNotificationSettings(userId) { settings ->
                currentSettings = settings
            }
        }

        organikListener = FirebaseManager.listenBinStatus("organik") { persentase, _, _, _ ->
            handleThreshold("organik", persentase, previousOrganik)
            previousOrganik = persentase
        }

        nonOrganikListener = FirebaseManager.listenBinStatus("nonOrganik") { persentase, _, _, _ ->
            handleThreshold("nonOrganik", persentase, previousNonOrganik)
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
                FirebaseManager.addNotification(
                    judul = "$binLabel Penuh!",
                    pesan = "Kapasitas $binLabel telah mencapai $currentPercent%. Segera kosongkan.",
                    tipe = "danger"
                )
            }

            FirebaseManager.updateDailyStats(binType, currentPercent)
            return
        }

        if (currentPercent >= 80 && previousPercent < 80) {
            if (currentSettings.hampirPenuh) {
                FirebaseManager.addNotification(
                    judul = "$binLabel Hampir Penuh",
                    pesan = "Kapasitas $binLabel di angka $currentPercent%. Segera perhatikan.",
                    tipe = "warning"
                )
            }

            FirebaseManager.updateDailyStats(binType, currentPercent)
        }
    }
}
