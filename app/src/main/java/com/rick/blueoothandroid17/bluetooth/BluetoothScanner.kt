package com.rick.blueoothandroid17.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * Dual-mode discovery:
 * - Classic: [BluetoothAdapter.startDiscovery] + ACTION_FOUND (still the Classic path).
 * - BLE: [android.bluetooth.le.BluetoothLeScanner] (API 21+; fine on minSdk 24).
 *
 * Adapter access uses [BluetoothManager] (current practice).
 * [BluetoothAdapter.getDefaultAdapter] is the older entry point and is avoided here.
 */
class BluetoothScanner(
    context: Context,
    private val onDevice: (ScannedDevice) -> Unit,
    private val onScanningChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext

    /**
     *  System level service introduced in Android 4.3 API 18:
     *      Provides a centralized way to access Bluetooth functionality on the device
     *      A wrapper and manger for both Classic Bluetooth and Bluetooth Low Energy
     *
     *  Used to get the adapter, a local Bluetooth radio.
     *  Full (GATT server/client) the generic attribute profile.
     *
     */
    private val bluetoothManager =
        appContext.getSystemService(BluetoothManager::class.java)

    /**
     *  Local BL adapter, bluetooth radio. entry point for bl interaction.
     *
     *  Discover bl devices, query lists of bonded paired devices.
     *
     *  Instantiate a [BluetoothDevice] using a known MAC address.
     *
     *  Create a [android.bluetooth.BluetoothServerSocket]
     *      Listen for communications from other devices
     */
    val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    val isBluetoothSupported: Boolean get() = adapter != null

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    @Volatile
    private var scanning = false

    /**
     *  Classic Bluetooth discovery.
     */
    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                /**
                 *  Classic discovery hit:
                 *      Intent carries a remote [BluetoothDevice]
                 *      (name, address, class, bond state)
                 *      plus optional RSSI
                 *
                 *  That device can later be used to connect
                 *      (e.g. via [android.bluetooth.BluetoothSocket])
                 */
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.parcelableDevice() ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        .takeUnless { it == Short.MIN_VALUE }
                        ?.toInt()
                    emit(device, rssi, fromClassic = true, fromBle = false)
                }

                /**
                 *  Classic discovery is time-limited:
                 *      After you call startDiscovery().
                 *      Android searched for a while, then stops and broadcasts ACTION_DISCOVERY_FINISHED.
                 *      You don't have to stop Classic yourself for that cycle to end.
                 *
                 *  Without this check, finishing Classic in Both mode would flash the UI to Idle while BLE was still delivering devices.
                 */
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Classic discovery ends on its own; if BLE is still running, stay "scanning".
                    if (adapter?.bluetoothLeScanner == null || !leScanActive) {
                        scanning = false
                        onScanningChanged(false)
                    }
                }
            }
        }
    }

    private var classicReceiverRegistered = false
    private var leScanActive = false

    /**
     *  BLE discovery.
     */
    private val leScanCallback = object : ScanCallback() {

        /**
         *  BLE discovery hit:
         *
         *
         * @param callbackType - one of three values
         *                          [ScanSettings.CALLBACK_TYPE_ALL_MATCHES]
         *                          [ScanSettings.CALLBACK_TYPE_NEW_MATCH]
         *                          [ScanSettings.CALLBACK_TYPE_MATCH_LOST]
         *
         * @param result - [ScanResult] carries a remote [BluetoothDevice]
         */
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            emit(result.device, result.rssi, fromClassic = false, fromBle = true)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
            onError("BLE scan failed (code $errorCode)")
            stopBleOnly()
            if (adapter?.isDiscovering != true) {
                scanning = false
                onScanningChanged(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(mode: ScanMode) {
        // copy adapter once for same instance passing into start methods
        // instead of ?.let and use you can null check a copy and use it a lot
        val bt =
            adapter // adapter is nullable, Kotlin won't smart cast a property the way it does with a local val.
        if (bt == null) {
            onError("Bluetooth is not supported on this device")
            return
        }
        if (!bt.isEnabled) {
            onError("Bluetooth is off")
            return
        }
        if (!BluetoothPermissions.hasAll(appContext)) {
            onError("Missing Bluetooth permissions")
            return
        }

        stop()

        scanning = true
        onScanningChanged(true)

        when (mode) {
            ScanMode.Classic -> startClassic(bt)
            ScanMode.Ble -> startBle(bt)
            ScanMode.Both -> {
                startClassic(bt)
                startBle(bt)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val bt = adapter
        stopClassic(bt)
        stopBleOnly()
        if (scanning) {
            scanning = false
            onScanningChanged(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startClassic(bt: BluetoothAdapter) {
        if (!classicReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            // RECEIVER_NOT_EXPORTED: modern registration flag (API 33+); safe on older via compat.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(classicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(classicReceiver, filter)
            }
            classicReceiverRegistered = true
        }
        // Cancel any in-flight discovery before starting a fresh one.
        if (bt.isDiscovering) {
            bt.cancelDiscovery()
        }
        if (!bt.startDiscovery()) {
            onError("Classic startDiscovery() returned false")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopClassic(bt: BluetoothAdapter?) {
        if (bt?.isDiscovering == true) {
            bt.cancelDiscovery()
        }
        if (classicReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(classicReceiver) }
            classicReceiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBle(bt: BluetoothAdapter) {
        val scanner = bt.bluetoothLeScanner
        if (scanner == null) {
            onError("BLE scanner unavailable (adapter off or LE unsupported)")
            return
        }
        // Balanced is a good default; later we can expose LOW_LATENCY / LOW_POWER and PHY.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner.startScan(/* filters = */ null, settings, leScanCallback)
        leScanActive = true
    }

    @SuppressLint("MissingPermission")
    private fun stopBleOnly() {
        if (!leScanActive) return
        runCatching {
            adapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        }
        leScanActive = false
    }

    /**
     *
     *
     * @param device - [BluetoothDevice] carries a remote [BluetoothDevice]
     * @param rssi - signal strength
     * @param fromClassic - true if Classic
     * @param fromBle - true if BLE
     */
    @SuppressLint("MissingPermission")
    private fun emit(
        device: BluetoothDevice,
        rssi: Int?,
        fromClassic: Boolean,
        fromBle: Boolean,
    ) {
        onDevice(
            ScannedDevice(
                address = device.address,
                name = device.safeName(),
                rssi = rssi,
                bondState = device.bondState,
                deviceType = device.type,
                seenOnClassic = fromClassic,
                seenOnBle = fromBle,
            )
        )
    }

    companion object {
        private const val TAG = "BluetoothScanner"
    }
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.safeName(): String? =
    try {
        // name can throw / return null without CONNECT on API 31+; permission is checked upstream.
        name
    } catch (_: SecurityException) {
        null
    }

/**
 *
 *  Helper method pulls a [BluetoothDevice] out of an [Intent]
 *
 *  When Classic scan finds something, Android sends ACTION_FOUND with extras.
 *
 *  One of them is the BlutoothDevice.EXTRA_DEVICE and cast to [BluetoothDevice]
 *
 * @return a [BluetoothDevice] or null if not found
 */
@Suppress("DEPRECATION")
private fun Intent.parcelableDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        // LEGACY parcelable extra API (deprecated in API 33).
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
