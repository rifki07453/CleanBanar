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
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import android.net.Uri

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private lateinit var authManager: AuthManager

    private val cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                uploadAndSetProfilePicture(resultUri)
            }
        } else if (result.resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR && result.data != null) {
            val cropError = com.yalantis.ucrop.UCrop.getError(result.data!!)
            Toast.makeText(requireContext(), "Gagal memotong foto: ${cropError?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // Setup destinasi file hasil crop
            val destinationUri = Uri.fromFile(java.io.File(requireContext().cacheDir, "cropped_profile_${System.currentTimeMillis()}.jpg"))
            
            // Konfigurasi uCrop untuk bentuk bulat dan tema hijau
            val options = com.yalantis.ucrop.UCrop.Options()
            options.setCircleDimmedLayer(true) // Memunculkan bingkai potong bulat
            options.setShowCropGrid(false) // Hilangkan kotak-kotak grid
            options.setToolbarTitle("Atur Foto Profil")
            options.setToolbarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary))
            options.setStatusBarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary_dark))
            options.setActiveControlsWidgetColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary))
            
            // Mulai Intent uCrop
            val uCropIntent = com.yalantis.ucrop.UCrop.of(uri, destinationUri)
                .withOptions(options)
                .withAspectRatio(1f, 1f) // Wajib persegi/bulat sempurna
                .withMaxResultSize(800, 800) // Ukuran maksimal agar ringan namun tajam
                .getIntent(requireContext())
                
            cropImageLauncher.launch(uCropIntent)
        }
    }

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
        
        val phone = authManager.getUserPhone()
        if (phone.isNotEmpty()) {
            binding.tvProfilePhone.text = phone
            binding.tvProfilePhone.visibility = View.VISIBLE
        } else {
            binding.tvProfilePhone.visibility = View.GONE
        }

        // Tampilkan foto profil (jika ada)
        val initialPhotoUrl = authManager.getUserPhotoUrl()
        if (initialPhotoUrl.isNotEmpty()) {
            binding.ivUserAvatar.imageTintList = null
            binding.ivUserAvatar.setPadding(0, 0, 0, 0) // Hapus padding agar foto penuh di lingkaran
            Glide.with(this)
                .load(initialPhotoUrl)
                .transform(com.bumptech.glide.load.resource.bitmap.CircleCrop())
                .placeholder(R.drawable.ic_profile)
                .into(binding.ivUserAvatar)
        }

        // Klik pada ikon kamera untuk ganti foto
        binding.flEditPhoto.setOnClickListener {
            pickMedia.launch("image/*")
        }
        
        // Klik pada avatar untuk melihat foto besar
        binding.flAvatarContainer.setOnClickListener {
            val photoUrl = authManager.getUserPhotoUrl()
            if (photoUrl.isNotEmpty()) {
                showFullScreenProfilePicture(photoUrl)
            } else {
                // Jika belum ada foto, tawarkan untuk upload
                pickMedia.launch("image/*")
            }
        }

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
            try {
                val ctx = context ?: return@setOnClickListener
                
                // Hentikan observer dan service secara sinkron
                com.example.cleanbanar.features.dashboard.BinObserver.stop()
                com.example.cleanbanar.features.dashboard.BinObserverService.stopService(ctx)
                
                // Hapus sesi lokal dan sign out Firebase
                authManager.logout()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadAndSetProfilePicture(uri: Uri) {
        val userId = authManager.getUserId()
        if (userId.isEmpty()) return

        Toast.makeText(requireContext(), "Memproses foto...", Toast.LENGTH_SHORT).show()

        try {
            // Kita gunakan ImageDecoder dengan ALLOCATOR_SOFTWARE agar terhindar dari bug "gambar putih/blank" dari Google Photos
            var bitmap: android.graphics.Bitmap? = null
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = android.graphics.ImageDecoder.createSource(requireContext().contentResolver, uri)
                bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                bitmap = android.provider.MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            }

            if (bitmap != null) {
                // Mengubah ke byte array tanpa menurunkan resolusi (100% quality) agar gambar tajam
                val baos = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, baos)
                val data = baos.toByteArray()
                FirebaseManager.uploadProfilePictureBytes(userId, data,
                    onSuccess = { photoUrl ->
                        authManager.updatePhotoUrl(photoUrl)
                        if (isAdded) {
                            binding.ivUserAvatar.imageTintList = null
                            binding.ivUserAvatar.setPadding(0, 0, 0, 0) // Hapus padding agar foto penuh di lingkaran
                            Glide.with(this)
                                .load(photoUrl)
                                .transform(com.bumptech.glide.load.resource.bitmap.CircleCrop())
                                .placeholder(R.drawable.ic_profile)
                                .into(binding.ivUserAvatar)
                            Toast.makeText(requireContext(), "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { e ->
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Gagal mengunggah foto: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            } else {
                Toast.makeText(requireContext(), "Gagal membaca file foto", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Gagal memproses foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showFullScreenProfilePicture(url: String) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        
        val imageView = android.widget.ImageView(requireContext())
        imageView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        imageView.setBackgroundColor(android.graphics.Color.BLACK)
        
        Glide.with(this)
            .load(url)
            .into(imageView)
            
        imageView.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setContentView(imageView)
        dialog.show()
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
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etNama = EditText(requireContext()).apply {
            setText(authManager.getUserName())
            textSize = 14f
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50.dpToPx()
            ).apply { bottomMargin = 12.dpToPx() }
        }

        val labelEmail = TextView(requireContext()).apply {
            text = "Email"
            textSize = 12f
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etEmail = EditText(requireContext()).apply {
            setText(authManager.getUserEmail())
            textSize = 14f
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50.dpToPx()
            ).apply { bottomMargin = 12.dpToPx() }
        }

        val labelPhone = TextView(requireContext()).apply {
            text = "Nomor WhatsApp (Opsional)"
            textSize = 12f
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
            setPadding(0, 0, 0, 4.dpToPx())
        }
        val etPhone = EditText(requireContext()).apply {
            setText(authManager.getUserPhone())
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            background = resources.getDrawable(R.drawable.edit_text_bg, null)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50.dpToPx()
            )
        }

        layout.addView(labelNama)
        layout.addView(etNama)
        layout.addView(labelEmail)
        layout.addView(etEmail)
        layout.addView(labelPhone)
        layout.addView(etPhone)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profil")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = etNama.text.toString().trim()
                val newEmail = etEmail.text.toString().trim()

                val newPhone = etPhone.text.toString().trim()

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
                val userRole = authManager.getUserRole()
                FirebaseManager.updateUser(userId, newName, newEmail, newPhone, userRole)

                // Update sesi lokal
                authManager.updateName(newName)
                if (newEmail.isNotEmpty()) authManager.updateEmail(newEmail)
                authManager.updatePhone(newPhone)

                // Update tampilan header profil
                binding.tvProfileName.text = newName
                if (newEmail.isNotEmpty()) binding.tvProfileEmail.text = newEmail
                
                if (newPhone.isNotEmpty()) {
                    binding.tvProfilePhone.text = newPhone
                    binding.tvProfilePhone.visibility = View.VISIBLE
                } else {
                    binding.tvProfilePhone.visibility = View.GONE
                }

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
        if (userId.isEmpty()) return

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
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val sw = Switch(requireContext()).apply {
                isChecked = true
                thumbTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.green_600))
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

        // Load state dari Firebase sekali saja (bukan persistent listener) untuk menghindari leak
        FirebaseManager.getNotificationSettings(userId) { settings ->
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
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
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
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
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
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_secondary))
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
