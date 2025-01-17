package com.meomeo.catdrive.utils

import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.meomeo.catdrive.MeowGoogleMapNotificationListener
import com.meomeo.catdrive.lib.Intents
import com.meomeo.catdrive.service.BleService
import com.meomeo.catdrive.ui.ActivityViewModel
import timber.log.Timber

class ServiceManager {
    companion object {
        fun startBroadcastService(activity: AppCompatActivity) {
            Timber.i("start services")
            PermissionCheck.requestEnableBluetooth(activity)

            val action = Intents.ENABLE_SERVICES
            activity.startService(Intent(activity, BleService::class.java).apply { setAction(action) })
            activity.startService(
                Intent(
                    activity, MeowGoogleMapNotificationListener::class.java
                ).apply { setAction(action) })
        }

        fun requestConnectDevice(activity: AppCompatActivity, device: BluetoothDevice) {
            Timber.d("requestConnectDevice: $device")
            val action = Intents.CONNECT_DEVICE
            val intent = Intent(activity, BleService::class.java).apply {
                setAction(action)
                putExtra("device", device)
            }
            activity.startService(intent)
        }

        fun stopBroadcastService(activity: AppCompatActivity) {
            Timber.i("stop services")
            val action = Intents.DISABLE_SERVICES
            // Expect the target service to stop itself
            activity.startService(
                Intent(
                    activity, BleService::class.java
                ).apply { setAction(action) })
            activity.startService(
                Intent(
                    activity, MeowGoogleMapNotificationListener::class.java
                ).apply { setAction(action) })
        }

        @Suppress("DEPRECATION")
        private fun <T> isServiceRunningInBackground(activity: AppCompatActivity, service: Class<T>): Boolean {
            val running = (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(
                Integer.MAX_VALUE
            ).any { it.service.className == service.name }
            val viewModel = ViewModelProvider(activity)[ActivityViewModel::class.java]
            return running && viewModel.serviceRunInBackground.value == true
        }

        fun isBroadcastServiceRunningInBackground(activity: AppCompatActivity): Boolean {
            return isServiceRunningInBackground(activity, BleService::class.java)
        }

    }
}