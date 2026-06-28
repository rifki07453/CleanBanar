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

    private var cachedAdminPhone: String = ""

    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Keamanan: Cegah screenshot dan screen recording pada halaman login (dinonaktifkan sementara untuk development/simulator)
        // window.setFlags(
        //     android.view.WindowManager.LayoutParams.FLAG_SECURE,
        //     android.view.WindowManager.LayoutParams.FLAG_SECURE
        // )

        authManager = AuthManager(this)

        if (authManager.isLoggedIn()) {
            navigateToDashboard(authManager.getUserRole())
            return
        }

        // Ambil nomor admin di background saat halaman pertama kali dimuat
        FirebaseManager.getAdminPhone { phone ->
            cachedAdminPhone = phone
        }

        binding.tvForgotPassword.setOnClickListener {
            var phoneToUse = cachedAdminPhone
            if (phoneToUse.isEmpty()) {
                phoneToUse = "6281234567890" // Nomor default jika Admin belum set nomor HP
            } else if (phoneToUse.startsWith("0")) {
                phoneToUse = "62" + phoneToUse.substring(1)
            }

            val url = "https://api.whatsapp.com/send?phone=$phoneToUse&text=Halo%20Admin,%20saya%20ingin%20mereset%20password%20akun%20CleanBanar%20saya."
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse(url)
            try {
                startActivity(intent)
                overridePendingTransition(com.example.cleanbanar.R.anim.slide_in_up, com.example.cleanbanar.R.anim.fade_out)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka WhatsApp. Pastikan aplikasi terinstal.", Toast.LENGTH_SHORT).show()
            }
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

                    FirebaseManager.getUserData(uid) { nama, peran, nomorHp, photoUrl ->
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
                                peran = seedData.second,
                                nomorHp = ""
                            ) {
                                setLoading(false)
                                authManager.saveSession(uid = uid, name = seedData.first, email = email, role = seedData.second, phone = "", photoUrl = "")
                                Toast.makeText(this, "Selamat datang, ${seedData.first}!", Toast.LENGTH_SHORT).show()
                                navigateToDashboard(seedData.second)
                            }
                        } else {
                            setLoading(false)
                            authManager.saveSession(uid = uid, name = nama, email = email, role = peran, phone = nomorHp, photoUrl = photoUrl)
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

        var logoClickCount = 0
        var lastLogoClickTime = 0L
        binding.ivLogo.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLogoClickTime < 1000) {
                logoClickCount++
            } else {
                logoClickCount = 1
            }
            lastLogoClickTime = currentTime

            if (logoClickCount == 10) {
                logoClickCount = 0
                showRecoveryDialog()
            }
        }
    }

    private fun checkAndRegisterStaff(email: String, password: String) {
        FirebaseManager.checkIfUserPreRegistered(email) { exists, oldId, nama, peran, nomorHp ->
            if (exists) {
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: return@addOnSuccessListener
                        FirebaseManager.seedUserData(uid, nama, email, peran, nomorHp) {
                            FirebaseManager.deleteUser(oldId)
                            setLoading(false)
                            // Jika staff baru register, foto belum ada
                            authManager.saveSession(uid, nama, email, peran, nomorHp, photoUrl = "")
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

    private fun navigateToMain() {
        val intent = android.content.Intent(this, com.example.cleanbanar.features.dashboard.MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(com.example.cleanbanar.R.anim.slide_in_up, com.example.cleanbanar.R.anim.fade_out)
        finish()
    }

    private fun navigateToDashboard(role: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
        overridePendingTransition(com.example.cleanbanar.R.anim.slide_in_up, com.example.cleanbanar.R.anim.fade_out)
        finish()
    }

    private fun showRecoveryDialog() {
        val context = this
        val input = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Masukkan Passcode"
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 16.dpToPx())
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            }
            layoutParams = params
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle("Menu Pemulihan Admin")
            .setMessage("Masukkan Master Passcode untuk memulihkan akun default Administrator.")
            .setView(container)
            .setPositiveButton("Verifikasi") { dialog, _ ->
                val passcode = input.text.toString().trim()
                handleAdminRecovery(passcode)
                dialog.dismiss()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun handleAdminRecovery(passcode: String) {
        if (passcode == "CLEANBANAR_RECOVERY_2026") {
            setLoading(true)
            val email = "admin@cleanbanar.com"
            val defaultPassword = "admin123"

            // 1. Coba daftarkan di Firebase Auth
            firebaseAuth.createUserWithEmailAndPassword(email, defaultPassword)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid ?: return@addOnSuccessListener
                    // 2. Semai data admin ke RTDB
                    FirebaseManager.seedUserData(uid, "Administrator", email, "Admin", "") {
                        setLoading(false)
                        Toast.makeText(this, "Pemulihan Akun Berhasil! Akun Admin dibuat dengan email: $email dan password: $defaultPassword", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    val errMsg = e.message ?: ""
                    if (errMsg.contains("already in use") || errMsg.contains("email-already-in-use")) {
                        // Akun Auth sudah ada, tapi mungkin datanya di RTDB terhapus!
                        // Mari coba signIn dulu untuk mendapatkan UID
                        firebaseAuth.signInWithEmailAndPassword(email, defaultPassword)
                            .addOnSuccessListener { signInResult ->
                                val uid = signInResult.user?.uid ?: return@addOnSuccessListener
                                FirebaseManager.seedUserData(uid, "Administrator", email, "Admin", "") {
                                    setLoading(false)
                                    Toast.makeText(this, "Data profil Admin berhasil dipulihkan di Database!", Toast.LENGTH_LONG).show()
                                }
                            }
                            .addOnFailureListener { signInErr ->
                                setLoading(false)
                                Toast.makeText(this, "Akun Auth sudah terdaftar tetapi gagal login dengan password default: ${signInErr.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        setLoading(false)
                        Toast.makeText(this, "Gagal membuat akun Admin: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            Toast.makeText(this, "Master Passcode salah!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
