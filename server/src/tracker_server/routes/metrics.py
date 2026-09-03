"""POST /api/v1/device-metrics/batch endpoint."""

from fastapi import APIRouter

from tracker_server.schemas import BatchResponse, MetricsBatchRequest
from tracker_server.services.ingest import ingest

router = APIRouter()

_METRICS_COLUMNS = [
    "captured_at",
    "battery_level",
    "battery_state",
    "storage_free_bytes",
    "storage_total_bytes",
    "network_state",
    "wifi_ssid",
]


@router.post("/device-metrics/batch", response_model=BatchResponse)
async def push_device_metrics(payload: MetricsBatchRequest) -> BatchResponse:
    rows = [
        (
            m.clientId,
            m.capturedAt,
            m.batteryLevel,
            m.batteryState,
            m.storageFreeBytes,
            m.storageTotalBytes,
            m.networkState,
            m.wifiSsid,
        )
        for m in payload.metrics
    ]
    return await ingest(
        table="device_metrics",
        device_id=payload.deviceId,
        columns=_METRICS_COLUMNS,
        rows=rows,
    )
