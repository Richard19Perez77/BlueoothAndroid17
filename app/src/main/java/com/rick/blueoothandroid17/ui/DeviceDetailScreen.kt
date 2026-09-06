package com.rick.blueoothandroid17.ui

import android.bluetooth.BluetoothDevice
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rick.blueoothandroid17.bluetooth.GattCharacteristicInfo
import com.rick.blueoothandroid17.bluetooth.GattPhase
import com.rick.blueoothandroid17.bluetooth.GattServiceInfo
import com.rick.blueoothandroid17.bluetooth.GattUiState
import com.rick.blueoothandroid17.bluetooth.ScannedDevice
import com.rick.blueoothandroid17.ui.theme.BlueoothAndroid17Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: ScannedDevice,
    gatt: GattUiState,
    onBack: () -> Unit,
    onConnectGatt: () -> Unit,
    onDisconnectGatt: () -> Unit,
    onClearGattStatus: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.name?.takeIf { it.isNotBlank() } ?: "Device") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(device.address, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${device.radioLabel} · ${bondLabel(device.bondState)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            device.rssi?.let {
                Text("RSSI $it dBm", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Text("GATT client stub", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Phone = GATT client. Accessory = GATT server. " +
                    "Connect → discoverServices() → list UUIDs → disconnect/close.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Phase: ${gatt.phaseLabel}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = device.seenOnBle &&
                        gatt.phase != GattPhase.Connecting &&
                        gatt.phase != GattPhase.Discovering &&
                        gatt.phase != GattPhase.Closing,
                    onClick = onConnectGatt,
                ) {
                    Text("Connect GATT")
                }
                OutlinedButton(
                    onClick = onDisconnectGatt,
                    enabled = gatt.phase == GattPhase.Connected ||
                        gatt.phase == GattPhase.Discovering ||
                        gatt.phase == GattPhase.Ready,
                ) {
                    Text("Disconnect")
                }
            }

            if (!device.seenOnBle) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This device was only seen on Classic discovery. " +
                        "GATT needs a BLE sighting (scan with Ble or Both).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            gatt.statusMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearGattStatus) { Text("Dismiss") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Services (${gatt.services.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(gatt.services, key = { it.uuid }) { service ->
                    ServiceBlock(service)
                }
            }
        }
    }
}

@Composable
private fun ServiceBlock(service: GattServiceInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (service.isPrimary) "Primary service" else "Secondary service",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(service.uuid, style = MaterialTheme.typography.bodyMedium)
        service.characteristics.forEach { characteristic ->
            CharacteristicLine(characteristic)
        }
    }
}

@Composable
private fun CharacteristicLine(characteristic: GattCharacteristicInfo) {
    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
        Text(characteristic.uuid, style = MaterialTheme.typography.bodySmall)
        Text(
            text = characteristic.propertiesLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun bondLabel(bondState: Int): String = when (bondState) {
    BluetoothDevice.BOND_BONDED -> "Bonded"
    BluetoothDevice.BOND_BONDING -> "Bonding"
    BluetoothDevice.BOND_NONE -> "None"
    else -> "Not bonded"
}

@Preview(showBackground = true)
@Composable
private fun DeviceDetailPreview() {
    BlueoothAndroid17Theme {
        DeviceDetailScreen(
            device = ScannedDevice(
                address = "AA:BB:CC:DD:EE:FF",
                name = "Demo Sensor",
                rssi = -55,
                bondState = BluetoothDevice.BOND_NONE,
                deviceType = BluetoothDevice.DEVICE_TYPE_LE,
                seenOnClassic = false,
                seenOnBle = true,
            ),
            gatt = GattUiState(
                phase = GattPhase.Ready,
                services = listOf(
                    GattServiceInfo(
                        uuid = "0000180f-0000-1000-8000-00805f9b34fb",
                        isPrimary = true,
                        characteristics = listOf(
                            GattCharacteristicInfo(
                                uuid = "00002a19-0000-1000-8000-00805f9b34fb",
                                propertiesLabel = "read, notify",
                            ),
                        ),
                    ),
                ),
                statusMessage = "Found 1 service(s)",
            ),
            onBack = {},
            onConnectGatt = {},
            onDisconnectGatt = {},
            onClearGattStatus = {},
        )
    }
}
