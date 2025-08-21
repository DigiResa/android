package com.digiresa.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object FcmStore { @Volatile var token: String? = null }

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val registerURL = "https://beta.digiresa.com/os/register.php"
    private val CHANNEL_ID = "default_channel"

    override fun onNewToken(token: String) {
        FcmStore.token = token
        Log.d("FCM", "Token: $token")
        Thread {
            try {
                val json = JSONObject()
                    .put("token", token)
                    .put("notif_type", "all")
                    .put("device_name", android.os.Build.MODEL)
                    .put("platform", "android")
                val conn = (URL(registerURL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                conn.inputStream.close()
            } catch (_: Exception) {}
        }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        createChannelIfNeeded()

        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("openLinkModal", true)
        }

        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // ou R.drawable.ic_notification si tu l’ajoutes
            .setContentTitle("DigiResa")
            .setContentText("Clique pour lier ton appareil")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)


    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
        }
    }
}
