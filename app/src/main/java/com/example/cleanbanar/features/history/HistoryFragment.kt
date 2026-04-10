package com.example.cleanbanar.features.history

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentHistoryBinding
import com.google.firebase.database.ValueEventListener

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

        // Simpangkan UI untuk role user tertentu sesuai instruksi (TopBar Staff lebih clean)
        val role = authManager.getUserRole()
        if (role == "petugas" || role == "staff") {
            binding.ivLogoHistory.visibility = View.GONE
            binding.tvOverviewTitle.visibility = View.GONE
            binding.tvSystemStatus.visibility = View.GONE
        }

        // Pull to refresh layout
        binding.swipeRefreshLayout.setOnRefreshListener {
            // Karena data sudah real-time via Firebase, swipe refresh memberikan visual feedback
            Handler(Looper.getMainLooper()).postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 1200)
        }
    }

    // ==========================================
    // Firebase Real-Time Listeners
    // ==========================================

    override fun observeData() {
        val role = authManager.getUserRole()
        val areaId = authManager.getAssignedAreaId()

        historyListener = FirebaseManager.listenHistoryFiltered(role, areaId) { historyList ->
            if (!isAdded) return@listenHistoryFiltered
            
            if (historyList.isEmpty()) {
                binding.rvHistory.visibility = View.GONE
                binding.historyEmptyState.visibility = View.VISIBLE
            } else {
                binding.rvHistory.visibility = View.VISIBLE
                binding.historyEmptyState.visibility = View.GONE
                historyAdapter.updateData(historyList)
            }
        }
    }

    override fun onDestroyView() {
        historyListener?.let { FirebaseManager.removeHistoryListener(it) }
        super.onDestroyView()
    }
}
