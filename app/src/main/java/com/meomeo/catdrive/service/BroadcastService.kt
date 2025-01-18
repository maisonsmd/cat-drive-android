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
import android.bluetooth.BluetoothGattCharacteristic
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
import android.util.Size
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.meomeo.catdrive.MainActivity
import com.meomeo.catdrive.R
import com.meomeo.catdrive.SHARED_PREFERENCES_FILE
import com.meomeo.catdrive.lib.BitmapHelper
import com.meomeo.catdrive.lib.BleCharacteristics
import com.meomeo.catdrive.lib.BleWriteQueue
import com.meomeo.catdrive.lib.BleWriteQueue.QueueItem
import com.meomeo.catdrive.lib.Intents
import com.meomeo.catdrive.lib.NavigationData
import com.meomeo.catdrive.utils.PermissionCheck
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import kotlin.math.ceil

@SuppressLint("MissingPermission")
class BleService : Service(), LocationListener {
    companion object {
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
    private var mConnectionState = BluetoothProfile.STATE_DISCONNECTED
    private var mDevice: BluetoothDevice? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private val mBinder = LocalBinder()
    private var mLastNavigationData: NavigationData? = null
    private var mDataWriteQueue: BleWriteQueue = BleWriteQueue()
    private var mIsSending: Boolean = false

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

    inner class ReconnectTask: TimerTask() {
        override fun run() {
            Timber.d("reconnect timer elapsed")
            if (!PermissionCheck.checkBluetoothPermissions(applicationContext)) {
                Timber.w("No BT permissions, stop reconnect timer!")
                stopReconnectTimer()
                return
            }
            if (mConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
                stopReconnectTimer()
                return
            }

            Timber.d("Trying to connect to last device...")
            if (PermissionCheck.isBluetoothEnabled(applicationContext))
                connectToLastDevice()
        }
    }

    inner class PingTask : TimerTask() {
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

            if (!runInBackground)
                return START_NOT_STICKY
        }

        if (intent?.action == Intents.DISCONNECT_DEVICE) {
            if (PermissionCheck.checkBluetoothPermissions(applicationContext)) {
                disconnect()
            }

            if (!runInBackground)
                return START_NOT_STICKY
        }

        return START_STICKY
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Timber.d("onConnectionStateChange: $status, $newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                // Attempts to discover services after successful connection.
                Timber.i("onConnectionStateChange: Connected! ${mDevice}, ${mBluetoothGatt}")
                mBluetoothGatt?.discoverServices()
                mConnectionState = BluetoothProfile.STATE_CONNECTING
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                // broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Timber.i("onConnectionStateChange: Disconnected!")
                if (mConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
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
            if (mConnectionState == BluetoothProfile.STATE_DISCONNECTED)
                return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.i("onServicesDiscovered: Success!")

                for (service in gatt!!.services) {
                    for (ch in service.characteristics) {
                        mDataWriteQueue.add(
                            QueueItem(
                                ch.uuid.toString(),
                                Math.random().toString().toByteArray()
                            )
                        )
                    }
                }
                mConnectionState = BluetoothProfile.STATE_CONNECTED

                mIsSending = false
                mDataWriteQueue.clear()

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

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            Timber.d("onCharacteristicWrite: $status (0 means success)")

            mIsSending = false
            if (mDataWriteQueue.size > 0) {
                write(mDataWriteQueue.pop())
            }
        }
    }

    private fun write(item: QueueItem) {
        Timber.d("writing ${item.uuid}=${item.data}")
        if (mConnectionState != BluetoothProfile.STATE_CONNECTED) {
            Timber.e("write: not connected")
            return
        }

        if (mIsSending) {
            Timber.d("Busy, queueing")
            mDataWriteQueue.add(item)
            return
        }

        Timber.d("Ble free to write, writing")
        mIsSending = true
        mBluetoothGatt?.let {
            val ch = findCharacteristic(item.uuid)
            if (ch == null) {
                Timber.e("No characteristic found for ${item.uuid}")
                return
            }

            ch.value = item.data
            it.writeCharacteristic(ch)
        }
    }

    private fun findCharacteristic(uuid: String): BluetoothGattCharacteristic? {
        var characteristic: BluetoothGattCharacteristic? = null
        mBluetoothGatt?.services?.forEach { service ->
            service.characteristics.forEach { ch ->
                if (ch.uuid.toString() == uuid)
                    characteristic = ch
            }
        }
        return characteristic
    }

    fun connectToLastDevice() {
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
        mBluetoothGatt =
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE).also {
                it.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                it.requestMtu(240)
            }
    }

    fun disconnect() {
        stopPingTimer()
        stopReconnectTimer()

        if (mConnectionState == BluetoothProfile.STATE_CONNECTED) {
            Timber.i("Disconnecting from device")
            mIsSending = false
            mDataWriteQueue.clear()
            mConnectionState = BluetoothProfile.STATE_DISCONNECTED
            mDevice = null
            mBluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
                mBluetoothGatt = null
            }

            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                Intent(Intents.CONNECTION_UPDATE).apply {
                    putExtra("status", "disconnected")
                }
            )
        }
    }

    fun sendPreferencesToDevice() {
        val sp =
            applicationContext.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        write(
            QueueItem(
                BleCharacteristics.CHA_SETT_THEME,
                sp.getBoolean("display_light_theme", true).toString().toByteArray()
            )
        )
        write(
            QueueItem(
                BleCharacteristics.CHA_SETT_BRIGHTNESS,
                sp.getInt("display_brightness", 50).toString().toByteArray()
            )
        )
        write(
            QueueItem(
                BleCharacteristics.CHA_SETT_SPEED_LIMIT,
                sp.getInt("speed_limit", 50).toString().toByteArray()
            )
        )
    }

    fun sendToDevice(data: NavigationData?) {
        write(
            QueueItem(
                BleCharacteristics.CHA_NAV_NEXT_ROAD,
                (data?.nextDirection?.nextRoad ?: "").toByteArray()
            )
        )
        write(
            QueueItem(
                BleCharacteristics.CHA_NAV_NEXT_ROAD_DESC,
                (data?.nextDirection?.nextRoadAdditionalInfo ?: "").toByteArray()
            )
        )
        write(
            QueueItem(
                BleCharacteristics.CHA_NAV_DISTANCE_TO_NEXT_TURN,
                (data?.nextDirection?.distance ?: "").toByteArray()
            )
        )
        write(QueueItem(BleCharacteristics.CHA_NAV_ETA, (data?.eta?.eta ?: "").toByteArray()))
        write(QueueItem(BleCharacteristics.CHA_NAV_ETE, (data?.eta?.ete ?: "").toByteArray()))
        write(
            QueueItem(
                BleCharacteristics.CHA_NAV_TOTAL_DISTANCE,
                (data?.eta?.distance ?: "").toByteArray()
            )
        )
        data?.actionIcon?.bitmap?.let { bitmap ->
            bitmap.let {
                val compressed =
                    with(BitmapHelper()) { toBlackAndWhiteBuffer(compressBitmap(it, Size(64, 64))) }
//                Timber.w("Compressed size: ${compressed.size}")
                compressed
            }.also {
                write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_NAV_TBT_ICON, it, true))
            }
        }
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
        mPingTimer!!.schedule(PingTask(), 1000, 25000)
    }

    private fun stopPingTimer() {
        if (mPingTimer != null) {
            mPingTimer!!.cancel()
            mPingTimer!!.purge()
            mPingTimer = null
        }
    }

    private fun startReconnectTimer() {
        stopReconnectTimer()
        mReconnectTimer = Timer()
        mReconnectTimer!!.schedule(ReconnectTask(), 1000, 15000)
    }

    private fun stopReconnectTimer() {
        if (mReconnectTimer != null) {
            mReconnectTimer!!.cancel()
            mReconnectTimer!!.purge()
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

        write(QueueItem(BleCharacteristics.CHA_NAV_SPEED, speed.toString().toByteArray()))
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