package com.meomeo.catdrive.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.meomeo.catdrive.R
import com.meomeo.catdrive.utils.PermissionCheck
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import timber.log.Timber

@Parcelize
@Serializable
data class BtDevice(
    val name: String,
    val address: String,
    val uuids: List<String>
) : Parcelable

class CustomAdapter(onSelectCallback: (BtDevice) -> Unit) : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {
    private var mDataSet: List<BtDevice> = emptyList()
    private val mOnSelectCallback = onSelectCallback

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
        val txtDeviceAddress: TextView
        val frame: FrameLayout

        init {
            txtDeviceName = view.findViewById(R.id.txtItemDeviceName)
            txtDeviceAddress = view.findViewById(R.id.txtItemMacAddress)
            frame = view.findViewById(R.id.frame)
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
        viewHolder.txtDeviceName.text = dataSet[position].name
        viewHolder.txtDeviceAddress.text = dataSet[position].address

        viewHolder.frame.setOnClickListener { _ -> mOnSelectCallback(dataSet[position]) }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size
}

class DeviceSelectionActivity : AppCompatActivity() {
    private lateinit var mViewDeviceAdapter: CustomAdapter

    private fun onDeviceSelected(device: BtDevice) {
        Timber.w(device.toString())
        setResult(RESULT_OK, Intent().apply { putExtra("device", device) })
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_selection)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mViewDeviceAdapter = CustomAdapter(this::onDeviceSelected)
        findViewById<RecyclerView?>(R.id.viewDeviceList).apply { adapter = mViewDeviceAdapter }
        findViewById<Button>(R.id.btnScan).apply {
            setOnClickListener {
                getDeviceList()
            }
        }

        getDeviceList()
    }

    private fun getDeviceList() {
        val adapter = (applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter!!

        if (!PermissionCheck.checkAllBluetoothPermission(this)) {
            Timber.e("No bluetooth permission")
            PermissionCheck.requestBluetoothAccessPermissions(this)
            return
        }

        val pairedDevices = adapter.bondedDevices
        val devices = mutableListOf<BtDevice>()
        for (bt in pairedDevices) devices.add(
            BtDevice(
                bt.name, bt.address, bt.uuids.map { it.toString() }
            )
        )

        mViewDeviceAdapter.dataSet = devices
    }

}