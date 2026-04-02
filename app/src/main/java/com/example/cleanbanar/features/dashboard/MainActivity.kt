package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.ui.BaseActivity
import com.example.cleanbanar.databinding.ActivityMainBinding
import com.example.cleanbanar.features.device.DeviceFragment
import com.example.cleanbanar.features.history.HistoryFragment
import com.example.cleanbanar.features.profile.ProfileFragment

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private lateinit var authManager: AuthManager
    private var userRole: String = "Admin"

    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        authManager = AuthManager(this)

        // Get role from intent first, fallback to session
        userRole = intent.getStringExtra("USER_ROLE") ?: authManager.getUserRole()

        val fragmentToLoad: Fragment = if (userRole == "Petugas") {
            PetugasDashboardFragment()
        } else {
            AdminDashboardFragment()
        }

        loadFragment(fragmentToLoad)

        // Bottom Navigation Logic
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(if (userRole == "Petugas") PetugasDashboardFragment() else AdminDashboardFragment())
                    true
                }
                R.id.nav_device -> {
                    loadFragment(DeviceFragment())
                    true
                }
                R.id.nav_history -> {
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
