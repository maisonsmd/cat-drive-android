package com.meomeo.catdrive.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.meomeo.catdrive.MeowGoogleMapNotificationListener
import com.meomeo.catdrive.lib.Intents

class PermissionCheck {
    companion object {
        fun checkNotificationsAccessPermission(context: Context): Boolean {
            Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ).also {
                return MeowGoogleMapNotificationListener::class.qualifiedName.toString() in it
            }
        }

        // TODO: From API 33 we must request permission for notification posting
        fun checkNotificationPostingPermission(context: Context): Boolean {
            return true
        }

        fun checkLocationAccessPermission(context: Context): Boolean {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return true
        }

        fun checkBluetoothAccessPermission(context: Context): Boolean {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun allPermissionsGranted(context: Context): Boolean {
            return checkNotificationsAccessPermission(context)
                    && checkNotificationPostingPermission(context)
                    && checkLocationAccessPermission(context)
        }

        fun requestLocationAccessPermission(activity: AppCompatActivity) {
            if (checkLocationAccessPermission(activity)) return
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                100
            )
        }

        fun requestNotificationAccessPermission(activity: AppCompatActivity) {
            @Suppress("DEPRECATION") activity.startActivityForResult(
                Intent(Intents.OpenNotificationListenerSettings),
                0
            )
        }

        fun requestBluetoothAccessPermissions(activity: AppCompatActivity) {
            if (checkBluetoothAccessPermission(activity)) return
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                100
            )
        }
    }
}
