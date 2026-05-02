package com.example.cleanbanar.features.history

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentHistoryBinding
import com.google.firebase.database.ValueEventListener

/**
 * Fragment untuk menampilkan riwayat aktivitas sistem (pengosongan sampah, peringatan penuh).
 */
class HistoryFragment : BaseFragment<FragmentHistoryBinding>() {

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var authManager: AuthManager
    private var historyListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHistoryBinding {
        return FragmentHistoryBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())
        historyAdapter = HistoryAdapter()
        binding.rvHistory.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            Handler(Looper.getMainLooper()).postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 1200)
        }
    }

    override fun observeData() {
        // Mendengarkan data riwayat dari Firebase secara real-time
        historyListener = FirebaseManager.listenHistory { historyData ->
            if (isAdded) {
                if (historyData.isEmpty()) {
                    binding.rvHistory.visibility = android.view.View.GONE
                    binding.historyEmptyState.visibility = android.view.View.VISIBLE
                } else {
                    binding.rvHistory.visibility = android.view.View.VISIBLE
                    binding.historyEmptyState.visibility = android.view.View.GONE
                    
                    // Konversi data Firebase ke format yang dikenali adapter
                    val formattedHistory = historyData.map { item ->
                        val aksi = item["aksi"] as String
                        val tipeSampah = item["tipeSampah"] as String
                        val binLabel = if (tipeSampah == "organik") "Organik" else "Non-Organik"
                        
                        mapOf(
                            "type" to if (aksi == "pengosongan") (if (tipeSampah == "organik") "dikosongkan" else "dikosongkan_blue") else "penuh",
                            "bin_type" to binLabel,
                            "petugas" to (item["namaLengkap"] ?: ""),
                            "capacity" to 0,
                            "timestamp" to (item["waktu"] ?: 0L)
                        )
                    }
                    historyAdapter.updateData(formattedHistory)
                }
            }
        }
    }

    override fun onDestroyView() {
        historyListener?.let { FirebaseManager.removeHistoryListener(it) }
        super.onDestroyView()
    }
}
