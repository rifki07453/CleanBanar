package com.taupik.myapp.features.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.taupik.myapp.core.ui.BaseFragment
import com.taupik.myapp.databinding.FragmentPetugasDashboardBinding

class PetugasDashboardFragment : BaseFragment<FragmentPetugasDashboardBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPetugasDashboardBinding {
        return FragmentPetugasDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        binding.btnEmptyOrganik.setOnClickListener {
            binding.progressOrganik.progress = 0
            Toast.makeText(requireContext(), "Sampah Organik telah dikosongkan. Menunggu update server...", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnEmptyNonOrganik.setOnClickListener {
            binding.progressNonOrganik.progress = 0
            Toast.makeText(requireContext(), "Sampah Non-Organik telah dikosongkan. Menunggu update server...", Toast.LENGTH_SHORT).show()
        }
    }
}
