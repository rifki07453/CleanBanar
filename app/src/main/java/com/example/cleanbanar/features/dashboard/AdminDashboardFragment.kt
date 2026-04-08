package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentAdminDashboardBinding
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardFragment : BaseFragment<FragmentAdminDashboardBinding>() {

    private lateinit var authManager: AuthManager
    private var organikListener: ValueEventListener? = null
    private var nonOrganikListener: ValueEventListener? = null
    private var deviceListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAdminDashboardBinding {
        return FragmentAdminDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())
        binding.tvAdminTitle.text = "Halo, ${authManager.getUserName()}"
    }

    override fun observeData() {
        // Listen to Organik bin
        organikListener = FirebaseManager.listenBinStatus("organik") { percentage, status, lastUpdate, _ ->
            if (!isAdded) return@listenBinStatus
            binding.tvOrganikPercent.text = "$percentage%"
            binding.progressOrganikAdmin.progress = percentage
            binding.tvOrganikUpdate.text = formatLastUpdate(lastUpdate)
            updateBinBadge(binding.tvOrganikBadge, percentage)
            updateProgressDrawable(binding.progressOrganikAdmin, percentage)
            recalculateSummary()
        }

        // Listen to Non-Organik bin
        nonOrganikListener = FirebaseManager.listenBinStatus("nonOrganik") { percentage, status, lastUpdate, _ ->
            if (!isAdded) return@listenBinStatus
            binding.tvNonOrganikPercent.text = "$percentage%"
            binding.progressNonOrganikAdmin.progress = percentage
            binding.tvNonOrganikUpdate.text = formatLastUpdate(lastUpdate)
            updateBinBadge(binding.tvNonOrganikBadge, percentage)
            updateProgressDrawable(binding.progressNonOrganikAdmin, percentage)
            recalculateSummary()
        }

        // Listen to device status
        deviceListener = FirebaseManager.listenDeviceStatus { connectionStatus, lastSeen ->
            if (!isAdded) return@listenDeviceStatus
            val isOnline = connectionStatus == "ONLINE"
            binding.tvDeviceStatus.text = connectionStatus
            binding.tvDeviceLastSeen.text = if (isOnline) "Terhubung" else "Terputus"
            binding.tvLastUpdate.text = formatTime(lastSeen)

            if (isOnline) {
                binding.tvDeviceStatus.setTextColor(resources.getColor(R.color.green_600, null))
                binding.tvDeviceStatus.setBackgroundResource(R.drawable.badge_green_bg)
                binding.tvSystemStatus.text = "Semua sistem berjalan normal"
                binding.tvSystemStatus.setTextColor(resources.getColor(R.color.green_600, null))
            } else {
                binding.tvDeviceStatus.setTextColor(resources.getColor(R.color.red_500, null))
                binding.tvDeviceStatus.setBackgroundResource(R.drawable.badge_red_bg)
                binding.tvSystemStatus.text = "Perangkat terputus!"
                binding.tvSystemStatus.setTextColor(resources.getColor(R.color.red_500, null))
            }

            // Update online count
            binding.tvOnlineBins.text = if (isOnline) "2" else "0"
        }
    }

    private fun recalculateSummary() {
        val orgPercent = binding.progressOrganikAdmin.progress
        val nonOrgPercent = binding.progressNonOrganikAdmin.progress
        var attention = 0
        if (orgPercent >= 75) attention++
        if (nonOrgPercent >= 75) attention++
        binding.tvAttentionBins.text = attention.toString()
    }

    private fun updateBinBadge(badge: android.widget.TextView, percentage: Int) {
        when {
            percentage < 50 -> {
                badge.text = "AMAN"
                badge.setTextColor(resources.getColor(R.color.green_600, null))
                badge.setBackgroundResource(R.drawable.badge_green_bg)
            }
            percentage < 80 -> {
                badge.text = "PERHATIAN"
                badge.setTextColor(resources.getColor(R.color.amber_600, null))
                badge.setBackgroundResource(R.drawable.badge_amber_bg)
            }
            else -> {
                badge.text = "PENUH"
                badge.setTextColor(resources.getColor(R.color.red_500, null))
                badge.setBackgroundResource(R.drawable.badge_red_bg)
            }
        }
    }

    private fun updateProgressDrawable(progressBar: android.widget.ProgressBar, percentage: Int) {
        val drawableRes = when {
            percentage < 50 -> R.drawable.progress_bar_green
            percentage < 80 -> R.drawable.progress_bar_amber
            else -> R.drawable.progress_bar_red
        }
        progressBar.progressDrawable = resources.getDrawable(drawableRes, null)
        progressBar.progress = percentage
    }

    private fun formatLastUpdate(timestamp: Long): String {
        if (timestamp == 0L) return "Menunggu data..."
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "$minutes menit lalu"
            else -> "${minutes / 60} jam lalu"
        }
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "—"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        deviceListener?.let { FirebaseManager.removeDeviceListener(it) }
        super.onDestroyView()
    }
}
