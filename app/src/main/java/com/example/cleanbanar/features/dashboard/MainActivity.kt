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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private lateinit var authManager: AuthManager
    private var userRole: String = "Admin"
    private var notifListener: com.google.firebase.database.ValueEventListener? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Izin notifikasi diperlukan untuk menerima peringatan penuh", Toast.LENGTH_SHORT).show()
            }
        }

    // Auth state listener — auto-redirect ke login jika sesi habis
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) {
            // Token expired atau user di-sign out — paksa kembali ke login
            if (!isFinishing && !isDestroyed) {
                try {
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ==========================================
    // Lifecycle & View Setup
    // ==========================================
    override fun inflateBinding(layoutInflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Keamanan: Cegah screenshot dan screen recording (dinonaktifkan sementara untuk development/simulator)
        // window.setFlags(
        //     android.view.WindowManager.LayoutParams.FLAG_SECURE,
        //     android.view.WindowManager.LayoutParams.FLAG_SECURE
        // )

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

        askNotificationPermission()

        notifListener = FirebaseManager.listenNotifications { notifData ->
            val unreadCount = notifData.count { !(it["sudahDibaca"] as? Boolean ?: true) }
            if (unreadCount > 0) {
                binding.bottomNavigation.getOrCreateBadge(R.id.nav_notification).apply {
                    isVisible = true
                }
            } else {
                binding.bottomNavigation.removeBadge(R.id.nav_notification)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
        notifListener?.let { FirebaseManager.removeNotificationListener(it) }
        super.onDestroy()
    }
}
