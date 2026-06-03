package com.example.cleanbanar.features.dashboard

import android.graphics.Typeface
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
    
    private var devicesListener: ValueEventListener? = null
    private val binListeners = mutableMapOf<String, Pair<ValueEventListener, ValueEventListener>>()

    private data class BinData(
        val deviceId: String,
        val deviceName: String,
        val type: String,
        var fillPercentage: Int = 0,
        var status: String = "Normal",
        var lastUpdate: Long = 0L,
        var lastEmptied: Long = 0L
    )

    // Key: "deviceId_binType"
    private val binDataMap = mutableMapOf<String, BinData>()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPetugasDashboardBinding {
        return FragmentPetugasDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())
        binding.tvSystemName.text = "Sistem Terpusat"
        rebuildCards()
    }

    override fun observeData() {
        devicesListener = FirebaseManager.listenDevices { devices ->
            if (!isAdded) return@listenDevices
            
            val currentDeviceIds = devices.map { it.id }.toSet()
            
            // Hapus listener & data perangkat yang sudah tidak ada
            val removedDevices = binListeners.keys - currentDeviceIds
            for (deviceId in removedDevices) {
                val listeners = binListeners[deviceId]
                if (listeners != null) {
                    FirebaseManager.removeBinListener(deviceId, "organik", listeners.first)
                    FirebaseManager.removeBinListener(deviceId, "nonOrganik", listeners.second)
                }
                binListeners.remove(deviceId)
                binDataMap.remove("${deviceId}_organik")
                binDataMap.remove("${deviceId}_nonOrganik")
            }
            
            for (device in devices) {
                if (!binDataMap.containsKey("${device.id}_organik")) {
                    binDataMap["${device.id}_organik"] = BinData(device.id, device.nama, "organik")
                } else {
                    binDataMap["${device.id}_organik"]?.let { it.copy(deviceName = device.nama) }
                }
                
                if (!binDataMap.containsKey("${device.id}_nonOrganik")) {
                    binDataMap["${device.id}_nonOrganik"] = BinData(device.id, device.nama, "nonOrganik")
                } else {
                    binDataMap["${device.id}_nonOrganik"]?.let { it.copy(deviceName = device.nama) }
                }

                if (!binListeners.containsKey(device.id)) {
                    val orgListener = FirebaseManager.listenBinStatus(device.id, "organik") { fillPercentage, status, lastUpdate, lastEmptied ->
                        if (!isAdded) return@listenBinStatus
                        binDataMap["${device.id}_organik"]?.apply {
                            this.fillPercentage = fillPercentage
                            this.status = status
                            this.lastUpdate = lastUpdate
                            this.lastEmptied = lastEmptied
                        }
                        rebuildCards()
                        updateOverallStatus()
                    }
                    
                    val nonOrgListener = FirebaseManager.listenBinStatus(device.id, "nonOrganik") { fillPercentage, status, lastUpdate, lastEmptied ->
                        if (!isAdded) return@listenBinStatus
                        binDataMap["${device.id}_nonOrganik"]?.apply {
                            this.fillPercentage = fillPercentage
                            this.status = status
                            this.lastUpdate = lastUpdate
                            this.lastEmptied = lastEmptied
                        }
                        rebuildCards()
                        updateOverallStatus()
                    }
                    
                    if (orgListener != null && nonOrgListener != null) {
                        binListeners[device.id] = Pair(orgListener, nonOrgListener)
                    }
                }
            }
            
            rebuildCards()
            updateOverallStatus()
        }
    }

    private fun rebuildCards() {
        binding.cardsContainer.removeAllViews()
        val sortedBins = binDataMap.values.sortedByDescending { it.fillPercentage }

        if (sortedBins.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "Belum ada perangkat yang terdaftar."
                setTextColor(resources.getColor(R.color.gray_500, null))
                setPadding(0, 32.dpToPx(), 0, 0)
            }
            binding.cardsContainer.addView(tv)
            return
        }

        for (bin in sortedBins) {
            val card = buildBinCard(bin)
            binding.cardsContainer.addView(card)
        }
    }

    private fun buildBinCard(bin: BinData): MaterialCardView {
        val isOrganik = bin.type == "organik"
        val label = if (isOrganik) "Organik" else "Non-Organik"
        val percent = bin.fillPercentage

        val (badgeText, badgeDrawable, badgeTextColor, progressDrawableRes) = when {
            percent >= 95 -> Quadruple("PENUH", R.drawable.badge_red_bg, R.color.red_500, R.drawable.progress_bar_red)
            percent >= 80 -> Quadruple("HAMPIR PENUH", R.drawable.badge_amber_bg, R.color.amber_600, R.drawable.progress_bar_amber)
            else -> Quadruple("TERSEDIA", R.drawable.badge_green_bg, R.color.emerald_600, R.drawable.progress_bar_green)
        }

        val statusText = when {
            percent >= 95 -> "Segera dikosongkan!"
            percent >= 80 -> "Perlu segera dikosongkan"
            percent >= 50 -> "Perkiraan penuh dlm 1 hari"
            else -> "Perkiraan penuh dlm 2 hari"
        }

        val cardView = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20.dpToPx() }
            radius = 20f.dpToPxF()
            cardElevation = 0f.dpToPxF()
            strokeWidth = 1.dpToPx()
            strokeColor = 0x1A000000 
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }

        val innerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        val topRow = RelativeLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24.dpToPx() }
        }

        val iconBgColor = if (isOrganik) android.graphics.Color.parseColor("#ECFDF5")
                          else android.graphics.Color.parseColor("#EFF6FF")
        val iconSrc = R.drawable.ic_trash_modern
        val iconColor = if (isOrganik) android.graphics.Color.parseColor("#16A34A")
                        else android.graphics.Color.parseColor("#2563EB")

        val iconCircleBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(iconBgColor)
        }

        val icon = ImageView(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(48.dpToPx(), 48.dpToPx()).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            background = iconCircleBg
            setImageResource(iconSrc)
            setColorFilter(iconColor)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
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
            text = "$label (${bin.deviceName})"
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

        val actualProgressDrawableRes = if (isOrganik) {
            R.drawable.progress_bar_green
        } else {
            if (percent < 80) R.drawable.progress_bar_blue else progressDrawableRes
        }
        
        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8.dpToPx()
            ).apply { bottomMargin = 8.dpToPx() }
            max = 100
            progress = percent
            progressDrawable = resources.getDrawable(actualProgressDrawableRes, null)
            scaleY = 1.0f 
        }

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

        val diffEmptied = System.currentTimeMillis() - bin.lastEmptied
        val daysSinceEmptied = diffEmptied / 86_400_000L

        val tvWarning = TextView(requireContext()).apply {
            textSize = 8f
            setTextColor(resources.getColor(R.color.red_500, null))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
            }
            visibility = View.GONE
        }

        if (bin.lastEmptied > 0L) {
            if (isOrganik && daysSinceEmptied >= 3) {
                tvWarning.text = "⚠️ Mulai membusuk (>$daysSinceEmptied hari)"
                tvWarning.visibility = View.VISIBLE
            } else if (!isOrganik && daysSinceEmptied >= 7) {
                tvWarning.text = "⚠️ Sudah menumpuk (>$daysSinceEmptied hari)"
                tvWarning.visibility = View.VISIBLE
            }
        }
        
        statusRow.addView(tvWarning)

        val btnEmpty = MaterialButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                42.dpToPx()
            )
            text = "Tandai Telah Dikosongkan"
            val txtColor = if (isOrganik) R.color.emerald_600 else R.color.blue_600
            val bgColor = if (isOrganik) R.color.green_50 else R.color.blue_50
            
            setTextColor(resources.getColor(txtColor, null))
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(resources.getColor(bgColor, null))
            textSize = 11f
            elevation = 0f
            cornerRadius = 10.dpToPx()

            setOnClickListener {
                handleEmptyBin(bin.deviceId, bin.type, this, isOrganik)
            }
        }

        innerLayout.addView(topRow)
        innerLayout.addView(capacityRow)
        innerLayout.addView(progressBar)
        innerLayout.addView(statusRow)
        innerLayout.addView(btnEmpty)
        cardView.addView(innerLayout)

        return cardView
    }

    private fun handleEmptyBin(deviceId: String, binType: String, button: MaterialButton, isOrganik: Boolean) {
        val originalText = button.text
        button.isEnabled = false
        button.text = "Memuat..."

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            FirebaseManager.emptyBin(deviceId, binType, authManager.getUserId(), authManager.getUserName())

            if (isAdded) {
                button.isEnabled = true
                button.text = "✓ Dikosongkan"

                val txtColor = if (isOrganik) R.color.emerald_600 else R.color.blue_600
                button.setTextColor(resources.getColor(txtColor, null))

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isAdded) {
                        button.text = originalText
                    }
                }, 2000)

                Toast.makeText(requireContext(), "Berhasil dikosongkan", Toast.LENGTH_SHORT).show()
            }
        }, 800)
    }

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
            else -> {
                binding.tvStatus.text = "Normal"
                binding.statusDot.setBackgroundResource(R.drawable.badge_green_bg)
            }
        }
    }

    private fun formatLastUpdate(timestamp: Long): String {
        if (timestamp == 0L) return "Belum diupdate"
        val diff   = System.currentTimeMillis() - timestamp
        val menit  = diff / 60_000L
        val jam    = diff / 3_600_000L
        val hari   = diff / 86_400_000L
        val minggu = diff / 604_800_000L
        val bulan  = diff / 2_592_000_000L
        return when {
            menit  < 1  -> "Baru saja"
            menit  < 60 -> "$menit menit lalu"
            jam    < 24 -> "$jam jam lalu"
            hari   < 7  -> "$hari hari lalu"
            minggu < 4  -> "$minggu minggu lalu"
            else        -> "$bulan bulan lalu"
        }
    }

    override fun onDestroyView() {
        devicesListener?.let { FirebaseManager.removeDeviceListener(it) }
        for ((deviceId, listeners) in binListeners) {
            FirebaseManager.removeBinListener(deviceId, "organik", listeners.first)
            FirebaseManager.removeBinListener(deviceId, "nonOrganik", listeners.second)
        }
        binListeners.clear()
        super.onDestroyView()
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpToPxF(): Float = this * resources.displayMetrics.density
}
