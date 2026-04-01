package com.taupik.myapp.features.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import com.taupik.myapp.core.ui.BaseFragment
import com.taupik.myapp.databinding.FragmentProfileBinding

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentProfileBinding {
        return FragmentProfileBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Init profile views logic here
    }
}
