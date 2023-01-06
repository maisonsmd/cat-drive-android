package com.meomeo.catdrive.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.meomeo.catdrive.MainActivity
import com.meomeo.catdrive.R
import com.meomeo.catdrive.ui.ActivityViewModel
import com.meomeo.catdrive.utils.PermissionCheck
import com.meomeo.catdrive.utils.ServiceManager

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var mServiceEnableSwitch: SwitchPreference
    private lateinit var mAccessNotificationCheckbox: CheckBoxPreference
    private lateinit var mPostNotificationCheckbox: CheckBoxPreference
    private lateinit var mAccessLocationCheckbox: CheckBoxPreference
    private lateinit var mAccessBluetoothCheckbox: CheckBoxPreference
    private lateinit var mConnectDeviceButton: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val viewModel = ViewModelProvider(requireActivity())[ActivityViewModel::class.java]

        viewModel.permissionUpdatedTimestamp.observe(viewLifecycleOwner) {
            refreshSettings()
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as MainActivity

        mServiceEnableSwitch = preferenceScreen.findPreference("enable_service")!!
        mAccessNotificationCheckbox = preferenceScreen.findPreference("access_notification")!!
        mPostNotificationCheckbox = preferenceScreen.findPreference("post_notification")!!
        mAccessLocationCheckbox = preferenceScreen.findPreference("access_location")!!
        mAccessBluetoothCheckbox = preferenceScreen.findPreference("access_bluetooth")!!
        mConnectDeviceButton = preferenceScreen.findPreference("connect_device")!!

        refreshSettings()

        mServiceEnableSwitch.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean)
                ServiceManager.startBroadcastService(mainActivity)
            else
                ServiceManager.stopBroadcastService(mainActivity)
            return@setOnPreferenceChangeListener true
        }

        mAccessNotificationCheckbox.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean)
                PermissionCheck.requestNotificationAccessPermission(activity as MainActivity)
            return@setOnPreferenceChangeListener false
        }

        mAccessLocationCheckbox.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean)
                PermissionCheck.requestLocationAccessPermission(activity as MainActivity)
            return@setOnPreferenceChangeListener false
        }

        mAccessBluetoothCheckbox.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean)
                PermissionCheck.requestBluetoothAccessPermissions(activity as MainActivity)
            return@setOnPreferenceChangeListener false
        }

        mConnectDeviceButton.setOnPreferenceClickListener { _ ->
            (activity as MainActivity).openDeviceSelectionActivity()
            return@setOnPreferenceClickListener false
        }
    }

    private fun refreshSettings() {
        val context = requireContext()

        mServiceEnableSwitch.isEnabled = PermissionCheck.allPermissionsGranted(context)
        mServiceEnableSwitch.isChecked = ServiceManager.isBroadcastServiceRunning(activity as MainActivity)

        PermissionCheck.checkNotificationsAccessPermission(context).also {
            mAccessNotificationCheckbox.isEnabled = !it
            mAccessNotificationCheckbox.isChecked = it
        }
        PermissionCheck.checkNotificationPostingPermission(context).also {
            mPostNotificationCheckbox.isEnabled = !it
            mPostNotificationCheckbox.isChecked = it
        }
        PermissionCheck.checkLocationAccessPermission(context).also {
            mAccessLocationCheckbox.isEnabled = !it
            mAccessLocationCheckbox.isChecked = it
        }
        PermissionCheck.checkAllBluetoothPermission(context).also {
            mAccessBluetoothCheckbox.isEnabled = !it
            mAccessBluetoothCheckbox.isChecked = it
        }
    }
}