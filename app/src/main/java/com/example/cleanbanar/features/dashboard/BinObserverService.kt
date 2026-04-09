package com.example.cleanbanar.features.dashboard

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.cleanbanar.R
import com.example.cleanbanar.core.data.AuthManager
import com.example.cleanbanar.features.dashboard.MainActivity

class BinObserverService : Service() {

    private lateinit var authManager: AuthManager

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = authManager.getUserId()
        
        // Start foreground with "Monitoring active" notification
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start the observer logic (re-using the singleton for state tracking)
        BinObserver.start(userId)

        return START_STICKY
    }

    override fun onDestroy() {
        BinObserver.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CleanBanar Monitoring")
            .setContentText("Monitoring aktif di latar belakang")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bin Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Saluran untuk layanan pemantauan tong sampah"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "BinObserverChannel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, BinObserverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BinObserverService::class.java)
            context.stopService(intent)
        }
    }
}
