package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.ui.BaseActivity
import com.example.cleanbanar.databinding.ActivityMainBinding
import com.example.cleanbanar.features.admin.StaffManagementFragment
import com.example.cleanbanar.features.history.HistoryFragment
import com.example.cleanbanar.features.notifications.NotificationFragment
import com.example.cleanbanar.features.profile.ProfileFragment
import com.example.cleanbanar.features.statistics.StatisticsFragment

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private lateinit var authManager: AuthManager
    private var userRole: String = "Admin"

    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        authManager = AuthManager(this)
        userRole = intent.getStringExtra("USER_ROLE") ?: authManager.getUserRole()

        // Load role-specific bottom navigation menu
        if (userRole == "Petugas") {
            setupStaffNavigation()
        } else {
            setupAdminNavigation()
        }
    }

    private fun setupAdminNavigation() {
        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_admin)

        // Default fragment
        loadFragment(AdminDashboardFragment())

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(AdminDashboardFragment())
                    true
                }
                R.id.nav_staff -> {
                    loadFragment(StaffManagementFragment())
                    true
                }
                R.id.nav_statistics -> {
                    loadFragment(StatisticsFragment())
                    true
                }
                R.id.nav_notification -> {
                    loadFragment(NotificationFragment())
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

    private fun setupStaffNavigation() {
        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_staff)

        // Default fragment
        loadFragment(PetugasDashboardFragment())

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(PetugasDashboardFragment())
                    true
                }
                R.id.nav_history -> {
                    loadFragment(HistoryFragment())
                    true
                }
                R.id.nav_notification -> {
                    loadFragment(NotificationFragment())
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
