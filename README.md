# Android Tracker

Monorepo for an Android device-usage tracking system: a client app that records
app-usage events and device metrics, and a server that ingests them from many
devices into PostgreSQL.

## Repository layout

```
.
├── client/    # Android application (Kotlin, Jetpack Compose)
│   └── README.md   # Client-specific docs (architecture, permissions, building)
├── server/    # Ingestion server (Python, FastAPI, async psycopg3 + PostgreSQL)
└── .github/   # CI workflows
```

| Folder   | Stack                                      | Role                                                      |
|----------|--------------------------------------------|-----------------------------------------------------------|
| `client/`| Kotlin, Compose, Room, Hilt, WorkManager, Ktor | Periodically captures usage + device metrics and pushes them to the server |
| `server/`| Python 3.12, FastAPI, async psycopg3, PostgreSQL | Idempotently stores batches from multiple devices, keyed by `deviceId` |

## How it works

- The **client** (`client/`) polls `UsageStatsManager` and device metrics into a
  local Room database, then pushes only *unsynced* records to a configurable
  server over HTTP/JSON.
- The **server** (`server/`) receives those batches and deduplicates by
  `(deviceId, clientId)` so client retries are idempotent. It stores data in
  PostgreSQL and supports any number of devices, each identified by `deviceId`.
- Both sides share the same HTTP API contract (see `server/README.md`).

## Running

### Server

```bash
cd server
uv sync
uv run uvicorn tracker_server.main:app --host 0.0.0.0 --port 8000
```

Or run it as a container with podman/docker (see `server/README.md` and the
`server/Dockerfile`).

### Client

```bash
cd client
./gradlew :app:assembleDebug
```

Continuous integration (e.g. GitHub Actions, `.github/workflows/build.yml`)
builds the debug APK on every push/PR and uploads it as an artifact.

## API contract

- `GET /api/v1/health` — server health + time sync.
- `POST /api/v1/usage-events/batch` — push usage events for a device.
- `POST /api/v1/device-metrics/batch` — push device metrics for a device.

Full details, including request/response schemas, are documented in
[`server/README.md`](server/README.md).
