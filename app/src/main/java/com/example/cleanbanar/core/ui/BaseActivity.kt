package com.taupik.myapp.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {
    private var _binding: VB? = null
    protected val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        setupViews()
        observeData()
    }

    abstract fun inflateBinding(layoutInflater: LayoutInflater): VB
    abstract fun setupViews()
    open fun observeData() {}

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
