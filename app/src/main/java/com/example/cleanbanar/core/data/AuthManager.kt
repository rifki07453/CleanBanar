package com.example.cleanbanar.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.auth.FirebaseAuth

/**
 * Pengelola autentikasi dan sesi pengguna menggunakan FirebaseAuth dan EncryptedSharedPreferences.
 * Data sesi dienkripsi dengan AES-256 agar tidak bisa dibaca meskipun di perangkat rooted.
 */
class AuthManager(context: Context) {

    companion object {
        private const val PREF_NAME = "clean_banar_auth"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_PHOTO_URL = "user_photo_url"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback ke SharedPreferences biasa jika device tidak mendukung
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Memeriksa apakah pengguna sudah masuk (login).
     * Menggunakan SharedPreferences sebagai cache role lokal.
     */
    fun isLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null && getUserRole().isNotEmpty()
    }

    /**
     * Menyimpan data sesi pengguna ke SharedPreferences.
     */
    fun saveSession(uid: String, name: String, email: String, role: String, phone: String = "", photoUrl: String = "") {
        prefs.edit().apply {
            putString(KEY_USER_ID, uid)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_ROLE, role)
            putString(KEY_USER_PHONE, phone)
            putString(KEY_USER_PHOTO_URL, photoUrl)
            apply()
        }
    }

    /**
     * Mendapatkan nama pengguna yang sedang masuk.
     */
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""

    /**
     * Mendapatkan email pengguna yang sedang masuk.
     */
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    /**
     * Mendapatkan peran pengguna yang sedang masuk (Admin/Petugas).
     */
    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "") ?: ""

    /**
     * Mendapatkan nomor HP pengguna yang sedang masuk.
     */
    fun getUserPhone(): String = prefs.getString(KEY_USER_PHONE, "") ?: ""

    /**
     * Mendapatkan tautan foto profil pengguna yang sedang masuk.
     */
    fun getUserPhotoUrl(): String = prefs.getString(KEY_USER_PHOTO_URL, "") ?: ""

    /**
     * Mendapatkan UID pengguna yang sedang masuk.
     */
    fun getUserId(): String = prefs.getString(KEY_USER_ID, "") ?: ""

    /**
     * Meluar (Logout): keluar dari Firebase dan hapus cache sesi lokal.
     */
    fun logout() {
        firebaseAuth.signOut()
        prefs.edit().clear().apply()
    }

    /** Update nama lokal di SharedPreferences */
    fun updateName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    /** Update email lokal di SharedPreferences */
    fun updateEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    /** Update nomor HP lokal di SharedPreferences */
    fun updatePhone(phone: String) {
        prefs.edit().putString(KEY_USER_PHONE, phone).apply()
    }

    /** Update tautan foto profil lokal di SharedPreferences */
    fun updatePhotoUrl(photoUrl: String) {
        prefs.edit().putString(KEY_USER_PHOTO_URL, photoUrl).apply()
    }
}
