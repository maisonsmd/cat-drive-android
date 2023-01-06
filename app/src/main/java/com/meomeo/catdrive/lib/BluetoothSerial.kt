package com.meomeo.catdrive.lib

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.widget.Toast
import com.meomeo.catdrive.ui.BtDevice
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothSerial(context: Context) {
    private val mContext = context
    private val mAdapter: BluetoothAdapter
    private var mDevice: BluetoothDevice? = null

    private var mSocket: BluetoothSocket? = null
    private var mOutputStream: OutputStream? = null
    private var mInputStream: InputStream? = null
    private var mConnectionCoroutine: Job? = null

    private var mOnConnectedCallback: ((device: BluetoothDevice) -> Unit)? = null
    private var mOnDisconnectedCallback: (() -> Unit)? = null

    init {
        val bluetoothManager = mContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mAdapter = bluetoothManager.adapter
    }

    fun connect(device: BtDevice) {
        Timber.d("connect")

        mAdapter.cancelDiscovery()
        closeConnection()

        Timber.d("mSocket: $mSocket, connected: ${mSocket?.isConnected}")
        mDevice = mAdapter.getRemoteDevice(device.address)
        if (mDevice == null || mDevice?.bondState != BluetoothDevice.BOND_BONDED) {
            Toast.makeText(mContext, "Unable find device: ${device.name}", Toast.LENGTH_SHORT).show()
            return
        }

        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //Standard SerialPortService ID

        mSocket = mDevice!!.createRfcommSocketToServiceRecord(uuid)
        Toast.makeText(mContext, "Connecting to ${device.name}", Toast.LENGTH_SHORT).show()

        if (mSocket == null) {
            Toast.makeText(mContext, "Unable to create socket", Toast.LENGTH_SHORT).show()
            return
        } else {
            connectInBackground()
        }

        // TODO: implement when receiving needed
        // https://stackoverflow.com/questions/13450406/how-to-receive-serial-data-using-android-bluetooth
        // beginListenForData()
    }

    fun isConnected(): Boolean {
        return (mDevice != null && mSocket != null && mSocket?.isConnected == true)
    }

    fun sendData(msg: String) {
        mOutputStream?.write(msg.toByteArray())
    }

    fun keepConnectionAlive() {
        if (mDevice != null && mSocket != null && mSocket?.isConnected == false) {
            connectInBackground()
        }
    }

    private fun onConnected(device: BluetoothDevice) {
        Timber.i("connected")
        Toast.makeText(mContext, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
        mOutputStream = mSocket!!.outputStream
        mInputStream = mSocket!!.inputStream
        mOnConnectedCallback?.let { it(device) }
    }

    private fun onConnectFailed(device: BluetoothDevice?, error: String) {
        Timber.e(error)
        Toast.makeText(mContext, "Unable to connect to ${device?.name}", Toast.LENGTH_SHORT).show()
    }

    private fun connectInBackground() {
        if (mConnectionCoroutine?.isActive == true) {
            mConnectionCoroutine?.cancel()
        }

        val device = mDevice!!

        mConnectionCoroutine = GlobalScope.launch(Dispatchers.Main) {
            val worker = GlobalScope.async(Dispatchers.Default) {
                mSocket?.connect()
                return@async mSocket?.isConnected
            }

            try {
                val connected = worker.await()
                if (connected == true)
                    onConnected(device)
                else
                    onConnectFailed(device, "unknown")
            } catch (e: Exception) {
                onConnectFailed(device, e.toString())
            }
        }
    }

    fun closeConnection() {
        Timber.w("closing connection")
        mConnectionCoroutine?.cancel()
        if (isConnected())
            mOnDisconnectedCallback?.let { it() }
        // stopWorker = true
        mOutputStream?.close()
        mInputStream?.close()
        mSocket?.close()

        mSocket = null
        mOutputStream = null
        mInputStream = null
    }

    sealed class Result<out R> {
        data class Success<out T>(val data: T) : Result<T>()
        data class Error(val exception: Exception) : Result<Nothing>()
    }
}