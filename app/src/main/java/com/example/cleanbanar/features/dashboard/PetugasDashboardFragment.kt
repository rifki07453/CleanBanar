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

        // Load area info
        FirebaseManager.getUserArea(authManager.getUserId()) { areaInfo ->
            if (!isAdded) return@getUserArea
            binding.tvAreaName.text = areaInfo.areaName
        }

        binding.btnDecorativeTrash.setOnClickListener {
            // Decorative only per user request
            Toast.makeText(requireContext(), "Menyegarkan status...", Toast.LENGTH_SHORT).show()
        }

        // Force initial card render with default 0% values before Firebase responds
        rebuildCards()
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

        // Determine status level exactly matching designs
        // Note: The design specific colors for badge and text:
        // "TERSEDIA" = text: #10B981 (emerald 500) bg: #D1FAE5 (emerald 100) or cyan
        // "HAMPIR PENUH" = text: #D97706 (amber 600) bg: #FEF3C7 (amber 100)
        
        val (badgeText, badgeDrawable, badgeTextColor, progressDrawableRes) = when {
            percent >= 95 -> Quadruple("PENUH", R.drawable.badge_red_bg, R.color.red_500, R.drawable.progress_bar_red)
            percent >= 80 -> Quadruple("HAMPIR PENUH", R.drawable.badge_amber_bg, R.color.amber_600, R.drawable.progress_bar_amber)
            else -> {
                // If it's available, Organik uses green badge, Non-Organik uses blue badge? 
                // The design shows Organik: TERSEDIA (emerald text, emerald bg). 
                // A generic TERSEDIA state uses green.
                Quadruple("TERSEDIA", R.drawable.badge_green_bg, R.color.emerald_600, R.drawable.progress_bar_green)
            }
        }

        val statusText = when {
            percent >= 95 -> "Segera dikosongkan!"
            percent >= 80 -> "Perlu segera dikosongkan"
            percent >= 50 -> "Perkiraan penuh dlm 1 hari"
            else -> "Perkiraan penuh dlm 2 hari"
        }

        // Card container
        val cardView = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20.dpToPx() }
            radius = 20f.dpToPxF()
            cardElevation = 0f.dpToPxF()
            strokeWidth = 1.dpToPx()
            strokeColor = 0x1A000000 // Very light outline
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }


        val innerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // ---- Top Row: Icon + Title + Badge ----
        val topRow = RelativeLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24.dpToPx() }
        }

        val iconBg = if (isOrganik) R.drawable.ic_bg_circle_green else R.drawable.ic_bg_circle_blue
        val iconSrc = if (isOrganik) R.drawable.ic_leaf_green else R.drawable.ic_bottle_blue

        val icon = ImageView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(40.dpToPx(), 40.dpToPx()).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setBackgroundResource(iconBg)
            setImageResource(iconSrc)
            scaleType = ImageView.ScaleType.CENTER
            // To simulate design, leaf/bottle is slightly smaller inside circle
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
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
            textSize = 14f
            setTextColor(resources.getColor(R.color.gray_900, null))
        }

        val tvUpdate = TextView(requireContext()).apply {
            text = formatLastUpdate(bin.lastUpdate)
            textSize = 9f
            setTextColor(resources.getColor(R.color.gray_400, null))
        }

        titleCol.addView(tvTitle)
        titleCol.addView(tvUpdate)

        // Badge
        val tvBadge = TextView(requireContext()).apply {
            text = badgeText
            textSize = 8f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(resources.getColor(badgeTextColor, null))
            setBackgroundResource(badgeDrawable)
            setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
            isAllCaps = true
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
            textSize = 11f
            setTextColor(resources.getColor(R.color.gray_500, null))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                bottomMargin = 2.dpToPx()
            }
        }

        val tvPercent = TextView(requireContext()).apply {
            text = "$percent%"
            setTypeface(typeface, Typeface.BOLD)
            textSize = 18f
            setTextColor(resources.getColor(R.color.gray_900, null))
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
        val actualProgressDrawableRes = if (isOrganik) {
            R.drawable.progress_bar_green
        } else {
            // Wait, for non-organik we want blue progress bar instead of green!
            // I'll make a programmatic blue tint, but if it's over 80% it uses amber/red globally.
            // But from design, Organik uses green track, Non-Organik uses blue track.
            if (percent < 80) R.drawable.progress_bar_blue else progressDrawableRes
        }
        
        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8.dpToPx() // THICKER bar to match design
            ).apply { bottomMargin = 8.dpToPx() }
            max = 100
            progress = percent
            progressDrawable = resources.getDrawable(actualProgressDrawableRes, null)
            scaleY = 1.0f // Ensures fat round corners
        }

        // ---- Status / Estimate Row ----
        val statusRow = RelativeLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20.dpToPx() }
        }

        val tvEstimate = TextView(requireContext()).apply {
            text = statusText
            textSize = 8f
            setTextColor(resources.getColor(R.color.gray_400, null))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        }

        statusRow.addView(tvEstimate)

        // ---- CTA Button ----
        val btnEmpty = MaterialButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                42.dpToPx()
            )
            text = "Tandai Telah Dikosongkan"
            // Color logic based on design: Organik button is light green, Non-Organik uses light blue
            val txtColor = if (isOrganik) R.color.emerald_600 else R.color.blue_600
            val bgColor = if (isOrganik) R.color.green_50 else R.color.blue_50
            
            setTextColor(resources.getColor(txtColor, null))
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(resources.getColor(bgColor, null))
            textSize = 11f
            elevation = 0f
            cornerRadius = 10.dpToPx()

            setOnClickListener {
                handleEmptyBin(bin.type, this, isOrganik)
            }
        }

        // Assemble card
        innerLayout.addView(topRow)
        innerLayout.addView(capacityRow)
        innerLayout.addView(progressBar)
        innerLayout.addView(statusRow)
        innerLayout.addView(btnEmpty)
        cardView.addView(innerLayout)

        return cardView
    }

    // ==========================================
    // Button Handler - Empty Bin
    // ==========================================
    private fun handleEmptyBin(binType: String, button: MaterialButton, isOrganik: Boolean) {
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

                val txtColor = if (isOrganik) R.color.emerald_600 else R.color.blue_600
                button.setTextColor(resources.getColor(txtColor, null))

                // Revert text after 2 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isAdded) {
                        button.text = originalText
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
                binding.statusDot.setBackgroundResource(R.drawable.badge_red_bg)
            }
            maxPercent >= 80 -> {
                binding.tvStatus.text = "Perhatian"
                binding.statusDot.setBackgroundResource(R.drawable.badge_amber_bg)
            }
            maxPercent >= 50 -> {
                binding.tvStatus.text = "Perhatian"
                binding.statusDot.setBackgroundResource(R.drawable.badge_amber_bg)
            }
            else -> {
                binding.tvStatus.text = "Normal"
                binding.statusDot.setBackgroundResource(R.drawable.badge_green_bg)
            }
        }
    }

    // ==========================================
    // Utility / Helper Functions
    // ==========================================
    private fun formatLastUpdate(timestamp: Long): String {
        if (timestamp == 0L) return "Belum diupdate"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "Update $minutes min lalu"
            else -> "Update ${minutes / 60} jam lalu"
        }
    }

    override fun onDestroyView() {
        organikListener?.let { FirebaseManager.removeBinListener("organik", it) }
        nonOrganikListener?.let { FirebaseManager.removeBinListener("nonOrganik", it) }
        super.onDestroyView()
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density
}
