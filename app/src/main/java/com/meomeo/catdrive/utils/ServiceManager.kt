package com.meomeo.catdrive.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.meomeo.catdrive.MeowGoogleMapNotificationListener
import com.meomeo.catdrive.lib.Intents
import com.meomeo.catdrive.service.BroadcastService
import timber.log.Timber

class ServiceManager {
    companion object {
        fun startBroadcastService(activity: AppCompatActivity) {
            Timber.i("start services")
            val action = Intents.EnableServices
            activity.startService(Intent(activity, BroadcastService::class.java).apply { setAction(action) })
            activity.startService(
                Intent(
                    activity, MeowGoogleMapNotificationListener::class.java
                ).apply { setAction(action) })
        }

        fun stopBroadcastService(activity: AppCompatActivity) {
            Timber.i("stop services")
            val action = Intents.DisableServices
            // Expect the target service to stop itself
            activity.startService(
                Intent(
                    activity, BroadcastService::class.java
                ).apply { setAction(action) })
            activity.startService(
                Intent(
                    activity, MeowGoogleMapNotificationListener::class.java
                ).apply { setAction(action) })
        }

        @Suppress("DEPRECATION")
        private fun <T> isServiceRunning(activity: AppCompatActivity, service: Class<T>): Boolean {
            return (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(
                Integer.MAX_VALUE
            )
                .any { it.service.className == service.name }
        }

        fun isBroadcastServiceRunning(activity: AppCompatActivity): Boolean {
            return isServiceRunning(activity, BroadcastService::class.java)
        }

    }
}