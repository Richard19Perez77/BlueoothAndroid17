package com.rick.blueoothandroid17.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Shows the permission model evolution:
 *
 * - **Legacy (API ≤ 30):** install-time BLUETOOTH / BLUETOOTH_ADMIN plus
 *   runtime location for BLE scan results.
 * - **Modern (API 31+):** runtime BLUETOOTH_SCAN + BLUETOOTH_CONNECT
 *   (and later BLUETOOTH_ADVERTISE), without location when using neverForLocation.
 */
object BluetoothPermissions {

    /** True when this device uses the Android 12+ nearby-device permission model. */
    val usesModernNearbyPermissions: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Permissions that must be granted at runtime before scanning.
     * Manifest still declares the install-time legacy Bluetooth permissions for API ≤ 30.
     */
    fun requiredRuntimePermissions(): Array<String> =
        if (usesModernNearbyPermissions) {
            // MODERN (API 31+)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            // LEGACY (API ≤ 30): location was required to receive BLE scan results.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAll(context: Context): Boolean =
        requiredRuntimePermissions().all { permission ->
            // validate against listed permission from passed in context
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED // each must be granted
        }

    /** Short label for the UI so the evolution is visible on-device. */
    fun modelLabel(): String =
        if (usesModernNearbyPermissions) {
            "Modern (API 31+): BLUETOOTH_SCAN + CONNECT"
        } else {
            "Legacy (API ≤ 30): location required for BLE scan"
        }
}
