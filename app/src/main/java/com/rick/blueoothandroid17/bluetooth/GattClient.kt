package com.rick.blueoothandroid17.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Thin GATT client wrapper for learning.
 *
 * Phone app = GATT **client**. Remote accessory = GATT **server**.
 * [BluetoothGattCallback] delivers results asynchronously (often off the main thread).
 */
class GattClient(
    private val context: Context,
    private val onPhase: (GattPhase) -> Unit,
    private val onServices: (List<GattServiceInfo>) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private var gatt: BluetoothGatt? = null

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onConnectionStateChange status=$status newState=$newState")
                onMessage("Connection failed (status $status)")
                onPhase(GattPhase.Failed)
                closeInternal(gatt)
                return
            }
            when (newState) {
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
                BluetoothProfile.STATE_DISCONNECTED -> {
                    onMessage("Disconnected")
                    onPhase(GattPhase.Idle)
                    closeInternal(gatt)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onMessage("Service discovery failed (status $status)")
                onPhase(GattPhase.Failed)
                return
            }
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

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        close()
        onServices(emptyList())
        onPhase(GattPhase.Connecting)
        onMessage("Connecting to ${device.address}")
        // autoConnect = false → direct attempt (typical for interactive UI).
        // TRANSPORT_LE prefers the LE radio when the device is dual-mode.
        // Newer SDKs deprecate some connectGatt overloads; this remains the common learning path.
        @Suppress("DEPRECATION")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        if (gatt == null) {
            onMessage("connectGatt() returned null")
            onPhase(GattPhase.Failed)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val current = gatt
        if (current == null) {
            onPhase(GattPhase.Idle)
            return
        }
        onPhase(GattPhase.Closing)
        onMessage("Disconnecting…")
        current.disconnect()
        // close() is finished in onConnectionStateChange(DISCONNECTED) or close().
    }

    @SuppressLint("MissingPermission")
    fun close() {
        onPhase(GattPhase.Closing)
        closeInternal(gatt)
        onPhase(GattPhase.Idle)
    }

    @SuppressLint("MissingPermission")
    private fun closeInternal(target: BluetoothGatt?) {
        runCatching { target?.close() }
        if (gatt === target) {
            gatt = null
        }
    }

    companion object {
        private const val TAG = "GattClient"

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
