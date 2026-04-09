package com.example.cleanbanar.features.history

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentHistoryBinding
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
                binding.rvHistory.visibility = android.view.View.GONE
                binding.historyEmptyState.visibility = android.view.View.VISIBLE
            } else {
                binding.rvHistory.visibility = android.view.View.VISIBLE
                binding.historyEmptyState.visibility = android.view.View.GONE
                historyAdapter.updateData(historyList)
            }
        }
    }

    override fun onDestroyView() {
        historyListener?.let { FirebaseManager.removeHistoryListener(it) }
        super.onDestroyView()
    }
}
