package com.example.cleanbanar.features.dashboard

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentPetugasDashboardBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.database.ValueEventListener

class PetugasDashboardFragment : BaseFragment<FragmentPetugasDashboardBinding>() {

    private lateinit var authManager: AuthManager
    private var organikListener: ValueEventListener? = null
    private var nonOrganikListener: ValueEventListener? = null

    // Track bin data for urgency sorting
    private data class BinData(
        val type: String,
        var fillPercentage: Int = 0,
        var status: String = "Normal",
        var lastUpdate: Long = 0L,
        var lastEmptied: Long = 0L
    )

    private val binDataMap = mutableMapOf(
        "organik" to BinData("organik"),
        "nonOrganik" to BinData("nonOrganik")
    )

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPetugasDashboardBinding {
        return FragmentPetugasDashboardBinding.inflate(inflater, container, false)
    }

    // ==========================================
    // View Setup & Area Info Loading
    // ==========================================
    override fun setupViews() {
        authManager = AuthManager(requireContext())

        // Load area and sub-area info
        FirebaseManager.getUserArea(authManager.getUserId()) { areaInfo ->
            if (!isAdded) return@getUserArea
            binding.tvAreaName.text = areaInfo.areaName
            binding.tvSubArea.text = areaInfo.subAreaName
        }
    }

    // ==========================================
    // Firebase Real-Time Listeners
    // ==========================================
    override fun observeData() {
        organikListener = FirebaseManager.listenBinStatus("organik") { fillPercentage, status, lastUpdate, lastEmptied ->
            if (!isAdded) return@listenBinStatus
            binDataMap["organik"] = BinData("organik", fillPercentage, status, lastUpdate, lastEmptied)
            rebuildCards()
            updateOverallStatus()
        }

        nonOrganikListener = FirebaseManager.listenBinStatus("nonOrganik") { fillPercentage, status, lastUpdate, lastEmptied ->
            if (!isAdded) return@listenBinStatus
            binDataMap["nonOrganik"] = BinData("nonOrganik", fillPercentage, status, lastUpdate, lastEmptied)
            rebuildCards()
            updateOverallStatus()
        }
    }

    // ==========================================
    // Dynamic Card Building (Urgency Sorted)
    // ==========================================
    private fun rebuildCards() {
        binding.cardsContainer.removeAllViews()

        // Sort by fillPercentage descending (highest urgency first)
        val sortedBins = binDataMap.values.sortedByDescending { it.fillPercentage }

        for (bin in sortedBins) {
            val card = buildBinCard(bin)
            binding.cardsContainer.addView(card)
        }
    }

    private fun buildBinCard(bin: BinData): MaterialCardView {
        val isOrganik = bin.type == "organik"
        val label = if (isOrganik) "Organik" else "Non-Organik"
        val percent = bin.fillPercentage

        // Determine status level
        val (badgeText, badgeDrawable, badgeTextColor, progressDrawableRes) = when {
            percent >= 95 -> Quadruple("PENUH", R.drawable.badge_outlined_red, R.color.red_500, R.drawable.progress_bar_red)
            percent >= 80 -> Quadruple("HAMPIR PENUH", R.drawable.badge_outlined_amber, R.color.orange_600, R.drawable.progress_bar_amber)
            else -> Quadruple("TERSEDIA", R.drawable.badge_outlined_green, R.color.green_600, R.drawable.progress_bar_green)
        }

        val statusText = when {
            percent >= 95 -> "Segera dikosongkan!"
            percent >= 80 -> "Perlu segera dikosongkan"
            percent >= 50 -> "Perlu dipantau"
            else -> "Dalam kondisi baik"
        }

        // Card
        val cardView = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dpToPx() }
            radius = 16f.dpToPxF()
            cardElevation = 0f.dpToPxF()
            strokeWidth = 1.dpToPx()
            strokeColor = 0xFFF1F3F5.toInt() // light gray stroke
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }


        val innerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
        }

        // ---- Top Row: Icon + Title + Badge ----
        val topRow = RelativeLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dpToPx() }
        }

        val iconBg = if (isOrganik) R.drawable.ic_bg_circle_green else R.drawable.ic_bg_circle_blue
        val iconSrc = if (isOrganik) R.drawable.ic_organik else R.drawable.ic_non_organik

        val icon = ImageView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(48.dpToPx(), 48.dpToPx()).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setBackgroundResource(iconBg)
            setImageResource(iconSrc)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(10.dpToPx(), 10.dpToPx(), 10.dpToPx(), 10.dpToPx())
            contentDescription = label
        }

        val titleCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.END_OF, icon.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
                marginStart = 16.dpToPx()
            }
        }

        val tvTitle = TextView(requireContext()).apply {
            text = label
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setTextColor(resources.getColor(R.color.gray_800, null))
        }

        val tvUpdate = TextView(requireContext()).apply {
            text = formatLastUpdate(bin.lastUpdate)
            textSize = 10f
            setTextColor(resources.getColor(R.color.gray_400, null))
        }

        titleCol.addView(tvTitle)
        titleCol.addView(tvUpdate)

        // Badge
        val tvBadge = TextView(requireContext()).apply {
            text = badgeText
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resources.getColor(badgeTextColor, null))
            setBackgroundResource(badgeDrawable)
            setPadding(12.dpToPx(), 5.dpToPx(), 12.dpToPx(), 5.dpToPx())
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }

        topRow.addView(icon)
        topRow.addView(titleCol)
        topRow.addView(tvBadge)

        // ---- Capacity Row ----
        val capacityRow = RelativeLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }
        }

        val tvCapLabel = TextView(requireContext()).apply {
            text = "Tingkat Kapasitas"
            setTypeface(typeface, Typeface.BOLD)
            textSize = 12f
            setTextColor(resources.getColor(R.color.gray_500, null))
        }

        val tvPercent = TextView(requireContext()).apply {
            text = "$percent%"
            setTypeface(typeface, Typeface.BOLD)
            textSize = 18f
            setTextColor(resources.getColor(R.color.gray_800, null))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        }

        capacityRow.addView(tvCapLabel)
        capacityRow.addView(tvPercent)

        // ---- Progress Bar ----
        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                12.dpToPx()
            ).apply { bottomMargin = 8.dpToPx() }
            max = 100
            progress = percent
            progressDrawable = resources.getDrawable(progressDrawableRes, null)
        }

        // ---- Status / Estimate Row ----
        val statusRow = RelativeLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }
        }

        val tvLastEmptied = TextView(requireContext()).apply {
            text = formatLastEmptied(bin.lastEmptied)
            textSize = 10f
            setTextColor(resources.getColor(R.color.gray_400, null))
        }

        val tvEstimate = TextView(requireContext()).apply {
            text = statusText
            textSize = 10f
            setTextColor(
                when {
                    percent >= 95 -> resources.getColor(R.color.red_500, null)
                    percent >= 80 -> resources.getColor(R.color.orange_600, null)
                    else -> resources.getColor(R.color.gray_400, null)
                }
            )
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        }

        statusRow.addView(tvLastEmptied)
        statusRow.addView(tvEstimate)

        // ---- Divider ----
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.dpToPx()
            ).apply {
                topMargin = 8.dpToPx()
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(resources.getColor(R.color.gray_100, null))
        }

        // ---- CTA Button ----
        val btnEmpty = MaterialButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            )
            text = "Tandai Telah Dikosongkan"
            setTextColor(resources.getColor(R.color.emerald_600, null))
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(resources.getColor(R.color.emerald_50, null))
            elevation = 0f
            cornerRadius = 12.dpToPx()

            setOnClickListener {
                handleEmptyBin(bin.type, this)
            }
        }

        // Assemble card
        innerLayout.addView(topRow)
        innerLayout.addView(capacityRow)
        innerLayout.addView(progressBar)
        innerLayout.addView(statusRow)
        innerLayout.addView(divider)
        innerLayout.addView(btnEmpty)
        cardView.addView(innerLayout)

        return cardView
    }

    // ==========================================
    // Button Handler - Empty Bin
    // ==========================================
    private fun handleEmptyBin(binType: String, button: MaterialButton) {
        val originalText = button.text
        button.isEnabled = false
        button.text = "Memuat..."

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Unified empty action
            FirebaseManager.emptyBin(binType, authManager.getUserName())
            
            // Add traceable history entry
            FirebaseManager.addHistoryEntry(
                action = "emptied",
                areaId = authManager.getAssignedAreaId(),
                userId = authManager.getUserId(),
                fullName = authManager.getUserName()
            )

            if (isAdded) {
                button.isEnabled = true
                button.text = "✓ Dikosongkan"
                button.setTextColor(resources.getColor(R.color.green_600, null))

                // Revert text after 2 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isAdded) {
                        button.text = originalText
                        button.setTextColor(resources.getColor(R.color.emerald_600, null))
                    }
                }, 2000)

                Toast.makeText(requireContext(), "Berhasil dikosongkan", Toast.LENGTH_SHORT).show()
            }
        }, 800)
    }

    // ==========================================
    // Overall System Status Computation
    // ==========================================
    private fun updateOverallStatus() {
        val maxPercent = binDataMap.values.maxOfOrNull { it.fillPercentage } ?: 0

        when {
            maxPercent >= 95 -> {
                binding.tvStatus.text = "Kritis"
                binding.tvStatus.setTextColor(resources.getColor(R.color.red_500, null))
                binding.statusDot.setBackgroundResource(R.drawable.badge_red_bg)
            }
            maxPercent >= 80 -> {
                binding.tvStatus.text = "Perhatian"
                binding.tvStatus.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.statusDot.setBackgroundResource(R.drawable.badge_amber_bg)
            }
            maxPercent >= 50 -> {
                binding.tvStatus.text = "Perhatian"
                binding.tvStatus.setTextColor(resources.getColor(R.color.amber_600, null))
                binding.statusDot.setBackgroundResource(R.drawable.badge_amber_bg)
            }
            else -> {
                binding.tvStatus.text = "Normal"
                binding.tvStatus.setTextColor(resources.getColor(R.color.white, null))
                binding.statusDot.setBackgroundResource(R.drawable.badge_green_bg)
            }
        }
    }

    // ==========================================
    // Utility / Helper Functions
    // ==========================================
    private fun formatLastUpdate(timestamp: Long): String {
        if (timestamp == 0L) return "Menunggu data..."
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "Update baru saja"
            minutes < 60 -> "Update $minutes menit lalu"
            else -> "Update ${minutes / 60} jam lalu"
        }
    }

    private fun formatLastEmptied(timestamp: Long): String {
        if (timestamp == 0L) return "Belum pernah dikosongkan"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "Dikosongkan baru saja"
            minutes < 60 -> "Dikosongkan $minutes menit lalu"
            hours < 24 -> "Dikosongkan $hours jam lalu"
            days == 1L -> "Dikosongkan 1 hari lalu"
            else -> "Dikosongkan $days hari lalu"
        }
    }

    override fun onDestroyView() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        super.onDestroyView()
    }

    // Helper data class (Kotlin doesn't have Quadruple)
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density
}
