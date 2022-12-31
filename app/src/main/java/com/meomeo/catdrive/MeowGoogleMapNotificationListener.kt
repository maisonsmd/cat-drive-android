package com.meomeo.catdrive

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.meomeo.catdrive.lib.NavigationNotification
import com.meomeo.catdrive.service.NavigationListener

class MeowGoogleMapNotificationListener : NavigationListener() {
    init {
        enabled = true
    }

    override fun onNavigationNotificationUpdated(navNotification: NavigationNotification) {
        super.onNavigationNotificationUpdated(navNotification)
    }
}