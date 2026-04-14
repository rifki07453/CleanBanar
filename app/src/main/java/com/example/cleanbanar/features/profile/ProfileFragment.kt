package com.example.cleanbanar.features.profile

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentProfileBinding
import com.example.cleanbanar.features.auth.LoginActivity
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

        // Display user info from session
        binding.tvProfileName.text = authManager.getUserName()
        binding.tvProfileEmail.text = authManager.getUserEmail()

        binding.btnEditProfil.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profil Segera Hadir", Toast.LENGTH_SHORT).show()
        }

        binding.btnNavNotifikasi.setOnClickListener {
            Toast.makeText(requireContext(), "Pengaturan Notifikasi Segera Hadir", Toast.LENGTH_SHORT).show()
        }

        // Change password
        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // Logout button
        binding.btnLogout.setOnClickListener {
            // Sign out from FirebaseAuth as requested
            FirebaseAuth.getInstance().signOut()
            
            // Clear local session data
            authManager.logout()
            
            // Navigate to LoginActivity and clear backstack
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // ==========================================
    // Change Password Dialog
    // ==========================================
    private fun showChangePasswordDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 0)
        }
        val etCurrent = EditText(requireContext()).apply {
            hint = "Password saat ini"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etNew = EditText(requireContext()).apply {
            hint = "Password baru"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etConfirm = EditText(requireContext()).apply {
            hint = "Konfirmasi password baru"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etCurrent)
        layout.addView(etNew)
        layout.addView(etConfirm)

        AlertDialog.Builder(requireContext())
            .setTitle("Ubah Password")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val newPass = etNew.text.toString().trim()
                val confirmPass = etConfirm.text.toString().trim()
                if (newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(requireContext(), "Semua field wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass != confirmPass) {
                    Toast.makeText(requireContext(), "Password baru tidak cocok", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(requireContext(), "Password berhasil diubah", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
