"""GET /api/v1/health endpoint."""

import time

from fastapi import APIRouter

from tracker_server.db import db_is_healthy
from tracker_server.schemas import HealthResponse

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    db_up = await db_is_healthy()
    return HealthResponse(
        status="ok",
        serverTime=int(time.time() * 1000),
        db="up" if db_up else "down",
    )
