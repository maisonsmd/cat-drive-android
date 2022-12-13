package com.meomeo.catdrive.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import com.meomeo.catdrive.MainActivity
import timber.log.Timber
import java.util.*

class BroadcastService : Service() {
    private var mTimer: Timer? = null

    internal class UpdateBallTask : TimerTask() {
        override fun run() {
            //calculate the new position of myBall
            Timber.v("elapsed")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        TODO("Return the communication channel to the service.")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mTimer?.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.v("onStartCommand: $intent")
        startForeground(1338, buildForegroundNotification());

        val updateBall: TimerTask = UpdateBallTask()
        mTimer = Timer()
        mTimer!!.scheduleAtFixedRate(updateBall, 0, 1000)

        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(): Notification? {
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
            .build()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String? {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

}