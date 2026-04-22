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

        // Header view is now uniform logic based on new design specs

        // Pull to refresh layout
        binding.swipeRefreshLayout.setOnRefreshListener {
            Handler(Looper.getMainLooper()).postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 1200)
        }
    }

    // ==========================================
    // Firebase Real-Time Listeners
    // ==========================================

    override fun observeData() {
        val cal = java.util.Calendar.getInstance()
        
        // Item 1: Hari ini, 10:45 AM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 10)
        cal.set(java.util.Calendar.MINUTE, 45)
        val time1 = cal.timeInMillis

        // Item 2: Hari ini, 09:12 AM
        cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
        cal.set(java.util.Calendar.MINUTE, 12)
        val time2 = cal.timeInMillis

        // Item 3: Kemarin, 15:30 PM
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 15)
        cal.set(java.util.Calendar.MINUTE, 30)
        val time3 = cal.timeInMillis

        val dummyHistory = listOf(
            mapOf(
                "type" to "dikosongkan",
                "bin_type" to "Organik",
                "petugas" to "Ahmad B.",
                "capacity" to 0,
                "timestamp" to time1
            ),
            mapOf(
                "type" to "penuh",
                "bin_type" to "Organik",
                "timestamp" to time2
            ),
            mapOf(
                "type" to "dikosongkan_blue",
                "bin_type" to "Non-Organik",
                "petugas" to "Sutejo",
                "capacity" to 0,
                "timestamp" to time3
            )
        )

        binding.rvHistory.visibility = android.view.View.VISIBLE
        binding.historyEmptyState.visibility = android.view.View.GONE
        historyAdapter.updateData(dummyHistory)
    }

    override fun onDestroyView() {
        historyListener?.let { FirebaseManager.removeHistoryListener(it) }
        super.onDestroyView()
    }
}
