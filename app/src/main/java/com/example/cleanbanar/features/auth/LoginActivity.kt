package com.taupik.myapp.features.auth

import android.os.Bundle
import android.view.LayoutInflater
import com.taupik.myapp.core.ui.BaseActivity
import com.taupik.myapp.databinding.ActivityLoginBinding

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.btnLogin.setOnClickListener {
            // Handle Login Action
        }
        
        binding.tvRegister.setOnClickListener {
            // Navigate to Register
        }
    }
}
