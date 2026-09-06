# Bluetooth on Android — beginner overview

This guide explains what this app is teaching, in the same order you should learn it.

You do not need prior Bluetooth experience. You should know basic Android ideas: Activities, permissions, and that Compose draws the UI.

---

## 1. What problem does Bluetooth solve?

Bluetooth lets nearby devices talk over short-range radio without Wi‑Fi or cables.

On phones you meet two common “flavors”:

| Flavor | Everyday examples | In this app |
|---|---|---|
| **Classic Bluetooth** | older speakers, keyboards, some headsets, car kits | **Classic** scan mode |
| **Bluetooth Low Energy (BLE)** | fitness bands, sensors, beacons, most new gadgets | **BLE** scan mode |

They share a radio family but use **different discovery APIs** on Android. That is why the app has separate modes and a **Both** mode that merges results.

---

## 2. What is a “minimal” Bluetooth app?

Before connecting, pairing, or streaming audio, the smallest useful app is a **scanner**:

1. Is Bluetooth available?
2. Is it turned on?
3. Do we have permission?
4. Start discovery / LE scan.
5. Show devices (name, address, signal strength).
6. Stop scanning when the screen goes away.

That is what this project does in v1. Everything newer (GATT, advertise, ranging) builds on a device you already found.

---

## 3. Classic vs BLE discovery (mental model)

```
┌─────────────────────┐     ┌──────────────────────────┐
│  Classic discovery  │     │  BLE scan                │
│  startDiscovery()   │     │  BluetoothLeScanner      │
│  ACTION_FOUND       │     │  ScanCallback            │
└─────────┬───────────┘     └────────────┬─────────────┘
          │                              │
          └──────────┬───────────────────┘
                     ▼
            Merge by MAC address
                     ▼
                 Device list
```

- **Classic:** `BluetoothAdapter.startDiscovery()` + a `BroadcastReceiver` for `BluetoothDevice.ACTION_FOUND`.
- **BLE:** `adapter.bluetoothLeScanner.startScan(...)` + a `ScanCallback`.
- **Both:** run both, merge rows that share the same address.

**RSSI** (received signal strength) is a rough clue about distance. It is noisy; do not treat it as a ruler. Newer Android APIs (ranging / Channel Sounding) aim at better distance later.

---

## 4. The big Android story: permissions changed

This project keeps **minSdk 24** on purpose so you can see **old and new** side by side.

### Timeline (simplified)

| Android | API | What apps needed to scan BLE |
|---|---|---|
| 6–11 | 23–30 | Bluetooth install permissions + **location** at runtime (and often Location **on**) |
| 12+ | 31+ | Runtime **`BLUETOOTH_SCAN`** / **`BLUETOOTH_CONNECT`** (and later advertise). Location can be skipped for pure device discovery with `neverForLocation`. |

### Legacy path (API ≤ 30)

Declared in the manifest with `android:maxSdkVersion="30"`:

- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`

At runtime the app asks for **fine location**. That surprised many users historically: “Why does a Bluetooth app need my location?” Because older Android tied BLE scan results to the location permission model.

### Modern path (API 31+)

- `BLUETOOTH_SCAN` with `usesPermissionFlags="neverForLocation"`
- `BLUETOOTH_CONNECT`

At runtime the app asks for those nearby-device permissions. The UI banner shows which model the phone is using.

**Code to read:** `bluetooth/BluetoothPermissions.kt` and `AndroidManifest.xml`.

---

## 5. Best practices this app already follows

| Practice | Why it matters |
|---|---|
| Get the adapter from `BluetoothManager` | Preferred over deprecated `BluetoothAdapter.getDefaultAdapter()` |
| Ask the user to enable BT via `ACTION_REQUEST_ENABLE` | Do not call deprecated `enable()` yourself |
| Stop scanning in `ON_STOP` | Scanning burns battery and can block other apps |
| Suppress “missing permission” only after a real check | Lint warnings are about runtime security; check first, then call APIs |
| Merge devices by address | Classic and BLE can see the same hardware twice |

---

## 6. How to read this codebase

Start here, in order:

1. **`AndroidManifest.xml`**  
   See legacy permissions capped at API 30 and modern nearby permissions.

2. **`BluetoothPermissions.kt`**  
   One `if (SDK_INT >= S)` chooses the runtime permission array.

3. **`BluetoothScanner.kt`**  
   Classic receiver + BLE `ScanCallback`. Comments mark evolution points (for example parcelable extras on API 33+).

4. **`BluetoothScanViewModel.kt`**  
   Holds UI state, starts/stops the scanner, merges devices, listens for adapter on/off.

5. **`MainActivity.kt` + `ScanScreen.kt` + `DeviceDetailScreen.kt`**  
   Permissions, enable-BT, scan list, tap → GATT phase UI.

6. **`GattClient.kt` / `GattModels.kt`**  
   Connect / discover / disconnect state machine.

### Suggested experiment

1. Run on an **Android 12+** phone → note the permission dialog (nearby devices).
2. If you have an older device or emulator image ≤ 11 → note **location** instead.
3. Scan **BLE**, then **Classic**, then **Both**. Watch which gadgets appear in which mode.
4. Put the app in the background while scanning → scanning should stop.

---

## 7. Glossary

| Term | Meaning |
|---|---|
| **Adapter** | The phone’s Bluetooth radio controller (`BluetoothAdapter`) |
| **MAC / address** | Hardware identifier string like `AA:BB:CC:DD:EE:FF` |
| **Bonded / paired** | Device has a saved security relationship with the phone |
| **GATT** | BLE connection protocol for services/characteristics (stub in app now) |
| **Advertise** | Phone broadcasts so others can find it (peripheral role) |
| **LE Audio** | Newer low-energy audio profile family |
| **Channel Sounding / Ranging** | Newer distance measurement APIs (Android 16+) |

---

## 8. GATT client stub (in the app now)

Tap a scanned device → detail screen → **Connect GATT**.

State machine to follow in code (`GattPhase` / `GattClient`):

`Idle → Connecting → Connected → Discovering → Ready`  
(failures go to `Failed`; leave with `Closing → Idle`)

| Piece | Role |
|---|---|
| `BluetoothDevice.connectGatt(...)` | Start client connection to the remote GATT server |
| `BluetoothGattCallback` | Async results (connection + discovery) |
| `discoverServices()` | Ask the peripheral what services it exposes |
| Service / characteristic UUIDs | What it “offers” at the GATT layer |
| `disconnect()` / `close()` | Tear down (always close when done) |

Read next: [Connect to a GATT server](https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server).

### Still to learn next

1. **Read / notify** — pull a characteristic, subscribe to updates.
2. **Classic `BluetoothSocket`** — RFCOMM stub for the Classic overview path.
3. **Advertise** — phone as peripheral (`BLUETOOTH_ADVERTISE`).
4. **Companion Device Manager** — association / presence.
5. **RangingManager** — distance when hardware + OS support it.

Scanning stays the foundation: if you cannot find the device, nothing later will work.

---

## 9. Common beginner pitfalls

- **Emulator has no radio** — use a real phone for meaningful results.
- **Bluetooth off** — permissions alone are not enough.
- **Legacy BLE with Location off** — permission granted but empty list; turn Location on (API ≤ 30).
- **Forgot to stop scanning** — battery drain and flaky results; this app stops on `ON_STOP`.
- **Expecting Classic APIs to find every BLE sensor** — wrong stack; use BLE scan.
- **Treating RSSI as exact distance** — it varies with pockets, bodies, and interference.

---

## 10. Official docs (when you want depth)

- [Bluetooth overview](https://developer.android.com/develop/connectivity/bluetooth)
- [Find BLE devices](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices)
- [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Range between devices](https://developer.android.com/develop/connectivity/ranging) (newer platform feature)

When the README and this overview disagree with live platform behavior, trust the official docs and the `SDK_INT` checks in the code.
