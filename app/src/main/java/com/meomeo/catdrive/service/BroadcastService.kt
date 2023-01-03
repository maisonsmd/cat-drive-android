package com.meomeo.catdrive.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.meomeo.catdrive.BuildConfig
import com.meomeo.catdrive.MainActivity
import com.meomeo.catdrive.lib.Intents
import timber.log.Timber


class BroadcastService : Service(), LocationListener {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.v("onStartCommand: $intent")

        // TODO: add ".intent" and move to a common intent constants file
        if (intent?.action == Intents.EnableServices) {
            startForeground(1201, buildForegroundNotification())
            subscribeToLocationUpdates()
        }

        if (intent?.action == Intents.DisableServices) {
            unsubcribeFromLocationUpdates()
            stopSelf()
        }

        return START_STICKY
    }

    private fun subscribeToLocationUpdates() {
        // Copied from main activity
        fun haveLocationAccessPermission(): Boolean {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return true
        }

        if (haveLocationAccessPermission()) {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
        }
    }

    private fun unsubcribeFromLocationUpdates() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(this)
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = createNotificationChannel(
            this::class.java.simpleName,
            this::class.java.simpleName
        )
        val builder: Notification.Builder = Notification.Builder(this, channelId)
        val intent = Intent(this, MainActivity::class.java)
        intent.action = System.currentTimeMillis().toString()
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return builder.setContentTitle("CatDrive service is running")
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onLocationChanged(location: Location) {
        val speed = location.speed * 3600 / 1000
        Timber.d("$speed km/h")

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(Intents.GpsUpdate).apply {
                putExtra("speed", speed)
            }
        )
    }
}