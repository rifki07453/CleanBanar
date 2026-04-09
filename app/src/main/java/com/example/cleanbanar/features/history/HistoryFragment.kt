package com.example.cleanbanar.features.history

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.cleanbanar.R
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
    private var historyListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHistoryBinding {
        return FragmentHistoryBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
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
        historyListener = FirebaseManager.listenHistory { historyList ->
            if (!isAdded) return@listenHistory
            historyAdapter.updateData(historyList)
        }
    }

    override fun onDestroyView() {
        historyListener?.let { FirebaseManager.removeHistoryListener(it) }
        super.onDestroyView()
    }
}
