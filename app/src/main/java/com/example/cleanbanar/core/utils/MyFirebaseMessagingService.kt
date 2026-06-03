package com.example.cleanbanar.core.utils

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Token Baru: $token")
        // Di sini Anda bisa mengirim token ini ke backend server Anda jika diperlukan
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Ambil data Judul dan Pesan dari Notifikasi atau Data Payload
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Pesan Baru"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: ""

        Log.d("FCM_MESSAGE", "Menerima pesan: $title - $message")

        // Tampilkan notifikasi menggunakan NotificationHelper yang sudah ada
        NotificationHelper.showNotification(this, title, message)
    }
}
