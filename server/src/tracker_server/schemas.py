"""Pydantic request/response models mirroring the Android client DTOs."""

from pydantic import BaseModel, Field


class UsageEvent(BaseModel):
    clientId: int
    eventType: int
    packageName: str
    className: str | None = None
    timestamp: int


class UsageBatchRequest(BaseModel):
    deviceName: str = Field(min_length=1)
    events: list[UsageEvent] = Field(default_factory=list)


class DeviceMetric(BaseModel):
    clientId: int
    capturedAt: int
    batteryLevel: int | None = None
    batteryState: int | None = None
    storageFreeBytes: int | None = None
    storageTotalBytes: int | None = None
    networkState: int | None = None
    wifiSsid: str | None = None


class MetricsBatchRequest(BaseModel):
    deviceName: str = Field(min_length=1)
    metrics: list[DeviceMetric] = Field(default_factory=list)


class RejectedItem(BaseModel):
    clientId: int
    reason: str | None = None


class BatchResponse(BaseModel):
    acceptedClientIds: list[int] = Field(default_factory=list)
    rejected: list[RejectedItem] = Field(default_factory=list)


class HealthResponse(BaseModel):
    status: str = "ok"
    serverTime: int
    db: str = "up"
