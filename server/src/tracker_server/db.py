"""Asynchronous PostgreSQL access layer using psycopg3's async connection pool."""

import logging

from psycopg_pool import AsyncConnectionPool

from tracker_server.config import settings
from tracker_server.migrations import MIGRATIONS

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


_CREATE_MIGRATIONS_TABLE = """
CREATE TABLE IF NOT EXISTS schema_migrations (
    version    TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
"""


async def _migration_applied(cur, version: str) -> bool:
    await cur.execute(
        "SELECT 1 FROM schema_migrations WHERE version = %s", (version,)
    )
    return (await cur.fetchone()) is not None


async def _record_applied(cur, version: str) -> None:
    await cur.execute(
        "INSERT INTO schema_migrations (version) VALUES (%s) ON CONFLICT (version) DO NOTHING",
        (version,),
    )


async def init_schema() -> None:
    """Ensure the schema is up to date by applying pending migrations."""
    p = await get_pool()
    async with p.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(_CREATE_MIGRATIONS_TABLE)

            for migration in MIGRATIONS:
                if await _migration_applied(cur, migration.version):
                    logger.info("Migration %s already applied", migration.version)
                    continue

                # If the version's resulting state already exists (pre-existing
                # database), record it without running its DDL.
                await cur.execute(migration.applied_check)
                already_present = (await cur.fetchone())[0] is True
                if already_present:
                    logger.info(
                        "Migration %s state already present, recording without DDL",
                        migration.version,
                    )
                    await _record_applied(cur, migration.version)
                    await conn.commit()
                    continue

                for statement in migration.statements:
                    await cur.execute(statement)
                await _record_applied(cur, migration.version)
                await conn.commit()
                logger.info("Applied migration %s", migration.version)

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
