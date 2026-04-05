package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentPetugasDashboardBinding
import com.google.firebase.database.ValueEventListener

class PetugasDashboardFragment : BaseFragment<FragmentPetugasDashboardBinding>() {

    private var organikListener: ValueEventListener? = null
    private var nonOrganikListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPetugasDashboardBinding {
        return FragmentPetugasDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        binding.btnEmptyOrganik.setOnClickListener {
            FirebaseManager.updateBinStatus("organik", 0, "TERSEDIA")
            val authManager = AuthManager(requireContext())
            FirebaseManager.addHistoryEntry("emptied", "organik", authManager.getUserName())
            Toast.makeText(requireContext(), "Sampah Organik telah dikosongkan", Toast.LENGTH_SHORT).show()
        }

        binding.btnEmptyNonOrganik.setOnClickListener {
            FirebaseManager.updateBinStatus("nonOrganik", 0, "TERSEDIA")
            val authManager = AuthManager(requireContext())
            FirebaseManager.addHistoryEntry("emptied", "nonOrganik", authManager.getUserName())
            Toast.makeText(requireContext(), "Sampah Non-Organik telah dikosongkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun observeData() {
        // Listen to Organik bin
        organikListener = FirebaseManager.listenBinStatus("organik") { percentage, _, lastUpdate ->
            if (!isAdded) return@listenBinStatus
            updateOrganikUI(percentage)
            binding.tvOrganikUpdate.text = formatLastUpdate(lastUpdate)
        }

        // Listen to Non-Organik bin
        nonOrganikListener = FirebaseManager.listenBinStatus("nonOrganik") { percentage, _, lastUpdate ->
            if (!isAdded) return@listenBinStatus
            updateNonOrganikUI(percentage)
            binding.tvNonOrganikUpdate.text = formatLastUpdate(lastUpdate)
        }
    }

    private fun updateOrganikUI(percent: Int) {
        binding.tvOrganikPercent.text = "$percent%"
        binding.progressOrganik.progress = percent
        when {
            percent < 50 -> {
                binding.tvOrganikStatus.text = "Dalam kondisi baik"
                binding.tvOrganikStatus.setTextColor(resources.getColor(R.color.gray_400, null))
                binding.tvOrganikBadge.text = "TERSEDIA"
                binding.tvOrganikBadge.setTextColor(resources.getColor(R.color.green_600, null))
                binding.tvOrganikBadge.setBackgroundResource(R.drawable.badge_green_bg)
                binding.progressOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_green, null)
                binding.progressOrganik.progress = percent
                binding.tvStatus.text = "Normal"
            }
            percent < 80 -> {
                binding.tvOrganikStatus.text = "Perlu dipantau"
                binding.tvOrganikStatus.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvOrganikBadge.text = "PERHATIAN"
                binding.tvOrganikBadge.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvOrganikBadge.setBackgroundResource(R.drawable.badge_amber_bg)
                binding.progressOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_amber, null)
                binding.progressOrganik.progress = percent
                binding.tvStatus.text = "Perhatian"
            }
            else -> {
                binding.tvOrganikStatus.text = "Segera dikosongkan"
                binding.tvOrganikStatus.setTextColor(resources.getColor(R.color.red_500, null))
                binding.tvOrganikBadge.text = "PENUH"
                binding.tvOrganikBadge.setTextColor(resources.getColor(R.color.red_500, null))
                binding.tvOrganikBadge.setBackgroundResource(R.drawable.badge_red_bg)
                binding.progressOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_red, null)
                binding.progressOrganik.progress = percent
                binding.tvStatus.text = "Perlu Tindakan"
            }
        }
    }

    private fun updateNonOrganikUI(percent: Int) {
        binding.tvNonOrganikPercent.text = "$percent%"
        binding.progressNonOrganik.progress = percent
        when {
            percent < 50 -> {
                binding.tvNonOrganikStatus.text = "Dalam kondisi baik"
                binding.tvNonOrganikStatus.setTextColor(resources.getColor(R.color.gray_400, null))
                binding.tvNonOrganikBadge.text = "TERSEDIA"
                binding.tvNonOrganikBadge.setTextColor(resources.getColor(R.color.green_600, null))
                binding.tvNonOrganikBadge.setBackgroundResource(R.drawable.badge_green_bg)
                binding.progressNonOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_green, null)
                binding.progressNonOrganik.progress = percent
            }
            percent < 80 -> {
                binding.tvNonOrganikStatus.text = "Perlu dipantau"
                binding.tvNonOrganikStatus.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvNonOrganikBadge.text = "PERHATIAN"
                binding.tvNonOrganikBadge.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.tvNonOrganikBadge.setBackgroundResource(R.drawable.badge_amber_bg)
                binding.progressNonOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_amber, null)
                binding.progressNonOrganik.progress = percent
            }
            else -> {
                binding.tvNonOrganikStatus.text = "Segera dikosongkan"
                binding.tvNonOrganikStatus.setTextColor(resources.getColor(R.color.red_500, null))
                binding.tvNonOrganikBadge.text = "PENUH"
                binding.tvNonOrganikBadge.setTextColor(resources.getColor(R.color.red_500, null))
                binding.tvNonOrganikBadge.setBackgroundResource(R.drawable.badge_red_bg)
                binding.progressNonOrganik.progressDrawable = resources.getDrawable(R.drawable.progress_bar_red, null)
                binding.progressNonOrganik.progress = percent
            }
        }
    }

    private fun formatLastUpdate(timestamp: Long): String {
        if (timestamp == 0L) return "Menunggu data..."
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "Update $minutes menit lalu"
            else -> "Update ${minutes / 60} jam lalu"
        }
    }

    override fun onDestroyView() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        super.onDestroyView()
    }
}
