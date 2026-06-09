package com.example.cleanbanar.features.history

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.core.data.FirebaseManager
import com.example.cleanbanar.core.ui.BaseFragment
import com.example.cleanbanar.databinding.FragmentHistoryBinding
import com.google.firebase.database.ValueEventListener

/**
 * Fragment untuk menampilkan riwayat aktivitas sistem (pengosongan sampah, peringatan penuh).
 */
class HistoryFragment : BaseFragment<FragmentHistoryBinding>() {

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var authManager: AuthManager
    private var historyListener: ValueEventListener? = null
    private var allHistoryData: List<Map<String, Any>> = emptyList()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHistoryBinding {
        return FragmentHistoryBinding.inflate(inflater, container, false)
    }

    override fun setupViews() {
        authManager = AuthManager(requireContext())
        historyAdapter = HistoryAdapter()
        binding.rvHistory.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (isAdded) {
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                } catch (e: Exception) {
                    // Abaikan jika fragment sudah dihancurkan
                }
            }, 1200)
        }

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterData(newText)
                return true
            }
        })

        binding.btnExportCsv.setOnClickListener {
            exportToCsv()
        }
    }

    override fun observeData() {
        // Mendengarkan data riwayat dari Firebase secara real-time
        historyListener = FirebaseManager.listenHistory { historyData ->
            if (isAdded) {
                if (historyData.isEmpty()) {
                    binding.rvHistory.visibility = android.view.View.GONE
                    binding.historyEmptyState.visibility = android.view.View.VISIBLE
                } else {
                    binding.rvHistory.visibility = android.view.View.VISIBLE
                    binding.historyEmptyState.visibility = android.view.View.GONE
                    
                    // Konversi data Firebase ke format yang dikenali adapter
                    val formattedHistory = historyData.map { item ->
                        val aksi = item["aksi"] as? String ?: ""
                        val tipeSampah = item["tipeSampah"] as? String ?: ""
                        val binLabel = if (tipeSampah == "organik") "Organik" else "Non-Organik"
                        
                        mapOf(
                            "type" to if (aksi == "pengosongan") (if (tipeSampah == "organik") "dikosongkan" else "dikosongkan_blue") else "penuh",
                            "bin_type" to binLabel,
                            "petugas" to (item["namaLengkap"] as? String ?: ""),
                            "capacity" to 0,
                            "timestamp" to (item["waktu"] as? Long ?: 0L)
                        )
                    }
                    allHistoryData = formattedHistory
                    filterData(binding.searchView.query.toString())
                }
            }
        }
    }

    private fun filterData(query: String?) {
        val filtered = if (query.isNullOrBlank()) {
            allHistoryData
        } else {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            allHistoryData.filter { item ->
                val type = item["bin_type"] as? String ?: ""
                val petugas = item["petugas"] as? String ?: ""
                val waktuStr = sdf.format(java.util.Date(item["timestamp"] as? Long ?: 0L))
                type.contains(query, ignoreCase = true) || 
                petugas.contains(query, ignoreCase = true) ||
                waktuStr.contains(query, ignoreCase = true)
            }
        }
        historyAdapter.updateData(filtered)
        
        if (filtered.isEmpty()) {
            binding.rvHistory.visibility = android.view.View.GONE
            binding.historyEmptyState.visibility = android.view.View.VISIBLE
        } else {
            binding.rvHistory.visibility = android.view.View.VISIBLE
            binding.historyEmptyState.visibility = android.view.View.GONE
        }
    }

    private fun exportToCsv() {
        if (allHistoryData.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "Tidak ada data untuk diekspor", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        android.widget.Toast.makeText(requireContext(), "Menyiapkan CSV...", android.widget.Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                val csvContent = StringBuilder()
                csvContent.append("Waktu,Tipe Sampah,Aksi,Petugas\n")
                
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                allHistoryData.forEach { item ->
                    val waktu = sdf.format(java.util.Date(item["timestamp"] as? Long ?: 0L))
                    val tipe = item["bin_type"] as? String ?: "-"
                    val aksi = if (item["type"] == "penuh") "Peringatan Penuh" else "Dikosongkan"
                    val petugas = item["petugas"] as? String ?: "-"
                    csvContent.append("\"$waktu\",\"$tipe\",\"$aksi\",\"$petugas\"\n")
                }

                val fileName = "Riwayat_CleanBanar_${System.currentTimeMillis()}.csv"
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = requireContext().contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { os ->
                            os.write(csvContent.toString().toByteArray())
                        }
                    }
                } else {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val file = java.io.File(dir, fileName)
                    java.io.FileOutputStream(file).use { os ->
                        os.write(csvContent.toString().toByteArray())
                    }
                }

                activity?.runOnUiThread {
                    android.widget.Toast.makeText(requireContext(), "CSV berhasil disimpan di folder Download", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(requireContext(), "Gagal menyimpan CSV", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroyView() {
        historyListener?.let { FirebaseManager.removeHistoryListener(it) }
        super.onDestroyView()
    }
}
