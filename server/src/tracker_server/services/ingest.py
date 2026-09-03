"""Ingestion logic for usage events and device metrics.

Each batch is ingested in a single transaction:

1. Resolve the ``deviceName`` to its numeric ``devices.id`` (upserting the
   device row if new).
2. Resolve repeated string values (package/class/wifi) to their dictionary
   ids, inserting missing entries.
3. Insert each event/metric row with numeric refs, converting epoch-millis
   timestamps to ``TIMESTAMPTZ``. A row is "accepted" when its
   ``(device_ref, client_id)`` insert actually happened; otherwise it already
   exists and is reported as rejected with reason "duplicate" so the client
   stops re-pushing it.
"""

import logging

from psycopg import sql

from tracker_server.db import get_pool
from tracker_server.schemas import (
    BatchResponse,
    DeviceMetric,
    MetricsBatchRequest,
    RejectedItem,
    UsageBatchRequest,
    UsageEvent,
)

logger = logging.getLogger("tracker_server.ingest")


async def _resolve_device(cur, device_name: str) -> int:
    """Return the numeric id for a device name, upserting the device if new."""
    await cur.execute(
        "INSERT INTO devices (device_name) VALUES (%s) "
        "ON CONFLICT (device_name) DO NOTHING RETURNING id",
        (device_name,),
    )
    row = await cur.fetchone()
    if row is not None:
        return row[0]
    await cur.execute(
        "SELECT id FROM devices WHERE device_name = %s", (device_name,)
    )
    return (await cur.fetchone())[0]


async def _resolve_dict(cur, table: str, column: str, values) -> dict[str, int]:
    """Map distinct string values of a dictionary table column to their ids.

    Missing entries are inserted first; the returned mapping covers every
    (non-null) input value.
    """
    distinct = sorted({v for v in values if v is not None})
    if not distinct:
        return {}

    insert = sql.SQL(
        "INSERT INTO {table} ({col}) VALUES (%s) "
        "ON CONFLICT ({col}) DO NOTHING RETURNING {col}, id"
    ).format(table=sql.Identifier(table), col=sql.Identifier(column))

    mapping: dict[str, int] = {}
    existing: list[str] = []
    for name in distinct:
        await cur.execute(insert, (name,))
        row = await cur.fetchone()
        if row is not None:
            mapping[row[0]] = row[1]
        else:
            existing.append(name)

    if existing:
        select = sql.SQL(
            "SELECT {col}, id FROM {table} WHERE {col} IN ({placeholders})"
        ).format(
            table=sql.Identifier(table),
            col=sql.Identifier(column),
            placeholders=sql.SQL(", ").join(sql.Placeholder() for _ in existing),
        )
        await cur.execute(select, existing)
        for name, cid in await cur.fetchall():
            mapping[name] = cid

    return mapping


_INSERT_USAGE = sql.SQL(
    "INSERT INTO usage_events "
    "(device_ref, client_id, event_type, package_ref, class_ref, timestamp) "
    "VALUES (%s, %s, %s, %s, %s, to_timestamp(%s / 1000.0)) "
    "ON CONFLICT (device_ref, client_id) DO NOTHING RETURNING client_id"
)


async def ingest_usage(payload: UsageBatchRequest) -> BatchResponse:
    if not payload.events:
        return BatchResponse()

    pool = await get_pool()
    async with pool.connection() as conn, conn.cursor() as cur:
        device_ref = await _resolve_device(cur, payload.deviceName)
        package_map = await _resolve_dict(
            cur, "packages", "name", (e.packageName for e in payload.events)
        )
        class_map = await _resolve_dict(
            cur, "classes", "name", (e.className for e in payload.events)
        )

        accepted: list[int] = []
        for e in payload.events:
            class_ref = class_map.get(e.className) if e.className else None
            record = await cur.execute(
                _INSERT_USAGE,
                (
                    device_ref,
                    e.clientId,
                    e.eventType,
                    package_map[e.packageName],
                    class_ref,
                    e.timestamp,
                ),
            )
            inserted = await record.fetchone()
            if inserted is not None:
                accepted.append(int(inserted[0]))
        await conn.commit()

    return _accept_reject(payload.events, accepted)


_INSERT_METRICS = sql.SQL(
    "INSERT INTO device_metrics "
    "(device_ref, client_id, captured_at, battery_level, battery_state, "
    " storage_free_bytes, storage_total_bytes, network_state, wifi_ref) "
    "VALUES (%s, %s, to_timestamp(%s / 1000.0), %s, %s, %s, %s, %s, %s) "
    "ON CONFLICT (device_ref, client_id) DO NOTHING RETURNING client_id"
)


async def ingest_metrics(payload: MetricsBatchRequest) -> BatchResponse:
    if not payload.metrics:
        return BatchResponse()

    pool = await get_pool()
    async with pool.connection() as conn, conn.cursor() as cur:
        device_ref = await _resolve_device(cur, payload.deviceName)
        wifi_map = await _resolve_dict(
            cur, "wifi", "ssid", (m.wifiSsid for m in payload.metrics)
        )

        accepted: list[int] = []
        for m in payload.metrics:
            wifi_ref = wifi_map.get(m.wifiSsid) if m.wifiSsid else None
            record = await cur.execute(
                _INSERT_METRICS,
                (
                    device_ref,
                    m.clientId,
                    m.capturedAt,
                    m.batteryLevel,
                    m.batteryState,
                    m.storageFreeBytes,
                    m.storageTotalBytes,
                    m.networkState,
                    wifi_ref,
                ),
            )
            inserted = await record.fetchone()
            if inserted is not None:
                accepted.append(int(inserted[0]))
        await conn.commit()

    return _accept_reject(payload.metrics, accepted)


def _accept_reject(rows: list[UsageEvent] | list[DeviceMetric], accepted) -> BatchResponse:
    client_ids = [r.clientId for r in rows]
    accepted_set = set(accepted)
    rejected = [
        RejectedItem(clientId=cid, reason="duplicate")
        for cid in client_ids
        if cid not in accepted_set
    ]
    return BatchResponse(acceptedClientIds=accepted, rejected=rejected)
