"""POST /api/v1/usage-events/batch endpoint."""

from fastapi import APIRouter

from tracker_server.schemas import BatchResponse, UsageBatchRequest
from tracker_server.services.ingest import ingest

router = APIRouter()

_USAGE_COLUMNS = [
    "event_type",
    "package_name",
    "class_name",
    "timestamp",
]


@router.post("/usage-events/batch", response_model=BatchResponse)
async def push_usage_events(payload: UsageBatchRequest) -> BatchResponse:
    rows = [
        (
            e.clientId,
            e.eventType,
            e.packageName,
            e.className,
            e.timestamp,
        )
        for e in payload.events
    ]
    return await ingest(
        table="usage_events",
        device_id=payload.deviceId,
        columns=_USAGE_COLUMNS,
        rows=rows,
    )
