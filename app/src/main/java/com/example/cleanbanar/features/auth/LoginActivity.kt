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
                showError("Email dan password tidak boleh kosong")
                return@setOnClickListener
            }

            hideError()
            setLoading(true)

            // Login via Firebase Authentication
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: return@addOnSuccessListener

                    // Ambil role dari Realtime Database (node "cleanbanar/users/{uid}")
                    FirebaseManager.getUserData(uid) { name, role ->
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
                                role = seedData.second
                            ) {
                                setLoading(false)
                                authManager.saveSession(
                                    uid = uid,
                                    name = seedData.first,
                                    email = email,
                                    role = seedData.second
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
                                role = role
                            )
                            Toast.makeText(this, "Selamat datang, $name!", Toast.LENGTH_SHORT).show()
                            navigateToDashboard(role)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    val errMsg = e.message ?: ""
                    android.util.Log.d("LoginActivity", "Auth error: $errMsg")

                    when {
                        // Jelas salah password (akun ada, tapi password salah)
                        errMsg.contains("password is invalid") ||
                        errMsg.contains("wrong-password") -> {
                            setLoading(false)
                            showError("Password salah")
                        }
                        // Tidak ada koneksi
                        errMsg.contains("network") -> {
                            setLoading(false)
                            showError("Tidak ada koneksi internet")
                        }
                        // Semua error lain → cek apakah email sudah didaftarkan Admin di DB
                        else -> checkAndRegisterStaff(email, password)
                    }
                }
        }
    }

    private fun checkAndRegisterStaff(email: String, password: String) {
        FirebaseManager.checkIfUserPreRegistered(email) { exists, oldId, name, role ->
            if (exists) {
                // Email sudah didaftarkan Admin. Buatkan akun Auth sekarang.
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: return@addOnSuccessListener
                        
                        // Pindahkan data dari ID lama (random push ID) ke ID Auth (uid)
                        FirebaseManager.seedUserData(uid, name, email, role) {
                            // Hapus data lama
                            FirebaseManager.deleteUser(oldId)
                            
                            // Simpan sesi dan arahkan ke dashboard
                            setLoading(false)
                            authManager.saveSession(uid, name, email, role)
                            Toast.makeText(this, "Akun berhasil diaktifkan! Selamat datang, $name", Toast.LENGTH_LONG).show()
                            navigateToDashboard(role)
                        }
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        if (e.message?.contains("already in use") == true || e.message?.contains("email-already-in-use") == true) {
                            showError("Password salah")
                        } else {
                            showError("Gagal mengaktifkan akun: ${e.message}")
                        }
                    }
            } else {
                setLoading(false)
                showError("Email tidak terdaftar. Silakan hubungi Administrator.")
            }
        }
    }

    private fun showError(message: String) {
        binding.errorContainer.visibility = android.view.View.VISIBLE
        binding.tvErrorMessage.text = message
    }
    
    private fun hideError() {
        binding.errorContainer.visibility = android.view.View.GONE
    }

    /**
     * Menentukan data awal (name, role) berdasarkan email.
     * Hanya berlaku untuk akun default sistem.
     * Return null jika email tidak dikenali sebagai akun sistem.
     */
    private fun getSeedDataForEmail(email: String): Pair<String, String>? {
        return when (email.lowercase()) {
            "admin@cleanbanar.com"   -> Pair("Administrator",    "Admin")
            "petugas@cleanbanar.com" -> Pair("Petugas Lapangan", "Petugas")
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
