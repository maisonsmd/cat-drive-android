package com.meomeo.catdrive

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.meomeo.catdrive.databinding.ActivityMainBinding
import com.meomeo.catdrive.lib.Intents
import com.meomeo.catdrive.service.BroadcastService
import com.meomeo.catdrive.ui.ActivityViewModel
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mViewModel: ActivityViewModel

    private val mLocationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
                Timber.i("Precise location access granted")
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
                Timber.i("Approx location access granted")
            }
            else -> {
                // No location access granted.
                Timber.w("No location access granted")
            }
        }.also {
            mViewModel.permissionUpdatedTimestamp.value = System.currentTimeMillis()
        }
    }

    fun haveNotificationsAccessPermission(): Boolean {
        Settings.Secure.getString(
            this.contentResolver, "enabled_notification_listeners"
        ).also {
            return MeowGoogleMapNotificationListener::class.qualifiedName.toString() in it
        }
    }

    fun haveNotificationPostingPermission(): Boolean {
        return true
    }

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

    fun allPermissionsGranted(): Boolean {
        return haveNotificationsAccessPermission() && haveNotificationPostingPermission() && haveLocationAccessPermission()
    }

    private fun checkPermissions() {
        if (!allPermissionsGranted()) {
            Toast.makeText(this, "Some permissions are not granted, see Settings page", Toast.LENGTH_LONG).show()
        }
    }

    fun requestNotificationAccessPermission() {
        @Suppress("DEPRECATION") this.startActivityForResult(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
            0
        )
    }

    fun requestLocationAccessPermission() {
        if (haveLocationAccessPermission()) return

        mLocationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @Suppress("DEPRECATION")
    fun <T> isServiceRunning(service: Class<T>): Boolean {
        return (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == service.name }
    }

    fun isBroadcastServiceRunning(): Boolean {
        return isServiceRunning(BroadcastService::class.java)
    }

    fun startBroadcastService() {
        Timber.i("start services")
        val action = Intents.EnableServices
        startService(Intent(applicationContext, BroadcastService::class.java).apply { setAction(action) })
        startService(Intent(
            applicationContext, MeowGoogleMapNotificationListener::class.java
        ).apply { setAction(action) })
    }

    fun stopBroadcastService() {
        Timber.i("stop services")
        val action = Intents.DisableServices
        // Expect the target service to stop itself
        startService(Intent(
            applicationContext, BroadcastService::class.java
        ).apply { setAction(action) })
        startService(Intent(
            applicationContext, MeowGoogleMapNotificationListener::class.java
        ).apply { setAction(action) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(object : Timber.DebugTree() {
            override fun createStackElementTag(element: StackTraceElement): String {
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
                R.id.navigation_home, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        mViewModel = ViewModelProvider(this)[com.meomeo.catdrive.ui.ActivityViewModel::class.java]
    }

    override fun onStart() {
        super.onStart()
        checkPermissions()
    }
}