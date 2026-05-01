package com.example.cleanbanar.core.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Pengelola Firebase Realtime Database untuk CleanBanar.
 *
 * Struktur Database:
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
 *     {id}/ { action: String, binType: String, userId: String, fullName: String, timestamp: Long }
 *   users/
 *     {id}/ { name: String, email: String, role: String }
 *   statistics/
 *     daily/
 *       {date}/ { organik: Int, nonOrganik: Int }
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"

    private val database: FirebaseDatabase? by lazy {
        try {
            // Menggunakan URL database region asia-southeast1 (Singapore)
            FirebaseDatabase.getInstance("https://cleanbanar-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase belum dikonfigurasi: ${e.message}")
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
    // Status Tempat Sampah - Baca & Tulis
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
                Log.w(TAG, "listenBinStatus dibatalkan: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeBinListener(binType: String, listener: ValueEventListener) {
        rootRef?.child("bins")?.child(binType)?.removeEventListener(listener)
    }

    /**
     * Memperbarui status kapasitas tempat sampah.
     * Kapasitas dibatasi antara 0-100% untuk menghindari data anomali.
     */
    fun updateBinStatus(binType: String, fillPercentage: Int, status: String) {
        val ref = rootRef?.child("bins")?.child(binType) ?: return
        val clampedPercent = fillPercentage.coerceIn(0, 100)
        ref.child("fillPercentage").setValue(clampedPercent)
        ref.child("status").setValue(status)
        ref.child("lastUpdate").setValue(System.currentTimeMillis())
    }

    // ==========================================
    // Aksi Terpadu - Konsistensi Data
    // ==========================================

    /**
     * Aksi terpadu untuk pengosongan sampah. Menjamin konsistensi data dengan:
     * 1. Meriset kapasitas ke 0% dan status ke "Normal"
     * 2. Mencatat waktu pengosongan terakhir
     * 3. Menambahkan entri riwayat (history)
     * 4. Mengirim notifikasi keberhasilan
     * 5. Memperbarui statistik harian
     */
    fun emptyBin(binType: String, actor: String) {
        val binLabel = if (binType == "organik") "Organik" else "Non-Organik"

        // 1. Riset kapasitas tempat sampah
        updateBinStatus(binType, 0, "Normal")

        // 2. Simpan waktu pengosongan terakhir
        rootRef?.child("bins")?.child(binType)?.child("lastEmptied")?.setValue(System.currentTimeMillis())

        // 3. Catat di riwayat
        addHistoryEntry(
            action = "emptied",
            binType = binType,
            userId = "SYSTEM",
            fullName = actor
        )

        // 4. Tambahkan notifikasi
        addNotification(
            title = "$binLabel Dikosongkan",
            message = "Sampah $binLabel telah dikosongkan oleh $actor.",
            type = "success"
        )

        // 5. Perbarui statistik harian dengan nilai riset
        updateDailyStats(binType, 0)
    }

    // ==========================================
    // Status Perangkat - Online/Offline & Tipe Jaringan
    // ==========================================
    /**
     * Mendengarkan status koneksi alat IoT secara real-time.
     * Mengembalikan status (ONLINE/OFFLINE), waktu terakhir terlihat, dan tipe jaringan (WIFI/BT).
     */
    fun listenDeviceStatus(callback: (connectionStatus: String, lastSeen: Long, networkType: String) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("device") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("connectionStatus").getValue(String::class.java) ?: "OFFLINE"
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                val networkType = snapshot.child("networkType").getValue(String::class.java) ?: "WIFI"
                callback(status, lastSeen, networkType)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenDeviceStatus dibatalkan: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeDeviceListener(listener: ValueEventListener) {
        rootRef?.child("device")?.removeEventListener(listener)
    }

    /**
     * Memperbarui tipe jaringan aktif pada database (WiFi atau Bluetooth).
     */
    fun updateDeviceNetworkType(networkType: String) {
        rootRef?.child("device")?.child("networkType")?.setValue(networkType)
    }

    // ==========================================
    // Notifikasi - Baca & Tulis
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
                Log.w(TAG, "listenNotifications dibatalkan: ${error.message}")
            }
        }
        ref.orderByChild("timestamp").addValueEventListener(listener)
        return listener
    }

    fun removeNotificationListener(listener: ValueEventListener) {
        rootRef?.child("notifications")?.removeEventListener(listener)
    }

    /**
     * Menambahkan notifikasi baru ke Firebase.
     * Digunakan oleh aksi manual (pengosongan) maupun otomatis (BinObserver).
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
    // Riwayat (History) - Baca & Tulis
    // ==========================================
    fun listenHistory(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("historyLogs") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val history = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children.reversed()) {
                    val map = mutableMapOf<String, Any>()
                    map["id"] = child.key ?: ""
                    map["action"] = child.child("action").getValue(String::class.java) ?: ""
                    map["binType"] = child.child("binType").getValue(String::class.java) ?: ""
                    map["userId"] = child.child("userId").getValue(String::class.java) ?: ""
                    map["fullName"] = child.child("fullName").getValue(String::class.java) ?: ""
                    map["timestamp"] = child.child("timestamp").getValue(Long::class.java) ?: 0L

                    history.add(map)
                }
                callback(history)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenHistory dibatalkan: ${error.message}")
            }
        }
        ref.orderByChild("timestamp").addValueEventListener(listener)
        return listener
    }

    fun removeHistoryListener(listener: ValueEventListener) {
        rootRef?.child("historyLogs")?.removeEventListener(listener)
    }

    fun addHistoryEntry(action: String, binType: String, userId: String, fullName: String) {
        val ref = rootRef?.child("historyLogs")?.push() ?: return
        ref.child("action").setValue(action)
        ref.child("binType").setValue(binType)
        ref.child("userId").setValue(userId)
        ref.child("fullName").setValue(fullName)
        ref.child("timestamp").setValue(System.currentTimeMillis())
    }


    // ==========================================
    // Pengelolaan Pengguna / Petugas
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
                Log.w(TAG, "listenUsers dibatalkan: ${error.message}")
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
            Log.w(TAG, "addUser: rootRef null, Firebase tidak terhubung")
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
                Log.w(TAG, "addUser gagal: ${e.message}")
                onFailure(e.message ?: "Gagal menyimpan data")
            }
    }

    /**
     * Memeriksa apakah email sudah didaftarkan oleh Admin di database.
     */
    fun checkIfUserPreRegistered(email: String, callback: (Boolean, String, String, String) -> Unit) {
        val ref = rootRef?.child("users") ?: run {
            callback(false, "", "", "")
            return
        }
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val dbEmail = child.child("email").getValue(String::class.java) ?: ""
                    if (dbEmail.trim().equals(email.trim(), ignoreCase = true)) {
                        val id = child.key ?: ""
                        val name = child.child("name").getValue(String::class.java) ?: ""
                        val role = child.child("role").getValue(String::class.java) ?: ""
                        callback(true, id, name, role)
                        return
                    }
                }
                callback(false, "", "", "")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "checkIfUserPreRegistered dibatalkan: ${error.message}")
                callback(false, "", "", "")
            }
        })
    }

    /**
     * Menyimpan data awal pengguna saat pendaftaran pertama kali.
     */
    fun seedUserData(
        uid: String,
        name: String,
        email: String,
        role: String,
        onComplete: () -> Unit
    ) {
        val ref = rootRef?.child("users")?.child(uid) ?: run {
            onComplete()
            return
        }
        val data = mapOf(
            "name" to name,
            "email" to email,
            "role" to role
        )
        ref.setValue(data)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener {
                Log.w(TAG, "seedUserData gagal: ${it.message}")
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
     * Mengambil data pengguna (nama, role) satu kali berdasarkan UID.
     */
    fun getUserData(uid: String, callback: (name: String, role: String) -> Unit) {
        val ref = rootRef?.child("users")?.child(uid) ?: run {
            callback("", "")
            return
        }
        ref.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val name = snapshot.child("name").getValue(String::class.java) ?: ""
                val role = snapshot.child("role").getValue(String::class.java) ?: ""
                callback(name, role)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "getUserData dibatalkan: ${error.message}")
                callback("", "")
            }
        })
    }

    // ==========================================
    // Statistik - Ringkasan Harian
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
                Log.w(TAG, "listenDailyStats dibatalkan: ${error.message}")
            }
        }
        ref.orderByKey().limitToLast(7).addValueEventListener(listener)
        return listener
    }

    fun removeStatsListener(listener: ValueEventListener) {
        rootRef?.child("statistics")?.child("daily")?.removeEventListener(listener)
    }

    /**
     * Memperbarui ringkasan statistik harian berdasarkan tipe sampah.
     */
    fun updateDailyStats(binType: String, percentage: Int) {
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val field = if (binType == "organik") "organik" else "nonOrganik"
        val ref = rootRef?.child("statistics")?.child("daily")?.child(dateKey) ?: return
        ref.child(field).setValue(percentage.coerceIn(0, 100))
    }

    /**
     * Menghitung jumlah kejadian "Penuh" (alert) dalam 7 hari terakhir.
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
                Log.w(TAG, "countPenuhEvents dibatalkan: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removePenuhListener(listener: ValueEventListener) {
        rootRef?.child("historyLogs")?.removeEventListener(listener)
    }

    // ==========================================
    // Pengaturan Notifikasi Pengguna
    // ==========================================

    data class NotificationSettings(
        val hampirPenuh: Boolean = true,
        val penuh: Boolean = true,
        val selesai: Boolean = true,
        val sistem: Boolean = true
    )

    /**
     * Menyimpan pengaturan notifikasi untuk pengguna tertentu.
     */
    fun saveNotificationSettings(userId: String, settings: NotificationSettings) {
        val ref = rootRef?.child("users")?.child(userId)?.child("notification_settings") ?: return
        ref.child("hampir_penuh").setValue(settings.hampirPenuh)
        ref.child("penuh").setValue(settings.penuh)
        ref.child("selesai").setValue(settings.selesai)
        ref.child("sistem").setValue(settings.sistem)
    }

    /**
     * Memuat pengaturan notifikasi pengguna (satu kali baca).
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
                Log.w(TAG, "loadNotificationSettings dibatalkan: ${error.message}")
                callback(NotificationSettings())
            }
        })
    }

    /**
     * Mendengarkan perubahan pengaturan notifikasi secara real-time.
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
                Log.w(TAG, "listenNotificationSettings dibatalkan: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeNotificationSettingsListener(userId: String, listener: ValueEventListener) {
        rootRef?.child("users")?.child(userId)?.child("notification_settings")?.removeEventListener(listener)
    }
}
