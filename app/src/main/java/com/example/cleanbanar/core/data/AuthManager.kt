package com.example.cleanbanar.core.data

import android.content.Context
import android.content.SharedPreferences

class AuthManager(context: Context) {

    companion object {
        private const val PREF_NAME = "clean_banar_auth"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_ASSIGNED_AREA = "assigned_area_id"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Attempt login with email and password.
     * Returns the User if credentials match a seeded user, null otherwise.
     */
    fun login(email: String, password: String): User? {
        val users = UserSeeder.getSeededUsers()
        val matchedUser = users.find { it.email == email && it.password == password }

        if (matchedUser != null) {
            saveSession(matchedUser)
        }

        return matchedUser
    }

    /**
     * Save user session to SharedPreferences.
     */
    private fun saveSession(user: User) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putInt(KEY_USER_ID, user.id)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_ROLE, user.role)
            putString(KEY_ASSIGNED_AREA, user.assignedAreaId)
            apply()
        }
    }

    /**
     * Check if user is currently logged in.
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Get the currently logged-in user's name.
     */
    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    /**
     * Get the currently logged-in user's email.
     */
    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "") ?: ""
    }

    /**
     * Get the currently logged-in user's role ("Admin" or "Petugas").
     */
    fun getUserRole(): String {
        return prefs.getString(KEY_USER_ROLE, "") ?: ""
    }

    /**
     * Get the currently logged-in user's ID.
     */
    fun getUserId(): String {
        return prefs.getInt(KEY_USER_ID, 0).toString()
    }

    /**
     * Get the currently logged-in user's assigned area ID.
     */
    fun getAssignedAreaId(): String {
        return prefs.getString(KEY_ASSIGNED_AREA, "") ?: ""
    }

    /**
     * Logout: clear session data.
     */
    fun logout() {
        prefs.edit().clear().apply()
    }
}
