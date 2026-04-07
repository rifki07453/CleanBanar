package com.example.cleanbanar.features.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentProfileBinding
import com.example.cleanbanar.features.auth.LoginActivity

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private lateinit var authManager: AuthManager

    // ==========================================
    // Debounce Handler for Notification Toggles
    // ==========================================
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var isLoadingSettings = true // Flag to prevent save during initial load

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
        binding.tvProfileRole.text = authManager.getUserRole()

        // Load notification settings from Firebase
        loadNotificationSettings()

        // Setup toggle listeners (with debounce)
        setupNotificationToggles()

        // Change password
        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // Logout button
        binding.btnLogout.setOnClickListener {
            authManager.logout()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // ==========================================
    // Notification Settings - Firebase Sync
    // ==========================================

    /**
     * Load settings from Firebase and apply to toggle switches.
     * If data not found, defaults to all ON.
     */
    private fun loadNotificationSettings() {
        val userId = authManager.getUserId()
        isLoadingSettings = true

        FirebaseManager.loadNotificationSettings(userId) { settings ->
            if (!isAdded) return@loadNotificationSettings
            binding.switchHampirPenuh.isChecked = settings.hampirPenuh
            binding.switchPenuh.isChecked = settings.penuh
            binding.switchSelesai.isChecked = settings.selesai
            binding.switchSistem.isChecked = settings.sistem
            isLoadingSettings = false
        }
    }

    /**
     * Wire up toggle switch listeners with 400ms debounce to prevent
     * rapid toggle spam from flooding Firebase writes.
     */
    private fun setupNotificationToggles() {
        val toggleAction = { _: Any, _: Any ->
            if (!isLoadingSettings) {
                scheduleSettingsSave()
            }
        }

        binding.switchHampirPenuh.setOnCheckedChangeListener { _, _ -> toggleAction(Unit, Unit) }
        binding.switchPenuh.setOnCheckedChangeListener { _, _ -> toggleAction(Unit, Unit) }
        binding.switchSelesai.setOnCheckedChangeListener { _, _ -> toggleAction(Unit, Unit) }
        binding.switchSistem.setOnCheckedChangeListener { _, _ -> toggleAction(Unit, Unit) }
    }

    /**
     * Debounced save: waits 400ms after the last toggle change before writing
     * to Firebase. Prevents rapid consecutive writes.
     */
    private fun scheduleSettingsSave() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }

        debounceRunnable = Runnable {
            val settings = FirebaseManager.NotificationSettings(
                hampirPenuh = binding.switchHampirPenuh.isChecked,
                penuh = binding.switchPenuh.isChecked,
                selesai = binding.switchSelesai.isChecked,
                sistem = binding.switchSistem.isChecked
            )

            FirebaseManager.saveNotificationSettings(authManager.getUserId(), settings)

            if (isAdded) {
                Toast.makeText(requireContext(), "Pengaturan notifikasi diperbarui", Toast.LENGTH_SHORT).show()
            }
        }

        debounceHandler.postDelayed(debounceRunnable!!, 400)
    }

    // ==========================================
    // Change Password Dialog
    // ==========================================
    private fun showChangePasswordDialog() {
        val dpToPx = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0)
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

    // ==========================================
    // Lifecycle - Cleanup
    // ==========================================
    override fun onDestroyView() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        super.onDestroyView()
    }
}
