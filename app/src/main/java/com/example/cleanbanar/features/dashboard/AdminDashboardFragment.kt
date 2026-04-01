package com.example.cleanbanar.features.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentAdminDashboardBinding

class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAdminDashboardBinding {
        return FragmentAdminDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        binding.cardManageUser.setOnClickListener {
            Toast.makeText(requireContext(), "Membuka halaman Manajemen Petugas", Toast.LENGTH_SHORT).show()
        }
        
        binding.cardStats.setOnClickListener {
            Toast.makeText(requireContext(), "Membuka halaman Laporan Analitik", Toast.LENGTH_SHORT).show()
        }
    }
}
