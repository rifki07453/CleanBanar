package com.example.cleanbanar.core.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.cleanbanar.R
import com.example.cleanbanar.features.dashboard.MainActivity
import kotlin.random.Random

object NotificationHelper {

    private const val CHANNEL_ID = "CleanBanarAlerts"
    private const val CHANNEL_NAME = "Peringatan Kapasitas Sampah"
    private const val CHANNEL_DESC = "Notifikasi penting saat kapasitas sampah penuh atau hampir penuh"

    /**
     * Membuat Notification Channel (Wajib untuk Android O ke atas).
     * Channel ini diset ke IMPORTANCE_HIGH agar notifikasi muncul sebagai Heads-up (di atas layar).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Menampilkan Heads-up Notification.
     */
    fun showNotification(context: Context, title: String, message: String) {
        // Cek izin POST_NOTIFICATIONS untuk Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Tidak memiliki izin, notifikasi tidak bisa ditampilkan
                return
            }
        }

        // Intent untuk membuka MainActivity ketika notifikasi diklik
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Membuat Builder Notifikasi
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo_cleanbanar_round) // Menggunakan logo CleanBanar
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // PRIORITY_HIGH agar muncul heads-up
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Suara, getaran, dan lampu LED default
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Menampilkan Notifikasi dengan ID unik (agar notifikasi tidak saling menimpa secara berlebihan, 
        // tapi dalam kasus ini kita gunakan random agar setiap alert baru muncul)
        with(NotificationManagerCompat.from(context)) {
            notify(Random.nextInt(), builder.build())
        }
    }
}
