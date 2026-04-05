package com.example.cleanbanar.features.admin

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentStaffManagementBinding {
        return FragmentStaffManagementBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        binding.btnAddStaff.setOnClickListener {
            showAddEditDialog(null, "", "")
        }
    }

    override fun observeData() {
        usersListener = FirebaseManager.listenUsers { users ->
            if (!isAdded) return@listenUsers
            val staffList = users.filter { it["role"] == "Petugas" }
            binding.tvStaffCount.text = "${staffList.size} petugas terdaftar"
            binding.staffListContainer.removeAllViews()

            for (user in staffList) {
                addStaffCard(
                    user["id"] as String,
                    user["name"] as String,
                    user["email"] as String
                )
            }

            if (staffList.isEmpty()) {
                addEmptyState()
            }
        }
    }

    private fun addStaffCard(userId: String, name: String, email: String) {
        val card = LayoutInflater.from(requireContext()).inflate(
            android.R.layout.simple_list_item_2, null
        )

        // Build card programmatically for full control
        val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dpToPx() }
            radius = 16f.dpToPxF()
            cardElevation = 2f.dpToPxF()
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Avatar
        val avatar = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(44.dpToPx(), 44.dpToPx())
            setImageResource(android.R.drawable.ic_menu_myplaces)
            setBackgroundResource(R.drawable.leaf_bg)
            setColorFilter(resources.getColor(R.color.emerald_600, null))
            setPadding(10.dpToPx(), 10.dpToPx(), 10.dpToPx(), 10.dpToPx())
        }

        // Info
        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(14.dpToPx(), 0, 0, 0)
        }
        val tvName = TextView(requireContext()).apply {
            text = name
            setTextColor(resources.getColor(R.color.gray_800, null))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvEmail = TextView(requireContext()).apply {
            text = email
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 11f
        }
        info.addView(tvName)
        info.addView(tvEmail)

        // Edit button
        val btnEdit = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(36.dpToPx(), 36.dpToPx()).apply {
                marginEnd = 8.dpToPx()
            }
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(resources.getColor(R.color.secondary, null))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            setOnClickListener { showAddEditDialog(userId, name, email) }
        }

        // Delete button
        val btnDelete = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(36.dpToPx(), 36.dpToPx())
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(resources.getColor(R.color.red_500, null))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            setOnClickListener { showDeleteDialog(userId, name) }
        }

        row.addView(avatar)
        row.addView(info)
        row.addView(btnEdit)
        row.addView(btnDelete)
        cardView.addView(row)
        binding.staffListContainer.addView(cardView)
    }

    private fun addEmptyState() {
        val tv = TextView(requireContext()).apply {
            text = "Belum ada petugas terdaftar"
            setTextColor(resources.getColor(R.color.gray_400, null))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 48.dpToPx(), 0, 0)
        }
        binding.staffListContainer.addView(tv)
    }

    private fun showAddEditDialog(userId: String?, currentName: String, currentEmail: String) {
        val isEdit = userId != null
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 0)
        }
        val etName = EditText(requireContext()).apply {
            hint = "Nama petugas"
            setText(currentName)
        }
        val etEmail = EditText(requireContext()).apply {
            hint = "Email petugas"
            setText(currentEmail)
        }
        layout.addView(etName)
        layout.addView(etEmail)

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) "Edit Petugas" else "Tambah Petugas")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                if (name.isEmpty() || email.isEmpty()) {
                    Toast.makeText(requireContext(), "Nama dan email wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isEdit) {
                    FirebaseManager.updateUser(userId!!, name, email)
                    Toast.makeText(requireContext(), "Petugas berhasil diupdate", Toast.LENGTH_SHORT).show()
                } else {
                    FirebaseManager.addUser(name, email, "Petugas")
                    Toast.makeText(requireContext(), "Petugas berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDeleteDialog(userId: String, name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Petugas")
            .setMessage("Yakin ingin menghapus $name?")
            .setPositiveButton("Hapus") { _, _ ->
                FirebaseManager.deleteUser(userId)
                Toast.makeText(requireContext(), "$name berhasil dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        usersListener?.let { FirebaseManager.removeUsersListener(it) }
        super.onDestroyView()
    }

    // Extension helpers
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density
}
