package com.rick.blueoothandroid17.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log

/**
 * Thin GATT client wrapper for learning.
 *
 * Phone app = GATT **client**. Remote accessory = GATT **server**.
 * [BluetoothGattCallback] delivers results asynchronously (often off the main thread).
 *
 * @property context - application context
 * @property onPhase - callback for phase changes
 * @property onServices - callback for discovered services
 * @property onMessage - callback for messages
 */
class GattClient(
    private val context: Context,
    private val onPhase: (GattPhase) -> Unit,
    private val onServices: (List<GattServiceInfo>) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    // Public API for Bluetooth GATT profile
    private var gatt: BluetoothGatt? = null

    /**
     *  Callback for GATT events. All callbacks are invoked on a Binder thread, not the main thread.
     *
     *  Android's async listener for GATT events.
     *
     *  You don't get return values from connectGatt / discoverServices for the real outcome
     *      Those methods only start work
     *      Results arrive later on this callback (often on a binder thread)
     */
    private val callback = object : BluetoothGattCallback() {

        /**
         * Callback indicating the connection state has changed.
         *
         * Fires when the link goes up/down (or fails while trying)
         *
         * When the connection succeeds, discover services
         *  When it fails or drops, tell the UI and close the GATT session.
         *
         * @param gatt - GATT client
         * @param status - connection status
         * @param newState - new connection state
         */
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // failed so close and stop
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onConnectionStateChange status=$status newState=$newState")
                onMessage("Connection failed (status $status)")
                onPhase(GattPhase.Failed)
                closeInternal(gatt)
                return
            }
            when (newState) {
                // link is up
                BluetoothProfile.STATE_CONNECTED -> {
                    onPhase(GattPhase.Connected)
                    onMessage("Connected — discovering services")
                    onPhase(GattPhase.Discovering)
                    // Next doc step after connect: discoverServices().
                    val started = gatt.discoverServices()
                    if (!started) {
                        onMessage("discoverServices() returned false")
                        onPhase(GattPhase.Failed)
                        closeInternal(gatt)
                    }
                }
                // link is down
                BluetoothProfile.STATE_DISCONNECTED -> {
                    onMessage("Disconnected")
                    onPhase(GattPhase.Idle)
                    closeInternal(gatt)
                }
            }
        }

        /**
         * Callback indicating the service discovery has finished.
         *
         * Fires when discovery finishes.
         *
         * @param gatt - GATT client
         * @param status - discovery status
         */
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // stop; no list
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onMessage("Service discovery failed (status $status)")
                onPhase(GattPhase.Failed)
                return
            }
            // map service with characteristic, provide label
            val services = gatt.services.orEmpty().map { service ->
                GattServiceInfo(
                    uuid = service.uuid.toString(),
                    isPrimary = service.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY,
                    characteristics = service.characteristics.orEmpty().map { characteristic ->
                        GattCharacteristicInfo(
                            uuid = characteristic.uuid.toString(),
                            propertiesLabel = propertiesLabel(characteristic.properties),
                        )
                    },
                )
            }
            onServices(services)
            onPhase(GattPhase.Ready)
            onMessage("Found ${services.size} service(s)")
        }
    }

    /**
     * Connect to a remote device.
     *
     * Starts a GATT client session to a remote device.
     *
     * It does not wait until you're fully connected.
     *  That arrives later in onConnectionStateChange
     *
     * @param device - remote device
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        close() // tear down session to prevent leaks
        onServices(emptyList()) // clear previous device's services list in UI
        onPhase(GattPhase.Connecting) // tell UI it's trying
        onMessage("Connecting to ${device.address}")
        // autoConnect = false → direct attempt (typical for interactive UI).
        // TRANSPORT_LE prefers the LE radio when the device is dual-mode (API 23+; our minSdk is 24).
        // Newer SDKs deprecate some connectGatt overloads; this remains the common learning path.
        @Suppress("DEPRECATION")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) // open link, save returned BluetoothGatt
        if (gatt == null) { // can fail but rare
            onMessage("connectGatt() returned null")
            onPhase(GattPhase.Failed)
        }
    }

    /**
     * Disconnect from a remote device.
     *
     * Drop the link gracefully.
     *
     * If we have a GATT handle, mark Closing and call disconnect()
     *  Real idle + close() happen when the stack reports disconnected
     *
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        val current = gatt // copy to avoid racing a null field mid-call
        if (current == null) {
            onPhase(GattPhase.Idle) // no session can set to Idle
            return
        }
        onPhase(GattPhase.Closing)
        onMessage("Disconnecting…")
        current.disconnect()
        // close() is finished in onConnectionStateChange(DISCONNECTED) or close().
    }

    /**
     * Close the GATT session.
     *
     */
    @SuppressLint("MissingPermission")
    fun close() {
        onPhase(GattPhase.Closing)
        closeInternal(gatt) // close current GATT
        onPhase(GattPhase.Idle)
    }

    /**
     * Close the GATT session.
     *
     * @param target - GATT client to close
     */
    @SuppressLint("MissingPermission")
    private fun closeInternal(target: BluetoothGatt?) {
        runCatching { target?.close() } // swallow exception, no crash
        if (gatt === target) {
            gatt = null
        }
    }

    companion object {
        private const val TAG = "GattClient"

        /**
         *  Convert characteristic properties into a human-readable label.
         *
         *  Turn a characteristic's property bit flags into a short UI string like "read, notify"
         *      Let the user see what you 'could' do with that UUID without calling read/write yet.
         *
         *  BluetoothGattCharacteristic are official properties
         *      Used to construct a GATT service
         *
         * @param properties - characteristic properties
         * @return - human-readable label
         */
        fun propertiesLabel(properties: Int): String {
            val parts = buildList {
                if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("read")
                if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("write")
                if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    add("write-no-resp")
                }
                if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("notify")
                if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("indicate")
            }
            return parts.joinToString(", ").ifEmpty { "none" }
        }
    }
}
