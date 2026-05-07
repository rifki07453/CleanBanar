package com.example.cleanbanar.features.dashboard

import android.content.Intent
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.ui.BaseActivity
import com.example.cleanbanar.databinding.ActivityMainBinding
import com.example.cleanbanar.features.admin.StaffManagementFragment
import com.example.cleanbanar.features.auth.LoginActivity
import com.example.cleanbanar.features.history.HistoryFragment
import com.example.cleanbanar.features.notifications.NotificationFragment
import com.example.cleanbanar.features.profile.ProfileFragment
import com.example.cleanbanar.features.statistics.StatisticsFragment
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private lateinit var authManager: AuthManager
    private var userRole: String = "Admin"

    // Auth state listener — auto-redirect ke login jika sesi habis
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) {
            // Token expired atau user di-sign out — paksa kembali ke login
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    // ==========================================
    // Lifecycle & View Setup
    // ==========================================
    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Keamanan: Cegah screenshot dan screen recording pada halaman login
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        authManager = AuthManager(this)
        userRole = intent.getStringExtra("USER_ROLE") ?: authManager.getUserRole()

        // Load role-specific bottom navigation menu
        if (userRole == "Petugas") {
            setupStaffNavigation()
        } else {
            setupAdminNavigation()
        }

        // Start BinObserverService for persistent background monitoring
        BinObserverService.startService(this)
    }

    override fun onStart() {
        super.onStart()
        // Mulai memantau status autentikasi Firebase
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        // Hentikan listener agar tidak ada memory leak
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }

    // ==========================================
    // Role-Specific Navigation Setup
    // ==========================================
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
                R.id.nav_statistics -> {
                    loadFragment(StatisticsFragment())
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
                R.id.nav_statistics -> {
                    loadFragment(StatisticsFragment())
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

    // ==========================================
    // Utility / Helper Functions
    // ==========================================
    private fun loadFragment(fragment: Fragment) {
        // Role Guard: Pastikan Petugas tidak bisa memuat fragment khusus Admin
        if (userRole == "Petugas" && fragment is StaffManagementFragment) {
            Toast.makeText(this, "Akses ditolak: Anda bukan Admin", Toast.LENGTH_SHORT).show()
            return
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    // ==========================================
    // Lifecycle - Cleanup
    // ==========================================
    override fun onDestroy() {
        BinObserver.stop()
        super.onDestroy()
    }
}
