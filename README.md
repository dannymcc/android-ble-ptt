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

- **PTT tab** — a big TX indicator. When the BLE button is pressed, the indicator goes coral; on release, it returns to dark.
- **Settings tab** — sectioned like VoxDMR. Under *Hardware*, the *BLE PTT button* row opens a pairing sheet that scans for buttons, lets you pick one, and remembers it. Paired buttons sit inline below with connection state and a remove icon. Under *Background*, a *Floating PTT overlay* row links to the "Display over other apps" grant.
- **Background reliability**
  - Foreground service with `foregroundServiceType=connectedDevice` keeps the BLE link alive with the screen off and other apps in the foreground.
  - Auto-reconnect uses `connectGatt(autoConnect=true)` so Android handles wake-on-advertise natively when the button sleeps and re-advertises after the next press.
  - **Floating TX pill** — when the button is held, a coral "● TX" pill appears at the top of the screen over whatever app is in the foreground (or over the lockscreen). Same pattern as Zello, so you can confirm a press without unlocking the phone.
- **Bluetooth-off gating** — if Bluetooth is off, Settings shows a banner and the BLE PTT row reroutes to the OS enable prompt instead of failing silently.

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

## Where the BLE code lives

`PttBleClient` in [`app/src/main/java/io/dmcc/bleptt/ble/PttBleClient.kt`](app/src/main/java/io/dmcc/bleptt/ble/PttBleClient.kt) is the bit a desktop developer cares about. It exposes:

- `state` — connection state (idle / scanning / connecting / connected / disconnected / error)
- `pressed` — `true` while the button is held, `false` on release
- `events` — flow of `Pressed` / `Released` / `Raw` events with timestamps
- `startScan()` / `stopScan()` / `connect(address)` / `disconnect()`

Foreground operation is in [`PttForegroundService`](app/src/main/java/io/dmcc/bleptt/service/PttForegroundService.kt) with `foregroundServiceType="connectedDevice"`.

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

## License

MIT
