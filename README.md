# BT Scan Lab (BluetoothAndroid17)

A small Android learning app for **Bluetooth Classic** and **Bluetooth Low Energy (BLE)** scanning.

The GitHub repo is `BluetoothAndroid17`. The application package is still `com.rick.blueoothandroid17`.

It is intentionally dual-purpose:

1. **Newest practices** on modern Android (nearby-device permissions, Compose UI, lifecycle-safe scanning).
2. **Legacy paths kept** (minSdk 24) so you can see how Bluetooth APIs and permissions evolved through Android 12+.

## What it does today

- Checks whether Bluetooth is supported and enabled
- Requests the correct runtime permissions for the device's API level
- Scans in **Classic**, **BLE**, or **Both** modes
- Lists devices with name, address, RSSI, bond state, and radio type
- Tap a device → **GATT client stub**: connect → discover services → list UUIDs → disconnect
- Stops scanning / closes GATT when the app leaves the foreground

## Quick start

1. Open the project in Android Studio.
2. Use a **physical device** when possible (emulators often lack real Bluetooth radios).
3. Run the `app` configuration.
4. Grant permissions, turn Bluetooth on, choose a scan mode, tap **Start scan**.

### SDK range

| Setting | Value | Why |
|---|---|---|
| `minSdk` | 24 | Keep pre-Android 12 permission/scan behavior for comparison |
| `compileSdk` / `targetSdk` | 37 | Current platform APIs |

## Project map

```
app/src/main/java/com/rick/blueoothandroid17/
├── MainActivity.kt                 # Permissions, enable-BT intent, lifecycle stop
├── bluetooth/
│   ├── BluetoothPermissions.kt     # Legacy vs modern permission model
│   ├── BluetoothScanner.kt         # Classic discovery + BLE scan
│   ├── GattClient.kt / GattModels  # GATT connect + service discovery stub
│   ├── BluetoothScanViewModel.kt   # UI state, merge devices, GATT
│   ├── ScanMode.kt
│   └── ScannedDevice.kt
└── ui/
    ├── ScanScreen.kt               # Compose scanner UI
    └── DeviceDetailScreen.kt       # Device + GATT phase / services
```

## Docs

- **[Beginner overview](docs/OVERVIEW.md)** — concepts, permission timeline, how to read the code, and what to build next.

## Roadmap (planned)

- Read / notify a characteristic
- Classic `BluetoothSocket` stub
- Advertise (phone as peripheral)
- Pairing / bonding UI
- Companion Device Manager
- LE Audio extras
- Android 16+ ranging / Channel Sounding (`RangingManager`)

## Notes

- On **Android 11 and older**, BLE scanning still needs **location permission**, and Location Services often must be **on**.
- On **Android 12+**, this app uses `BLUETOOTH_SCAN` with `neverForLocation`, so location is not required for scanning.
- Prefer `BluetoothAdapter.ACTION_REQUEST_ENABLE` over the deprecated `BluetoothAdapter.enable()`.
