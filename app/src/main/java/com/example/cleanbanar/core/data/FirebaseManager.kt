package com.example.cleanbanar.core.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Pengelola Firebase Realtime Database untuk CleanBanar.
 * Seluruh field database dan kunci data menggunakan Bahasa Indonesia untuk konsistensi.
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"

    private val database: FirebaseDatabase? by lazy {
        try {
            // URL diambil dari google-services.json, tidak di-hardcode di kode
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val rootRef: DatabaseReference? by lazy {
        database?.getReference("cleanbanar")
    }

    // ==========================================
    // Status Tempat Sampah
    // ==========================================
    fun listenBinStatus(binType: String, callback: (persentase: Int, status: String, terakhirUpdate: Long, terakhirDikosongkan: Long) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("bins")?.child(binType) ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val persentase = snapshot.child("persentaseIsi").getValue(Int::class.java) ?: 0
                val status = snapshot.child("status").getValue(String::class.java) ?: "Normal"
                val terakhirUpdate = snapshot.child("terakhirUpdate").getValue(Long::class.java) ?: 0L
                val terakhirDikosongkan = snapshot.child("terakhirDikosongkan").getValue(Long::class.java) ?: 0L
                callback(persentase, status, terakhirUpdate, terakhirDikosongkan)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenBinStatus error: ${error.message} (code=${error.code})")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun updateBinStatus(binType: String, persentase: Int, status: String) {
        val ref = rootRef?.child("bins")?.child(binType) ?: return
        ref.child("persentaseIsi").setValue(persentase.coerceIn(0, 100))
        ref.child("status").setValue(status)
        ref.child("terakhirUpdate").setValue(System.currentTimeMillis())
    }

    fun emptyBin(binType: String, userId: String, userName: String) {
        val safeBinType = sanitize(binType)
        val safeUserName = sanitize(userName)
        val safeUserId = sanitize(userId)
        val binLabel = if (safeBinType == "organik") "Organik" else "Non-Organik"
        updateBinStatus(safeBinType, 0, "Normal")
        rootRef?.child("bins")?.child(safeBinType)?.child("terakhirDikosongkan")?.setValue(System.currentTimeMillis())
        addHistoryEntry("pengosongan", safeBinType, safeUserId, safeUserName)
        addNotification("$binLabel Dikosongkan", "Sampah $binLabel telah dikosongkan oleh $safeUserName.", "success")
        updateDailyStats(safeBinType, 0)
    }

    /** Sanitasi input agar aman ditulis sebagai Firebase path key */
    private fun sanitize(input: String): String =
        input.trim()
            .replace(".", "_")
            .replace("#", "")
            .replace("$", "")
            .replace("[", "")
            .replace("]", "")
            .replace("/", "_")

    // ==========================================
    // Status Perangkat
    // ==========================================
    fun listenDeviceStatus(callback: (statusKoneksi: String, terakhirTerlihat: Long, tipeJaringan: String) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("device") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("statusKoneksi").getValue(String::class.java) ?: "OFFLINE"
                val terakhir = snapshot.child("terakhirTerlihat").getValue(Long::class.java) ?: 0L
                val tipe = snapshot.child("tipeJaringan").getValue(String::class.java) ?: "WIFI"
                callback(status, terakhir, tipe)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenDeviceStatus error: ${error.message} (code=${error.code})")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun updateDeviceNetworkType(tipe: String) {
        rootRef?.child("device")?.child("tipeJaringan")?.setValue(tipe)
    }

    // ==========================================
    // Notifikasi
    // ==========================================
    fun listenNotifications(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("notifications") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val daftar = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children.reversed()) {
                    daftar.add(mapOf(
                        "id" to (child.key ?: ""),
                        "judul" to (child.child("judul").getValue(String::class.java) ?: ""),
                        "pesan" to (child.child("pesan").getValue(String::class.java) ?: ""),
                        "tipe" to (child.child("tipe").getValue(String::class.java) ?: "info"),
                        "waktu" to (child.child("waktu").getValue(Long::class.java) ?: 0L),
                        "sudahDibaca" to (child.child("sudahDibaca").getValue(Boolean::class.java) ?: false)
                    ))
                }
                callback(daftar)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenNotifications error: ${error.message} (code=${error.code})")
            }
        }
        ref.orderByChild("waktu").limitToLast(30).addValueEventListener(listener)
        return listener
    }

    fun addNotification(judul: String, pesan: String, tipe: String) {
        val ref = rootRef?.child("notifications")?.push() ?: return
        ref.child("judul").setValue(judul)
        ref.child("pesan").setValue(pesan)
        ref.child("tipe").setValue(tipe)
        ref.child("waktu").setValue(System.currentTimeMillis())
        ref.child("sudahDibaca").setValue(false)
    }

    fun clearAllNotifications() {
        rootRef?.child("notifications")?.removeValue()
    }

    // ==========================================
    // Riwayat (History)
    // ==========================================
    fun listenHistory(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("historyLogs") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val riwayat = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children.reversed()) {
                    riwayat.add(mapOf(
                        "id" to (child.key ?: ""),
                        "aksi" to (child.child("aksi").getValue(String::class.java) ?: ""),
                        "tipeSampah" to (child.child("tipeSampah").getValue(String::class.java) ?: ""),
                        "idPengguna" to (child.child("idPengguna").getValue(String::class.java) ?: ""),
                        "namaLengkap" to (child.child("namaLengkap").getValue(String::class.java) ?: ""),
                        "waktu" to (child.child("waktu").getValue(Long::class.java) ?: 0L)
                    ))
                }
                callback(riwayat)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenHistory error: ${error.message} (code=${error.code})")
            }
        }
        ref.orderByChild("waktu").limitToLast(50).addValueEventListener(listener)
        return listener
    }

    fun addHistoryEntry(aksi: String, tipeSampah: String, idPengguna: String, namaLengkap: String) {
        val ref = rootRef?.child("historyLogs")?.push() ?: return
        ref.child("aksi").setValue(aksi)
        ref.child("tipeSampah").setValue(tipeSampah)
        ref.child("idPengguna").setValue(idPengguna)
        ref.child("namaLengkap").setValue(namaLengkap)
        ref.child("waktu").setValue(System.currentTimeMillis())
    }

    // ==========================================
    // Pengguna
    // ==========================================
    fun listenUsers(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("users") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    users.add(mapOf(
                        "id" to (child.key ?: ""),
                        "nama" to (child.child("nama").getValue(String::class.java) ?: ""),
                        "email" to (child.child("email").getValue(String::class.java) ?: ""),
                        "peran" to (child.child("peran").getValue(String::class.java) ?: ""),
                        "nomorHp" to (child.child("nomorHp").getValue(String::class.java) ?: "")
                    ))
                }
                callback(users)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenUsers error: ${error.message} (code=${error.code})")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun addUser(nama: String, email: String, peran: String, nomorHp: String = "", onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val ref = rootRef?.child("users")?.push() ?: run { onFailure("Firebase error"); return }
        ref.setValue(mapOf("nama" to nama, "email" to email, "peran" to peran, "nomorHp" to nomorHp))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Error") }
    }

    fun checkIfUserPreRegistered(email: String, callback: (Boolean, String, String, String, String) -> Unit) {
        rootRef?.child("users")?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    if ((child.child("email").getValue(String::class.java) ?: "").trim().equals(email.trim(), true)) {
                        callback(true, child.key ?: "", child.child("nama").getValue(String::class.java) ?: "", child.child("peran").getValue(String::class.java) ?: "", child.child("nomorHp").getValue(String::class.java) ?: "")
                        return
                    }
                }
                callback(false, "", "", "", "")
            }
            override fun onCancelled(error: DatabaseError) { callback(false, "", "", "", "") }
        })
    }

    fun seedUserData(uid: String, nama: String, email: String, peran: String, nomorHp: String = "", onComplete: () -> Unit) {
        rootRef?.child("users")?.child(uid)?.setValue(mapOf("nama" to nama, "email" to email, "peran" to peran, "nomorHp" to nomorHp))
            ?.addOnCompleteListener { onComplete() }
    }

    fun updateUser(userId: String, nama: String, email: String, nomorHp: String) {
        val ref = rootRef?.child("users")?.child(userId) ?: return
        ref.child("nama").setValue(nama)
        ref.child("email").setValue(email)
        ref.child("nomorHp").setValue(nomorHp)
    }

    fun deleteUser(userId: String) {
        rootRef?.child("users")?.child(userId)?.removeValue()
    }

    fun getUserData(uid: String, callback: (nama: String, peran: String, nomorHp: String) -> Unit) {
        rootRef?.child("users")?.child(uid)?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.child("nama").getValue(String::class.java) ?: "", snapshot.child("peran").getValue(String::class.java) ?: "", snapshot.child("nomorHp").getValue(String::class.java) ?: "")
            }
            override fun onCancelled(error: DatabaseError) { callback("", "", "") }
        })
    }

    fun getAdminPhone(callback: (String) -> Unit) {
        rootRef?.child("users")?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val role = child.child("peran").getValue(String::class.java) ?: ""
                    if (role == "Admin") {
                        val phone = child.child("nomorHp").getValue(String::class.java) ?: ""
                        if (phone.isNotEmpty()) {
                            callback(phone)
                            return
                        }
                    }
                }
                callback("")
            }
            override fun onCancelled(error: DatabaseError) { callback("") }
        })
    }

    // ==========================================
    // Statistik & Notifikasi (Internal Helper)
    // ==========================================
    fun listenDailyStats(callback: (List<Map<String, Any>>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("statistics")?.child("daily") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val stats = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    stats.add(mapOf(
                        "tanggal" to (child.key ?: ""),
                        "organik" to (child.child("organik").getValue(Int::class.java) ?: 0),
                        "nonOrganik" to (child.child("nonOrganik").getValue(Int::class.java) ?: 0)
                    ))
                }
                callback(stats)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenDailyStats error: ${error.message} (code=${error.code})")
            }
        }
        ref.limitToLast(7).addValueEventListener(listener)
        return listener
    }

    fun countPenuhEvents(callback: (Int) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("historyLogs") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var count = 0
                for (child in snapshot.children) {
                    val aksi = child.child("aksi").getValue(String::class.java)
                    if (aksi == "alert" || aksi == "penuh") count++
                }
                callback(count)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "countPenuhEvents error: ${error.message} (code=${error.code})")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun updateDailyStats(binType: String, percentage: Int) {
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        rootRef?.child("statistics")?.child("daily")?.child(dateKey)?.child(if (binType == "organik") "organik" else "nonOrganik")?.setValue(percentage)
    }

    data class NotificationSettings(
        val hampirPenuh: Boolean = true,
        val penuh: Boolean = true,
        val selesai: Boolean = true,
        val sistem: Boolean = true
    )

    fun saveNotificationSettings(userId: String, settings: NotificationSettings) {
        val ref = rootRef?.child("users")?.child(userId)?.child("pengaturan_notifikasi") ?: return
        ref.child("hampir_penuh").setValue(settings.hampirPenuh)
        ref.child("penuh").setValue(settings.penuh)
        ref.child("selesai").setValue(settings.selesai)
        ref.child("sistem").setValue(settings.sistem)
    }

    fun listenNotificationSettings(userId: String, callback: (NotificationSettings) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("users")?.child(userId)?.child("pengaturan_notifikasi") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(NotificationSettings(
                    hampirPenuh = snapshot.child("hampir_penuh").getValue(Boolean::class.java) ?: true,
                    penuh = snapshot.child("penuh").getValue(Boolean::class.java) ?: true,
                    selesai = snapshot.child("selesai").getValue(Boolean::class.java) ?: true,
                    sistem = snapshot.child("sistem").getValue(Boolean::class.java) ?: true
                ))
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenNotificationSettings error: ${error.message} (code=${error.code})")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeBinListener(binType: String, listener: ValueEventListener) { rootRef?.child("bins")?.child(binType)?.removeEventListener(listener) }
    fun removeDeviceListener(listener: ValueEventListener) { rootRef?.child("device")?.removeEventListener(listener) }
    fun removeNotificationListener(listener: ValueEventListener) { rootRef?.child("notifications")?.removeEventListener(listener) }
    fun removeHistoryListener(listener: ValueEventListener) { rootRef?.child("historyLogs")?.removeEventListener(listener) }
    fun removeUsersListener(listener: ValueEventListener) { rootRef?.child("users")?.removeEventListener(listener) }
    fun removeStatsListener(listener: ValueEventListener) { rootRef?.child("statistics")?.child("daily")?.removeEventListener(listener) }
    fun removePenuhListener(listener: ValueEventListener) { rootRef?.child("historyLogs")?.removeEventListener(listener) }
    fun removeNotificationSettingsListener(userId: String, listener: ValueEventListener) { rootRef?.child("users")?.child(userId)?.child("pengaturan_notifikasi")?.removeEventListener(listener) }
}
