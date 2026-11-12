from pathlib import Path

from doran_ai.config import REPO_ROOT, Settings
from pydantic import Field

VAR_DIR = REPO_ROOT / "server" / "var"


class ServerSettings(Settings):
    doran_database_url: str = f"sqlite:///{(VAR_DIR / 'doran.db').as_posix()}"
    doran_media_dir: Path = Field(default=VAR_DIR / "media")
    # 서버를 켤 때 앨범용 시연 여행 기록을 넣을지. 빈 앨범부터 찍는 화면 촬영에서는 0으로 끈다
    doran_seed_demo: bool = True
