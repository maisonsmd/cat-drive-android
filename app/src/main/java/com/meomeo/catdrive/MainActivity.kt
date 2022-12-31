package com.meomeo.catdrive

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.meomeo.catdrive.databinding.ActivityMainBinding
import com.meomeo.catdrive.service.BroadcastService
import timber.log.Timber
import timber.log.Timber.DebugTree


class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding

    private fun haveNotificationsAccess(): Boolean {
        Settings.Secure.getString(
            this.contentResolver, "enabled_notification_listeners"
        ).also {
            Timber.v("Checking if service has notification access")
            return MeowGoogleMapNotificationListener::class.qualifiedName.toString() in it
        }
    }

    @Suppress("DEPRECATION")
    public fun <T> isServiceRunning(service: Class<T>): Boolean {
        return (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Integer.MAX_VALUE)
            .any { it -> it.service.className == service.name }
    }

    public fun isBroadcastServiceRunning(): Boolean {
        return isServiceRunning(BroadcastService::class.java)
    }

    private fun checkNotificationsAccess() {
        if (!haveNotificationsAccess()) {
            showMissingNotificationsAccessSnackbar()
            Timber.e("No notification access for ${MeowGoogleMapNotificationListener::class.qualifiedName}")
        } else {
            Timber.i("Notification access granted for ${MeowGoogleMapNotificationListener::class.qualifiedName}")
        }
    }

    private fun showMissingNotificationsAccessSnackbar() {
        Snackbar.make(findViewById(android.R.id.content), R.string.missing_permissions, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.action_settings) {
                this.startActivityForResult(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), 0)
            }.show()
    }

    public fun startBroadcastService() {
        startService(Intent(applicationContext, BroadcastService::class.java))
    }

    fun stopBroadcastService() {
        stopService(Intent(applicationContext, BroadcastService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(object : DebugTree() {
            @Nullable
            override fun createStackElementTag(element: StackTraceElement): String? {
                return super.createStackElementTag(element) + ":" + element.lineNumber
            }
        })

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        val navView: BottomNavigationView = mBinding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_notifications, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onStart() {
        super.onStart()
        checkNotificationsAccess()
    }
}