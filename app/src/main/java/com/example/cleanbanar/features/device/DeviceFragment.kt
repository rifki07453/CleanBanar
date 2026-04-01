package com.taupik.myapp.features.device

import android.view.LayoutInflater
import android.view.ViewGroup
import com.taupik.myapp.core.ui.BaseFragment
import com.taupik.myapp.databinding.FragmentDeviceBinding

class DeviceFragment : BaseFragment<FragmentDeviceBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentDeviceBinding {
        return FragmentDeviceBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Init device map views logic here
    }
}
