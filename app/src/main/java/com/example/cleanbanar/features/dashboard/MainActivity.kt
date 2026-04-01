package com.example.cleanbanar.features.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.example.cleanbanar.R
import com.example.cleanbanar.core.ui.BaseActivity
import com.example.cleanbanar.databinding.ActivityMainBinding
import com.example.cleanbanar.features.device.DeviceFragment
import com.example.cleanbanar.features.history.HistoryFragment
import com.example.cleanbanar.features.profile.ProfileFragment

class MainActivity : BaseActivity<ActivityMainBinding>() {

    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Assume user role dictates which dashboard is shown (e.g., passed via Intent)
        val role = intent.getStringExtra("USER_ROLE") ?: "Admin"
        
        val fragmentToLoad: Fragment = if (role == "Petugas") {
            PetugasDashboardFragment()
        } else {
            AdminDashboardFragment()
        }

        loadFragment(fragmentToLoad)

        // Bottom Navigation Logic
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(if (role == "Petugas") PetugasDashboardFragment() else AdminDashboardFragment())
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
