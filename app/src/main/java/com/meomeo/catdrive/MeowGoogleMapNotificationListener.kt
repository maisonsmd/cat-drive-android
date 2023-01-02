package com.meomeo.catdrive

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.meomeo.catdrive.lib.NavigationNotification
import com.meomeo.catdrive.service.NavigationListener
import timber.log.Timber
import kotlin.reflect.jvm.internal.impl.load.java.BuiltinSpecialPropertiesKt

class MeowGoogleMapNotificationListener : NavigationListener() {
    private val mBinder = LocalBinder()

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): MeowGoogleMapNotificationListener = this@MeowGoogleMapNotificationListener
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Bind by this process
        if (intent?.action == "${BuildConfig.APPLICATION_ID}.local_bind") {
            return mBinder
        }
        // Bind by OS
        return super.onBind(intent)
    }

    init {
        enabled = true
    }

    fun enable(value: Boolean) {
        Timber.i("enable called: $value")
        enabled = value
    }

    override fun onNavigationNotificationUpdated(navNotification: NavigationNotification) {
        Timber.i("mmmm updated ${navNotification.navigationData.nextDirection.toString()}")

        val intent = Intent("${BuildConfig.APPLICATION_ID}.INTENT_NAVIGATION_DATA")
        intent.putExtra("navigation_data_update", navNotification.navigationData)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }
}