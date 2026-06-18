package com.example.cleanbanar.features.dashboard

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.ValueEventListener
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

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

    // ==========================================
    // QR Code Scanning State
    // ==========================================
    private var selectedBinToVerify: BinData? = null

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents.trim()
            verifyAndProceed(scannedCode)
        } else {
            Toast.makeText(requireContext(), "Pindaian dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPetugasDashboardBinding {
        return FragmentPetugasDashboardBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())
        binding.tvSystemName.text = "Sistem Terpusat"
        
        binding.fabScanQr.setOnClickListener {
            // Default target is null because the flow starts by scanning
            showVerificationOptions(null)
        }
        
        binding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.emerald_500)
        binding.swipeRefresh.setOnRefreshListener {
            // Walaupun Firebase Realtime, kita beri efek visual refresh
            rebuildCards()
            updateOverallStatus()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(requireContext(), "Data diperbarui", Toast.LENGTH_SHORT).show()
            }, 1000)
        }
        
        // Terapkan animasi bernapas pada background hijau header
        val headerBlock = view?.findViewById<android.widget.FrameLayout>(R.id.headerBlock)
        if (headerBlock != null) {
            com.example.cleanbanar.core.utils.AnimationUtils.applyHeaderBreathingEffect(headerBlock)
        }
        
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
        val devicesMap = binDataMap.values.groupBy { it.deviceId }
        
        if (devicesMap.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "Belum ada perangkat yang terdaftar."
                setTextColor(resources.getColor(R.color.gray_500, null))
                setPadding(0, 32.dpToPx(), 0, 0)
            }
            binding.cardsContainer.addView(tv)
            return
        }

        val sortedDevices = devicesMap.entries.sortedByDescending { entry ->
            entry.value.maxOfOrNull { it.fillPercentage } ?: 0
        }
 
        for ((deviceId, bins) in sortedDevices) {
            val deviceName = bins.firstOrNull()?.deviceName ?: deviceId
            val card = buildDeviceCard(deviceId, deviceName, bins)
            binding.cardsContainer.addView(card)
        }
    }

    private fun buildDeviceCard(deviceId: String, deviceName: String, bins: List<BinData>): MaterialCardView {
        val cardView = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dpToPx() }
            radius = 16f.dpToPxF()
            cardElevation = 0f.dpToPxF()
            strokeWidth = 1.dpToPx()
            strokeColor = 0x1A000000 
            setCardBackgroundColor(resources.getColor(R.color.white, null))
        }

        val innerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        val tvDeviceName = TextView(requireContext()).apply {
            text = deviceName
            setTypeface(typeface, Typeface.BOLD)
            textSize = 14f
            setTextColor(resources.getColor(R.color.gray_900, null))
            setPadding(0, 0, 0, 12.dpToPx())
        }
        innerLayout.addView(tvDeviceName)

        val orgBin = bins.find { it.type == "organik" }
        val nonOrgBin = bins.find { it.type == "nonOrganik" }

        if (orgBin != null) innerLayout.addView(buildCompactBinRow(orgBin))
        if (nonOrgBin != null) innerLayout.addView(buildCompactBinRow(nonOrgBin))

        cardView.addView(innerLayout)
        
        cardView.setOnClickListener {
            // Gunakan salah satu bin (organik/non-organik) sebagai referensi untuk menampilkan dialog
            val referenceBin = orgBin ?: nonOrgBin
            if (referenceBin != null) {
                showConfirmationDialog(referenceBin)
            }
        }
        
        return cardView
    }

    private fun buildCompactBinRow(bin: BinData): View {
        val isOrganik = bin.type == "organik"
        val label = if (isOrganik) "Organik" else "Non-Organik"
        val percent = bin.fillPercentage

        val (badgeText, badgeDrawable, badgeTextColor, progressDrawableRes) = when {
            percent >= 95 -> Quadruple("PENUH", R.drawable.badge_red_bg, R.color.red_500, R.drawable.progress_bar_red)
            percent >= 80 -> Quadruple("HAMPIR", R.drawable.badge_amber_bg, R.color.amber_600, R.drawable.progress_bar_amber)
            else -> Quadruple("AMAN", R.drawable.badge_green_bg, R.color.emerald_600, R.drawable.progress_bar_green)
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dpToPx() }
        }

        val topLayout = RelativeLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvLabel = TextView(requireContext()).apply {
            text = label
            textSize = 12f
            setTextColor(resources.getColor(R.color.gray_700, null))
        }

        val tvPercent = TextView(requireContext()).apply {
            text = "$percent%"
            setTypeface(typeface, Typeface.BOLD)
            textSize = 12f
            setTextColor(resources.getColor(R.color.gray_900, null))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        }

        topLayout.addView(tvLabel)
        topLayout.addView(tvPercent)

        val actualProgressDrawableRes = if (isOrganik) {
            R.drawable.progress_bar_green
        } else {
            if (percent < 80) R.drawable.progress_bar_blue else progressDrawableRes
        }
        
        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                6.dpToPx()
            ).apply { 
                topMargin = 4.dpToPx() 
                bottomMargin = 4.dpToPx()
            }
            max = 100
            progressDrawable = resources.getDrawable(actualProgressDrawableRes, null)
            scaleY = 1.0f 
            
            // Animasi halus dari 0 ke persen
            com.example.cleanbanar.core.utils.AnimationUtils.animateProgressBar(this, percent)
        }

        row.addView(topLayout)
        row.addView(progressBar)

        return row
    }

    // ==========================================
    // QR Code Verification Flow logic
    // ==========================================

    private fun showVerificationOptions(bin: BinData?) {
        selectedBinToVerify = bin
        
        val options = arrayOf("Pindai QR Code (Kamera)", "Ketik ID Manual")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (bin != null) "Verifikasi Kehadiran" else "Pilih Metode Verifikasi")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> startQRScan()
                    1 -> showManualIdInputDialog()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun startQRScan() {
        val promptText = if (selectedBinToVerify != null) {
            "Arahkan kamera ke QR Code di baksampah ${selectedBinToVerify?.deviceName}\n(Tekan tombol Kembali di HP jika ingin ketik ID Manual)"
        } else {
            "Pindai QR Perangkat\n(Tekan tombol Kembali di HP jika ingin ketik ID Manual)"
        }

        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(promptText)
            setCameraId(0) 
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setCaptureActivity(com.example.cleanbanar.core.ui.PortraitCaptureActivity::class.java)
            setOrientationLocked(true)
        }
        barcodeLauncher.launch(options)
    }

    private fun showManualIdInputDialog() {
        val inputLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 16.dpToPx())
        }

        val etInput = TextInputEditText(requireContext()).apply {
            hint = selectedBinToVerify?.let { "Contoh: ${it.deviceId}" } ?: "Contoh: DEV-001"
            maxLines = 1
        }
        inputLayout.addView(etInput)

        val message = selectedBinToVerify?.let { "Silakan ketik ID perangkat untuk ${it.deviceName}:" } ?: "Silakan ketik ID perangkat secara manual:"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Masukkan ID Perangkat")
            .setMessage(message)
            .setView(inputLayout)
            .setPositiveButton("Verifikasi") { dialog, _ ->
                val typedId = etInput.text.toString().trim()
                if (typedId.isEmpty()) {
                    Toast.makeText(requireContext(), "ID tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                verifyAndProceed(typedId)
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun verifyAndProceed(code: String) {
        if (selectedBinToVerify == null) {
            // General scan mode from FAB
            // Try to find the device that matches the scanned code
            val matchingOrgBin = binDataMap.values.find { it.deviceId.equals(code, ignoreCase = true) && it.type == "organik" }
            if (matchingOrgBin != null) {
                showConfirmationDialog(matchingOrgBin)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Perangkat Tidak Ditemukan")
                    .setMessage("QR Code/ID ($code) tidak cocok dengan perangkat manapun di database.")
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        } else {
            // Contextual scan from old logic (just in case they use manual ID)
            val target = selectedBinToVerify ?: return
            if (code.equals(target.deviceId, ignoreCase = true)) {
                showConfirmationDialog(target)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Verifikasi Gagal")
                    .setMessage("QR Code/ID yang dimasukkan ($code) tidak cocok dengan tempat sampah ini (${target.deviceName} / ${target.deviceId}).")
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        }
    }

    private fun showConfirmationDialog(bin: BinData) {
        val orgBin = binDataMap["${bin.deviceId}_organik"]
        val nonOrgBin = binDataMap["${bin.deviceId}_nonOrganik"]

        val orgPercent = orgBin?.fillPercentage ?: 0
        val nonOrgPercent = nonOrgBin?.fillPercentage ?: 0

        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
        }

        // Lokasi / Keterangan Tempat
        val tvLocation = TextView(requireContext()).apply {
            text = "Lokasi: ${bin.deviceName}"
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setTextColor(resources.getColor(R.color.emerald_600, null))
            setPadding(0, 0, 0, 8.dpToPx())
        }
        dialogView.addView(tvLocation)

        val tvDesc = TextView(requireContext()).apply {
            text = "Pilih bak sampah yang telah selesai dikosongkan:"
            textSize = 14f
            setTextColor(resources.getColor(R.color.gray_600, null))
            setPadding(0, 0, 0, 16.dpToPx())
        }
        dialogView.addView(tvDesc)

        // Wrapper for Checkbox Organik
        val cbOrganikContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dpToPx() }
            setBackgroundResource(R.drawable.edit_text_bg)
            setPadding(8.dpToPx(), 12.dpToPx(), 8.dpToPx(), 12.dpToPx())
        }

        val cbOrganik = CheckBox(requireContext()).apply {
            text = "Bak Organik ($orgPercent% terisi)"
            textSize = 15f
            setTypeface(typeface, if (orgPercent >= 80) Typeface.BOLD else Typeface.NORMAL)
            isChecked = bin.type == "organik" || orgPercent >= 80
            setTextColor(resources.getColor(if (orgPercent >= 80) R.color.red_500 else R.color.gray_900, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        cbOrganikContainer.addView(cbOrganik)
        dialogView.addView(cbOrganikContainer)

        // Wrapper for Checkbox Non-Organik
        val cbNonOrganikContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dpToPx() }
            setBackgroundResource(R.drawable.edit_text_bg)
            setPadding(8.dpToPx(), 12.dpToPx(), 8.dpToPx(), 12.dpToPx())
        }

        val cbNonOrganik = CheckBox(requireContext()).apply {
            text = "Bak Non-Organik ($nonOrgPercent% terisi)"
            textSize = 15f
            setTypeface(typeface, if (nonOrgPercent >= 80) Typeface.BOLD else Typeface.NORMAL)
            isChecked = bin.type == "nonOrganik" || nonOrgPercent >= 80
            setTextColor(resources.getColor(if (nonOrgPercent >= 80) R.color.red_500 else R.color.gray_900, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        cbNonOrganikContainer.addView(cbNonOrganik)
        dialogView.addView(cbNonOrganikContainer)

        cbOrganikContainer.setOnClickListener { cbOrganik.isChecked = !cbOrganik.isChecked }
        cbNonOrganikContainer.setOnClickListener { cbNonOrganik.isChecked = !cbNonOrganik.isChecked }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi Pengosongan")
            .setView(dialogView)
            .setPositiveButton("Kosongkan") { dialog, _ ->
                val emptyOrganik = cbOrganik.isChecked
                val emptyNonOrganik = cbNonOrganik.isChecked
                
                if (!emptyOrganik && !emptyNonOrganik) {
                    Toast.makeText(requireContext(), "Tidak ada bak yang dipilih", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                executeEmptying(bin.deviceId, emptyOrganik, emptyNonOrganik)
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun executeEmptying(deviceId: String, emptyOrg: Boolean, emptyNonOrg: Boolean) {
        val ctx = requireContext()
        if (emptyOrg) {
            FirebaseManager.emptyBin(deviceId, "organik", authManager.getUserId(), authManager.getUserName())
            BinObserver.triggerSelesaiNotification(ctx, "Organik", deviceId, authManager.getUserName())
        }
        if (emptyNonOrg) {
            FirebaseManager.emptyBin(deviceId, "nonOrganik", authManager.getUserId(), authManager.getUserName())
            BinObserver.triggerSelesaiNotification(ctx, "Non-Organik", deviceId, authManager.getUserName())
        }
        Toast.makeText(ctx, "Berhasil dikosongkan", Toast.LENGTH_SHORT).show()
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
