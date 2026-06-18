package com.example.cleanbanar.features.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cleanbanar.core.data.DeviceModel
import com.example.cleanbanar.databinding.ItemDeviceAdminBinding

class DeviceAdapter(
    private var devices: List<DeviceModel>,
    private val onItemClick: (DeviceModel) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    fun updateData(newDevices: List<DeviceModel>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeviceAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class ViewHolder(private val binding: ItemDeviceAdminBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: DeviceModel) {
            binding.tvDeviceName.text = device.nama
            binding.tvDeviceId.text = "ID: ${device.id}"

            val isOnline = device.statusKoneksi == "ONLINE"
            if (isOnline) {
                binding.tvDeviceStatus.text = "ONLINE"
                binding.tvDeviceStatus.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, com.example.cleanbanar.R.color.primary))
                binding.dotStatus.setBackgroundResource(com.example.cleanbanar.R.drawable.dot_timeline_green)
                (binding.dotStatus.parent as android.widget.LinearLayout).setBackgroundResource(com.example.cleanbanar.R.drawable.bg_badge_green)
            } else {
                binding.tvDeviceStatus.text = "OFFLINE"
                binding.tvDeviceStatus.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, com.example.cleanbanar.R.color.red_600))
                binding.dotStatus.setBackgroundResource(com.example.cleanbanar.R.drawable.dot_timeline_red)
                (binding.dotStatus.parent as android.widget.LinearLayout).setBackgroundResource(com.example.cleanbanar.R.drawable.badge_red_bg)
            }

            com.example.cleanbanar.core.utils.AnimationUtils.applyBouncyTouchEffect(binding.root)
            binding.root.setOnClickListener {
                onItemClick(device)
            }
        }
    }
}
