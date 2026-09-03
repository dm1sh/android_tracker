"""Migration 001: the original schema as it existed before the rework.

This reflects the schema produced by the pre-migration ``init_schema()``:
devices keyed by a text ``device_id`` with first/last-seen timestamps, and
denormalized usage event / device metric tables against that text id.
"""

from __future__ import annotations

from tracker_server.migrations import Migration

MIGRATION = Migration(
    version="001_initial",
    applied_check="SELECT to_regclass('public.devices') IS NOT NULL",
    statements=[
        """
        CREATE TABLE IF NOT EXISTS devices (
            id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            device_id     TEXT NOT NULL UNIQUE,
            first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """,
        """
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
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS idx_usage_device_time
            ON usage_events (device_id, timestamp)
        """,
        """
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
        )
        """,
        """
        CREATE INDEX IF NOT EXISTS idx_metrics_device_time
            ON device_metrics (device_id, captured_at)
        """,
    ],
)
