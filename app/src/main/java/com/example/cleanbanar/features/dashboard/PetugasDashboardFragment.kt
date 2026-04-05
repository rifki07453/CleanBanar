package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.example.cleanbanar.R
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentPetugasDashboardBinding

class PetugasDashboardFragment : BaseFragment<FragmentPetugasDashboardBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPetugasDashboardBinding {
        return FragmentPetugasDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Set initial status messages based on fill levels
        updateOrganikStatus(45)
        updateNonOrganikStatus(85)

        binding.btnEmptyOrganik.setOnClickListener {
            binding.progressOrganik.progress = 0
            updateOrganikStatus(0)
            Toast.makeText(requireContext(), "Sampah Organik telah dikosongkan", Toast.LENGTH_SHORT).show()
        }

        binding.btnEmptyNonOrganik.setOnClickListener {
            binding.progressNonOrganik.progress = 0
            updateNonOrganikStatus(0)
            Toast.makeText(requireContext(), "Sampah Non-Organik telah dikosongkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateOrganikStatus(percent: Int) {
        binding.tvOrganikPercent.text = "$percent%"
        when {
            percent < 50 -> {
                binding.tvOrganikStatus.text = "Dalam kondisi baik"
                binding.tvOrganikStatus.setTextColor(resources.getColor(R.color.gray_400, null))
                binding.tvOrganikBadge.text = "TERSEDIA"
                binding.tvOrganikBadge.setTextColor(resources.getColor(R.color.green_600, null))
                binding.tvOrganikBadge.setBackgroundResource(R.drawable.badge_green_bg)
                binding.progressOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_green, null)
            }
            percent < 80 -> {
                binding.tvOrganikStatus.text = "Perlu dipantau"
                binding.tvOrganikStatus.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvOrganikBadge.text = "PERHATIAN"
                binding.tvOrganikBadge.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvOrganikBadge.setBackgroundResource(R.drawable.badge_amber_bg)
                binding.progressOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_amber, null)
            }
            else -> {
                binding.tvOrganikStatus.text = "Segera dikosongkan"
                binding.tvOrganikStatus.setTextColor(resources.getColor(R.color.red_500, null))
                binding.tvOrganikBadge.text = "PENUH"
                binding.tvOrganikBadge.setTextColor(resources.getColor(R.color.red_500, null))
                binding.tvOrganikBadge.setBackgroundResource(R.drawable.badge_red_bg)
                binding.progressOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_red, null)
            }
        }
    }

    private fun updateNonOrganikStatus(percent: Int) {
        binding.tvNonOrganikPercent.text = "$percent%"
        when {
            percent < 50 -> {
                binding.tvNonOrganikStatus.text = "Dalam kondisi baik"
                binding.tvNonOrganikStatus.setTextColor(resources.getColor(R.color.gray_400, null))
                binding.tvNonOrganikBadge.text = "TERSEDIA"
                binding.tvNonOrganikBadge.setTextColor(resources.getColor(R.color.green_600, null))
                binding.tvNonOrganikBadge.setBackgroundResource(R.drawable.badge_green_bg)
                binding.progressNonOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_green, null)
            }
            percent < 80 -> {
                binding.tvNonOrganikStatus.text = "Perlu dipantau"
                binding.tvNonOrganikStatus.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvNonOrganikBadge.text = "PERHATIAN"
                binding.tvNonOrganikBadge.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvNonOrganikBadge.setBackgroundResource(R.drawable.badge_amber_bg)
                binding.progressNonOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_amber, null)
            }
            else -> {
                binding.tvNonOrganikStatus.text = "Segera dikosongkan"
                binding.tvNonOrganikStatus.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvNonOrganikBadge.text = "HAMPIR PENUH"
                binding.tvNonOrganikBadge.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvNonOrganikBadge.setBackgroundResource(R.drawable.badge_amber_bg)
                binding.progressNonOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_amber, null)
            }
        }
    }
}
