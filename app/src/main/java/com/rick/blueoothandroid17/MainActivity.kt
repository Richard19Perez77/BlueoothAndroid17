package com.rick.blueoothandroid17

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rick.blueoothandroid17.bluetooth.BluetoothPermissions
import com.rick.blueoothandroid17.bluetooth.BluetoothScanViewModel
import com.rick.blueoothandroid17.ui.DeviceDetailScreen
import com.rick.blueoothandroid17.ui.ScanScreen
import com.rick.blueoothandroid17.ui.theme.BlueoothAndroid17Theme

class MainActivity : ComponentActivity() {

    private val viewModel: BluetoothScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueoothAndroid17Theme {
                ScannerApp(viewModel)
            }
        }
    }
}

@Composable
private fun ScannerApp(viewModel: BluetoothScanViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshAdapterState()
    }

    /**
     * tie the activity compose lifecycle to bluetooth work
     * connects when user leaves or comes back
     */
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // refresh permissions, adapter on/off
                Lifecycle.Event.ON_START -> {
                    viewModel.refreshPermissions()
                    viewModel.refreshAdapterState()
                }
                // release radios from stop scan and close GATT
                Lifecycle.Event.ON_STOP -> viewModel.releaseRadios()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // remove the observer, avoid leaking on composition loss
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selected = state.selectedDevice
    if (selected != null) {
        DeviceDetailScreen(
            device = selected,
            gatt = state.gatt,
            onBack = viewModel::closeDevice,
            onConnectGatt = viewModel::connectGatt,
            onDisconnectGatt = viewModel::disconnectGatt,
            onClearGattStatus = viewModel::clearGattStatus,
        )
    } else {
        ScanScreen(
            state = state,
            onRequestPermissions = {
                permissionLauncher.launch(BluetoothPermissions.requiredRuntimePermissions())
            },
            onEnableBluetooth = {
                enableBtLauncher.launch(viewModel.enableBluetoothIntent())
            },
            onScanModeSelected = viewModel::setScanMode,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onClearStatus = viewModel::clearStatus,
            onDeviceClick = viewModel::openDevice,
        )
    }
}
