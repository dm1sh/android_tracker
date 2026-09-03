"""Application configuration loaded from environment variables."""

import os


class Settings:
    """Database configuration sourced from environment variables."""

    def __init__(self) -> None:
        self.postgres_user: str = os.environ.get("postgres_user", "tracker")
        self.postgres_password: str = os.environ.get("postgres_password", "changeme")
        self.postgres_db: str = os.environ.get("postgres_db", "tracker")
        self.postgres_host: str = os.environ.get("postgres_host", "localhost")
        self.postgres_port: int = int(os.environ.get("postgres_port", "5432"))

    @property
    def conninfo(self) -> str:
        """psycopg connection string for the async pool."""
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )


settings = Settings()
