package com.rick.blueoothandroid17.bluetooth

/**
 *  UI-facing device row.
 *      Address is the merge key across Classic discovery and BLE scans.
 *
 * @property address - MAC address (medium/media access control address)
 * @property name - non-blank new name is preferred, null at first sight
 * @property rssi - received signal strength indicator (power level)
 * @property bondState - state of the bond with the device
 * @property deviceType - type of the device, Classic, LE, Dual, Unknown
 * @property seenOnClassic - shows if the device was seen in Classic discovery
 * @property seenOnBle - shows if the device was seen during BLE scan
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
    // computed from two seenOn flags shown
    val radioLabel: String
        get() = when {
            seenOnClassic && seenOnBle -> "Dual (Classic + LE)"
            seenOnBle -> "BLE"
            seenOnClassic -> "Classic"
            else -> "Unknown"
        }
}
