package com.example.cleanbanar.core.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * BluetoothHelper - Utilitas untuk komunikasi Serial Bluetooth (SPP) dengan ESP32.
 */
class BluetoothHelper {

    companion object {
        private const val TAG = "BluetoothHelper"
        // ID Layanan Serial Port Standar (SPP UUID)
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    /**
     * Mengambil daftar perangkat yang sudah terpasang (paired) untuk mencari ESP32.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Menghubungkan aplikasi ke perangkat Bluetooth tertentu secara asinkron.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                // Matikan pencarian perangkat agar proses koneksi lebih cepat
                bluetoothAdapter?.cancelDiscovery()

                // Mencoba koneksi standar (SPP UUID)
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                try {
                    bluetoothSocket?.connect()
                } catch (e: IOException) {
                    Log.w(TAG, "Metode standar gagal, mencoba metode fallback (reflection)...")
                    // Fallback: Menggunakan refleksi untuk memanggil metode tersembunyi pada Android
                    // Ini sering memperbaiki error "read ret -1" atau "socket closed" pada ESP32
                    bluetoothSocket = device::class.java.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                    bluetoothSocket?.connect()
                }

                outputStream = bluetoothSocket?.outputStream
                onResult(true, "Terhubung ke ${device.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Koneksi gagal total: ${e.message}")
                onResult(false, "Gagal terhubung: ${e.message}")
                close()
            }
        }.start()
    }

    /**
     * Mengirim data string (seperti SSID/Password) ke ESP32 melalui stream.
     */
    fun sendData(data: String): Boolean {
        return try {
            outputStream?.write(data.toByteArray())
            outputStream?.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Gagal mengirim data: ${e.message}")
            false
        }
    }

    /**
     * Menutup koneksi bluetooth dan stream data.
     */
    fun close() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Gagal menutup koneksi: ${e.message}")
        }
        outputStream = null
        bluetoothSocket = null
    }
}
