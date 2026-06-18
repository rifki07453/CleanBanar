package com.example.cleanbanar.features.admin

import android.app.AlertDialog
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
import com.bumptech.glide.Glide

/**
 * Fragment untuk manajemen petugas lapangan oleh Admin.
 */
class StaffManagementFragment : BaseFragment<FragmentStaffManagementBinding>() {

    private var usersListener: ValueEventListener? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentStaffManagementBinding {
        return FragmentStaffManagementBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etNewName.text.toString().trim()
            val email = binding.etNewEmail.text.toString().trim()
            val phone = binding.etNewPhone.text.toString().trim()
            val password = binding.etNewPassword.text.toString().trim()

            when {
                name.isEmpty() -> toast("Harap isi Nama Lengkap")
                email.isEmpty() -> toast("Harap isi Email")
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> toast("Format email tidak valid")
                password.length < 6 -> toast("Password minimal 6 karakter")
                else -> createStaffAccount(name, email, password, phone)
            }
        }
    }

    override fun observeData() {
        usersListener = FirebaseManager.listenUsers { users ->
            binding.staffListContainer.removeAllViews()
            // Hanya tampilkan akun dengan peran "Petugas", kecualikan Admin/Administrator
            val petugasList = users.filter { user ->
                val peran = (user["peran"] as? String ?: "").trim()
                peran.equals("Petugas", ignoreCase = true)
            }
            if (petugasList.isEmpty()) {
                binding.emptyState.visibility = android.view.View.VISIBLE
            } else {
                binding.emptyState.visibility = android.view.View.GONE
                for (user in petugasList) {
                    addStaffCard(
                        userId = user["id"] as? String ?: "",
                        name = user["nama"] as? String ?: "",
                        email = user["email"] as? String ?: "",
                        photoUrl = user["photoUrl"] as? String ?: ""
                    )
                }
            }
        }
    }

    private fun createStaffAccount(name: String, email: String, password: String, phone: String) {
        binding.btnCreateAccount.isEnabled = false
        binding.btnCreateAccount.text = "Memproses..."

        // Menggunakan nama parameter baru (nama, peran)
        FirebaseManager.addUser(
            nama = name,
            email = email,
            peran = "Petugas",
            nomorHp = phone,
            onSuccess = {
                binding.etNewName.text.clear()
                binding.etNewEmail.text.clear()
                binding.etNewPhone.text.clear()
                binding.etNewPassword.text?.clear()
                binding.btnCreateAccount.isEnabled = true
                binding.btnCreateAccount.text = "Buat Akun Petugas"
                toast("✓ Akun petugas $name berhasil ditambahkan")
            },
            onFailure = { errorMsg ->
                binding.btnCreateAccount.isEnabled = true
                binding.btnCreateAccount.text = "Buat Akun Petugas"
                toast("Gagal: $errorMsg")
            }
        )
    }

    private fun showDeleteDialog(userId: String, name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Petugas")
            .setMessage("Yakin ingin menghapus akses \"$name\"?\n\nCatatan: Data akan dihapus secara permanen.")
            .setPositiveButton("Hapus") { _, _ ->
                FirebaseManager.deleteUser(userId)
                toast("$name berhasil dihapus")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun addStaffCard(userId: String, name: String, email: String, photoUrl: String = "") {
        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
            radius = 20f.dpF
            cardElevation = 0f.dpF
            strokeWidth = 1.dp
            strokeColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.bg_main)
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
            gravity = Gravity.CENTER_VERTICAL
        }

        val initial = if (name.isNotEmpty()) name.first().uppercase() else "P"
        val avatarBg = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
            background = getCircleDrawable(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.blue_50))
        }
        val avatarTv = TextView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = initial
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.blue_600))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val avatarImg = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        avatarBg.addView(avatarTv)
        avatarBg.addView(avatarImg)

        if (photoUrl.isNotEmpty()) {
            avatarTv.visibility = android.view.View.GONE
            Glide.with(requireContext())
                .load(photoUrl)
                .circleCrop()
                .into(avatarImg)
        } else {
            avatarTv.visibility = android.view.View.VISIBLE
            Glide.with(requireContext()).clear(avatarImg)
        }

        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16.dp, 0, 0, 0)
        }
        val tvName = TextView(requireContext()).apply {
            text = name
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_primary))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvEmail = TextView(requireContext()).apply {
            text = email
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.text_tertiary))
            textSize = 12f
            setPadding(0, 4.dp, 0, 0)
        }
        info.addView(tvName)
        info.addView(tvEmail)

        val deleteIcon = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_VERTICAL }
            setImageResource(android.R.drawable.ic_menu_delete)
            imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.cleanbanar.R.color.red_500))
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            background = getRoundedRectDrawable(android.graphics.Color.TRANSPARENT, 8f.dpF)
            setOnClickListener { showDeleteDialog(userId, name) }
        }

        row.addView(avatarBg)
        row.addView(info)
        row.addView(deleteIcon)
        cardView.addView(row)
        binding.staffListContainer.addView(cardView)
    }

    private fun getCircleDrawable(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun getRoundedRectDrawable(color: Int, radius: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun addEmptyState() {
        // Obsolete: using XML empty state instead
    }

    override fun onDestroyView() {
        usersListener?.let { FirebaseManager.removeUsersListener(it) }
        super.onDestroyView()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dpF: Float get() = this * resources.displayMetrics.density
}
