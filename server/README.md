# Tracker Server

Backend for the **Android Tracker** app. Receives usage events and device
metrics from one or more devices (identified by `deviceName`), stores them in a
**PostgreSQL** database, and provides idempotent batch ingestion so clients can
retry safely.

## Tech stack

- **Python** 3.12
- **FastAPI** (async)
- **psycopg3** (async, with built-in connection pool)
- **uv** for project & dependency management

## Configuration

The database connection is configured entirely through environment variables:

| Variable           | Description                  | Default       |
|--------------------|------------------------------|---------------|
| `postgres_user`    | PostgreSQL user              | `tracker`     |
| `postgres_password`| PostgreSQL password          | `changeme`    |
| `postgres_db`      | Database name                | `tracker`     |
| `postgres_host`    | Database host                | `localhost`   |
| `postgres_port`    | Database port (int)          | `5432`        |

See [`.env.example`](.env.example) for a template map. Actual secrets should be
stored in an uncommitted `.env` file (or passed directly to the container).

## Running locally with uv

```bash
cd server
uv sync            # create venv and install deps (generates uv.lock)
uv run uvicorn tracker_server.main:app --host 0.0.0.0 --port 8000
```

The server listens on `0.0.0.0:8000` so devices on the LAN/VPN can reach it.

## Running with podman (Dockerfile)

Build and run the image:

```bash
podman build -t tracker-server server/
podman run --rm -p 8000:8000 --env-file server/.env tracker-server
```

The five `postgres_*` variables must be present in the runtime environment
(`--env-file` reads them from your `.env` file).

## API

All endpoints return/accept JSON over HTTP, no authentication.

### `GET /api/v1/health`

```json
{ "status": "ok", "serverTime": 1710000000000, "db": "up" }
```

`serverTime` is epoch milliseconds. `db` reports connectivity for diagnostics
but the endpoint still returns `ok`.

### `POST /api/v1/usage-events/batch`

```json
{
  "deviceName": "Google_Pixel_8",
  "events": [
    { "clientId": 123, "eventType": 1, "packageName": "com.example",
      "className": "MainActivity", "timestamp": 1710000000000 }
  ]
}
```

### `POST /api/v1/device-metrics/batch`

```json
{
  "deviceName": "Google_Pixel_8",
  "metrics": [
    { "clientId": 456, "capturedAt": 1710000000000, "batteryLevel": 87,
      "batteryState": 2, "storageFreeBytes": 204800000000,
      "storageTotalBytes": 256000000000, "networkState": 1,
      "wifiSsid": "MyNetwork" }
  ]
}
```

`batteryState` is the raw Android `BatteryManager.BATTERY_STATUS_*` constant:

| Value | Meaning       |
|-------|---------------|
| `1`   | `UNKNOWN`     |
| `2`   | `CHARGING`    |
| `3`   | `DISCHARGING` |
| `4`   | `NOT_CHARGING`|
| `5`   | `FULL`        |

`networkState` is the raw `NetworkCapabilities.TRANSPORT_*` constant
(`CELLULAR=0`, `WIFI=1`, `BLUETOOTH=2`, `ETHERNET=3`); `NONE`/`VPN`/`OTHER`
are sent as `null`.

Both batch endpoints respond `200` with:

```json
{ "acceptedClientIds": [123], "rejected": [ { "clientId": 789, "reason": "duplicate" } ] }
```

- `acceptedClientIds` — ids that were inserted for the first time.
- `rejected` — ids that were already present
  (`(device_ref, client_id)` unique), reason `"duplicate"`.

## Database schema & migrations

The schema is evolved with an ordered, idempotent migration framework
(`tracker_server/migrations`). On startup `db.init_schema()` applies each
pending migration once, in its own transaction, tracking applied versions in
the `schema_migrations` table. Add a future change as a new module with a newer
`version` and register it in `migrations/__init__.py` `MIGRATIONS`.

Highlights:

- `devices` is keyed by `id`, with a unique `device_name`.
- `packages`, `classes`, `wifi` are dictionary tables; `usage_events` /
  `device_metrics` reference them by numeric id (`package_ref`, `class_ref`,
  `wifi_ref`).
- Epoch-millis ints on the wire are stored as `TIMESTAMPTZ`
  (`to_timestamp(millis / 1000.0)`); `class_ref` and `wifi_ref` are nullable.
- Ingestion is deduplicated on `(device_ref, client_id)`.

> **Breaking change (schema v2):** JSON fields renamed `deviceId` -> `deviceName`,
> `batteryState`/`networkState` became `int|null`, and the storage layout
> changed. The schema upgrade is applied automatically in place on existing
> databases, but old-format clients must be updated to the new wire format.
