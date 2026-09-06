package com.rick.blueoothandroid17.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rick.blueoothandroid17.bluetooth.ScanMode
import com.rick.blueoothandroid17.bluetooth.ScanUiState
import com.rick.blueoothandroid17.bluetooth.ScannedDevice
import com.rick.blueoothandroid17.ui.theme.BlueoothAndroid17Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    state: ScanUiState,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onScanModeSelected: (ScanMode) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onClearStatus: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Bluetooth Scanner") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = state.permissionModelLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            StatusRow(
                supported = state.bluetoothSupported,
                enabled = state.bluetoothEnabled,
                permissionsGranted = state.permissionsGranted,
                scanning = state.scanning,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.permissionsGranted) {
                    Button(onClick = onRequestPermissions) {
                        Text("Grant permissions")
                    }
                }
                if (state.bluetoothSupported && !state.bluetoothEnabled) {
                    Button(onClick = onEnableBluetooth) {
                        Text("Enable Bluetooth")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Scan mode", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.scanMode == mode,
                        onClick = { onScanModeSelected(mode) },
                        label = { Text(mode.name) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartScan,
                    enabled = state.bluetoothSupported &&
                        state.bluetoothEnabled &&
                        state.permissionsGranted &&
                        !state.scanning,
                ) {
                    Text("Start scan")
                }
                Button(
                    onClick = onStopScan,
                    enabled = state.scanning,
                ) {
                    Text("Stop")
                }
            }

            state.statusMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearStatus) { Text("Dismiss") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Devices (${state.devices.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.devices, key = { it.address }) { device ->
                    DeviceRow(device)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    supported: Boolean,
    enabled: Boolean,
    permissionsGranted: Boolean,
    scanning: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    when {
                        !supported -> "BT unsupported"
                        enabled -> "BT on"
                        else -> "BT off"
                    },
                )
            },
        )
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(if (permissionsGranted) "Permissions OK" else "Need permissions")
            },
        )
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(if (scanning) "Scanning…" else "Idle") },
        )
    }
}

@Composable
private fun DeviceRow(device: ScannedDevice) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = device.name?.takeIf { it.isNotBlank() } ?: "Unknown device",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = buildString {
                append(device.radioLabel)
                device.rssi?.let { append(" · RSSI $it dBm") }
                append(" · ")
                append(bondLabel(device.bondState))
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun bondLabel(bondState: Int): String = when (bondState) {
    BluetoothDevice.BOND_BONDED -> "Bonded"
    BluetoothDevice.BOND_BONDING -> "Bonding"
    else -> "Not bonded"
}

@Preview(showBackground = true)
@Composable
private fun ScanScreenPreview() {
    BlueoothAndroid17Theme {
        ScanScreen(
            state = ScanUiState(
                bluetoothEnabled = true,
                permissionsGranted = true,
                devices = listOf(
                    ScannedDevice(
                        address = "AA:BB:CC:DD:EE:FF",
                        name = "Demo Sensor",
                        rssi = -62,
                        bondState = BluetoothDevice.BOND_NONE,
                        deviceType = BluetoothDevice.DEVICE_TYPE_LE,
                        seenOnClassic = false,
                        seenOnBle = true,
                    ),
                ),
            ),
            onRequestPermissions = {},
            onEnableBluetooth = {},
            onScanModeSelected = {},
            onStartScan = {},
            onStopScan = {},
            onClearStatus = {},
        )
    }
}
