from pathlib import Path
from typing import Protocol
from uuid import uuid4


class MediaStorage(Protocol):
    def put(self, data: bytes, *, prefix: str, suffix: str) -> str: ...
    def read(self, key: str) -> bytes: ...
    def delete(self, key: str) -> None: ...


class LocalMediaStorage:
    def __init__(self, root: Path):
        self.root = root.resolve()

    def _path(self, key: str) -> Path:
        path = (self.root / key).resolve()
        if not path.is_relative_to(self.root):
            raise ValueError("저장 경로가 올바르지 않습니다")
        return path

    def put(self, data: bytes, *, prefix: str, suffix: str) -> str:
        key = f"{prefix}/{uuid4().hex}{suffix}"
        path = self._path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return key

    def read(self, key: str) -> bytes:
        return self._path(key).read_bytes()

    def delete(self, key: str) -> None:
        self._path(key).unlink(missing_ok=True)
