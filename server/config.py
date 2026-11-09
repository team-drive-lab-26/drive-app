from pathlib import Path

from doran_ai.config import REPO_ROOT, Settings
from pydantic import Field

VAR_DIR = REPO_ROOT / "server" / "var"


class ServerSettings(Settings):
    doran_database_url: str = f"sqlite:///{(VAR_DIR / 'doran.db').as_posix()}"
    doran_media_dir: Path = Field(default=VAR_DIR / "media")
