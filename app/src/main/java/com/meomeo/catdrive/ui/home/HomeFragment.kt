package com.meomeo.catdrive.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.DrawFilter
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.meomeo.catdrive.BuildConfig
import com.meomeo.catdrive.R
import com.meomeo.catdrive.databinding.FragmentHomeBinding
import com.meomeo.catdrive.lib.BitmapDither
import com.meomeo.catdrive.lib.NavigationData
import timber.log.Timber


class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private var _debugImage = false
    private val binding get() = _binding!!
    private val navigationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val bd = BitmapDither()
            Timber.i("mmmm received ${context} - ${intent}")
            val data = intent.getParcelableExtra("navigation_data_update") as NavigationData?
            if (data != null) {
                val bitmap =
                    if (!_debugImage) data.actionIcon.bitmap
                    else resources.getDrawable(R.drawable.roundabout).toBitmap()
                val compressed = bd.compressBitmap(bitmap, Size(32, 32))

                binding.txtRoadName.text = data.nextDirection.spannedList?.first()
                binding.txtNextRoadName.text = data.nextDirection.spannedList?.drop(1)?.joinToString(" ")
                binding.txtDistance.text = data.nextDirection.distance
                binding.txtEta.text = data.eta.toString()
                binding.imgTurnIcon.setImageBitmap(bitmap)
                binding.imgScaled.setImageDrawable(
                    AliasingDrawableWrapper(
                        bitmap?.scale(32, 32, false)?.toDrawable(resources)
                    )
                )
                binding.imgFinal.setImageDrawable(AliasingDrawableWrapper(compressed.toDrawable(resources)))
            }
        }
    }

    class AliasingDrawableWrapper(wrapped: Drawable?) : DrawableWrapper(wrapped) {
        override fun draw(canvas: Canvas) {
            val oldDrawFilter = canvas.drawFilter
            canvas.drawFilter = DRAW_FILTER
            super.draw(canvas)
            canvas.drawFilter = oldDrawFilter
        }

        companion object {
            private val DRAW_FILTER: DrawFilter = PaintFlagsDrawFilter(Paint.FILTER_BITMAP_FLAG, 0)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
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
    }

    override fun onStop() {
        super.onStop()
        Timber.e("Stop")
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(navigationReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}