package com.rick.blueoothandroid17.bluetooth

/**
 * UI-facing device row. Address is the merge key across Classic discovery and BLE scans.
 */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int?,
    val bondState: Int,
    val deviceType: Int,
    val seenOnClassic: Boolean,
    val seenOnBle: Boolean,
) {
    val radioLabel: String
        get() = when {
            seenOnClassic && seenOnBle -> "Dual (Classic + LE)"
            seenOnBle -> "BLE"
            seenOnClassic -> "Classic"
            else -> "Unknown"
        }
}
