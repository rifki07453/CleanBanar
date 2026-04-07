package com.example.cleanbanar.features.dashboard

import android.util.Log
import com.example.cleanbanar.core.data.FirebaseManager
import com.google.firebase.database.ValueEventListener

/**
 * BinObserver — Threshold-based notification trigger.
 *
 * This singleton listens to real-time bin capacity changes from Firebase
 * and automatically generates notifications and history entries when
 * thresholds are crossed.
 *
 * Responsibilities:
 *   - Track previous capacity state for each bin
 *   - Trigger "Hampir Penuh" notification at ≥80%
 *   - Trigger "Penuh" notification + history at ≥95%
 *   - Prevent duplicate notifications via last-state comparison
 *   - Respect user notification preferences before writing
 *
 * Architecture note:
 *   This runs on the frontend as a hybrid workaround. Ideally,
 *   threshold logic would live in Firebase Cloud Functions or
 *   on the ESP32 device itself.
 */
object BinObserver {

    private const val TAG = "BinObserver"

    // ==========================================
    // State Tracking
    // ==========================================

    /** Previous known capacity for each bin type (to detect threshold crossings) */
    private var previousOrganik: Int = -1
    private var previousNonOrganik: Int = -1

    /** Firebase listeners (to remove on stop) */
    private var organikListener: ValueEventListener? = null
    private var nonOrganikListener: ValueEventListener? = null
    private var settingsListener: ValueEventListener? = null

    /** Flag to prevent starting multiple times */
    private var isRunning = false

    /** Current user's notification preferences (loaded from Firebase) */
    private var currentSettings = FirebaseManager.NotificationSettings()
    private var currentUserId: String = ""

    // ==========================================
    // Lifecycle - Start / Stop
    // ==========================================

    /**
     * Start observing bin status changes.
     * Safe to call multiple times — will no-op if already running.
     * @param userId The current logged-in user ID for notification preferences.
     */
    fun start(userId: String = "") {
        if (isRunning) {
            Log.d(TAG, "BinObserver already running, skipping start.")
            return
        }
        isRunning = true
        currentUserId = userId
        Log.d(TAG, "BinObserver started for user: $userId")

        // Listen to user's notification preferences in real-time
        if (userId.isNotEmpty()) {
            settingsListener = FirebaseManager.listenNotificationSettings(userId) { settings ->
                currentSettings = settings
                Log.d(TAG, "Notification settings updated: $settings")
            }
        }

        organikListener = FirebaseManager.listenBinStatus("organik") { percentage, _, _ ->
            handleThreshold("organik", percentage, previousOrganik)
            previousOrganik = percentage
        }

        nonOrganikListener = FirebaseManager.listenBinStatus("nonOrganik") { percentage, _, _ ->
            handleThreshold("nonOrganik", percentage, previousNonOrganik)
            previousNonOrganik = percentage
        }
    }

    /**
     * Stop observing bin status changes and clean up listeners.
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
        Log.d(TAG, "BinObserver stopped.")
    }

    // ==========================================
    // Threshold Logic
    // ==========================================

    /**
     * Evaluate whether a bin's capacity has crossed a critical threshold.
     *
     * Rules:
     *   - Ignore the very first reading (previousPercent == -1) to prevent
     *     false alerts on app startup.
     *   - Only trigger when crossing UP through a threshold, not when
     *     the value stays above the threshold on subsequent updates.
     *   - ≥95%: "Penuh" → danger notification + history alert entry
     *   - ≥80%: "Hampir Penuh" → warning notification only
     *   - Check user notification settings before writing notifications
     */
    private fun handleThreshold(binType: String, currentPercent: Int, previousPercent: Int) {
        // Skip the first reading after app start (no previous baseline)
        if (previousPercent == -1) return

        // Skip if capacity decreased (e.g., bin was emptied) or didn't change
        if (currentPercent <= previousPercent) return

        // Validate: ignore anomalous sensor data
        if (currentPercent < 0 || currentPercent > 100) {
            Log.w(TAG, "Anomalous reading for $binType: $currentPercent%, ignoring.")
            return
        }

        val binLabel = if (binType == "organik") "Organik" else "Non-Organik"

        // Threshold: PENUH (≥95%, crossed from below 95%)
        if (currentPercent >= 95 && previousPercent < 95) {
            Log.d(TAG, "$binLabel crossed PENUH threshold: $previousPercent% → $currentPercent%")

            // Always write history regardless of notification settings
            FirebaseManager.addHistoryEntry(
                action = "alert",
                bin = binType,
                actor = "Sistem"
            )

            // Only write notification if user has "penuh" enabled
            if (currentSettings.penuh) {
                FirebaseManager.addNotification(
                    title = "$binLabel Penuh!",
                    message = "Kapasitas $binLabel telah mencapai $currentPercent%. Segera kosongkan.",
                    type = "danger"
                )
            }

            // Update daily stats with the peak value
            FirebaseManager.updateDailyStats(binType, currentPercent)
            return
        }

        // Threshold: HAMPIR PENUH (≥80%, crossed from below 80%)
        if (currentPercent >= 80 && previousPercent < 80) {
            Log.d(TAG, "$binLabel crossed HAMPIR PENUH threshold: $previousPercent% → $currentPercent%")

            // Only write notification if user has "hampir_penuh" enabled
            if (currentSettings.hampirPenuh) {
                FirebaseManager.addNotification(
                    title = "$binLabel Hampir Penuh",
                    message = "Kapasitas $binLabel di angka $currentPercent%. Segera perhatikan.",
                    type = "warning"
                )
            }

            // Update daily stats
            FirebaseManager.updateDailyStats(binType, currentPercent)
        }
    }
}
