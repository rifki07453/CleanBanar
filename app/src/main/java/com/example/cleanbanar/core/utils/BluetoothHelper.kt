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
                var connected = false
                var lastErrorMessage = ""

                // TAHAP 1: Mencoba koneksi standar (Secure SPP)
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    bluetoothSocket?.connect()
                    connected = true
                    Log.d(TAG, "Terhubung via metode standar (Secure)")
                } catch (e1: IOException) {
                    Log.w(TAG, "Metode standar gagal: ${e1.message}. Mencoba Insecure...")
                    
                    // TAHAP 2: Mencoba Insecure Socket (Seringkali memperbaiki masalah pairing di Android baru)
                    try {
                        bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                        bluetoothSocket?.connect()
                        connected = true
                        Log.d(TAG, "Terhubung via metode Insecure")
                    } catch (e2: IOException) {
                        Log.w(TAG, "Metode Insecure gagal: ${e2.message}. Mencoba Fallback Reflection...")
                        
                        // TAHAP 3: Metode Refleksi (Solusi pamungkas untuk error 'read ret -1' atau 'socket closed')
                        try {
                            bluetoothSocket = device::class.java.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                                .invoke(device, 1) as BluetoothSocket
                            bluetoothSocket?.connect()
                            connected = true
                            Log.d(TAG, "Terhubung via metode Fallback Reflection")
                        } catch (e3: Exception) {
                            lastErrorMessage = e3.message ?: "Semua metode koneksi gagal"
                        }
                    }
                }

                if (connected) {
                    outputStream = bluetoothSocket?.outputStream
                    onResult(true, "Terhubung ke ${device.name}")
                } else {
                    onResult(false, "Gagal terhubung: $lastErrorMessage")
                    close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Koneksi gagal total: ${e.message}")
                onResult(false, "Error Fatal: ${e.message}")
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
     * Membaca string dari inputStream sampai menemukan karakter newline (\n) atau timeout.
     */
    fun readStringUntilNewline(timeoutMillis: Long = 8000): String? {
        val inputStream = bluetoothSocket?.inputStream ?: return null
        val buffer = ByteArray(1024)
        val stringBuilder = StringBuilder()
        val startTime = System.currentTimeMillis()

        try {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                if (inputStream.available() > 0) {
                    val bytes = inputStream.read(buffer)
                    val chunk = String(buffer, 0, bytes)
                    stringBuilder.append(chunk)
                    
                    if (stringBuilder.contains("\n")) {
                        return stringBuilder.toString().trim()
                    }
                } else {
                    Thread.sleep(50)
                }
            }
            Log.w(TAG, "Timeout membaca data dari Bluetooth")
        } catch (e: Exception) {
            Log.e(TAG, "Error saat membaca data: ${e.message}")
        }
        return null // Timeout atau error
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
