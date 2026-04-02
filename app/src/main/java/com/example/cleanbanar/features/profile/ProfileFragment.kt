package com.example.cleanbanar.features.profile

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentProfileBinding
import com.example.cleanbanar.features.auth.LoginActivity

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private lateinit var authManager: AuthManager

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentProfileBinding {
        return FragmentProfileBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())

        // Display user info from session
        binding.tvProfileName.text = authManager.getUserName()
        binding.tvProfileEmail.text = authManager.getUserEmail()
        binding.tvProfileRole.text = authManager.getUserRole()

        // Logout button
        binding.btnLogout.setOnClickListener {
            authManager.logout()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}
