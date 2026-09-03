"""Shared ingestion logic for usage events and device metrics.

Both batch endpoints receive a list of rows from a single device. For each row
the device record is upserted, then each row is inserted with a unique
(device_id, client_id) constraint using ON CONFLICT DO NOTHING. A row whose
insert actually happened is "accepted"; if the insert was skipped because the
(device_id, client_id) pair already exists, it is reported as rejected with
reason "duplicate" so the client stops re-pushing it.
"""

import logging

from psycopg import sql

from tracker_server.schemas import BatchResponse, RejectedItem
from tracker_server.db import get_pool

logger = logging.getLogger("tracker_server.ingest")

_DEVICE_UPSERT = sql.SQL(
    "INSERT INTO devices (device_id) VALUES ({0}) "
    "ON CONFLICT (device_id) DO UPDATE SET last_seen_at = now()"
)


async def ingest(
    table: str,
    device_id: str,
    columns: list[str],
    rows: list[tuple],
) -> BatchResponse:
    """Insert rows into `table` for `device_id`.

    Each tuple in `rows` is (client_id, *values) where `values` correspond to
    `columns` in order. Returns the accept/reject response.
    """
    if not rows:
        return BatchResponse()

    pool = await get_pool()
    prepared = sql.SQL(
        "INSERT INTO {table} ({cols}) VALUES ({vals}) "
        "ON CONFLICT (device_id, client_id) DO NOTHING RETURNING client_id"
    ).format(
        table=sql.Identifier(table),
        cols=sql.SQL(", ").join(
            [sql.Identifier("device_id"), sql.Identifier("client_id")]
            + [sql.Identifier(c) for c in columns]
        ),
        vals=sql.SQL(", ").join(
            [sql.Placeholder()] * (2 + len(columns))
        ),
    )

    accepted: list[int] = []
    async with pool.connection() as conn, conn.cursor() as cur:
        await cur.execute(_DEVICE_UPSERT, (device_id,))
        for client_id, *values in rows:
            record = await cur.execute(
                prepared, (device_id, client_id, *values)
            )
            inserted = await record.fetchone()
            if inserted is not None:
                accepted.append(int(inserted[0]))
        await conn.commit()

    rejected_set = [r[0] for r in rows if r[0] not in set(accepted)]
    rejected = [
        RejectedItem(clientId=cid, reason="duplicate") for cid in rejected_set
    ]
    return BatchResponse(acceptedClientIds=accepted, rejected=rejected)
