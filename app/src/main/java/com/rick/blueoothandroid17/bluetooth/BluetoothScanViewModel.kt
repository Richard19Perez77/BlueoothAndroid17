package com.rick.blueoothandroid17.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 *  UI-facing device scan state.
 *
 *  Bluetooth is async, this state is the translation layer.
 *      hardware events -> fields -> widgets
 *
 *  VM functions, startScan, openDevice, connectGatt, receiver,...
 *      update the state and the UI recomposes.
 *
 * Default values are needed so first frame before init refreshes still renders without null crashes.
 *
 * @property bluetoothSupported - true if the device supports Bluetooth
 * @property bluetoothEnabled - true if Bluetooth is on
 * @property permissionsGranted  - true if all required permissions are granted
 * @property permissionModelLabel - human-readable label of the permissions
 * @property scanMode - which radios to listen on
 * @property scanning - true if scanning
 * @property devices - list of devices
 * @property statusMessage - message to show in the UI, nullable and dismissable
 * @property selectedDevice - non-null when the device detail / GATT stub screen is open
 * @property gatt - GattUiState for device details
 */
data class ScanUiState(
    val bluetoothSupported: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    val permissionModelLabel: String = BluetoothPermissions.modelLabel(),
    val scanMode: ScanMode = ScanMode.Ble,
    val scanning: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
    val statusMessage: String? = null,
    /** Non-null when the device detail / GATT stub screen is open. */
    val selectedDevice: ScannedDevice? = null,
    val gatt: GattUiState = GattUiState(),
)

/**
 * ViewModel for managing the Bluetooth scan state and interactions.
 *
 * @constructor - Application context
 *
 *
 * @param application - application context
 */
class BluetoothScanViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        ScanUiState(
            // overrides permissionsGranted from a real check
            // first screen won't say 'need permissions' when granted
            permissionsGranted = BluetoothPermissions.hasAll(application),
        )
    )
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val devicesByAddress = linkedMapOf<String, ScannedDevice>()

    private val bluetoothManager =
        application.getSystemService(BluetoothManager::class.java)

    /**
     *  Scanner for discovering Bluetooth devices.
     */
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

    /**
     *
     *  GATT client for connecting to Bluetooth devices and discovering services.
     *
     *  Allow Compose to show phase, services and messages
     *
     *  GattClient (Bluetooth callbacks, often binder thread)
     *     │
     *     ├─ onPhase(phase)      → uiState.gatt.phase
     *     ├─ onServices(list)    → uiState.gatt.services
     *     └─ onMessage(text)     → uiState.gatt.statusMessage
     *
     * Dispatchers.Main.immediate
     *      UI state should be updated in a controlled way on the main dispatcher.
     *
     *  Build one GattClient for the VM
     *      Whenever it reports phase/services/message
     *      Copy that into ScanUiState.gatt on the main thread.
     *
     *  Called throughout the VM here and in the UI.
     */
    private val gattClient = GattClient(
        context = application,
        onPhase = { phase ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _uiState.update { it.copy(gatt = it.gatt.copy(phase = phase)) }
            }
        },
        onServices = { services ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _uiState.update { it.copy(gatt = it.gatt.copy(services = services)) }
            }
        },
        onMessage = { message ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _uiState.update { it.copy(gatt = it.gatt.copy(statusMessage = message)) }
            }
        },
    )

    /**
     *  System level service introduced in Android 4.3 API 18:
     */
    private val adapterStateReceiver = object : BroadcastReceiver() {

        /**
         *
         * Bluetooth adapter turned on/off.
         *  Relate state transitions to the UI state and stop scanning or close GATT if Bluetooth is off.
         *
         *  Listens for ACTION_STATE_CHANGED and Android sends this whenever the phone's Bluetooth radio state changes (STATE_ON, STATE_OFF, turning on/off, etc.)
         *
         *  Registered in vm init
         *  Unregistered in vm onCleared
         *
         *  System level service introduced in Android 4.3 API 18:
         *
         *  When Bluetooth toggles, refresh the UI;
         *      if it's off, stop the scanner and close GATT.
         *
         * @param context - application context
         * @param intent - Intent with ACTION_STATE_CHANGED
         */
        override fun onReceive(context: Context, intent: Intent) {
            // ignore wrong action
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            refreshAdapterState() // UI chips (BT on / off)
            if (!scanner.isBluetoothEnabled) {
                scanner.stop() // end discovering
                gattClient.close() // drop any GATT session
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

    fun clearGattStatus() {
        _uiState.update { it.copy(gatt = it.gatt.copy(statusMessage = null)) }
    }

    /** System intent to ask the user to enable Bluetooth (preferred over adapter.enable()). */
    fun enableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    /** Open device detail; stop scanning so the radio is free for GATT. */
    fun openDevice(device: ScannedDevice) {
        scanner.stop()
        gattClient.close()
        _uiState.update {
            it.copy(
                selectedDevice = device,
                gatt = GattUiState(),
                statusMessage = null,
            )
        }
    }

    fun closeDevice() {
        gattClient.close()
        _uiState.update {
            it.copy(selectedDevice = null, gatt = GattUiState())
        }
    }

    @SuppressLint("MissingPermission")
    fun connectGatt() {
        refreshPermissions()
        refreshAdapterState()
        val state = _uiState.value
        val selected = state.selectedDevice
        when {
            selected == null -> return
            !state.permissionsGranted -> {
                _uiState.update {
                    it.copy(gatt = it.gatt.copy(statusMessage = "Grant permissions first"))
                }
            }

            !state.bluetoothEnabled -> {
                _uiState.update {
                    it.copy(gatt = it.gatt.copy(statusMessage = "Turn Bluetooth on first"))
                }
            }

            !selected.seenOnBle -> {
                _uiState.update {
                    it.copy(
                        gatt = it.gatt.copy(
                            statusMessage = "GATT is a BLE path — this row was only seen on Classic",
                        ),
                    )
                }
            }

            else -> {
                scanner.stop()
                val remote = bluetoothManager?.adapter?.getRemoteDevice(selected.address)
                if (remote == null) {
                    _uiState.update {
                        it.copy(gatt = it.gatt.copy(statusMessage = "Could not resolve BluetoothDevice"))
                    }
                    return
                }
                gattClient.connect(remote)
            }
        }
    }

    fun disconnectGatt() {
        gattClient.disconnect()
    }

    /** Call when the activity leaves the foreground. */
    fun releaseRadios() {
        scanner.stop()
        gattClient.close()
    }

    /**
     *
     *  Keep one list row per MAC as scan callbacks stream in, then refresh the UI state.
     *      Including the open detail screen if that device is selected
     *
     *  Classic and BLE can both see the same hardware, and the same device can report many times with new RSSI/name.
     *      Without merging you'd get duplicate rows and stale detail data.
     *
     *  Same address = one device
     *      Fold in better name/RSSI/bond/radio flags
     *      Re-sort and keep open detail row in sync
     *
     * @param incoming - device to add or merge
     */
    private fun mergeDevice(incoming: ScannedDevice) {
        viewModelScope.launch {

            // lookup by MAC
            val existing = devicesByAddress[incoming.address]

            // build merged with no existing and existing then copy
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

            // save into ScannedDevice's Map by address
            devicesByAddress[incoming.address] = merged
            val sorted = devicesByAddress.values
                .sortedWith(
                    compareByDescending<ScannedDevice> {
                        it.rssi ?: Int.MIN_VALUE // strongest rssi first
                    }.thenBy { it.name ?: it.address }, // then name, address
                )
            _uiState.update { state ->
                state.copy(
                    // set the scan list
                    devices = sorted,
                    // replace with updated row from sorted or old selection
                    selectedDevice = state.selectedDevice?.let { selected ->
                        sorted.find {
                            it.address == selected.address
                        } ?: selected
                    },
                )
            }
        }
    }

    /**
     *  Called when the ViewModel is no longer used and will be destroyed.
     *
     */
    override fun onCleared() {
        releaseRadios()
        runCatching {
            getApplication<Application>().unregisterReceiver(adapterStateReceiver)
        }
        super.onCleared()
    }
}
