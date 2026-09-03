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

## Local self-contained toolchain

For development and debugging without any system-wide JDK or Android SDK
installed, this project keeps its own toolchain **inside the `client/`
directory**. It is git-ignored, so it never affects the CI build.

| Directory | Contents |
|-----------|----------|
| `client/.java-jre/` | Temurin JDK 17 (`jdk-17.0.20.1+1`) |
| `client/.android-sdk/` | Android SDK command-line tools, `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools` (includes `adb`) |

The `client/local.properties` file points Gradle at the local SDK
(`sdk.dir=.android-sdk`). It is git-ignored and not required by CI (which
installs its own SDK via GitHub Actions).

To build with the local toolchain, set `JAVA_HOME` to the bundled JDK:

```bash
cd client
JAVA_HOME="$(pwd)/.java-jre/jdk-17.0.20.1+1" ./gradlew :app:assembleDebug
```

For convenience in interactive shells, you can export the paths once:

```bash
export JAVA_HOME="$(pwd)/.java-jre/jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$(pwd)/.android-sdk/platform-tools:$PATH"
```

### Re-creating the toolchain from scratch

If the `.java-jre/` or `.android-sdk/` directories are deleted, they can be
rebuilt with:

```bash
cd client

# JDK 17 (Temurin)
mkdir -p .java-jre
curl -fSL -o /tmp/jdk17.tar.gz \
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz"
tar xzf /tmp/jdk17.tar.gz -C .java-jre
rm /tmp/jdk17.tar.gz

# Android SDK command-line tools
mkdir -p .android-sdk/cmdline-tools
curl -fSL -o /tmp/cmdline-tools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d /tmp
mv /tmp/cmdline-tools .android-sdk/cmdline-tools/latest
rm /tmp/cmdline-tools.zip

# Install SDK packages (note: target dirs are used as-is; delete any
# left-over "-2" duplicates if a previous install was interrupted)
yes | .android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=.android-sdk \
  "platform-tools" "build-tools;37.0.0" "platforms;android-37.0"
```

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
