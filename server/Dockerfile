# Tracker Server container image (podman / docker compatible).
# Multi-stage: resolve and install dependencies with uv, then run in a slim
# Python image with only the runtime venv.

# --- Builder stage ---
FROM python:3.12-slim AS builder

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

# Install uv in the builder image.
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /app

# Copy dependency manifests and install dependencies (without the project) first
# to take advantage of layer caching.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

# Now copy source and install the project package.
COPY src ./src
RUN uv sync --frozen --no-dev

# --- Runtime stage ---
FROM python:3.12-slim AS runtime

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Copy the fully populated virtualenv, then the source.
COPY --from=builder /app/.venv /app/.venv
COPY src ./src

EXPOSE 8000

# The five postgres_* variables must be supplied at runtime (e.g. --env-file).
CMD ["uvicorn", "tracker_server.main:app", "--host", "0.0.0.0", "--port", "8000"]
