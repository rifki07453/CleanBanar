package com.example.cleanbanar.features.profile

import android.app.AlertDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentProfileBinding
import com.example.cleanbanar.features.auth.LoginActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private lateinit var authManager: AuthManager

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentProfileBinding {
        return FragmentProfileBinding.inflate(inflater, container, false)
    }

    // ==========================================
    // View Setup & User Info
    // ==========================================
    override fun setupViews() {
        authManager = AuthManager(requireContext())

        // Tampilkan data user dari sesi lokal
        binding.tvProfileName.text = authManager.getUserName()
        binding.tvProfileEmail.text = authManager.getUserEmail()

        // Edit Profil — dinamis & tersimpan ke Firebase
        binding.btnEditProfil.setOnClickListener {
            showEditProfileDialog()
        }

        // Pengaturan Notifikasi — fungsional dengan toggle Firebase
        binding.btnNavNotifikasi.setOnClickListener {
            showNotificationSettingsDialog()
        }

        // Keamanan & Password — re-auth Firebase
        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // Keluar Akun
        binding.btnLogout.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            
            // Hentikan background service agar tidak memory leak/crash
            com.example.cleanbanar.features.dashboard.BinObserverService.stopService(ctx)
            
            // Hapus sesi lokal dan sign out Firebase
            // Redirection ke halaman login akan otomatis di-handle oleh AuthStateListener di MainActivity
            authManager.logout()
        }
    }

    override fun observeData() {}

    // ==========================================
    // Edit Profil Dialog (Dinamis → Firebase)
    // ==========================================
    private fun showEditProfileDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 0)
        }

        val labelNama = TextView(requireContext()).apply {
            text = "Nama Lengkap"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etNama = EditText(requireContext()).apply {
            setText(authManager.getUserName())
            textSize = 14f
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTextColor(android.graphics.Color.parseColor("#111827"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50.dpToPx()
            ).apply { bottomMargin = 12.dpToPx() }
        }

        val labelEmail = TextView(requireContext()).apply {
            text = "Email"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etEmail = EditText(requireContext()).apply {
            setText(authManager.getUserEmail())
            textSize = 14f
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTextColor(android.graphics.Color.parseColor("#111827"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50.dpToPx()
            )
        }

        layout.addView(labelNama)
        layout.addView(etNama)
        layout.addView(labelEmail)
        layout.addView(etEmail)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profil")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = etNama.text.toString().trim()
                val newEmail = etEmail.text.toString().trim()

                when {
                    newName.isEmpty() -> {
                        Toast.makeText(requireContext(), "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    newEmail.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() -> {
                        Toast.makeText(requireContext(), "Format email tidak valid", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }

                val userId = authManager.getUserId()
                FirebaseManager.updateUser(userId, newName, newEmail)

                // Update sesi lokal
                authManager.updateName(newName)
                if (newEmail.isNotEmpty()) authManager.updateEmail(newEmail)

                // Update tampilan header profil
                binding.tvProfileName.text = newName
                if (newEmail.isNotEmpty()) binding.tvProfileEmail.text = newEmail

                Toast.makeText(requireContext(), "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==========================================
    // Pengaturan Notifikasi (Firebase Toggle)
    // ==========================================
    @Suppress("DEPRECATION")
    private fun showNotificationSettingsDialog() {
        val userId = authManager.getUserId()
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }

        fun makeRow(label: String): Pair<LinearLayout, Switch> {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dpToPx() }
            }
            val tv = TextView(requireContext()).apply {
                text = label
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#374151"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val sw = Switch(requireContext()).apply {
                isChecked = true
                thumbTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#16A34A"))
            }
            row.addView(tv)
            row.addView(sw)
            return Pair(row, sw)
        }

        val (rowHampir, swHampir) = makeRow("Hampir Penuh")
        val (rowPenuh, swPenuh) = makeRow("Penuh")
        val (rowSelesai, swSelesai) = makeRow("Selesai Dikosongkan")
        val (rowSistem, swSistem) = makeRow("Notifikasi Sistem")

        layout.addView(rowHampir)
        layout.addView(rowPenuh)
        layout.addView(rowSelesai)
        layout.addView(rowSistem)

        // Load state dari Firebase
        FirebaseManager.listenNotificationSettings(userId) { settings ->
            if (isAdded) {
                swHampir.isChecked = settings.hampirPenuh
                swPenuh.isChecked = settings.penuh
                swSelesai.isChecked = settings.selesai
                swSistem.isChecked = settings.sistem
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Pengaturan Notifikasi")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val newSettings = FirebaseManager.NotificationSettings(
                    hampirPenuh = swHampir.isChecked,
                    penuh = swPenuh.isChecked,
                    selesai = swSelesai.isChecked,
                    sistem = swSistem.isChecked
                )
                FirebaseManager.saveNotificationSettings(userId, newSettings)
                Toast.makeText(requireContext(), "Pengaturan notifikasi disimpan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==========================================
    // Change Password Dialog (Firebase Re-Auth)
    // ==========================================
    private fun showChangePasswordDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 0)
        }

        val labelCurrent = TextView(requireContext()).apply {
            text = "Password Saat Ini"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etCurrent = EditText(requireContext()).apply {
            hint = "Masukkan password lama"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 50.dpToPx()
            ).apply { bottomMargin = 12.dpToPx() }
        }

        val labelNew = TextView(requireContext()).apply {
            text = "Password Baru"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etNew = EditText(requireContext()).apply {
            hint = "Minimal 6 karakter"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 50.dpToPx()
            ).apply { bottomMargin = 12.dpToPx() }
        }

        val labelConfirm = TextView(requireContext()).apply {
            text = "Konfirmasi Password Baru"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etConfirm = EditText(requireContext()).apply {
            hint = "Ulangi password baru"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 50.dpToPx()
            )
        }

        layout.addView(labelCurrent)
        layout.addView(etCurrent)
        layout.addView(labelNew)
        layout.addView(etNew)
        layout.addView(labelConfirm)
        layout.addView(etConfirm)

        AlertDialog.Builder(requireContext())
            .setTitle("Ubah Password")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val currentPass = etCurrent.text.toString().trim()
                val newPass = etNew.text.toString().trim()
                val confirmPass = etConfirm.text.toString().trim()

                when {
                    currentPass.isEmpty() -> Toast.makeText(requireContext(), "Password lama wajib diisi", Toast.LENGTH_SHORT).show()
                    newPass.length < 6 -> Toast.makeText(requireContext(), "Password baru minimal 6 karakter", Toast.LENGTH_SHORT).show()
                    newPass != confirmPass -> Toast.makeText(requireContext(), "Konfirmasi password tidak cocok", Toast.LENGTH_SHORT).show()
                    else -> changePasswordWithReAuth(currentPass, newPass)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun changePasswordWithReAuth(currentPassword: String, newPassword: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = authManager.getUserEmail()

        if (user == null || email.isEmpty()) {
            Toast.makeText(requireContext(), "Sesi tidak valid. Silakan login ulang.", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Re-auth berhasil, update password
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "✓ Password berhasil diubah", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Password lama salah. Coba lagi.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
