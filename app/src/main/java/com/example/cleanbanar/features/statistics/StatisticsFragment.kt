package com.example.cleanbanar.features.statistics

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentStatisticsBinding
import com.google.firebase.database.ValueEventListener

class StatisticsFragment : BaseFragment<FragmentStatisticsBinding>() {

    private var statsListener: ValueEventListener? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentStatisticsBinding {
        return FragmentStatisticsBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        // Initial empty state
    }

    override fun observeData() {
        statsListener = FirebaseManager.listenDailyStats { stats ->
            if (!isAdded) return@listenDailyStats
            updateAverages(stats)
            updateDailyChart(stats)
        }
    }

    private fun updateAverages(stats: List<Map<String, Any>>) {
        if (stats.isEmpty()) return

        val avgOrganik = stats.map { (it["organik"] as? Int) ?: 0 }.average().toInt()
        val avgNonOrganik = stats.map { (it["nonOrganik"] as? Int) ?: 0 }.average().toInt()

        binding.tvOrganikAvg.text = "$avgOrganik%"
        binding.tvNonOrganikAvg.text = "$avgNonOrganik%"

        // Animate bar widths
        binding.barOrganik.post {
            val parentWidth = (binding.barOrganik.parent as View).width
            binding.barOrganik.layoutParams = binding.barOrganik.layoutParams.apply {
                width = (parentWidth * avgOrganik / 100)
            }
        }
        binding.barNonOrganik.post {
            val parentWidth = (binding.barNonOrganik.parent as View).width
            binding.barNonOrganik.layoutParams = binding.barNonOrganik.layoutParams.apply {
                width = (parentWidth * avgNonOrganik / 100)
            }
        }
    }

    private fun updateDailyChart(stats: List<Map<String, Any>>) {
        binding.chartContainer.removeAllViews()

        val maxHeight = 120 // dp
        val dayLabels = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

        for (i in 0 until 7) {
            val organikVal = if (i < stats.size) (stats[i]["organik"] as? Int) ?: 0 else 0
            val nonOrganikVal = if (i < stats.size) (stats[i]["nonOrganik"] as? Int) ?: 0 else 0

            val dayColumn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }

            // Bar group (two bars side by side)
            val barGroup = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            // Organik bar
            val orgBar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    12.dpToPx(),
                    (maxHeight * organikVal / 100).dpToPx()
                ).apply { marginEnd = 2.dpToPx() }
                setBackgroundColor(resources.getColor(R.color.emerald_600, null))
            }

            // Non-Organik bar
            val nonOrgBar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    12.dpToPx(),
                    (maxHeight * nonOrganikVal / 100).dpToPx()
                )
                setBackgroundColor(resources.getColor(R.color.secondary, null))
            }

            barGroup.addView(orgBar)
            barGroup.addView(nonOrgBar)

            // Day label
            val label = TextView(requireContext()).apply {
                text = if (i < dayLabels.size) dayLabels[i] else ""
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

    override fun onDestroyView() {
        statsListener?.let { FirebaseManager.removeStatsListener(it) }
        super.onDestroyView()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
