package com.example.cleanbanar.features.auth

import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import android.app.AlertDialog
import android.widget.LinearLayout
import com.example.cleanbanar.R
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
        // Keamanan: Cegah screenshot dan screen recording pada halaman login
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        authManager = AuthManager(this)

        if (authManager.isLoggedIn()) {
            navigateToDashboard(authManager.getUserRole())
            return
        }

        binding.tvForgotPassword.setOnClickListener {
            val emailInput = binding.etEmail.text.toString().trim()
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 0)
            }
            
            val etResetEmail = android.widget.EditText(this).apply {
                hint = "Masukkan email Anda"
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                background = resources.getDrawable(R.drawable.edit_text_bg, null)
                setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 50.dpToPx()
                ).apply { bottomMargin = 12.dpToPx() }
                if (emailInput.isNotEmpty()) {
                    setText(emailInput)
                }
            }
            layout.addView(etResetEmail)

            AlertDialog.Builder(this)
                .setTitle("Lupa Password?")
                .setMessage("Kami akan mengirimkan link untuk mereset password ke email Anda.")
                .setView(layout)
                .setPositiveButton("Kirim Link") { _, _ ->
                    val emailToReset = etResetEmail.text.toString().trim()
                    if (emailToReset.isEmpty()) {
                        Toast.makeText(this, "Email tidak boleh kosong", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    firebaseAuth.sendPasswordResetEmail(emailToReset)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Link reset password telah dikirim ke $emailToReset", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Gagal mengirim link: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .setNegativeButton("Batal", null)
                .show()
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

            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: return@addOnSuccessListener

                    FirebaseManager.getUserData(uid) { nama, peran ->
                        if (peran.isEmpty()) {
                            val seedData = getSeedDataForEmail(email)
                            if (seedData == null) {
                                setLoading(false)
                                firebaseAuth.signOut()
                                Toast.makeText(this, "Akun tidak memiliki akses sistem. Hubungi Administrator.", Toast.LENGTH_LONG).show()
                                return@getUserData
                            }

                            FirebaseManager.seedUserData(
                                uid = uid,
                                nama = seedData.first,
                                email = email,
                                peran = seedData.second
                            ) {
                                setLoading(false)
                                authManager.saveSession(uid = uid, name = seedData.first, email = email, role = seedData.second)
                                Toast.makeText(this, "Selamat datang, ${seedData.first}!", Toast.LENGTH_SHORT).show()
                                navigateToDashboard(seedData.second)
                            }
                        } else {
                            setLoading(false)
                            authManager.saveSession(uid = uid, name = nama, email = email, role = peran)
                            Toast.makeText(this, "Selamat datang, $nama!", Toast.LENGTH_SHORT).show()
                            navigateToDashboard(peran)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    val errMsg = e.message ?: ""
                    when {
                        errMsg.contains("password is invalid") || errMsg.contains("wrong-password") -> {
                            setLoading(false)
                            showError("Password salah")
                        }
                        errMsg.contains("network") -> {
                            setLoading(false)
                            showError("Tidak ada koneksi internet")
                        }
                        else -> checkAndRegisterStaff(email, password)
                    }
                }
        }
    }

    private fun checkAndRegisterStaff(email: String, password: String) {
        FirebaseManager.checkIfUserPreRegistered(email) { exists, oldId, nama, peran ->
            if (exists) {
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: return@addOnSuccessListener
                        FirebaseManager.seedUserData(uid, nama, email, peran) {
                            FirebaseManager.deleteUser(oldId)
                            setLoading(false)
                            authManager.saveSession(uid, nama, email, peran)
                            Toast.makeText(this, "Akun berhasil diaktifkan! Selamat datang, $nama", Toast.LENGTH_LONG).show()
                            navigateToDashboard(peran)
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

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
