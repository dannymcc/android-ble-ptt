# Android BLE PTT (PoC)

Proof of concept for talking to "Zello-style" Bluetooth LE push-to-talk buttons (Focket PTT-Z01, PRYME BT-PTT-Z, and the broader HM-10 / TI CC254x family) from Android.

Built as a styling-compatible companion to [VoxDMR](https://voxdmr.jcalado.com) so the code is easy to lift into the app itself. See [voxdmr-site#9](https://github.com/jcalado/voxdmr-site/issues/9) for the feature request this PoC is meant to unblock.

## Get the APK

Grab the latest debug build from **[Releases → Latest build (main)](../../releases/tag/latest-main)**. Sideload it via your phone's file manager, or install via ADB:

```sh
adb install android-ble-ptt-main.apk
```

Tagged versions (`v0.1.0`, etc.) also publish to Releases.

## What it does

- **PTT tab** — a big TX indicator. When the BLE button is held the indicator goes coral; on release it returns to dark. That's it — no audio, no DMR, no network. The point is to prove the press round-trips into the app reliably.
- **Settings tab** — sectioned like VoxDMR's existing Settings screen. Under *Hardware*, the *BLE PTT button* row sits next to a placeholder *Hardware key* row so it's obvious where the new option slots in. Tapping the BLE row opens a bottom-sheet picker that scans for buttons and remembers the one you pick. Paired buttons appear inline below with connection state and a remove control.
- **Verified hardware** — Focket **PTT-Z01**. Anything else using the HM-10 / TI CC254x `0xFFE0` / `0xFFE1` transparent-UART profile should work without changes; the community DIY [oryjkov/100pct-ptt](https://github.com/oryjkov/100pct-ptt) is a known compatible reference.

### Background reliability (Zello-style)

The whole reason for a native BLE central is that the connection survives screen-off and a different app in the foreground. Four mechanisms make a press observable even on a sleeping, locked phone:

1. **Foreground service** with `foregroundServiceType="connectedDevice"` keeps the process alive and the BLE link open.
2. **`connectGatt(autoConnect=true)` on reconnect** — Android handles wake-on-advertise natively, so the moment the sleeping button re-advertises after the next press, the OS reconnects us. More battery-friendly and more reliable than an app-side polling loop.
3. **Wake lock + haptic on press** — when the button is held, a `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` lock turns the screen on and a short vibration fires. So even with no display and no overlay grant, the rider feels the press registered. Capped at 30 s so a stuck connection can't burn the screen.
4. **Floating TX pill** — a coral `● TX` pill rendered via `TYPE_APPLICATION_OVERLAY` with `FLAG_SHOW_WHEN_LOCKED`, drawn over whatever app is in the foreground or over the lockscreen. Same pattern as Zello. Requires the *Display over other apps* grant, surfaced from Settings → *Background → Floating PTT overlay*.

### Failure-mode handling

- **Bluetooth off** — banner in Settings and the BLE PTT row reroutes to `BluetoothAdapter.ACTION_REQUEST_ENABLE` instead of failing silently. The adapter state is tracked via a `BroadcastReceiver` on `ACTION_STATE_CHANGED`, so the UI updates the instant Bluetooth flips.
- **Missing permissions** — first-time pair flow defers opening the pairing sheet until both runtime permissions and Bluetooth-enabled are confirmed, so the sheet never opens empty.
- **Stale scan results** — Android's BLE scanner replays cached advertisements when a new scan begins, which made already-paired-and-removed buttons keep showing up. Filtered out by checking `ScanResult.timestampNanos` against the scan-start timestamp.

## BLE protocol it expects

Captured against a Focket **PTT-Z01** (BLE address `A4:C1:38:25:66:1D`) with nRF Connect:

| Attribute                   | UUID                                | Properties        | Purpose                                            |
| --------------------------- | ----------------------------------- | ----------------- | -------------------------------------------------- |
| Button service              | `0xFFE0`                            | Primary service   | Identifies the PTT button                          |
| Button state characteristic | `0xFFE1`                            | **Notify**, Write | Notifies `0x01` on press, `0x00` on release        |
| CCCD on `0xFFE1`            | `0x2902`                            | —                 | Write `0x0001` to enable notifications             |

Behaviour:

- The button is powered off when idle. It only advertises for a short window after a physical press, advertising under a name beginning `PTT`.
- On connect, the app subscribes to notifications on `0xFFE1`.
- Each press sends a notify of `0x01`; each release sends `0x00`.
- The app auto-reconnects on disconnect since the button sleeps and re-advertises on the next press.

`0xFFE0` / `0xFFE1` is the classic HM-10 / TI CC254x "transparent UART" BLE profile, so most cheap PTT button modules behave identically. The community DIY project [oryjkov/100pct-ptt](https://github.com/oryjkov/100pct-ptt) uses the same UUIDs and `0x01` / `0x00` encoding.

## Where the code lives

The five files worth looking at if you're lifting any of this into VoxDMR:

- [`ble/PttBleClient.kt`](app/src/main/java/io/dmcc/bleptt/ble/PttBleClient.kt) — the BLE central. Scan, connect, CCCD-enable, notify-handling, adapter-state observer, autoConnect reconnect. Exposes `state`, `pressed`, `events`, `bluetoothEnabled` as Kotlin `StateFlow`/`SharedFlow`.
- [`service/PttForegroundService.kt`](app/src/main/java/io/dmcc/bleptt/service/PttForegroundService.kt) — `connectedDevice` foreground service that holds the link alive and drives the overlay.
- [`overlay/PttOverlayController.kt`](app/src/main/java/io/dmcc/bleptt/overlay/PttOverlayController.kt) — wake lock + vibration + `TYPE_APPLICATION_OVERLAY` pill.
- [`data/PairedRepository.kt`](app/src/main/java/io/dmcc/bleptt/data/PairedRepository.kt) — paired-button persistence (SharedPreferences, ASCII RS/US separators).
- [`ui/SettingsScreen.kt`](app/src/main/java/io/dmcc/bleptt/ui/SettingsScreen.kt) — the VoxDMR-style sectioned-row layout for the pair / list / remove flow.

Per-platform notes (for the cross-platform port back to VoxDMR's Linux + Windows targets):

- **Android:** `BluetoothGatt` + `setCharacteristicNotification`, then write `ENABLE_NOTIFICATION_VALUE` to the `0x2902` descriptor. Needs runtime `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (and Location on Android 8–11).
- **Linux:** BlueZ via D-Bus (or [`bluer`](https://github.com/bluez/bluer) if Rust).
- **Windows:** WinRT `GattCharacteristic.ValueChanged`.

## Build it yourself

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17, Android SDK with `compileSdk = 34`, `minSdk = 26`.

## Permissions

- `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` (Android 12+, `neverForLocation` set on scan so no Location prompt)
- Legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` / `ACCESS_FINE_LOCATION` for Android 8–11
- `POST_NOTIFICATIONS` for the foreground-service notification on Android 13+
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` for the BLE-keepalive service
- `SYSTEM_ALERT_WINDOW` (special permission, user-granted in system Settings) for the floating TX overlay
- `WAKE_LOCK` + `VIBRATE` for the wake-on-press / haptic feedback

## License

MIT
