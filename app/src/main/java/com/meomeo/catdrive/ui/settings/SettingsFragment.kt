package com.meomeo.catdrive.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.meomeo.catdrive.R
import com.meomeo.catdrive.service.BroadcastService
import timber.log.Timber

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    @Suppress("DEPRECATION")
    private fun <T: Any> isServiceRunning(serviceClass: Class<T>) : Boolean {
        val manager = requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        for (service in runningServices)
            if (serviceClass.name.equals(service.service.className))
                return true
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceEnableSwitch = preferenceScreen.findPreference<SwitchPreferenceCompat>("service_enable")
        serviceEnableSwitch!!.isChecked = isServiceRunning(BroadcastService::class.java)
        serviceEnableSwitch!!.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                Timber.e("start service")
                requireActivity().startForegroundService(Intent(activity, BroadcastService::class.java))
            } else {
                Timber.e("stop service")
                requireActivity().stopService(Intent(activity, BroadcastService::class.java))
            }
            return@setOnPreferenceChangeListener true
        }
    }
}