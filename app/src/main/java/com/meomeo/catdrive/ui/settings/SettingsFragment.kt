package com.meomeo.catdrive.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.meomeo.catdrive.MainActivity
import com.meomeo.catdrive.R
import com.meomeo.catdrive.service.BroadcastService

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceEnableSwitch = preferenceScreen.findPreference<SwitchPreferenceCompat>("service_enable")
        serviceEnableSwitch!!.isChecked = (activity as MainActivity).isBroadcastServiceRunning()

        serviceEnableSwitch!!.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                (activity as MainActivity).startBroadcastService()
            } else {
                (activity as MainActivity).stopBroadcastService()
            }
            return@setOnPreferenceChangeListener true
        }
    }
}