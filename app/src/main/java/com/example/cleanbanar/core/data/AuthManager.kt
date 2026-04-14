package com.example.cleanbanar.core.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

class AuthManager(context: Context) {

    companion object {
        private const val PREF_NAME = "clean_banar_auth"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_ASSIGNED_AREA = "assigned_area_id"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Check if user is currently logged in via FirebaseAuth.
     * SharedPrefs digunakan sebagai cache role lokal supaya tidak perlu query DB tiap buka app.
     */
    fun isLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null && getUserRole().isNotEmpty()
    }

    /**
     * Save user session data (name, email, role, area) to SharedPreferences.
     * Dipanggil setelah Firebase Auth berhasil & role berhasil diambil dari DB.
     */
    fun saveSession(uid: String, name: String, email: String, role: String, assignedAreaId: String = "") {
        prefs.edit().apply {
            putString(KEY_USER_ID, uid)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_ROLE, role)
            putString(KEY_ASSIGNED_AREA, assignedAreaId)
            apply()
        }
    }

    /**
     * Get the currently logged-in user's name.
     */
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""

    /**
     * Get the currently logged-in user's email.
     */
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    /**
     * Get the currently logged-in user's role ("Admin" or "Petugas").
     */
    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "") ?: ""

    /**
     * Get the currently logged-in user's UID.
     */
    fun getUserId(): String = prefs.getString(KEY_USER_ID, "") ?: ""

    /**
     * Get the currently logged-in user's assigned area ID.
     */
    fun getAssignedAreaId(): String = prefs.getString(KEY_ASSIGNED_AREA, "") ?: ""

    /**
     * Logout: sign out dari Firebase, clear SharedPreferences cache.
     */
    fun logout() {
        firebaseAuth.signOut()
        prefs.edit().clear().apply()
    }
}
