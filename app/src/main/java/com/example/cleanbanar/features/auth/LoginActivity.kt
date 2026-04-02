package com.example.cleanbanar.features.auth

import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.ui.BaseActivity
import com.example.cleanbanar.databinding.ActivityLoginBinding
import com.example.cleanbanar.features.dashboard.MainActivity

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private lateinit var authManager: AuthManager

    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        authManager = AuthManager(this)

        // Auto-login: if user already has an active session, skip to MainActivity
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

            val user = authManager.login(email, password)

            if (user != null) {
                Toast.makeText(this, "Selamat datang, ${user.name}!", Toast.LENGTH_SHORT).show()
                navigateToDashboard(user.role)
            } else {
                Toast.makeText(this, "Email atau password salah", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvRegister.setOnClickListener {
            Toast.makeText(this, "Fitur registrasi belum tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToDashboard(role: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
        finish() // Close login screen so user can't go back with back button
    }
}
