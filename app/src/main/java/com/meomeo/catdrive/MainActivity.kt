package com.meomeo.catdrive

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.meomeo.catdrive.databinding.ActivityMainBinding
import com.meomeo.catdrive.ui.ActivityViewModel
import com.meomeo.catdrive.ui.BtDevice
import com.meomeo.catdrive.ui.DeviceSelectionActivity
import com.meomeo.catdrive.utils.PermissionCheck
import com.meomeo.catdrive.utils.ServiceManager
import timber.log.Timber
import java.security.Provider.Service

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mViewModel: ActivityViewModel

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // notify settings fragment to update the permissions to view
        mViewModel.permissionUpdatedTimestamp.value = System.currentTimeMillis()
    }

    @SuppressLint("MissingPermission")
    private val mDeviceSelectionRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val device = it.data!!.getParcelableExtra<BtDevice>("device")!!
            Timber.d(device.uuids.toString())
            if (PermissionCheck.checkBluetoothScanPermission(this)) {
                ServiceManager.requestConnectDevice(this, device)
            } else {
                Toast.makeText(this, "No bluetooth permission or BT not enabled!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        if (!PermissionCheck.allPermissionsGranted(this)) {
            Toast.makeText(this, "Some permissions are not granted, see Settings page", Toast.LENGTH_LONG).show()
        }
    }

    fun openDeviceSelectionActivity() {
        mDeviceSelectionRequest.launch(Intent(applicationContext, DeviceSelectionActivity::class.java))
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

        mViewModel = ViewModelProvider(this)[ActivityViewModel::class.java]
    }

    override fun onStart() {
        super.onStart()
        checkPermissions()
    }
}