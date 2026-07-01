package com.example.cleanbanar.features.statistics

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentStatisticsBinding
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class StatisticsFragment : BaseFragment<FragmentStatisticsBinding>() {

    // ==========================================
    // State & Selection Variables
    // ==========================================
    private var allDailyStats: List<Map<String, Any>> = emptyList()
    private var isCapacityMode = true
    private var comparePeriodDays = 7

    // ==========================================
    // Firebase Listener References
    // ==========================================
    private var statsListener: ValueEventListener? = null
    private var penuhListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentStatisticsBinding {
        return FragmentStatisticsBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Setup Spinner for comparison period
        val periods = arrayOf("1 Minggu Lalu", "2 Minggu Lalu", "1 Bulan Lalu")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerComparePeriod.adapter = spinnerAdapter
        binding.spinnerComparePeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                comparePeriodDays = when (position) {
                    0 -> 7
                    1 -> 14
                    2 -> 28
                    else -> 7
                }
                updateUI()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Spinner for Chart Mode
        val chartModes = arrayOf("Kapasitas", "Pengosongan")
        val chartModeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, chartModes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerChartMode.adapter = chartModeAdapter
        binding.spinnerChartMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                isCapacityMode = position == 0
                updateUI()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ==========================================
    // Firebase Real-Time Listeners
    // ==========================================
    override fun observeData() {
        // Listen to daily statistics for chart and averages
        statsListener = FirebaseManager.listenDailyStats { stats ->
            if (!isAdded) return@listenDailyStats
            allDailyStats = stats
            updateUI()
        }

        // Listen to history node for "total penuh" count (last 7 days)
        penuhListener = FirebaseManager.countPenuhEvents { count ->
            if (!isAdded) return@countPenuhEvents
            binding.tvTotalPenuh.animateCount(0, count.toInt())
        }
    }

    // ==========================================
    // Data Processing & UI Sync
    // ==========================================

    private fun updateUI() {
        if (allDailyStats.isEmpty()) return

        // Generate 5 weeks of data (current week + 4 past weeks) aligned to Monday-Sunday
        val past35Dates = getFixedWeeksDates(5)
        val statsMap = allDailyStats.associateBy { it["tanggal"] as? String ?: "" }

        // Process daily stats, fallback to 0 if no record exists
        val processedStats = past35Dates.map { dateKey ->
            val firebaseData = statsMap[dateKey]
            mapOf(
                "tanggal" to dateKey,
                "organik" to (firebaseData?.get("organik") as? Int ?: 0),
                "nonOrganik" to (firebaseData?.get("nonOrganik") as? Int ?: 0),
                "organikEmptyCount" to (firebaseData?.get("organikEmptyCount") as? Int ?: 0),
                "nonOrganikEmptyCount" to (firebaseData?.get("nonOrganikEmptyCount") as? Int ?: 0)
            )
        }

        // Split into current week (last 7 days, indices 28 to 34)
        val currentWeekStats = processedStats.takeLast(7)

        // Split into comparison week (7 days ending comparePeriodDays ago)
        val compareStartIndex = 28 - comparePeriodDays
        val compareWeekStats = processedStats.subList(compareStartIndex, compareStartIndex + 7)

        // Update top boxes & average capacity calculations
        updateAverages(currentWeekStats, compareWeekStats)
        updateWeeklyAverage(currentWeekStats)

        // Update weekly chart & Y-axis scale
        updateDailyChart(currentWeekStats)

        // Update comparison section card
        updateComparison(currentWeekStats, compareWeekStats)
    }

    /**
     * Calculate and display per-bin type averages from the daily stats.
     * Compares this week's daily peak capacity with previous week's to show dynamic trends.
     */
    private fun updateAverages(currentWeekStats: List<Map<String, Any>>, compareWeekStats: List<Map<String, Any>>) {
        if (currentWeekStats.isEmpty()) return

        val activeCurrentOrg = currentWeekStats.map { (it["organik"] as? Int) ?: 0 }.filter { it > 0 }
        val activeCurrentNonOrg = currentWeekStats.map { (it["nonOrganik"] as? Int) ?: 0 }.filter { it > 0 }

        val avgOrganik = if (activeCurrentOrg.isNotEmpty()) activeCurrentOrg.average().toInt() else 0
        val avgNonOrganik = if (activeCurrentNonOrg.isNotEmpty()) activeCurrentNonOrg.average().toInt() else 0

        binding.tvOrganikAvg.animateCount(0, avgOrganik)
        binding.tvNonOrganikAvg.animateCount(0, avgNonOrganik)

        val activeCompareOrg = compareWeekStats.map { (it["organik"] as? Int) ?: 0 }.filter { it > 0 }
        val activeCompareNonOrg = compareWeekStats.map { (it["nonOrganik"] as? Int) ?: 0 }.filter { it > 0 }

        val prevAvgOrganik = if (activeCompareOrg.isNotEmpty()) activeCompareOrg.average().toInt() else 0
        val prevAvgNonOrganik = if (activeCompareNonOrg.isNotEmpty()) activeCompareNonOrg.average().toInt() else 0

        val diffOrg = avgOrganik - prevAvgOrganik
        val diffNonOrg = avgNonOrganik - prevAvgNonOrganik

        binding.tvOrganikTrend.text = when {
            diffOrg < 0 -> "Turun ${abs(diffOrg)}%"
            diffOrg > 0 -> "Naik ${diffOrg}%"
            else -> "Stabil"
        }

        binding.tvNonOrganikTrend.text = when {
            diffNonOrg < 0 -> "Turun ${abs(diffNonOrg)}%"
            diffNonOrg > 0 -> "Naik ${diffNonOrg}%"
            else -> "Stabil"
        }
    }

    /**
     * Calculate the overall weekly average across both bin types.
     * Formula: average of all daily peak capacities.
     */
    private fun updateWeeklyAverage(stats: List<Map<String, Any>>) {
        if (stats.isEmpty()) {
            binding.tvWeeklyAvg.text = "0%"
            return
        }

        val activeValues = stats.flatMap { entry ->
            listOf(
                (entry["organik"] as? Int) ?: 0,
                (entry["nonOrganik"] as? Int) ?: 0
            )
        }.filter { it > 0 }

        val weeklyAvg = if (activeValues.isNotEmpty()) activeValues.average().toInt() else 0
        binding.tvWeeklyAvg.animateCount(0, weeklyAvg, "%")
    }

    // ==========================================
    // Chart Rendering - Daily Bar Chart
    // ==========================================

    /**
     * Build the 7-day bar chart dynamically.
     * Draw Y-axis labels and scales the bar heights proportionally.
     */
    private fun updateDailyChart(currentWeekStats: List<Map<String, Any>>) {
        binding.chartContainer.removeAllViews()
        binding.yAxisContainer.removeAllViews()

        val maxHeight = 120 // dp

        // 1. Calculate Y-Axis boundaries
        val roundedMax: Int
        val tickValues: List<String>

        if (isCapacityMode) {
            roundedMax = 100
            tickValues = listOf("100%", "75%", "50%", "25%", "0%")
            binding.tvChartTitle.text = "Grafik Kapasitas (7 Hari)"
            binding.tvLegendOrganik.text = " Organik (%)   "
            binding.tvLegendNonOrganik.text = " Non-Organik (%)"
        } else {
            val maxOrg = currentWeekStats.maxOf { (it["organikEmptyCount"] as? Int) ?: 0 }
            val maxNonOrg = currentWeekStats.maxOf { (it["nonOrganikEmptyCount"] as? Int) ?: 0 }
            val maxCount = maxOf(maxOrg, maxNonOrg)
            val baseMax = maxOf(4, maxCount)
            // Round up to multiple of 4 to have nice integer subdivisions
            roundedMax = if (baseMax % 4 == 0) baseMax else ((baseMax / 4) + 1) * 4

            val tick4 = roundedMax
            val tick3 = (roundedMax * 3) / 4
            val tick2 = (roundedMax * 2) / 4
            val tick1 = roundedMax / 4

            tickValues = listOf("$tick4", "$tick3", "$tick2", "$tick1", "0")
            binding.tvChartTitle.text = "Grafik Pengosongan (7 Hari)"
            binding.tvLegendOrganik.text = " Organik (kali)   "
            binding.tvLegendNonOrganik.text = " Non-Organik (kali)"
        }

        // 2. Render Y-Axis Ticks
        for (i in 0 until 4) {
            val label = TextView(requireContext()).apply {
                text = tickValues[i]
                textSize = 9f
                setTextColor(resources.getColor(R.color.gray_400, null))
                gravity = Gravity.END or Gravity.TOP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            binding.yAxisContainer.addView(label)
        }

        val zeroLabel = TextView(requireContext()).apply {
            text = tickValues.last()
            textSize = 9f
            setTextColor(resources.getColor(R.color.gray_400, null))
            gravity = Gravity.END or Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        binding.yAxisContainer.addView(zeroLabel)

        // Spacer at bottom of Y-axis matching the day label padding & text height
        val bottomSpacer = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                18.dpToPx()
            )
        }
        binding.yAxisContainer.addView(bottomSpacer)

        // 3. Render Chart Bars
        for (i in 0 until 7) {
            val entry = currentWeekStats[i]
            val dateKey = entry["tanggal"] as String
            val dayName = getDayLabel(dateKey)

            val organikVal = if (isCapacityMode) {
                (entry["organik"] as? Int) ?: 0
            } else {
                (entry["organikEmptyCount"] as? Int) ?: 0
            }

            val nonOrganikVal = if (isCapacityMode) {
                (entry["nonOrganik"] as? Int) ?: 0
            } else {
                (entry["nonOrganikEmptyCount"] as? Int) ?: 0
            }

            val orgBarHeight = if (roundedMax > 0) (maxHeight * organikVal / roundedMax) else 0
            val nonOrgBarHeight = if (roundedMax > 0) (maxHeight * nonOrganikVal / roundedMax) else 0

            val dayColumn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }

            val barGroup = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            // Organik bar (green)
            val orgBar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    12.dpToPx(),
                    0 // Start from 0 for animation
                ).apply { marginEnd = 2.dpToPx() }
                setBackgroundColor(resources.getColor(R.color.emerald_600, null))
            }

            // Non-Organik bar (blue)
            val nonOrgBar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    12.dpToPx(),
                    0 // Start from 0 for animation
                )
                setBackgroundColor(resources.getColor(R.color.secondary, null))
            }

            // Animate organic bar
            android.animation.ValueAnimator.ofInt(0, orgBarHeight.dpToPx()).apply {
                duration = 1000
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animation ->
                    val param = orgBar.layoutParams
                    param.height = animation.animatedValue as Int
                    orgBar.layoutParams = param
                }
                start()
            }

            // Animate non-organic bar
            android.animation.ValueAnimator.ofInt(0, nonOrgBarHeight.dpToPx()).apply {
                duration = 1000
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animation ->
                    val param = nonOrgBar.layoutParams
                    param.height = animation.animatedValue as Int
                    nonOrgBar.layoutParams = param
                }
                start()
            }

            barGroup.addView(orgBar)
            barGroup.addView(nonOrgBar)

            val label = TextView(requireContext()).apply {
                text = dayName
                textSize = 9f
                setTextColor(resources.getColor(R.color.gray_400, null))
                gravity = Gravity.CENTER
                setPadding(0, 4.dpToPx(), 0, 0)
            }

            dayColumn.addView(barGroup)
            dayColumn.addView(label)
            binding.chartContainer.addView(dayColumn)
        }
    }

    // ==========================================
    // Comparison Details Logic
    // ==========================================
    private fun updateComparison(currentWeekStats: List<Map<String, Any>>, compareWeekStats: List<Map<String, Any>>) {
        val periodName = when (comparePeriodDays) {
            7 -> "minggu lalu"
            14 -> "2 minggu lalu"
            28 -> "bulan lalu"
            else -> "minggu lalu"
        }

        if (isCapacityMode) {
            // Compare average Capacity (%)
            val currentAvgOrg = currentWeekStats.map { (it["organik"] as? Int) ?: 0 }.average()
            val compareAvgOrg = compareWeekStats.map { (it["organik"] as? Int) ?: 0 }.average()

            val currentAvgNonOrg = currentWeekStats.map { (it["nonOrganik"] as? Int) ?: 0 }.average()
            val compareAvgNonOrg = compareWeekStats.map { (it["nonOrganik"] as? Int) ?: 0 }.average()

            binding.tvCompareOrganikValue.animateCount(0, currentAvgOrg.toInt(), "%")
            binding.tvCompareNonOrganikValue.animateCount(0, currentAvgNonOrg.toInt(), "%")

            val diffOrg = currentAvgOrg - compareAvgOrg
            val diffNonOrg = currentAvgNonOrg - compareAvgNonOrg

            // Organic capacity comparison
            binding.tvCompareOrganikTrend.text = "${if (diffOrg >= 0) "+" else ""}${diffOrg.toInt()}% dibanding $periodName"
            if (diffOrg < 0) {
                binding.tvCompareOrganikTrend.setTextColor(resources.getColor(R.color.emerald_600, null))
            } else if (diffOrg > 0) {
                binding.tvCompareOrganikTrend.setTextColor(resources.getColor(R.color.red_500, null))
            } else {
                binding.tvCompareOrganikTrend.setTextColor(resources.getColor(R.color.gray_400, null))
            }

            // Non-organic capacity comparison
            binding.tvCompareNonOrganikTrend.text = "${if (diffNonOrg >= 0) "+" else ""}${diffNonOrg.toInt()}% dibanding $periodName"
            if (diffNonOrg < 0) {
                binding.tvCompareNonOrganikTrend.setTextColor(resources.getColor(R.color.emerald_600, null))
            } else if (diffNonOrg > 0) {
                binding.tvCompareNonOrganikTrend.setTextColor(resources.getColor(R.color.red_500, null))
            } else {
                binding.tvCompareNonOrganikTrend.setTextColor(resources.getColor(R.color.gray_400, null))
            }

        } else {
            // Compare total Empty Counts
            val currentTotalOrg = currentWeekStats.sumOf { (it["organikEmptyCount"] as? Int) ?: 0 }
            val compareTotalOrg = compareWeekStats.sumOf { (it["organikEmptyCount"] as? Int) ?: 0 }

            val currentTotalNonOrg = currentWeekStats.sumOf { (it["nonOrganikEmptyCount"] as? Int) ?: 0 }
            val compareTotalNonOrg = compareWeekStats.sumOf { (it["nonOrganikEmptyCount"] as? Int) ?: 0 }

            binding.tvCompareOrganikValue.animateCount(0, currentTotalOrg, " kali")
            binding.tvCompareNonOrganikValue.animateCount(0, currentTotalNonOrg, " kali")

            val diffOrg = currentTotalOrg - compareTotalOrg
            val diffNonOrg = currentTotalNonOrg - compareTotalNonOrg

            // Organic empty count comparison
            binding.tvCompareOrganikTrend.text = "${if (diffOrg >= 0) "+" else ""}$diffOrg kali dibanding $periodName"
            if (diffOrg > 0) {
                binding.tvCompareOrganikTrend.setTextColor(resources.getColor(R.color.emerald_600, null))
            } else if (diffOrg < 0) {
                binding.tvCompareOrganikTrend.setTextColor(resources.getColor(R.color.red_500, null))
            } else {
                binding.tvCompareOrganikTrend.setTextColor(resources.getColor(R.color.gray_400, null))
            }

            // Non-organic empty count comparison
            binding.tvCompareNonOrganikTrend.text = "${if (diffNonOrg >= 0) "+" else ""}$diffNonOrg kali dibanding $periodName"
            if (diffNonOrg > 0) {
                binding.tvCompareNonOrganikTrend.setTextColor(resources.getColor(R.color.emerald_600, null))
            } else if (diffNonOrg < 0) {
                binding.tvCompareNonOrganikTrend.setTextColor(resources.getColor(R.color.red_500, null))
            } else {
                binding.tvCompareNonOrganikTrend.setTextColor(resources.getColor(R.color.gray_400, null))
            }
        }
    }

    // ==========================================
    // Date & Local Day Helpers
    // ==========================================
    private fun getFixedWeeksDates(weeksCount: Int): List<String> {
        val dates = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        // Set to Monday of the current week
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        // Go back (weeksCount - 1) weeks
        calendar.add(Calendar.WEEK_OF_YEAR, -(weeksCount - 1))
        
        // Generate days chronologically from that Monday up to the end of the current week
        val totalDays = weeksCount * 7
        for (i in 0 until totalDays) {
            dates.add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }

    private fun getDayLabel(dateString: String): String {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString) ?: return ""
            val cal = Calendar.getInstance().apply { time = date }
            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "Min"
                Calendar.MONDAY -> "Sen"
                Calendar.TUESDAY -> "Sel"
                Calendar.WEDNESDAY -> "Rab"
                Calendar.THURSDAY -> "Kam"
                Calendar.FRIDAY -> "Jum"
                Calendar.SATURDAY -> "Sab"
                else -> ""
            }
        } catch (e: Exception) {
            return ""
        }
    }

    // ==========================================
    // Lifecycle - Cleanup
    // ==========================================
    override fun onDestroyView() {
        statsListener?.let { FirebaseManager.removeStatsListener(it) }
        penuhListener?.let { FirebaseManager.removePenuhListener(it) }
        super.onDestroyView()
    }

    // ==========================================
    // Utility
    // ==========================================
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun TextView.animateCount(start: Int, end: Int, suffix: String = "") {
        val animator = android.animation.ValueAnimator.ofInt(start, end)
        animator.duration = 1200 // 1.2 detik agar terlihat mulus dan keren
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            this.text = "$value$suffix"
        }
        animator.start()
    }
}
