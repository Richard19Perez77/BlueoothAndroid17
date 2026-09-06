package com.rick.blueoothandroid17.bluetooth

/**
 * Which radios to listen on.
 *
 * Classic and BLE are separate stacks on Android;
 *
 * "Ble" runs BLE scan only.
 * "Classic" runs Classic discovery only.
 * "Both" runs discovery + LE scan together and merges results by MAC.
 */
enum class ScanMode {
    Classic,
    Ble,
    Both,
}
