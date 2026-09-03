"""Ordered, idempotent database migrations for the tracker server.

Migrations are applied in ascending version order, each at most once, in its
own transaction. The set of already-applied versions is tracked in the
`schema_migrations` table.

Two compatibility cases are handled:

* A **fresh** database runs every migration in order.
* A database that already holds a previous schema state (e.g. one created by
  the pre-migration ``init_schema()``) is detected via each migration's
  :attr:`Migration.applied_check`: if the check reports the migration's result
  is already present, the version is recorded and its DDL is skipped.

Adding a future schema change means appending a new module (e.g.
``003_foo.py``) with a newer ``version`` and registering it in
:data:`MIGRATIONS`; deployed databases pick it up on the next startup.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Migration:
    """A single versioned migration."""

    version: str
    """Unique, ordered version label (e.g. "001_initial")."""

    applied_check: str
    """SQL returning a single boolean row that is true when this migration's
    resulting schema is already present (used for pre-existing databases)."""

    statements: list[str]
    """SQL statements to run, in order, inside one transaction."""


from tracker_server.migrations import m001_initial, m002_upgrade  # noqa: E402

MIGRATIONS: tuple[Migration, ...] = (
    m001_initial.MIGRATION,
    m002_upgrade.MIGRATION,
)
