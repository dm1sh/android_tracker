"""POST /api/v1/usage-events/batch endpoint."""

from fastapi import APIRouter

from tracker_server.schemas import BatchResponse, UsageBatchRequest
from tracker_server.services.ingest import ingest_usage

router = APIRouter()


@router.post("/usage-events/batch", response_model=BatchResponse)
async def push_usage_events(payload: UsageBatchRequest) -> BatchResponse:
    return await ingest_usage(payload)
