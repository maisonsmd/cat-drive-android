package com.meomeo.catdrive.ui.home

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.meomeo.catdrive.MainActivity
import com.meomeo.catdrive.MeowGoogleMapNotificationListener
import com.meomeo.catdrive.R
import com.meomeo.catdrive.databinding.FragmentHomeBinding
import com.meomeo.catdrive.lib.BitmapHelper
import com.meomeo.catdrive.lib.Intents
import com.meomeo.catdrive.lib.NavigationData
import timber.log.Timber


class HomeFragment : Fragment() {
    private var mUiBinding: FragmentHomeBinding? = null
    private var mDebugImage = false

    private var mNavigationService: MeowGoogleMapNotificationListener? = null
    private var mNavigationServiceBound = false
    private val binding get() = mUiBinding!!

    private val navigationConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is MeowGoogleMapNotificationListener.LocalBinder)
                return

            mNavigationService = service.getService()
            mNavigationServiceBound = true

            Timber.d("service connected")
            displayNavigationData(mNavigationService!!.lastNavigationData)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mNavigationServiceBound = false
            Timber.d("service disconnected")
        }
    }

    private val navigationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            // Timber.i("received ${context} - ${intent}")
            val data = intent.getParcelableExtra("navigation_data") as NavigationData?
            displayNavigationData(data)
        }
    }

    private val gpsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            // Timber.i("received ${context} - ${intent}")
            val speed = intent.getFloatExtra("speed", 0f).toInt()
            binding.txtSpeed.text = "$speed km/h"
        }
    }

    fun displayNavigationData(data: NavigationData?) {
        val bh = BitmapHelper()

        val bitmap =
            if (!mDebugImage) data?.actionIcon?.bitmap
            else resources.getDrawable(R.drawable.roundabout).toBitmap()
        val compressed = bh.compressBitmap(bitmap, Size(32, 32))

        binding.imgTurnIcon.setImageBitmap(bitmap)
        binding.imgScaled.setImageDrawable(
            BitmapHelper.AliasingDrawableWrapper(
                bitmap?.scale(32, 32, false)?.toDrawable(resources)
            )
        )
        binding.imgFinal.setImageDrawable(BitmapHelper.AliasingDrawableWrapper(compressed.toDrawable(resources)))

        if (data == null) {
            binding.txtRoadName.text = "unknown"
            binding.txtNextRoadName.text = "unknown"
            binding.txtDistance.text = "unknown"
            binding.txtEta.text = "unknown"
            return
        }

        binding.txtRoadName.text = data.nextDirection.spannedList?.first()
        binding.txtNextRoadName.text = data.nextDirection.spannedList?.drop(1)?.joinToString(" ")
        binding.txtDistance.text = data.nextDirection.distance
        binding.txtEta.text = data.eta.toString()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        mUiBinding = FragmentHomeBinding.inflate(inflater, container, false)

        val mainActivity = activity as MainActivity
        binding.btnOpenDevices.setOnClickListener { _ ->
            mainActivity.openDeviceSelectionActivity()
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        // Listen to Navigation dat
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(navigationReceiver, IntentFilter(Intents.NavigationUpdate))
        // Listen to GPS data
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(gpsReceiver, IntentFilter(Intents.GpsUpdate))

        Intent(requireContext(), MeowGoogleMapNotificationListener::class.java)
            .also { intent -> intent.action = Intents.BindLocalService }
            .also { intent -> requireActivity().bindService(intent, navigationConnection, Context.BIND_AUTO_CREATE) }
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(navigationReceiver)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(gpsReceiver)

        mNavigationServiceBound = false
        requireActivity().unbindService(navigationConnection)

        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mUiBinding = null
    }
}