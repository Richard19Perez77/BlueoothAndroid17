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

data class GattCharacteristicInfo(
    val uuid: String,
    val propertiesLabel: String,
)

data class GattServiceInfo(
    val uuid: String,
    val isPrimary: Boolean,
    val characteristics: List<GattCharacteristicInfo>,
)

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
