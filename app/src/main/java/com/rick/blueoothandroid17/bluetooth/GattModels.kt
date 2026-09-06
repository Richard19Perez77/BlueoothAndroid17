package com.rick.blueoothandroid17.bluetooth

/**
 * Learning-focused GATT client state machine.
 *
 * Typical ticket flow:
 * Idle → Connecting → Connected → Discovering → Ready
 * Any failure → Failed (connection closed)
 * Ready/Failed/… → Closing → Idle
 *
 * Maps to:
 * https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server
 */
enum class GattPhase {
    Idle,
    Connecting,
    Connected,
    Discovering,
    Ready,
    Failed,
    Closing,
}

/**
 *  UI-facing GATT characteristic row.
 *
 *  Immutable Holder for one GATT characteristic shown in the UI.
 *
 *  After discoverServices(), each BLE service contains characteristics (the actual readable/writable/notificable values).
 *
 *  We don't pass the full Andriod BluetoothGattCharacteristic objet to compose
 *      We map it into this simple model.
 *
 * @property uuid - UUID of the characteristic, what value this is
 * @property propertiesLabel - human-readable label of the properties
 *                              read/write/notify/etc
 */
data class GattCharacteristicInfo(
    val uuid: String,
    val propertiesLabel: String,
)

/**
 *  UI-facing GATT service row.
 *
 *  After discoverServices(), each BLE device contains services (the logical grouping of characteristics).
 *
 *  We don't pass the full Andriod BluetoothGattService objet to compose
 *
 * @property uuid - UUID of the service, what services this is
 * @property isPrimary - true if the service is a primary service
 * @property characteristics - list of characteristics
 *                                  UUID + read/write/notify label
 */
data class GattServiceInfo(
    val uuid: String,
    val isPrimary: Boolean,
    val characteristics: List<GattCharacteristicInfo>,
)

/**
 *  UI-facing GATT client stub.
 *
 *  Everything the device detail screen needs to know about the GATT stub
 *      one snapshot the viewModel holds and compose reads.
 *
 *  We don't pass the full Andriod BluetoothGatt objet to compose
 *
 *  We map the GATT client state machine into a simple model for the UI.
 *
 *  Perform:
 *      on close of device creates a new isntance,
 *      on new instance of state is Idle
 *
 * @property phase - current state of the stub
 * @property services - list of services, empty until ready
 * @property statusMessage - message to show in the UI, nullable and dismissable
 */
data class GattUiState(
    val phase: GattPhase = GattPhase.Idle,
    val services: List<GattServiceInfo> = emptyList(),
    val statusMessage: String? = null,
) {
    val phaseLabel: String
        get() = when (phase) {
            GattPhase.Idle -> "Idle"
            GattPhase.Connecting -> "Connecting…"
            GattPhase.Connected -> "Connected"
            GattPhase.Discovering -> "Discovering services…"
            GattPhase.Ready -> "Ready (services discovered)"
            GattPhase.Failed -> "Failed"
            GattPhase.Closing -> "Closing…"
        }
}
