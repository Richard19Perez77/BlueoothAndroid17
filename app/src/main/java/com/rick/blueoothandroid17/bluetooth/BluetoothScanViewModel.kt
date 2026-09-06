package com.rick.blueoothandroid17.bluetooth

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val bluetoothSupported: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    val permissionModelLabel: String = BluetoothPermissions.modelLabel(),
    val scanMode: ScanMode = ScanMode.Ble,
    val scanning: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
    val statusMessage: String? = null,
)

class BluetoothScanViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        ScanUiState(
            permissionsGranted = BluetoothPermissions.hasAll(application),
        )
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val devicesByAddress = linkedMapOf<String, ScannedDevice>()

    private val scanner = BluetoothScanner(
        context = application,
        onDevice = { device -> mergeDevice(device) },
        onScanningChanged = { scanning ->
            _uiState.update { it.copy(scanning = scanning) }
        },
        onError = { message ->
            _uiState.update { it.copy(statusMessage = message) }
        },
    )

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            refreshAdapterState()
            if (!scanner.isBluetoothEnabled) {
                scanner.stop()
            }
        }
    }

    init {
        refreshAdapterState()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(
                adapterStateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(adapterStateReceiver, filter)
        }
    }

    fun refreshPermissions() {
        _uiState.update {
            it.copy(permissionsGranted = BluetoothPermissions.hasAll(getApplication()))
        }
    }

    fun refreshAdapterState() {
        _uiState.update {
            it.copy(
                bluetoothSupported = scanner.isBluetoothSupported,
                bluetoothEnabled = scanner.isBluetoothEnabled,
            )
        }
    }

    fun setScanMode(mode: ScanMode) {
        val wasScanning = _uiState.value.scanning
        _uiState.update { it.copy(scanMode = mode) }
        if (wasScanning) {
            // Restart so the active radios match the new mode.
            startScan()
        }
    }

    fun startScan() {
        refreshPermissions()
        refreshAdapterState()
        val state = _uiState.value
        when {
            !state.bluetoothSupported -> {
                _uiState.update { it.copy(statusMessage = "Bluetooth not supported") }
            }
            !state.bluetoothEnabled -> {
                _uiState.update { it.copy(statusMessage = "Turn Bluetooth on first") }
            }
            !state.permissionsGranted -> {
                _uiState.update { it.copy(statusMessage = "Grant permissions first") }
            }
            else -> {
                devicesByAddress.clear()
                _uiState.update {
                    it.copy(devices = emptyList(), statusMessage = null, scanning = true)
                }
                scanner.start(state.scanMode)
            }
        }
    }

    fun stopScan() {
        scanner.stop()
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    /** System intent to ask the user to enable Bluetooth (preferred over adapter.enable()). */
    fun enableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    /**
     *  Merges a newly scanned device with an existing one in the adapter.
     *
     *  Keeps one list row per MAC address, even when Classic and BLE both see the same phone / speaker / sensor, or when the same device reports again with a new RSSI.
     *
     * @param incoming - a new device
     */
    private fun mergeDevice(incoming: ScannedDevice) {
        viewModelScope.launch {
            val existing = devicesByAddress[incoming.address]
            val merged = existing?.copy(
                name = incoming.name?.takeIf { it.isNotBlank() } ?: existing.name,
                rssi = incoming.rssi ?: existing.rssi,
                bondState = when {
                    incoming.bondState != BluetoothDevice.BOND_NONE -> incoming.bondState
                    else -> existing.bondState
                },
                deviceType = if (incoming.deviceType != BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                    incoming.deviceType
                } else {
                    existing.deviceType
                },
                seenOnClassic = existing.seenOnClassic || incoming.seenOnClassic,
                seenOnBle = existing.seenOnBle || incoming.seenOnBle,
            )
                ?: incoming
            devicesByAddress[incoming.address] = merged
            val sorted = devicesByAddress.values
                .sortedWith(
                    compareByDescending<ScannedDevice> { it.rssi ?: Int.MIN_VALUE }
                        .thenBy { it.name ?: it.address },
                )
            _uiState.update { it.copy(devices = sorted) }
        }
    }

    override fun onCleared() {
        scanner.stop()
        runCatching {
            getApplication<Application>().unregisterReceiver(adapterStateReceiver)
        }
        super.onCleared()
    }
}
