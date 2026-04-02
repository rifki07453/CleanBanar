package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentAdminDashboardBinding

class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {

    private lateinit var authManager: AuthManager

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAdminDashboardBinding {
        return FragmentAdminDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())

        // Show logged-in user name in the header
        binding.tvAdminTitle.text = "Halo, ${authManager.getUserName()}"

        binding.cardManageUser.setOnClickListener {
            Toast.makeText(requireContext(), "Membuka halaman Manajemen Petugas", Toast.LENGTH_SHORT).show()
        }

        binding.cardStats.setOnClickListener {
            Toast.makeText(requireContext(), "Membuka halaman Laporan Analitik", Toast.LENGTH_SHORT).show()
        }
    }
}
