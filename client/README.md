# Android Tracker

An Android application that periodically records device usage and metrics, stores them in a local Room database, and pushes them to a configurable server over HTTP.

- **App name:** Android Tracker
- **Application ID:** `dm1sh.android_tracker`
- **minSdk 25 (Android 7.1)** · **targetSdk 37 (Android 17)** · **compileSdk 37**

## Features

- Periodically fetches `UsageStatsManager` events and stores them locally.
- Periodically records device metrics (battery level/state, storage free/total, network state, Wi-Fi SSID) into a separate table.
- Pushes only *unsynced* local records to the server and marks them synced after acknowledgement.
- Settings screen to configure:
  - Server URL
  - Fetch interval (minutes)
  - Push interval (minutes)
  - Device ID (defaults to the device model name)
  - Buttons to run a local DB update or a server push manually

## Architecture

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose (Material 3) |
| DI | Hilt |
| Persistence | Room |
| Background | WorkManager |
| HTTP | Ktor client (OkHttp engine) + kotlinx-serialization |
| Settings | DataStore (Preferences) |

Two periodic works drive collection with independent intervals:

- `usage-fetch` — fetches usage events **and** captures device metrics in a
  single run; interval from the **fetch interval** setting.
- `server-push` — pushes unsynced records to the server; interval from the
  **push interval** setting, constrained to a connected network
  (`NetworkType.CONNECTED`).

WorkManager enforces a **15-minute minimum** periodic interval; the UI clamps/validates inputs accordingly.

Workers run once with **no retries**. If a run fails (e.g. usage access is
missing or the server is unreachable), the failure is recorded in the status
card rather than retried.

## Permissions

Declared in the manifest and handled at runtime:

- `PACKAGE_USAGE_STATS` — special permission granting access to usage events; user is redirected to the Usage Access settings page.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — needed to read the Wi-Fi SSID (Android 13+). If denied, SSID is stored as `null`.
- `ACCESS_LOCAL_NETWORK` — required on Android 16/17+ (API 36+) to reach **local** servers; requested at runtime on those versions. On older versions access is implicit via `INTERNET`.
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — normal network permissions.

## Notes

- Usage events are only queryable while the device is unlocked, and only when at least one recent app event exists.
- The server is expected to run inside a VPN environment, so no authentication is used; each payload carries a `deviceId`.
- Because the server may be reached over a VPN/remote address, `ACCESS_LOCAL_NETWORK` is primarily relevant when pointing the server URL at a LAN address during development.

## Building

Continuous integration builds the debug APK via GitHub Actions (`.github/workflows/build.yml`). The workflow runs on every push/PR and on a nightly schedule, then uploads the APK as a build artifact.

Toolchain used by the project:

- JDK 17
- AGP 9.2.0 (with built-in Kotlin) — required for `compileSdk 37`
- Gradle 9.4.1
- `compileSdk`/`targetSdk` = 37 (Android 17), `minSdk` = 25

Open the project in Android Studio, or build locally from the command line:

```bash
cd client
./gradlew :app:assembleDebug
```

The resulting APK is written to `app/build/outputs/apk/debug/`.

## Server API

JSON over HTTP, no authentication. Every request includes a `deviceId`; the server should deduplicate by `(deviceId, clientId)` so retries are idempotent. On success the client marks only the acknowledged `clientId`s as synced.

### 1. Push usage events

```
POST /api/v1/usage-events/batch
```

Request:

```json
{
  "deviceId": "Google_Pixel_8",
  "events": [
    { "clientId": 123, "eventType": 1, "packageName": "com.example", "className": "MainActivity", "timestamp": 1710000000000 }
  ]
}
```

Response `200`:

```json
{
  "acceptedClientIds": [123],
  "rejected": [ { "clientId": 789, "reason": "duplicate" } ]
}
```

### 2. Push device metrics

```
POST /api/v1/device-metrics/batch
```

Request:

```json
{
  "deviceId": "Google_Pixel_8",
  "metrics": [
    {
      "clientId": 456,
      "capturedAt": 1710000000000,
      "batteryLevel": 87,
      "batteryState": "CHARGING",
      "storageFreeBytes": 204800000000,
      "storageTotalBytes": 256000000000,
      "networkState": "WIFI",
      "wifiSsid": "MyNetwork"
    }
  ]
}
```

Response: same `acceptedClientIds` / `rejected` shape as above.

### 3. Health / time sync (optional)

```
GET /api/v1/health
```

Response `200`:

```json
{ "status": "ok", "serverTime": 1710000000000 }
```

The client does not currently use `serverTime`, but it is provided for optional clock-offset correction.
