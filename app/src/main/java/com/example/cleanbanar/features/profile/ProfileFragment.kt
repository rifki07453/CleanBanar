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

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private lateinit var authManager: AuthManager

    // ==========================================
    // Debounce Handler for Notification Toggles
    // ==========================================
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var isLoadingSettings = true // Flag to prevent save during initial load

    private var isNotifExpanded = false

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

        // Accordion for Notifikasi
        binding.btnNavNotifikasi.setOnClickListener {
            isNotifExpanded = !isNotifExpanded
            if (isNotifExpanded) {
                binding.expandableNotifSection.visibility = View.VISIBLE
                binding.ivExpandNotif.rotation = 180f
            } else {
                binding.expandableNotifSection.visibility = View.GONE
                binding.ivExpandNotif.rotation = 0f
            }
        }

        binding.btnEditProfil.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profil Segera Hadir", Toast.LENGTH_SHORT).show()
        }

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

        // Fast local load to prevent bounce
        loadLocalSettingsState()
        // Load notification settings from Firebase
        loadNotificationSettings()
        // Setup toggle listeners (with debounce)
        setupNotificationToggles()
    }

    // ==========================================
    // Notification Settings - Local & Firebase Sync
    // ==========================================

    private fun loadLocalSettingsState() {
        val prefs = requireContext().getSharedPreferences("notif_prefs_${authManager.getUserId()}", Context.MODE_PRIVATE)
        binding.switchHampirPenuh.isChecked = prefs.getBoolean("hampirPenuh", true)
        binding.switchPenuh.isChecked = prefs.getBoolean("penuh", true)
        binding.switchSelesai.isChecked = prefs.getBoolean("selesai", true)
        binding.switchSistem.isChecked = prefs.getBoolean("sistem", true)
    }

    private fun saveLocalSettingsState(hampir: Boolean, penuh: Boolean, selesai: Boolean, sistem: Boolean) {
        val prefs = requireContext().getSharedPreferences("notif_prefs_${authManager.getUserId()}", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("hampirPenuh", hampir)
            .putBoolean("penuh", penuh)
            .putBoolean("selesai", selesai)
            .putBoolean("sistem", sistem)
            .apply()
    }

    private fun loadNotificationSettings() {
        val userId = authManager.getUserId()
        isLoadingSettings = true

        FirebaseManager.loadNotificationSettings(userId) { settings ->
            if (!isAdded) return@loadNotificationSettings
            binding.switchHampirPenuh.isChecked = settings.hampirPenuh
            binding.switchPenuh.isChecked = settings.penuh
            binding.switchSelesai.isChecked = settings.selesai
            binding.switchSistem.isChecked = settings.sistem
            
            // Sync to local
            saveLocalSettingsState(settings.hampirPenuh, settings.penuh, settings.selesai, settings.sistem)
            isLoadingSettings = false
        }
    }

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

    private fun scheduleSettingsSave() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }

        debounceRunnable = Runnable {
            val hampir = binding.switchHampirPenuh.isChecked
            val penuh = binding.switchPenuh.isChecked
            val selesai = binding.switchSelesai.isChecked
            val sistem = binding.switchSistem.isChecked

            val settings = FirebaseManager.NotificationSettings(
                hampirPenuh = hampir,
                penuh = penuh,
                selesai = selesai,
                sistem = sistem
            )

            // Save locally immediately to avoid UI state loss on restart
            saveLocalSettingsState(hampir, penuh, selesai, sistem)
            // Sync to Firebase
            FirebaseManager.saveNotificationSettings(authManager.getUserId(), settings)

            if (isAdded) {
                Toast.makeText(requireContext(), "Preferensi tersimpan", Toast.LENGTH_SHORT).show()
            }
        }

        debounceHandler.postDelayed(debounceRunnable!!, 500)
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

    override fun onDestroyView() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        super.onDestroyView()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
