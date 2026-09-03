"""FastAPI application entry point for the Android Tracker server.

Run with:
    uv run uvicorn tracker_server.main:app --host 0.0.0.0 --port 8000
"""

import logging
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI

from tracker_server import db
from tracker_server.config import settings
from tracker_server.routes import health, metrics, usage

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger("tracker_server.main")


@asynccontextmanager
async def lifespan(_: FastAPI):
    await db.open_pool()
    try:
        await db.init_schema()
    except Exception:
        logger.exception("Failed to initialise database schema")
    yield
    await db.close_pool()


app = FastAPI(title="Android Tracker Server", version="0.1.0", lifespan=lifespan)
app.include_router(health.router, prefix="/api/v1")
app.include_router(usage.router, prefix="/api/v1")
app.include_router(metrics.router, prefix="/api/v1")


def main() -> None:
    uvicorn.run(
        "tracker_server.main:app",
        host="0.0.0.0",
        port=8000,
        proxy_headers=True,
        forwarded_allow_ips="*",
    )


if __name__ == "__main__":
    main()
