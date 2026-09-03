"""Migration 002: normalize + re-key the schema for the ingestion rework.

Upgrades the ``001_initial`` schema in place:

* ``devices``: rename ``device_id`` -> ``device_name``, drop first/last-seen.
* Add dictionary tables ``packages``, ``classes``, ``wifi`` and backfill them
  from existing denormalized values.
* ``usage_events`` / ``device_metrics``: replace the text device id with a
  numeric FK to ``devices(id)``, replace package/class/ssid strings with
  dictionary FKs, and convert epoch-millis timestamps to ``TIMESTAMPTZ``.
* ``battery_state`` / ``network_state`` become integers using the raw Android
  ``BatteryManager.BATTERY_STATUS_*`` and ``NetworkCapabilities.TRANSPORT_*``
  constant values; old string values that don't map become NULL.
"""

from __future__ import annotations

from tracker_server.migrations import Migration

MIGRATION = Migration(
    version="002_normalize_and_types",
    applied_check=(
        "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
        "WHERE table_name = 'devices' AND column_name = 'device_name')"
    ),
    statements=[
        # ---- devices ----
        "ALTER TABLE devices RENAME COLUMN device_id TO device_name",
        "ALTER TABLE devices DROP COLUMN first_seen_at",
        "ALTER TABLE devices DROP COLUMN last_seen_at",
        # ---- dictionary tables ----
        "CREATE TABLE packages (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name TEXT NOT NULL UNIQUE)",
        "CREATE TABLE classes (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name TEXT NOT NULL UNIQUE)",
        "CREATE TABLE wifi (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, ssid TEXT NOT NULL UNIQUE)",
        "INSERT INTO packages (name) SELECT DISTINCT package_name FROM usage_events ON CONFLICT (name) DO NOTHING",
        "INSERT INTO classes (name) SELECT DISTINCT class_name FROM usage_events WHERE class_name IS NOT NULL ON CONFLICT (name) DO NOTHING",
        "INSERT INTO wifi (ssid) SELECT DISTINCT wifi_ssid FROM device_metrics WHERE wifi_ssid IS NOT NULL ON CONFLICT (ssid) DO NOTHING",
        # ---- usage_events: add refs + timestamptz ----
        "ALTER TABLE usage_events ADD COLUMN device_ref BIGINT REFERENCES devices(id)",
        "ALTER TABLE usage_events ADD COLUMN package_ref BIGINT REFERENCES packages(id)",
        "ALTER TABLE usage_events ADD COLUMN class_ref BIGINT REFERENCES classes(id)",
        "ALTER TABLE usage_events ADD COLUMN timestamp_ts TIMESTAMPTZ",
        """
        UPDATE usage_events AS ue
        SET device_ref   = d.id,
            package_ref  = p.id,
            class_ref    = c.id,
            timestamp_ts = to_timestamp(ue.timestamp / 1000.0)
        FROM devices AS d
        CROSS JOIN packages AS p
        LEFT JOIN classes AS c ON c.name = ue.class_name
        WHERE d.device_name = ue.device_id
          AND p.name = ue.package_name
        """,
        # ---- usage_events: enforce refs, drop old columns, re-key ----
        "ALTER TABLE usage_events ALTER COLUMN device_ref SET NOT NULL",
        "ALTER TABLE usage_events ALTER COLUMN package_ref SET NOT NULL",
        "ALTER TABLE usage_events ALTER COLUMN timestamp_ts SET NOT NULL",
        "ALTER TABLE usage_events DROP CONSTRAINT uq_usage_device_client",
        "DROP INDEX IF EXISTS idx_usage_device_time",
        "ALTER TABLE usage_events DROP COLUMN device_id",
        "ALTER TABLE usage_events DROP COLUMN package_name",
        "ALTER TABLE usage_events DROP COLUMN class_name",
        "ALTER TABLE usage_events DROP COLUMN timestamp",
        "ALTER TABLE usage_events RENAME COLUMN timestamp_ts TO timestamp",
        "ALTER TABLE usage_events ADD CONSTRAINT uq_usage_device_client UNIQUE (device_ref, client_id)",
        "CREATE INDEX idx_usage_device_time ON usage_events (device_ref, timestamp)",
        # ---- device_metrics: add refs + timestamptz + int enums ----
        "ALTER TABLE device_metrics ADD COLUMN device_ref BIGINT REFERENCES devices(id)",
        "ALTER TABLE device_metrics ADD COLUMN wifi_ref BIGINT REFERENCES wifi(id)",
        "ALTER TABLE device_metrics ADD COLUMN captured_at_ts TIMESTAMPTZ",
        "ALTER TABLE device_metrics ADD COLUMN battery_state_int INT",
        "ALTER TABLE device_metrics ADD COLUMN network_state_int INT",
        """
        UPDATE device_metrics AS dm
        SET device_ref        = d.id,
            wifi_ref          = w.id,
            captured_at_ts    = to_timestamp(dm.captured_at / 1000.0),
            battery_state_int = CASE dm.battery_state
                WHEN 'UNKNOWN'      THEN 1
                WHEN 'CHARGING'     THEN 2
                WHEN 'DISCHARGING'  THEN 3
                WHEN 'NOT_CHARGING' THEN 4
                WHEN 'FULL'         THEN 5
                ELSE NULL END,
            network_state_int = CASE dm.network_state
                WHEN 'CELLULAR'   THEN 0
                WHEN 'WIFI'       THEN 1
                WHEN 'BLUETOOTH'  THEN 2
                WHEN 'ETHERNET'   THEN 3
                ELSE NULL END
        FROM devices AS d
        LEFT JOIN wifi AS w ON w.ssid = dm.wifi_ssid
        WHERE d.device_name = dm.device_id
        """,
        # ---- device_metrics: enforce refs, drop old columns, re-key ----
        "ALTER TABLE device_metrics ALTER COLUMN device_ref SET NOT NULL",
        "ALTER TABLE device_metrics ALTER COLUMN captured_at_ts SET NOT NULL",
        "ALTER TABLE device_metrics DROP CONSTRAINT uq_metrics_device_client",
        "DROP INDEX IF EXISTS idx_metrics_device_time",
        "ALTER TABLE device_metrics DROP COLUMN device_id",
        "ALTER TABLE device_metrics DROP COLUMN wifi_ssid",
        "ALTER TABLE device_metrics DROP COLUMN battery_state",
        "ALTER TABLE device_metrics DROP COLUMN network_state",
        "ALTER TABLE device_metrics DROP COLUMN captured_at",
        "ALTER TABLE device_metrics RENAME COLUMN captured_at_ts TO captured_at",
        "ALTER TABLE device_metrics RENAME COLUMN battery_state_int TO battery_state",
        "ALTER TABLE device_metrics RENAME COLUMN network_state_int TO network_state",
        "ALTER TABLE device_metrics ADD CONSTRAINT uq_metrics_device_client UNIQUE (device_ref, client_id)",
        "CREATE INDEX idx_metrics_device_time ON device_metrics (device_ref, captured_at)",
    ],
)
