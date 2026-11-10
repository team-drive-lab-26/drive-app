from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

REPO_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=REPO_ROOT / ".env", env_file_encoding="utf-8", extra="ignore", env_ignore_empty=True
    )

    tourapi_service_key: str = ""
    data_go_kr_service_key: str = ""
    doran_llm_endpoint: str = ""
    doran_llm_model: str = ""
    doran_llm_api_key: str = ""
    groq_api_key: str = ""
    doran_llm_timeout_seconds: float = 8.0
    doran_region: str = "gangneung"
    doran_data_dir: Path = Field(default=REPO_ROOT / "ai" / "data")

    @property
    def region_dir(self) -> Path:
        return self.doran_data_dir / self.doran_region
