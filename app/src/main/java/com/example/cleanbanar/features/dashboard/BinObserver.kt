package com.example.cleanbanar.features.dashboard

import android.util.Log
import com.example.cleanbanar.core.data.FirebaseManager
import com.google.firebase.database.ValueEventListener

/**
 * BinObserver — Pemicu notifikasi berdasarkan ambang batas (threshold).
 *
 * Mendengarkan perubahan kapasitas tempat sampah secara real-time dan
 * membuat notifikasi serta entri riwayat secara otomatis.
 */
object BinObserver {

    private const val TAG = "BinObserver"

    // Kapasitas sebelumnya untuk mendeteksi kenaikan ambang batas
    private var previousOrganik: Int = -1
    private var previousNonOrganik: Int = -1

    // Listener Firebase
    private var organikListener: ValueEventListener? = null
    private var nonOrganikListener: ValueEventListener? = null
    private var settingsListener: ValueEventListener? = null

    private var isRunning = false
    private var currentSettings = FirebaseManager.NotificationSettings()
    private var currentUserId: String = ""

    /**
     * Mulai mengamati perubahan status tempat sampah.
     */
    fun start(userId: String = "") {
        if (isRunning) return
        isRunning = true
        currentUserId = userId

        // Ambil preferensi notifikasi pengguna secara real-time
        if (userId.isNotEmpty()) {
            settingsListener = FirebaseManager.listenNotificationSettings(userId) { settings ->
                currentSettings = settings
            }
        }

        organikListener = FirebaseManager.listenBinStatus("organik") { fillPercentage, _, _, _ ->
            handleThreshold("organik", fillPercentage, previousOrganik)
            previousOrganik = fillPercentage
        }

        nonOrganikListener = FirebaseManager.listenBinStatus("nonOrganik") { fillPercentage, _, _, _ ->
            handleThreshold("nonOrganik", fillPercentage, previousNonOrganik)
            previousNonOrganik = fillPercentage
        }
    }

    /**
     * Berhenti mengamati dan bersihkan semua listener.
     */
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

    /**
     * Evaluasi apakah kapasitas tempat sampah telah melewati ambang batas kritis.
     * 
     * Aturan:
     * - >= 95%: Penuh -> Notifikasi bahaya + entri riwayat
     * - >= 80%: Hampir Penuh -> Notifikasi peringatan
     */
    private fun handleThreshold(binType: String, currentPercent: Int, previousPercent: Int) {
        // Lewati pembacaan pertama setelah aplikasi dimulai
        if (previousPercent == -1) return

        // Abaikan jika kapasitas turun (dikongsongkan) atau tidak berubah
        if (currentPercent <= previousPercent) return

        // Abaikan data sensor yang tidak valid (anomali)
        if (currentPercent < 0 || currentPercent > 100) return

        val binLabel = if (binType == "organik") "Organik" else "Non-Organik"

        // Ambang batas: PENUH (>= 95%)
        if (currentPercent >= 95 && previousPercent < 95) {
            FirebaseManager.addHistoryEntry(
                action = "alert",
                binType = binType,
                userId = "SYSTEM",
                fullName = "Sistem Otomatis"
            )

            if (currentSettings.penuh) {
                FirebaseManager.addNotification(
                    title = "$binLabel Penuh!",
                    message = "Kapasitas $binLabel telah mencapai $currentPercent%. Segera kosongkan.",
                    type = "danger"
                )
            }

            FirebaseManager.updateDailyStats(binType, currentPercent)
            return
        }

        // Ambang batas: HAMPIR PENUH (>= 80%)
        if (currentPercent >= 80 && previousPercent < 80) {
            if (currentSettings.hampirPenuh) {
                FirebaseManager.addNotification(
                    title = "$binLabel Hampir Penuh",
                    message = "Kapasitas $binLabel di angka $currentPercent%. Segera perhatikan.",
                    type = "warning"
                )
            }

            FirebaseManager.updateDailyStats(binType, currentPercent)
        }
    }
}
