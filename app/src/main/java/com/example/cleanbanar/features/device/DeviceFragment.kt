package com.example.cleanbanar.features.device

import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentDeviceBinding

class DeviceFragment : BaseFragment<FragmentDeviceBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentDeviceBinding {
        return FragmentDeviceBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Init device map views logic here
    }
}
