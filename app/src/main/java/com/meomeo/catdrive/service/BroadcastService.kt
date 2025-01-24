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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import com.meomeo.catdrive.utils.Misc
import com.meomeo.catdrive.utils.PermissionCheck
import timber.log.Timber
import java.util.UUID
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
    private var mIsBackgroundRunningEnabled: Boolean = false
    private var mFirstPing: Boolean = true
    private var mConnectionState = BluetoothProfile.STATE_DISCONNECTED
    private var mDevice: BluetoothDevice? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private val mBinder = LocalBinder()
    private var mLastNavigationData: NavigationData? = null
    private var mDataWriteQueue: BleWriteQueue = BleWriteQueue()
    private var mIsSending: Boolean = false
    private var mIconMap: MutableMap<String, ByteArray> = mutableMapOf()
    private val mHandler = Handler(Looper.getMainLooper())
    private val mRunnable = Runnable { onTimerTick() }

    private val navigationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            mLastNavigationData = intent.getParcelableExtra("navigation_data") as NavigationData?
            sendToDevice(mLastNavigationData)
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
            backgroundRunningEnabled = true
            startForeground(NOTIFICATION_ID, buildForegroundNotification())

            LocalBroadcastManager.getInstance(this)
                .registerReceiver(navigationReceiver, IntentFilter(Intents.NAVIGATION_UPDATE))

            subscribeToLocationUpdates()

            if (!mHandler.hasCallbacks(mRunnable))
                onTimerTick()
        }

        if (intent?.action == Intents.DISABLE_SERVICES) {
            backgroundRunningEnabled = false
            disconnect()
            unsubscribeFromLocationUpdates()

            LocalBroadcastManager.getInstance(this).unregisterReceiver(navigationReceiver)
            mNotificationBuilder = null

            if (mHandler.hasCallbacks(mRunnable))
                mHandler.removeCallbacks(mRunnable)

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        if (intent?.action == Intents.CONNECT_DEVICE) {
            if (PermissionCheck.checkBluetoothPermissions(applicationContext)) {
                val device = intent.getParcelableExtra<BluetoothDevice>("device")!!
                connect(device)
            }

            if (!backgroundRunningEnabled)
                return START_NOT_STICKY
        }

        if (intent?.action == Intents.DISCONNECT_DEVICE) {
            if (PermissionCheck.checkBluetoothPermissions(applicationContext)) {
                disconnect()
            }

            if (!backgroundRunningEnabled)
                return START_NOT_STICKY
        }

        return START_STICKY
    }

    val connectedDevice: BluetoothDevice?
        get() = if (mConnectionState == BluetoothProfile.STATE_CONNECTED) mDevice else null

    var backgroundRunningEnabled
        get() = mIsBackgroundRunningEnabled
        private set(value) {
            if (mIsBackgroundRunningEnabled == value)
                return
            mIsBackgroundRunningEnabled = value
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                Intent(Intents.BACKGROUND_SERVICE_STATUS).apply {
                    putExtra("service", this::class.java.simpleName)
                    putExtra("run_in_background", value)
                }
            )
        }

    private fun onTimerTick() {
        ping()
        checkReconnect()
        mHandler.postDelayed(mRunnable,15000)
    }

    private fun checkReconnect() {
        if (mConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
            return
        }
        if (!PermissionCheck.checkBluetoothPermissions(applicationContext)) {
            return
        }
        if (mConnectionState != BluetoothProfile.STATE_DISCONNECTED) {
            return
        }

        Timber.d("Trying to connect to last device...")
        if (PermissionCheck.isBluetoothEnabled(applicationContext))
            connectToLastDevice()
    }

    private fun ping() {
        if (mConnectionState != BluetoothProfile.STATE_CONNECTED) {
            return
        }
        if (mFirstPing) {
            // resend prefs just in case the device did not received the prefs at first connection
            sendPreferencesToDevice()
            mFirstPing = false
        } else if (mLastNavigationData != null) {
            sendToDevice(mLastNavigationData)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Timber.d("onConnectionStateChange: $status, $newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                // Attempts to discover services after successful connection.
                Timber.i("onConnectionStateChange: Connected! ${mDevice?.address}")
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

                mConnectionState = BluetoothProfile.STATE_CONNECTED

                mIsSending = false
                mDataWriteQueue.clear()
                mIconMap.clear()

                updateNotificationText("Connected to ${mDevice!!.name}")
                sendPreferencesToDevice()
                mFirstPing = true

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
//        Timber.d("writing ${item.uuid}=${item.data.toString(Charsets.UTF_8)}")
        if (mConnectionState != BluetoothProfile.STATE_CONNECTED) {
            return
        }

        if (mIsSending) {
            Timber.d("Busy with ${mDataWriteQueue.size} requests, queueing")
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
        val service = mBluetoothGatt?.getService(UUID.fromString(BleCharacteristics.SERVICE_UUID))
        service?.characteristics?.forEach { ch ->
            if (ch.uuid.toString() == uuid)
                characteristic = ch
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
        } else {
            Timber.w("No last device found")
        }
    }

    private fun connect(device: BluetoothDevice) {
        Timber.i("Connecting to device $device")
        mDevice = device
        mBluetoothGatt =
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE).also {
                it.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                it.requestMtu(240)
            }
    }

    fun disconnect() {
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

        val map = mapOf(
            "lightTheme" to sp.getBoolean("display_light_theme", true).toString(),
            "brightness" to sp.getInt("display_brightness", 50).toString(),
            "speedLimit" to sp.getInt("speed_limit", 50).toString()
        )

        write(
            QueueItem(BleCharacteristics.CHA_SETTINGS, Misc.toKeyValString(map).toByteArray())
        )
    }

    fun sendToDevice(data: NavigationData?) {
        val bitmap = data?.actionIcon?.bitmap

        val compressed: ByteArray? = bitmap?.let {
            BitmapHelper.toBlackAndWhiteBuffer(
                BitmapHelper.compressBitmap(
                    bitmap,
                    Size(64, 62)
                )
            )
        }

        val iconHash = if (compressed != null) Misc.md5(compressed)
            .let { it.substring(it.length - 10, it.length) } else ""

        val map = mapOf(
            "nextRd" to Misc.sanitize(data?.nextDirection?.nextRoad ?: ""),
            "nextRdDesc" to Misc.sanitize(data?.nextDirection?.nextRoadAdditionalInfo ?: ""),
            "distToNext" to Misc.sanitize(data?.nextDirection?.distance ?: ""),
            "totalDist" to Misc.sanitize(data?.tripInfo?.distance ?: ""),
            "eta" to Misc.sanitize(data?.tripInfo?.eta ?: ""),
            "ete" to Misc.sanitize(data?.tripInfo?.ete ?: ""),
            "iconHash" to iconHash
        )

        write(QueueItem(BleCharacteristics.CHA_NAV, Misc.toKeyValString(map).toByteArray()))

        // Only send once
        if (iconHash != "" && compressed != null && !mIconMap.containsKey(iconHash)) {
            if (mConnectionState == BluetoothProfile.STATE_CONNECTED) {
                // Store the bitmap for later use
                mIconMap[iconHash] = compressed

                compressed.let {
                    val withIconHash = ("$iconHash;").toByteArray()
                    write(QueueItem(BleCharacteristics.CHA_NAV_TBT_ICON, withIconHash + it, true))
                }
            }
        } else {
            Timber.i("Icon $iconHash is already sent before")
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

    override fun onLocationChanged(location: Location) {
        val speed = ceil(location.speed * 3600f / 1000f).toInt()
//        Timber.d("Speed: $speed")

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(Intents.GPS_UPDATE).apply {
                putExtra("speed", speed)
            }
        )

        write(QueueItem(BleCharacteristics.CHA_GPS_SPEED, speed.toString().toByteArray()))
    }

    private fun updateNotificationText(text: String) {
        if (mNotificationBuilder == null)
            return
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotificationBuilder!!.setContentText(text)
        mNotificationBuilder!!.setOnlyAlertOnce(true)
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
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        return mNotificationBuilder!!.build()
    }
}