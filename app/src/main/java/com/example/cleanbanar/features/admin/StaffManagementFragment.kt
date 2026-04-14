package com.example.cleanbanar.features.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentStaffManagementBinding
import com.google.firebase.database.ValueEventListener

class StaffManagementFragment : BaseFragment<FragmentStaffManagementBinding>() {

    private var usersListener: ValueEventListener? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentStaffManagementBinding {
        return FragmentStaffManagementBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Back button
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Create account button
        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etNewName.text.toString().trim()
            val email = binding.etNewEmail.text.toString().trim()
            val password = binding.etNewPassword.text.toString().trim()

            when {
                name.isEmpty() -> toast("Harap isi Nama Lengkap")
                email.isEmpty() -> toast("Harap isi Email")
                password.length < 6 -> toast("Password minimal 6 karakter")
                else -> createStaffAccount(name, email, password)
            }
        }
    }

    override fun observeData() {
        // Read: Realtime listener from Firebase, filter role = "Petugas"
        usersListener = FirebaseManager.listenUsers { users ->
            if (!isAdded) return@listenUsers
            val staffList = users.filter {
                (it["role"] as? String)?.lowercase() == "petugas"
            }

            binding.staffListContainer.removeAllViews()

            if (staffList.isEmpty()) {
                addEmptyState()
            } else {
                for (user in staffList) {
                    addStaffCard(
                        userId = user["id"] as? String ?: "",
                        name = user["name"] as? String ?: "",
                        email = user["email"] as? String ?: ""
                    )
                }
            }
        }
    }

    // Create: Write to Firebase Realtime Database
    // Note: Ini menyimpan data akun petugas ke Realtime DB path "users".
    // Firebase Auth tidak digunakan karena project ini menggunakan
    // SharedPreferences + UserSeeder untuk autentikasi lokal.
    // Jika ingin mengintegrasikan Firebase Auth di masa depan,
    // gunakan secondary FirebaseApp instance untuk menghindari logout sesi Admin.
    private fun createStaffAccount(name: String, email: String, password: String) {
        binding.btnCreateAccount.isEnabled = false
        binding.btnCreateAccount.text = "Memproses..."

        FirebaseManager.addUser(name, email, "Petugas")

        // Clear form
        binding.etNewName.text.clear()
        binding.etNewEmail.text.clear()
        binding.etNewPassword.text.clear()

        binding.btnCreateAccount.isEnabled = true
        binding.btnCreateAccount.text = "Buat Akun Petugas"

        toast("Akun petugas $name berhasil dibuat")
    }

    // Delete: Remove from Firebase Realtime Database
    private fun showDeleteDialog(userId: String, name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Petugas")
            .setMessage("Yakin ingin menghapus akses \"$name\"?\n\nCatatan: Data dihapus dari database. Untuk mencabut akses Firebase Auth secara penuh, diperlukan Cloud Functions.")
            .setPositiveButton("Hapus") { _, _ ->
                FirebaseManager.deleteUser(userId)
                toast("$name berhasil dihapus")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun addStaffCard(userId: String, name: String, email: String) {
        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp }
            radius = 16f.dpF
            cardElevation = 1f.dpF
            strokeWidth = 1
            strokeColor = android.graphics.Color.parseColor("#F3F4F6")
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Avatar dengan inisial
        val initial = if (name.isNotEmpty()) name.first().uppercase() else "P"
        val avatarBg = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
            setBackgroundResource(R.drawable.bg_rounded_light_blue)
        }
        val avatarTv = TextView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = initial
            setTextColor(android.graphics.Color.parseColor("#2563EB"))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        avatarBg.addView(avatarTv)

        // Info teks
        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(14.dp, 0, 0, 0)
        }
        val tvName = TextView(requireContext()).apply {
            text = name
            setTextColor(android.graphics.Color.parseColor("#111827"))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvEmail = TextView(requireContext()).apply {
            text = email
            setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            textSize = 11f
        }
        info.addView(tvName)
        info.addView(tvEmail)

        // Tombol hapus (kotak merah)
        val btnDel = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(36.dp, 36.dp)
            setBackgroundResource(R.drawable.bg_rounded_light_red)
            isClickable = true
            isFocusable = true
            setOnClickListener { showDeleteDialog(userId, name) }
        }
        val btnDelIcon = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(android.graphics.Color.parseColor("#EF4444"))
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        }
        btnDel.addView(btnDelIcon)

        row.addView(avatarBg)
        row.addView(info)
        row.addView(btnDel)
        cardView.addView(row)
        binding.staffListContainer.addView(cardView)
    }

    private fun addEmptyState() {
        val tv = TextView(requireContext()).apply {
            text = "Belum ada akun petugas terdaftar"
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 40.dp, 0, 24.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        binding.staffListContainer.addView(tv)
    }

    override fun onDestroyView() {
        usersListener?.let { FirebaseManager.removeUsersListener(it) }
        super.onDestroyView()
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dpF: Float get() = this * resources.displayMetrics.density
}
