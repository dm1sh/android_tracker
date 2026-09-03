"""Asynchronous PostgreSQL access layer using psycopg3's async connection pool."""

import logging

from psycopg_pool import AsyncConnectionPool
from psycopg import sql

from tracker_server.config import settings

logger = logging.getLogger("tracker_server.db")

pool: AsyncConnectionPool | None = None


async def open_pool() -> AsyncConnectionPool:
    """Create and open the global connection pool."""
    global pool
    logger.info("Opening PostgreSQL pool for %s@%s:%s/%s",
                settings.postgres_user, settings.postgres_host,
                settings.postgres_port, settings.postgres_db)
    pool = AsyncConnectionPool(
        settings.conninfo,
        min_size=1,
        max_size=10,
        open=False,
    )
    await pool.open(wait=True, timeout=10.0)
    return pool


async def close_pool() -> None:
    """Close the global connection pool if open."""
    global pool
    if pool is not None:
        await pool.close()
        pool = None


async def get_pool() -> AsyncConnectionPool:
    """Return the global pool, raising if it is not initialised."""
    if pool is None:
        raise RuntimeError("Database pool has not been initialised")
    return pool


SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS devices (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id     TEXT NOT NULL UNIQUE,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS usage_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id    TEXT   NOT NULL REFERENCES devices(device_id),
    client_id    BIGINT NOT NULL,
    event_type   INT    NOT NULL,
    package_name TEXT   NOT NULL,
    class_name   TEXT,
    timestamp    BIGINT NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_usage_device_client UNIQUE (device_id, client_id)
);
CREATE INDEX IF NOT EXISTS idx_usage_device_time
    ON usage_events (device_id, timestamp);

CREATE TABLE IF NOT EXISTS device_metrics (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id           TEXT   NOT NULL REFERENCES devices(device_id),
    client_id           BIGINT NOT NULL,
    captured_at         BIGINT NOT NULL,
    battery_level       INT,
    battery_state       TEXT,
    storage_free_bytes  BIGINT,
    storage_total_bytes BIGINT,
    network_state       TEXT,
    wifi_ssid           TEXT,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_metrics_device_client UNIQUE (device_id, client_id)
);
CREATE INDEX IF NOT EXISTS idx_metrics_device_time
    ON device_metrics (device_id, captured_at);
"""


async def init_schema() -> None:
    """Apply the schema idempotently at application startup."""
    p = await get_pool()
    async with p.connection() as conn, conn.cursor() as cur:
        await cur.execute(SCHEMA_SQL)
        await conn.commit()
    logger.info("Database schema is ready")


async def db_is_healthy() -> bool:
    """Run a trivial query to report DB connectivity for /health."""
    try:
        p = await get_pool()
        async with p.connection() as conn:
            await (await conn.execute("SELECT 1"))
        return True
    except Exception:
        return False


def quote_ident(name: str) -> sql.Composed:
    """Build a safely quoted identifier for dynamic table names."""
    return sql.Identifier(name)
