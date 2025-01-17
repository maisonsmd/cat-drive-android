package com.meomeo.catdrive.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.meomeo.catdrive.MainActivity
import com.meomeo.catdrive.R
import com.meomeo.catdrive.SHARED_PREFERENCES_FILE
import com.meomeo.catdrive.lib.BleCharacteristics
import com.meomeo.catdrive.lib.Intents
import com.meomeo.catdrive.lib.NavigationData
import com.meomeo.catdrive.utils.PermissionCheck
import org.json.JSONObject
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.math.ceil

@SuppressLint("MissingPermission")
class BleService : Service(), LocationListener {
    companion object {
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2
        private const val NOTIFICATION_ID = 1201
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): BleService = this@BleService
    }

    private lateinit var mAdapter: BluetoothAdapter
    private var mNotificationBuilder: Notification.Builder? = null
    private var mRunInBackground: Boolean = false
    private var mPingTimer: Timer? = null
    private var mReconnectTimer: Timer? = null
    private var mFirstPing: Boolean = true
    private var mConnectionState = STATE_DISCONNECTED
    private var mDevice: BluetoothDevice? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private val mBinder = LocalBinder()
    private var mLastNavigationData: NavigationData? = null

    private val navigationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            mLastNavigationData = intent.getParcelableExtra("navigation_data") as NavigationData?
            sendToDevice(mLastNavigationData)
        }
    }


    val connectedDevice: BluetoothDevice?
        get() = mDevice

    var runInBackground
        get() = mRunInBackground
        private set(value) {
            if (mRunInBackground == value)
                return
            mRunInBackground = value
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                Intent(Intents.BACKGROUND_SERVICE_STATUS).apply {
                    putExtra("service", this::class.java.simpleName)
                    putExtra("run_in_background", value)
                }
            )
        }

    override fun onBind(intent: Intent?): IBinder? {
        // Bind by activity
        if (intent?.action == Intents.BIND_LOCAL_SERVICE) {
            return mBinder
        }
        // Bind by OS
        return null
    }

    override fun onCreate() {
        mAdapter =
            (applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.v("onStartCommand: $intent")

        if (intent?.action == Intents.ENABLE_SERVICES) {
            runInBackground = true
            startForeground(NOTIFICATION_ID, buildForegroundNotification())

            LocalBroadcastManager.getInstance(this)
                .registerReceiver(navigationReceiver, IntentFilter(Intents.NAVIGATION_UPDATE))

            subscribeToLocationUpdates()
            startReconnectTimer()
        }

        if (intent?.action == Intents.DISABLE_SERVICES) {
            runInBackground = false
            disconnect()
            unsubscribeFromLocationUpdates()

            LocalBroadcastManager.getInstance(this).unregisterReceiver(navigationReceiver)
            mNotificationBuilder = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            stopPingTimer()
            stopReconnectTimer()
        }

        if (intent?.action == Intents.CONNECT_DEVICE) {
            if (PermissionCheck.checkBluetoothPermissions(applicationContext)) {
                val device = intent.getParcelableExtra<BluetoothDevice>("device")!!
                connect(device)
            }
        }

        return START_STICKY
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                // Attempts to discover services after successful connection.
                Timber.i("onConnectionStateChange: Connected!")
                mBluetoothGatt?.discoverServices()
                mConnectionState = STATE_CONNECTING

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                // broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Timber.i("onConnectionStateChange: Disconnected!")
                if (mConnectionState != STATE_DISCONNECTED) {
                    disconnect()
                }

                updateNotificationText("No device connected")
                startReconnectTimer()
                stopPingTimer()

                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                    Intent(Intents.CONNECTION_UPDATE).apply {
                        putExtra("status", "disconnected")
                    }
                )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            // Discovery finishes after onServicesDiscovered is called
            if (mConnectionState == STATE_DISCONNECTED)
                return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.i("onServicesDiscovered: Success!")

                mConnectionState = STATE_CONNECTED

                updateNotificationText("Connected to ${mDevice!!.name}")
                stopReconnectTimer()
                startPingTimer()
                sendPreferencesToDevice()

                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                    Intent(Intents.CONNECTION_UPDATE).apply {
                        putExtra("status", "connected")
                        putExtra("device_name", mDevice!!.name)
                        putExtra("device_address", mDevice!!.address)
                    }
                )
            } else {
                Timber.w("onServicesDiscovered received: $status")
            }
        }
    }

    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        return mBluetoothGatt?.services
    }

    fun connectToLastDevice() {
        if (mDevice != null)
            return

        val sp = applicationContext.getSharedPreferences(
            SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE
        )
        val name = sp.getString("last_device_name", null)
        val address = sp.getString("last_device_address", null)
        if (name != null && address != null) {
            Timber.i("trying connecting to $name address $address")
            mAdapter.getRemoteDevice(address)?.let {
                connect(it)
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        Timber.i("Connecting to device $device")
        mDevice = device
        mBluetoothGatt = device.connectGatt(this, true, gattCallback)
    }

    fun disconnect() {
        if (mConnectionState == STATE_CONNECTED) {
            Timber.i("Disconnecting from device")
            mConnectionState = STATE_DISCONNECTED
            mDevice = null
            mBluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
                mBluetoothGatt = null
            }
        }
    }

    fun sendPreferencesToDevice() {
        mBluetoothGatt?.let {
            val characteristic =
                it.getService(UUID.fromString(BleCharacteristics.TEST_SERVICE))
                    .getCharacteristic(UUID.fromString(BleCharacteristics.CHARACTERISTIC_HEARTBEAT))
            characteristic.value = "Hello".toByteArray()
            characteristic.setValue("hello".toByteArray());
            it.writeCharacteristic(characteristic)
        }
    }

    fun sendToDevice(data: NavigationData?) {
    }

    private fun subscribeToLocationUpdates() {
        if (PermissionCheck.checkLocationAccessPermission(applicationContext)) {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
        }
    }

    private fun unsubscribeFromLocationUpdates() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(this)
    }

    private fun startPingTimer() {
        stopPingTimer()
        mPingTimer = Timer()
        mFirstPing = true
        mPingTimer!!.schedule(object : TimerTask() {
            override fun run() {
                Timber.d("Ping timer elapsed")
                if (mFirstPing) {
                    // resend prefs just in case the device did not received the prefs at first connection
                    sendPreferencesToDevice()
                    mFirstPing = false
                } else if (mLastNavigationData != null) {
                    sendToDevice(mLastNavigationData)
                }
            }
        }, 1000, 25000)
    }

    private fun stopPingTimer() {
        if (mPingTimer != null) {
            mPingTimer!!.cancel()
            mPingTimer = null
        }
    }

    private fun startReconnectTimer() {
        stopReconnectTimer()
        mReconnectTimer = Timer()
        mReconnectTimer!!.schedule(object : TimerTask() {
            override fun run() {
                Timber.d("reconnect timer elapsed")
                if (!PermissionCheck.checkBluetoothPermissions(applicationContext)) {
                    Timber.w("No BT permissions, stop reconnect timer!")
                    stopReconnectTimer()
                    return
                }
                if (mConnectionState != STATE_DISCONNECTED) {
                    stopReconnectTimer()
                    return
                }

//                setupSerialConnection()
                Timber.d("Trying to connect to last device...")
                if (PermissionCheck.isBluetoothEnabled(applicationContext))
                    connectToLastDevice()
            }
        }, 1000, 15000)
    }

    private fun stopReconnectTimer() {
        if (mReconnectTimer != null) {
            mReconnectTimer!!.cancel()
            mReconnectTimer = null
        }
    }

    override fun onLocationChanged(location: Location) {
        val speed = ceil(location.speed * 3600f / 1000f).toInt()
        Timber.d("Speed: $speed")

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(Intents.GPS_UPDATE).apply {
                putExtra("speed", speed)
            }
        )

        val json: JSONObject = JSONObject().apply {
            put("speed", speed)
        }

//        sendToDevice(json)
    }


    private fun updateNotificationText(text: String) {
        if (mNotificationBuilder == null)
            return
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotificationBuilder!!.setContentText(text)
        notificationManager.notify(NOTIFICATION_ID, mNotificationBuilder!!.build())
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = createNotificationChannel(
            this::class.java.simpleName,
            this::class.java.simpleName
        )
        val builder: Notification.Builder = Notification.Builder(this, channelId)
        val intent = Intent(this, MainActivity::class.java)
        intent.action = System.currentTimeMillis().toString()
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mNotificationBuilder = builder.setContentTitle("CatDrive service is meowing")
            .setContentText("Méo!")
            .setSmallIcon(R.drawable.catface)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
        return mNotificationBuilder!!.build()
    }
}