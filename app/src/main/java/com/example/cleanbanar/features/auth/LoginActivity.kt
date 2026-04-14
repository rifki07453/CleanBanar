package com.example.cleanbanar.features.auth

import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseActivity
import com.example.cleanbanar.databinding.ActivityLoginBinding
import com.example.cleanbanar.features.dashboard.MainActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private lateinit var authManager: AuthManager
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        authManager = AuthManager(this)

        // Auto-login: jika FirebaseAuth masih punya sesi aktif & role tersimpan di cache
        if (authManager.isLoggedIn()) {
            navigateToDashboard(authManager.getUserRole())
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan password tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)

            // Login via Firebase Authentication
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: return@addOnSuccessListener

                    // Ambil role dari Realtime Database (node "cleanbanar/users/{uid}")
                    FirebaseManager.getUserData(uid) { name, role, assignedAreaId ->
                        if (role.isEmpty()) {
                            // Data belum ada di DB → seed otomatis berdasarkan email
                            val seedData = getSeedDataForEmail(email)
                            if (seedData == null) {
                                setLoading(false)
                                firebaseAuth.signOut()
                                Toast.makeText(
                                    this,
                                    "Akun tidak memiliki akses sistem. Hubungi Administrator.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@getUserData
                            }

                            // Tulis data user ke Realtime Database lalu masuk
                            FirebaseManager.seedUserData(
                                uid = uid,
                                name = seedData.first,
                                email = email,
                                role = seedData.second,
                                assignedAreaId = seedData.third
                            ) {
                                setLoading(false)
                                authManager.saveSession(
                                    uid = uid,
                                    name = seedData.first,
                                    email = email,
                                    role = seedData.second,
                                    assignedAreaId = seedData.third
                                )
                                Toast.makeText(this, "Selamat datang, ${seedData.first}!", Toast.LENGTH_SHORT).show()
                                navigateToDashboard(seedData.second)
                            }
                        } else {
                            setLoading(false)
                            // Simpan sesi ke SharedPreferences sebagai cache lokal
                            authManager.saveSession(
                                uid = uid,
                                name = name,
                                email = email,
                                role = role,
                                assignedAreaId = assignedAreaId
                            )
                            Toast.makeText(this, "Selamat datang, $name!", Toast.LENGTH_SHORT).show()
                            navigateToDashboard(role)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    val msg = when {
                        e.message?.contains("no user record") == true ||
                        e.message?.contains("user-not-found") == true ->
                            "Email tidak terdaftar"
                        e.message?.contains("password is invalid") == true ||
                        e.message?.contains("wrong-password") == true ->
                            "Password salah"
                        e.message?.contains("network") == true ->
                            "Tidak ada koneksi internet"
                        else -> "Login gagal: ${e.message}"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
        }
    }

    /**
     * Menentukan data awal (name, role, assignedAreaId) berdasarkan email.
     * Hanya berlaku untuk akun default sistem.
     * Return null jika email tidak dikenali sebagai akun sistem.
     */
    private fun getSeedDataForEmail(email: String): Triple<String, String, String>? {
        return when (email.lowercase()) {
            "admin@cleanbanar.com"   -> Triple("Administrator",    "Admin",   "")
            "petugas@cleanbanar.com" -> Triple("Petugas Lapangan", "Petugas", "A1")
            else -> null
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Memuat..." else "Masuk"
    }

    private fun navigateToDashboard(role: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
        finish()
    }
}
