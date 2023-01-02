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
import com.meomeo.catdrive.BuildConfig
import com.meomeo.catdrive.MeowGoogleMapNotificationListener
import com.meomeo.catdrive.R
import com.meomeo.catdrive.databinding.FragmentHomeBinding
import com.meomeo.catdrive.lib.BitmapHelper
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
            Timber.i("mmmm received ${context} - ${intent}")
            val data = intent.getParcelableExtra("navigation_data_update") as NavigationData?
            displayNavigationData(data)
        }
    }

    fun displayNavigationData(data: NavigationData?) {
        val bh = BitmapHelper()
        if (data == null) {
            binding.txtRoadName.text = "unknown"
            binding.txtNextRoadName.text = "unknown"
            binding.txtDistance.text = "unknown"
            binding.txtEta.text = "unknown"
            binding.imgTurnIcon.setImageBitmap(null)
            binding.imgScaled.setImageDrawable(null)
            binding.imgFinal.setImageDrawable(null)
            return
        }

        val bitmap =
            if (!mDebugImage) data.actionIcon.bitmap
            else resources.getDrawable(R.drawable.roundabout).toBitmap()
        val compressed = bh.compressBitmap(bitmap, Size(32, 32))

        binding.txtRoadName.text = data.nextDirection.spannedList?.first()
        binding.txtNextRoadName.text = data.nextDirection.spannedList?.drop(1)?.joinToString(" ")
        binding.txtDistance.text = data.nextDirection.distance
        binding.txtEta.text = data.eta.toString()
        binding.imgTurnIcon.setImageBitmap(bitmap)
        binding.imgScaled.setImageDrawable(
            BitmapHelper.AliasingDrawableWrapper(
                bitmap?.scale(32, 32, false)?.toDrawable(resources)
            )
        )
        binding.imgFinal.setImageDrawable(BitmapHelper.AliasingDrawableWrapper(compressed.toDrawable(resources)))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        mUiBinding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        Timber.e("Start")
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                navigationReceiver,
                IntentFilter("${BuildConfig.APPLICATION_ID}.INTENT_NAVIGATION_DATA")
            )

        Intent(
            requireContext(),
            MeowGoogleMapNotificationListener::class.java
        ).also { intent -> intent.action = "${BuildConfig.APPLICATION_ID}.local_bind" }
            .also { intent ->
                requireActivity().bindService(intent, navigationConnection, Context.BIND_AUTO_CREATE)
            }
    }

    override fun onStop() {
        Timber.e("Stop")

        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(navigationReceiver)

        mNavigationServiceBound = false
        requireActivity().unbindService(navigationConnection)

        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mUiBinding = null
    }
}