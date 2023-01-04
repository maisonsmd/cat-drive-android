package com.meomeo.catdrive.ui

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.meomeo.catdrive.R
import com.meomeo.catdrive.utils.PermissionCheck
import timber.log.Timber

data class BtDevice(
    val name: String,
    val address: String,
    val connected: Boolean
)

class CustomAdapter() : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {
    private var mDataSet: List<BtDevice> = emptyList()
    var dataSet
        get() = mDataSet
        set(value) {
            if (value == mDataSet)
                return
            mDataSet = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDeviceName: TextView
        val txtDeviceAddess: TextView

        init {
            txtDeviceName = view.findViewById(R.id.txtItemDeviceName)
            txtDeviceAddess = view.findViewById(R.id.txtItemMacAddress)
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
        viewHolder.txtDeviceName.text = mDataSet[position].name
        viewHolder.txtDeviceAddess.text = mDataSet[position].address
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = mDataSet.size
}

class DeviceSelectionActivity : AppCompatActivity() {
    private lateinit var mViewDeviceAdapter: CustomAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_selection)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mViewDeviceAdapter = CustomAdapter()
        findViewById<RecyclerView?>(R.id.viewDeviceList).apply { adapter = mViewDeviceAdapter }
        findViewById<Button>(R.id.btnScan).apply {
            setOnClickListener {
                getDeviceList()
            }
        }

        getDeviceList()
    }

    private fun getDeviceList() {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (!PermissionCheck.checkBluetoothAccessPermission(this)) {
            Timber.e("No bluetooth permission")
            PermissionCheck.requestBluetoothAccessPermissions(this)
            return
        }

        val pairedDevices = mBluetoothAdapter.bondedDevices
        val devices = mutableListOf<BtDevice>()
        for (bt in pairedDevices) devices.add(
            BtDevice(
                bt.name, bt.address, false
            )
        )

        mViewDeviceAdapter.dataSet = devices
    }

}