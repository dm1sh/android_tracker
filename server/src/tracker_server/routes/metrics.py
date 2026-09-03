"""POST /api/v1/device-metrics/batch endpoint."""

from fastapi import APIRouter

from tracker_server.schemas import BatchResponse, MetricsBatchRequest
from tracker_server.services.ingest import ingest_metrics

router = APIRouter()


@router.post("/device-metrics/batch", response_model=BatchResponse)
async def push_device_metrics(payload: MetricsBatchRequest) -> BatchResponse:
    return await ingest_metrics(payload)
