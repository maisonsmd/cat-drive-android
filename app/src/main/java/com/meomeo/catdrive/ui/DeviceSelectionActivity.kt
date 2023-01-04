package com.meomeo.catdrive.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.meomeo.catdrive.R
import timber.log.Timber


class CustomAdapter(private val dataSet: Array<String>) : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDeviceName: TextView

        init {
            // Define click listener for the ViewHolder's View
            txtDeviceName = view.findViewById(R.id.txtItemDeviceName)
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.device_row_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.txtDeviceName.text = dataSet[position]
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size
}

class DeviceSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_selection)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val texts = arrayOf("Meow", "Mèo", "Mèo meo meo méo")
        val adapter = CustomAdapter(texts)

        val viewDeviceList = findViewById<RecyclerView>(R.id.viewDeviceList)
        viewDeviceList.adapter = adapter
    }

    private fun getDeviceList() {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (haveBluetoothAccessPermission()) {
            val pairedDevices = mBluetoothAdapter.bondedDevices
            val s: MutableList<String> = ArrayList()
            for (bt in pairedDevices) s.add(bt.name)
            Timber.d(s.toString())
        }
    }

    private fun haveBluetoothAccessPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}