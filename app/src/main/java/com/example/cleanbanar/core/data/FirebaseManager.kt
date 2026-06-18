package com.example.cleanbanar.core.data

import android.util.Log
import android.net.Uri
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

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
    fun listenBinStatus(deviceId: String, binType: String, callback: (persentase: Int, status: String, terakhirUpdate: Long, terakhirDikosongkan: Long) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("devices")?.child(deviceId)?.child("bins")?.child(binType) ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val persentase = snapshot.child("persentaseIsi").getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                val status = snapshot.child("status").getValue(String::class.java) ?: "Normal"
                val terakhirUpdate = snapshot.child("terakhirUpdate").getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toLong() ?: 0L
                val terakhirDikosongkan = snapshot.child("terakhirDikosongkan").getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toLong() ?: 0L
                callback(persentase, status, terakhirUpdate, terakhirDikosongkan)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenBinStatus error: ${error.message} (code=${error.code})")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun updateBinStatus(deviceId: String, binType: String, persentase: Int, status: String) {
        val ref = rootRef?.child("devices")?.child(deviceId)?.child("bins")?.child(binType) ?: return
        ref.child("persentaseIsi").setValue(persentase.coerceIn(0, 100))
        ref.child("status").setValue(status)
        ref.child("terakhirUpdate").setValue(System.currentTimeMillis())
        updateDailyStats(binType, persentase)
    }

    fun emptyBin(deviceId: String, binType: String, userId: String, userName: String) {
        val safeBinType = sanitize(binType)
        val safeUserName = sanitize(userName)
        val safeUserId = sanitize(userId)
        updateBinStatus(deviceId, safeBinType, 0, "Normal")
        rootRef?.child("devices")?.child(deviceId)?.child("bins")?.child(safeBinType)?.child("terakhirDikosongkan")?.setValue(System.currentTimeMillis())
        addHistoryEntry("pengosongan", safeBinType, safeUserId, safeUserName)
        incrementEmptyCount(safeBinType)
        // Catatan: notifikasi "Selesai Dikosongkan" kini ditangani oleh
        // BinObserver.triggerSelesaiNotification() berdasarkan pengaturan notifikasi pengguna
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
    fun listenDevices(callback: (List<DeviceModel>) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("devices") ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val devices = mutableListOf<DeviceModel>()
                for (child in snapshot.children) {
                    try {
                        val id = child.child("id").getValue(String::class.java) ?: child.key ?: ""
                        val nama = child.child("nama").getValue(String::class.java) ?: ""
                        val status = child.child("statusKoneksi").getValue(String::class.java) ?: "OFFLINE"
                        val terakhir = child.child("terakhirTerlihat").getValue(Any::class.java)?.toString()?.toLongOrNull() ?: 0L
                        val tipe = child.child("tipeJaringan").getValue(String::class.java) ?: "WIFI"
                        
                        val pinsNode = child.child("config").child("pins")
                        val pins = PinConfig(
                            trigOrganik = pinsNode.child("trigOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 5,
                            echoOrganik = pinsNode.child("echoOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 18,
                            trigNonOrganik = pinsNode.child("trigNonOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 16,
                            echoNonOrganik = pinsNode.child("echoNonOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 17,
                            trigLuarOrganik = pinsNode.child("trigLuarOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 22,
                            echoLuarOrganik = pinsNode.child("echoLuarOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 23,
                            trigLuarNonOrganik = pinsNode.child("trigLuarNonOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 19,
                            echoLuarNonOrganik = pinsNode.child("echoLuarNonOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 21,
                            servoOrganik = pinsNode.child("servoOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 4,
                            servoNonOrganik = pinsNode.child("servoNonOrganik").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 15
                        )
                        
                        val tinggiTong = child.child("config").child("tinggiTong").getValue(Any::class.java)?.toString()?.toDoubleOrNull() ?: 50.0
                        val batasPenuh = child.child("config").child("batasPenuh").getValue(Any::class.java)?.toString()?.toDoubleOrNull() ?: 5.0
                        val batasJarakTangan = child.child("config").child("batasJarakTangan").getValue(Any::class.java)?.toString()?.toDoubleOrNull() ?: 15.0
                        
                        val ipAddr = child.child("ipAddress").getValue(String::class.java) ?: "-"
                        val fbSsid = child.child("ssid").getValue(String::class.java) ?: "-"
                        val sinyal = child.child("kekuatanSinyal").getValue(Any::class.java)?.toString()?.toIntOrNull() ?: 0
                        
                        devices.add(DeviceModel(id, nama, status, terakhir, tipe, ipAddr, fbSsid, sinyal, DeviceConfig(pins, tinggiTong, batasPenuh, batasJarakTangan)))
                    } catch (e: Exception) {
                        Log.e(TAG, "Gagal memproses device ${child.key}: ${e.message}")
                    }
                }
                callback(devices)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenDevices error: ${error.message} (code=${error.code})")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun addDevice(id: String, nama: String, pins: PinConfig) {
        val ref = rootRef?.child("devices")?.child(id) ?: return
        ref.child("id").setValue(id)
        ref.child("nama").setValue(nama)
        ref.child("statusKoneksi").setValue("OFFLINE")
        ref.child("terakhirTerlihat").setValue(0L)
        ref.child("tipeJaringan").setValue("WIFI")
        ref.child("config").child("pins").setValue(pins)
        ref.child("config").child("tinggiTong").setValue(50.0)
        ref.child("config").child("batasPenuh").setValue(5.0)
        ref.child("config").child("batasJarakTangan").setValue(15.0)
        ref.child("bins").child("organik").setValue(mapOf("persentaseIsi" to 0, "status" to "Normal", "terakhirUpdate" to 0L, "terakhirDikosongkan" to 0L))
        ref.child("bins").child("nonOrganik").setValue(mapOf("persentaseIsi" to 0, "status" to "Normal", "terakhirUpdate" to 0L, "terakhirDikosongkan" to 0L))
    }

    fun deleteDevice(id: String) {
        rootRef?.child("devices")?.child(id)?.removeValue()
    }

    fun updateDevicePins(id: String, pins: PinConfig) {
        rootRef?.child("devices")?.child(id)?.child("config")?.child("pins")?.setValue(pins)
    }

    fun updateDeviceConfig(id: String, tinggiTong: Double, batasPenuh: Double, batasJarakTangan: Double) {
        rootRef?.child("devices")?.child(id)?.child("config")?.child("tinggiTong")?.setValue(tinggiTong)
        rootRef?.child("devices")?.child(id)?.child("config")?.child("batasPenuh")?.setValue(batasPenuh)
        rootRef?.child("devices")?.child(id)?.child("config")?.child("batasJarakTangan")?.setValue(batasJarakTangan)
    }

    fun updateDeviceNetworkType(deviceId: String, tipe: String) {
        rootRef?.child("devices")?.child(deviceId)?.child("tipeJaringan")?.setValue(tipe)
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
        ref.orderByChild("waktu").limitToLast(10).addValueEventListener(listener)
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
                        "nomorHp" to (child.child("nomorHp").getValue(String::class.java) ?: ""),
                        "photoUrl" to (child.child("photoUrl").getValue(String::class.java) ?: "")
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
        ref.setValue(mapOf("nama" to nama, "email" to email, "peran" to peran, "nomorHp" to nomorHp, "photoUrl" to ""))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Error") }
    }

    fun checkIfUserPreRegistered(email: String, callback: (Boolean, String, String, String, String) -> Unit) {
        val ref = rootRef?.child("users")
        if (ref == null) {
            callback(false, "", "", "", "")
            return
        }
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
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
        val ref = rootRef?.child("users")?.child(uid)
        if (ref == null) {
            onComplete()
            return
        }
        ref.setValue(mapOf("nama" to nama, "email" to email, "peran" to peran, "nomorHp" to nomorHp, "photoUrl" to ""))
            .addOnCompleteListener { onComplete() }
    }

    fun updateUser(userId: String, nama: String, email: String, nomorHp: String, peran: String) {
        val ref = rootRef?.child("users")?.child(userId) ?: return
        ref.child("nama").setValue(nama)
        ref.child("email").setValue(email)
        ref.child("nomorHp").setValue(nomorHp)
        
        // Simpan nomor admin ke node publik agar bisa dibaca dari halaman login
        if (peran == "Admin" && nomorHp.isNotEmpty()) {
            rootRef?.child("public_info")?.child("admin_phone")?.setValue(nomorHp)
        }
    }

    fun deleteUser(userId: String) {
        rootRef?.child("users")?.child(userId)?.removeValue()
    }

    fun getUserData(uid: String, callback: (nama: String, peran: String, nomorHp: String) -> Unit) {
        val ref = rootRef?.child("users")?.child(uid)
        if (ref == null) {
            callback("", "", "")
            return
        }
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(
                    snapshot.child("nama").getValue(String::class.java) ?: "",
                    snapshot.child("peran").getValue(String::class.java) ?: "",
                    snapshot.child("nomorHp").getValue(String::class.java) ?: ""
                )
            }
            override fun onCancelled(error: DatabaseError) { callback("", "", "") }
        })
    }

    fun getAdminPhone(callback: (String) -> Unit) {
        val ref = rootRef?.child("public_info")?.child("admin_phone")
        if (ref == null) {
            callback("")
            return
        }
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val phone = snapshot.getValue(String::class.java) ?: ""
                callback(phone)
            }
            override fun onCancelled(error: DatabaseError) { callback("") }
        })
    }

    // ==========================================
    // Foto Profil (Firebase Storage)
    // ==========================================
    fun uploadProfilePicture(userId: String, imageUri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("profile_pictures/$userId.jpg")
        
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val photoUrl = uri.toString()
                    updateUserPhotoUrl(userId, photoUrl)
                    onSuccess(photoUrl)
                }.addOnFailureListener { e ->
                    onFailure(e)
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    fun uploadProfilePictureBytes(userId: String, data: ByteArray, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        Thread {
            try {
                // Menggunakan Catbox.moe API (100% tanpa kompresi, stabil, dan mendukung file besar)
                val url = java.net.URL("https://catbox.moe/user/api.php")
                val conn = url.openConnection() as java.net.HttpURLConnection
                val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                
                val os = java.io.DataOutputStream(conn.outputStream)
                
                // Add reqtype
                os.writeBytes("--$boundary\r\n")
                os.writeBytes("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n")
                os.writeBytes("fileupload\r\n")
                
                // Add fileToUpload
                os.writeBytes("--$boundary\r\n")
                os.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"profile.jpg\"\r\n")
                os.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                os.write(data)
                os.writeBytes("\r\n")
                
                // End boundary
                os.writeBytes("--$boundary--\r\n")
                os.flush()
                os.close()

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    val response = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val imageUrl = response.toString().trim()
                    if (imageUrl.startsWith("http")) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            updateUserPhotoUrl(userId, imageUrl)
                            onSuccess(imageUrl)
                        }
                    } else {
                        throw Exception("Gagal mendapatkan URL valid dari server: $imageUrl")
                    }
                } else {
                    val errorReader = java.io.BufferedReader(java.io.InputStreamReader(conn.errorStream))
                    val errorMsg = errorReader.readText()
                    throw Exception("HTTP $responseCode: $errorMsg")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onFailure(e)
                }
            }
        }.start()
    }

    fun updateUserPhotoUrl(userId: String, photoUrl: String) {
        val ref = rootRef?.child("users")?.child(userId) ?: return
        ref.child("photoUrl").setValue(photoUrl)
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
                        "organik" to (child.child("organik").getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toInt() ?: 0),
                        "nonOrganik" to (child.child("nonOrganik").getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toInt() ?: 0),
                        "organikEmptyCount" to (child.child("organikEmptyCount").getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toInt() ?: 0),
                        "nonOrganikEmptyCount" to (child.child("nonOrganikEmptyCount").getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toInt() ?: 0)
                    ))
                }
                callback(stats)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenDailyStats error: ${error.message} (code=${error.code})")
            }
        }
        ref.limitToLast(35).addValueEventListener(listener)
        return listener
    }

    fun countPenuhEvents(callback: (Int) -> Unit): ValueEventListener? {
        val ref = rootRef?.child("historyLogs") ?: run {
            callback(0)
            return null
        }
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
        val ref = rootRef?.child("statistics")?.child("daily")?.child(dateKey)?.child(if (binType == "organik") "organik" else "nonOrganik")
        if (ref == null) return
        
        // Jangan timpa dengan 0 (saat dikosongkan), agar bar chart tetap menampilkan max capacity di hari tersebut
        if (percentage == 0) return
        
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentMax = snapshot.getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                if (percentage > currentMax) {
                    ref.setValue(percentage)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun incrementEmptyCount(binType: String) {
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val field = if (binType == "organik") "organikEmptyCount" else "nonOrganikEmptyCount"
        val ref = rootRef?.child("statistics")?.child("daily")?.child(dateKey)?.child(field) ?: return
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCount = snapshot.getValue(Any::class.java)?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                ref.setValue(currentCount + 1)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
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

    fun getNotificationSettings(userId: String, callback: (NotificationSettings) -> Unit) {
        val ref = rootRef?.child("users")?.child(userId)?.child("pengaturan_notifikasi")
        if (ref == null) {
            callback(NotificationSettings())
            return
        }
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(NotificationSettings(
                    hampirPenuh = snapshot.child("hampir_penuh").getValue(Boolean::class.java) ?: true,
                    penuh = snapshot.child("penuh").getValue(Boolean::class.java) ?: true,
                    selesai = snapshot.child("selesai").getValue(Boolean::class.java) ?: true,
                    sistem = snapshot.child("sistem").getValue(Boolean::class.java) ?: true
                ))
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "getNotificationSettings error: ${error.message} (code=${error.code})")
                callback(NotificationSettings())
            }
        })
    }

    fun removeBinListener(deviceId: String, binType: String, listener: ValueEventListener) { rootRef?.child("devices")?.child(deviceId)?.child("bins")?.child(binType)?.removeEventListener(listener) }
    fun removeDeviceListener(listener: ValueEventListener) { rootRef?.child("devices")?.removeEventListener(listener) }
    fun removeNotificationListener(listener: ValueEventListener) { rootRef?.child("notifications")?.removeEventListener(listener) }
    fun removeHistoryListener(listener: ValueEventListener) { rootRef?.child("historyLogs")?.removeEventListener(listener) }
    fun removeUsersListener(listener: ValueEventListener) { rootRef?.child("users")?.removeEventListener(listener) }
    fun removeStatsListener(listener: ValueEventListener) { rootRef?.child("statistics")?.child("daily")?.removeEventListener(listener) }
    fun removePenuhListener(listener: ValueEventListener) { rootRef?.child("historyLogs")?.removeEventListener(listener) }
    fun removeNotificationSettingsListener(userId: String, listener: ValueEventListener) { rootRef?.child("users")?.child(userId)?.child("pengaturan_notifikasi")?.removeEventListener(listener) }
}
