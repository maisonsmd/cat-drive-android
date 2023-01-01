package com.meomeo.catdrive

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.meomeo.catdrive.lib.NavigationNotification
import com.meomeo.catdrive.service.NavigationListener
import timber.log.Timber

class MeowGoogleMapNotificationListener : NavigationListener() {
    init {
        enabled = true
    }

    override fun onNavigationNotificationUpdated(navNotification: NavigationNotification) {
        Timber.i("mmmm updated ${navNotification.mNavigationData.nextDirection.toString()}")

        val intent = Intent("${BuildConfig.APPLICATION_ID}.INTENT_NAVIGATION_DATA")
        intent.putExtra("navigation_data_update", navNotification.mNavigationData)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }
}