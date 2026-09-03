# Tracker Server

Backend for the **Android Tracker** app. Receives usage events and device
metrics from one or more devices (identified by `deviceId`), stores them in a
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
  "deviceId": "Google_Pixel_8",
  "events": [
    { "clientId": 123, "eventType": 1, "packageName": "com.example",
      "className": "MainActivity", "timestamp": 1710000000000 }
  ]
}
```

### `POST /api/v1/device-metrics/batch`

```json
{
  "deviceId": "Google_Pixel_8",
  "metrics": [
    { "clientId": 456, "capturedAt": 1710000000000, "batteryLevel": 87,
      "batteryState": "CHARGING", "storageFreeBytes": 204800000000,
      "storageTotalBytes": 256000000000, "networkState": "WIFI",
      "wifiSsid": "MyNetwork" }
  ]
}
```

Both batch endpoints respond `200` with:

```json
{ "acceptedClientIds": [123], "rejected": [ { "clientId": 789, "reason": "duplicate" } ] }
```

- `acceptedClientIds` — ids that were inserted for the first time.
- `rejected` — ids that were already present (`(deviceId, clientId)` unique),
  reason `"duplicate"`.
