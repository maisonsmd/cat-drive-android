package com.meomeo.catdrive

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MeowGoogleMapNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.i("meonoti", "onNotificationPosted: $sbn")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        Log.i("meonoti", "onNotificationRemoved: $sbn")
    }
}