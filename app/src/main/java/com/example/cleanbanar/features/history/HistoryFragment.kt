package com.taupik.myapp.features.history

import android.view.LayoutInflater
import android.view.ViewGroup
import com.taupik.myapp.core.ui.BaseFragment
import com.taupik.myapp.databinding.FragmentHistoryBinding

class HistoryFragment : BaseFragment<FragmentHistoryBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHistoryBinding {
        return FragmentHistoryBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Init history list views logic here
    }
}
