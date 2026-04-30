package com.example.cleanbanar.core.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// =========================================================================
// TODO (PBL GROUP) - PANDUAN SETUP FIREBASE UNTUK MASING-MASING ANGGOTA:
// =========================================================================
// 1. Download file `google-services.json` dari project Firebase kamu sendiri.
// 2. Masukkan/timpa file tersebut ke dalam folder `app/` di Android Studio.
// 3. Pastikan di Firebase Console kamu sudah mengaktifkan:
//    - Authentication (Sign-in provider: Email/Password)
//    - Realtime Database (Set Rules .read = true, .write = true)
// =========================================================================

/**
 * Firebase Realtime Database manager for CleanBanar.
 *
 * Database structure:
 * cleanbanar/
 *   bins/
 *     organik/   { percentage: Int, status: String, lastUpdate: Long }
 *     nonOrganik/{ percentage: Int, status: String, lastUpdate: Long }
 *   device/
 *     connectionStatus: String ("ONLINE" / "OFFLINE")
 *     lastSeen: Long
 *   notifications/
 *     {id}/ { title: String, message: String, type: String, timestamp: Long, read: Boolean }
 *   historyLogs/
 *     {id}/ { action: String, areaId: String, userId: String, fullName: String, timestamp: Long }
 *   users/
 *     {id}/ { name: String, email: String, role: String }
 *   statistics/
 *     daily/
 *       {date}/ { organik: Int, nonOrganik: Int }
 */
object FirebaseManager {

    // ==========================================
    // Constants & Firebase References
    // ==========================================

    private const val TAG = "FirebaseManager"

    private val database: FirebaseDatabase? by lazy {
        try {
            // URL Realtime Database region asia-southeast1 (Singapore) wajib di-hardcode
            // karena FirebaseDatabase.getInstance() tanpa URL bisa gagal di region ini.
            FirebaseDatabase.getInstance("https://cleanbanar-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured: ${e.message}")
            null
        }
    }

    private val rootRef: DatabaseReference? by lazy {
        database?.getReference("cleanbanar")
    }

    private fun isAvailable(): Boolean {
        return rootRef != null
    }

    // ==========================================
    // Bin Status - Read & Write
    // ==========================================
    fun listenBinStatus(binType: String, callback: (percentage: Int, status: String, lastUpdate: Long, lastEmptied: Long) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("bins")?.child(binType) ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fillPercentage = snapshot.child("fillPercentage").getValue(Int::class.java) ?: 0
                val status = snapshot.child("status").getValue(String::class.java) ?: "Normal"
                val lastUpdate = snapshot.child("lastUpdate").getValue(Long::class.java) ?: 0L
                val lastEmptied = snapshot.child("lastEmptied").getValue(Long::class.java) ?: 0L
                callback(fillPercentage, status, lastUpdate, lastEmptied)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenBinStatus cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeBinListener(binType: String, listener: ValueEventListener) {
        rootRef?.child("bins")?.child(binType)?.removeEventListener(listener)
    }

    /**
     * Update bin capacity with validation.
     * Capacity is clamped between 0-100% to prevent anomalous data.
     */
    fun updateBinStatus(binType: String, fillPercentage: Int, status: String) {
        val ref = rootRef?.child("bins")?.child(binType) ?: return
        val clampedPercent = fillPercentage.coerceIn(0, 100)
        ref.child("fillPercentage").setValue(clampedPercent)
        ref.child("status").setValue(status)
        ref.child("lastUpdate").setValue(System.currentTimeMillis())
    }

    // ==========================================
    // Unified Actions - Data Consistency
    // ==========================================

    /**
     * Unified "empty bin" action. Ensures data consistency by:
     * 1. Resetting capacity to 0% and status to "TERSEDIA"
     * 2. Writing a history entry ("emptied")
     * 3. Sending a "selesai" notification
     * 4. Updating daily statistics
     *
     * This is the ONLY method that should be called when a petugas
     * marks a bin as emptied, to avoid fragmented writes.
     */
    fun emptyBin(binType: String, actor: String) {
        val binLabel = if (binType == "organik") "Organik" else "Non-Organik"

        // 1. Reset bin capacity
        updateBinStatus(binType, 0, "Normal")

        // 2. Write lastEmptied timestamp
        rootRef?.child("bins")?.child(binType)?.child("lastEmptied")?.setValue(System.currentTimeMillis())

        // 3. Record in history
        // Note: For now we pass "A1" as default area if not specified
        // In real use, we should pass the actual areaId from dashboard

        // 4. Send notification
        addNotification(
            title = "$binLabel Dikosongkan",
            message = "Sampah $binLabel telah dikosongkan oleh $actor.",
            type = "success"
        )

        // 5. Update daily stats with reset value
        updateDailyStats(binType, 0)
    }

    // ==========================================
    // Device Status - Online/Offline
    // ==========================================
    fun listenDeviceStatus(callback: (connectionStatus: String, lastSeen: Long) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("device") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("connectionStatus").getValue(String::class.java) ?: "OFFLINE"
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                callback(status, lastSeen)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenDeviceStatus cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeDeviceListener(listener: ValueEventListener) {
        rootRef?.child("device")?.removeEventListener(listener)
    }

    // ==========================================
    // Notifications - Read & Write
    // ==========================================
    fun listenNotifications(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("notifications") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifications = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children.reversed()) {
                    val map = mutableMapOf<String, Any>()
                    map["id"] = child.key ?: ""
                    map["title"] = child.child("title").getValue(String::class.java) ?: ""
                    map["message"] = child.child("message").getValue(String::class.java) ?: ""
                    map["type"] = child.child("type").getValue(String::class.java) ?: "info"
                    map["timestamp"] = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    map["read"] = child.child("read").getValue(Boolean::class.java) ?: false
                    notifications.add(map)
                }
                callback(notifications)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenNotifications cancelled: ${error.message}")
            }
        }
        ref.orderByChild("timestamp").addValueEventListener(listener)
        return listener
    }

    fun removeNotificationListener(listener: ValueEventListener) {
        rootRef?.child("notifications")?.removeEventListener(listener)
    }

    /**
     * Write a new notification to Firebase.
     * Used by both manual actions (emptyBin) and automatic triggers (BinObserver).
     */
    fun addNotification(title: String, message: String, type: String) {
        val ref = rootRef?.child("notifications")?.push() ?: return
        ref.child("title").setValue(title)
        ref.child("message").setValue(message)
        ref.child("type").setValue(type)
        ref.child("timestamp").setValue(System.currentTimeMillis())
        ref.child("read").setValue(false)
    }

    // ==========================================
    // History - Read & Write
    // ==========================================
    /**
     * Listener for history logs with RBAC (Role-Based Access Control).
     * Staff see only their area, Admin see all.
     */
    fun listenHistoryFiltered(role: String, areaId: String, callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("historyLogs") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val history = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children.reversed()) {
                    val logAreaId = child.child("areaId").getValue(String::class.java) ?: ""
                    
                    // Filter: if Petugas, only show same areaId
                    if (role == "Petugas" && logAreaId != areaId) continue

                    val map = mutableMapOf<String, Any>()
                    map["id"] = child.key ?: ""
                    map["action"] = child.child("action").getValue(String::class.java) ?: ""
                    map["areaId"] = logAreaId
                    map["userId"] = child.child("userId").getValue(String::class.java) ?: ""
                    map["fullName"] = child.child("fullName").getValue(String::class.java) ?: ""
                    map["timestamp"] = child.child("timestamp").getValue(Long::class.java) ?: 0L

                    history.add(map)
                }
                callback(history)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenHistoryFiltered cancelled: ${error.message}")
            }
        }
        ref.orderByChild("timestamp").addValueEventListener(listener)
        return listener
    }

    fun removeHistoryListener(listener: ValueEventListener) {
        rootRef?.child("historyLogs")?.removeEventListener(listener)
    }

    fun addHistoryEntry(action: String, areaId: String, userId: String, fullName: String) {
        val ref = rootRef?.child("historyLogs")?.push() ?: return
        ref.child("action").setValue(action)
        ref.child("areaId").setValue(areaId)
        ref.child("userId").setValue(userId)
        ref.child("fullName").setValue(fullName)
        ref.child("timestamp").setValue(System.currentTimeMillis())
    }


    // ==========================================
    // Users / Staff Management
    // ==========================================
    fun listenUsers(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("users") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    val map = mutableMapOf<String, Any>()
                    map["id"] = child.key ?: ""
                    map["name"] = child.child("name").getValue(String::class.java) ?: ""
                    map["email"] = child.child("email").getValue(String::class.java) ?: ""
                    map["role"] = child.child("role").getValue(String::class.java) ?: ""
                    users.add(map)
                }
                callback(users)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenUsers cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeUsersListener(listener: ValueEventListener) {
        rootRef?.child("users")?.removeEventListener(listener)
    }

    fun addUser(
        name: String,
        email: String,
        role: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val ref = rootRef?.child("users")?.push() ?: run {
            Log.w(TAG, "addUser: rootRef is null, Firebase tidak terhubung")
            onFailure("Firebase tidak terhubung. Periksa koneksi internet.")
            return
        }
        val data = mapOf(
            "name" to name,
            "email" to email,
            "role" to role
        )
        ref.setValue(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.w(TAG, "addUser failed: ${e.message}")
                onFailure(e.message ?: "Gagal menyimpan data")
            }
    }

    /**
     * Cek apakah email sudah didaftarkan oleh Admin di Realtime DB (tanpa akun Auth).
     * Menggunakan full-scan client-side (bukan orderByChild) untuk menghindari
     * kebutuhan Firebase index di Realtime Database Rules.
     */
    fun checkIfUserPreRegistered(email: String, callback: (Boolean, String, String, String) -> Unit) {
        val ref = rootRef?.child("users") ?: run {
            Log.w(TAG, "checkIfUserPreRegistered: rootRef null, Firebase tidak terhubung")
            callback(false, "", "", "")
            return
        }
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "checkIfUserPreRegistered: total users di DB = ${snapshot.childrenCount}")
                for (child in snapshot.children) {
                    val dbEmail = child.child("email").getValue(String::class.java) ?: ""
                    Log.d(TAG, "  checking: dbEmail='$dbEmail' vs input='${email.trim()}'")
                    if (dbEmail.trim().equals(email.trim(), ignoreCase = true)) {
                        val id = child.key ?: ""
                        val name = child.child("name").getValue(String::class.java) ?: ""
                        val role = child.child("role").getValue(String::class.java) ?: ""
                        Log.d(TAG, "  FOUND: id=$id, name=$name, role=$role")
                        callback(true, id, name, role)
                        return
                    }
                }
                Log.w(TAG, "checkIfUserPreRegistered: email '$email' tidak ditemukan di DB")
                callback(false, "", "", "")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "checkIfUserPreRegistered cancelled: ${error.message}")
                callback(false, "", "", "")
            }
        })
    }

    /**
     * Seed data awal user ke Realtime Database menggunakan UID dari Firebase Auth.
     * Dipanggil saat login pertama kali dan data belum ada di DB.
     * Menyimpan di node: cleanbanar/users/{uid}
     */
    fun seedUserData(
        uid: String,
        name: String,
        email: String,
        role: String,
        assignedAreaId: String,
        onComplete: () -> Unit
    ) {
        val ref = rootRef?.child("users")?.child(uid) ?: run {
            onComplete()
            return
        }
        val data = mapOf(
            "name" to name,
            "email" to email,
            "role" to role,
            "assignedAreaId" to assignedAreaId
        )
        ref.setValue(data)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener {
                Log.w(TAG, "seedUserData failed: ${it.message}")
                onComplete()
            }
    }

    fun updateUser(userId: String, name: String, email: String) {
        val ref = rootRef?.child("users")?.child(userId) ?: return
        ref.child("name").setValue(name)
        ref.child("email").setValue(email)
    }

    fun deleteUser(userId: String) {
        rootRef?.child("users")?.child(userId)?.removeValue()
    }

    /**
     * Fetch user data (name, role, assignedAreaId) once from Realtime Database.
     * Used after Firebase Auth login to retrieve role for role-based navigation.
     * Calls callback with empty strings if user node not found.
     */
    fun getUserData(uid: String, callback: (name: String, role: String, assignedAreaId: String) -> Unit) {
        val ref = rootRef?.child("users")?.child(uid) ?: run {
            callback("", "", "")
            return
        }
        ref.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val name = snapshot.child("name").getValue(String::class.java) ?: ""
                val role = snapshot.child("role").getValue(String::class.java) ?: ""
                val area = snapshot.child("assignedAreaId").getValue(String::class.java) ?: ""
                callback(name, role, area)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "getUserData cancelled: ${error.message}")
                callback("", "", "")
            }
        })
    }

    // ==========================================
    // Statistics - Daily Summaries & Aggregation
    // ==========================================
    fun listenDailyStats(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("statistics")?.child("daily") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val stats = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    val map = mutableMapOf<String, Any>()
                    map["date"] = child.key ?: ""
                    map["organik"] = child.child("organik").getValue(Int::class.java) ?: 0
                    map["nonOrganik"] = child.child("nonOrganik").getValue(Int::class.java) ?: 0
                    stats.add(map)
                }
                callback(stats)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenDailyStats cancelled: ${error.message}")
            }
        }
        ref.orderByKey().limitToLast(7).addValueEventListener(listener)
        return listener
    }

    fun removeStatsListener(listener: ValueEventListener) {
        rootRef?.child("statistics")?.child("daily")?.removeEventListener(listener)
    }

    /**
     * Update the daily statistics summary for a given bin type.
     * Stores the latest capacity value under today's date key.
     * This is lightweight aggregation — no heavy computation.
     */
    fun updateDailyStats(binType: String, percentage: Int) {
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val field = if (binType == "organik") "organik" else "nonOrganik"
        val ref = rootRef?.child("statistics")?.child("daily")?.child(dateKey) ?: return
        ref.child(field).setValue(percentage.coerceIn(0, 100))
    }

    /**
     * Count the number of "penuh" (alert) events from the history node
     * within the last 7 days. Used by StatisticsFragment for the summary cards.
     */
    fun countPenuhEvents(callback: (Int) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("historyLogs") ?: return null
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var count = 0
                for (child in snapshot.children) {
                    val action = child.child("action").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    if (action == "alert" && timestamp >= sevenDaysAgo) {
                        count++
                    }
                }
                callback(count)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "countPenuhEvents cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removePenuhListener(listener: ValueEventListener) {
        rootRef?.child("historyLogs")?.removeEventListener(listener)
    }

    // ==========================================
    // Notification Settings - Per User Preferences
    // ==========================================

    /**
     * Data class representing user notification preferences.
     * Default values are all true (all notifications ON).
     */
    data class NotificationSettings(
        val hampirPenuh: Boolean = true,
        val penuh: Boolean = true,
        val selesai: Boolean = true,
        val sistem: Boolean = true
    )

    /**
     * Save notification settings for a specific user.
     * Stored at: users/{userId}/notification_settings/
     */
    fun saveNotificationSettings(userId: String, settings: NotificationSettings) {
        val ref = rootRef?.child("users")?.child(userId)?.child("notification_settings") ?: return
        ref.child("hampir_penuh").setValue(settings.hampirPenuh)
        ref.child("penuh").setValue(settings.penuh)
        ref.child("selesai").setValue(settings.selesai)
        ref.child("sistem").setValue(settings.sistem)
    }

    /**
     * Load notification settings for a user (one-time read).
     * If data not found, returns defaults (all ON).
     */
    fun loadNotificationSettings(userId: String, callback: (NotificationSettings) -> Unit) {
        val ref = rootRef?.child("users")?.child(userId)?.child("notification_settings") ?: run {
            callback(NotificationSettings())
            return
        }
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val settings = NotificationSettings(
                    hampirPenuh = snapshot.child("hampir_penuh").getValue(Boolean::class.java) ?: true,
                    penuh = snapshot.child("penuh").getValue(Boolean::class.java) ?: true,
                    selesai = snapshot.child("selesai").getValue(Boolean::class.java) ?: true,
                    sistem = snapshot.child("sistem").getValue(Boolean::class.java) ?: true
                )
                callback(settings)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "loadNotificationSettings cancelled: ${error.message}")
                callback(NotificationSettings())
            }
        })
    }

    /**
     * Listen to notification settings changes in real-time.
     * Returns the listener so it can be removed on cleanup.
     */
    fun listenNotificationSettings(userId: String, callback: (NotificationSettings) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("users")?.child(userId)?.child("notification_settings") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val settings = NotificationSettings(
                    hampirPenuh = snapshot.child("hampir_penuh").getValue(Boolean::class.java) ?: true,
                    penuh = snapshot.child("penuh").getValue(Boolean::class.java) ?: true,
                    selesai = snapshot.child("selesai").getValue(Boolean::class.java) ?: true,
                    sistem = snapshot.child("sistem").getValue(Boolean::class.java) ?: true
                )
                callback(settings)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenNotificationSettings cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeNotificationSettingsListener(userId: String, listener: ValueEventListener) {
        rootRef?.child("users")?.child(userId)?.child("notification_settings")?.removeEventListener(listener)
    }

    // ==========================================
    // Area / Sub-Area - User Assignment
    // ==========================================

    /**
     * Data class for area assignment.
     * Currently hardcoded with defaults; structured for future Firebase migration.
     * Future: fetched from users/{userId}/assignedArea + areas/{areaId}
     */
    data class AreaInfo(
        val areaName: String = "SDN 1 Banjarmasin",
        val subAreaName: String = "Halaman Belakang"
    )

    /**
     * Get the assigned area for a user.
     * For now, returns hardcoded defaults.
     * TODO: Replace with Firebase lookup when area management is implemented.
     */
    fun getUserArea(userId: String, callback: (AreaInfo) -> Unit) {
        // Future implementation:
        // val ref = rootRef?.child("users")?.child(userId) ?: run {
        //     callback(AreaInfo())
        //     return
        // }
        // ref.addListenerForSingleValueEvent(...)
        callback(AreaInfo())
    }
}
