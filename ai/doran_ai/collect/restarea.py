import json
import sys
import time
from pathlib import Path
from typing import Any

import httpx

from .tourapi import TourApiError, _items

BASE_URL = "https://api.data.go.kr/openapi/tn_pubr_public_rest_area_api"
REQUEST_INTERVAL_S = 1.0


def fetch_raw(
    service_key: str, *, limit: int | None = None, rows: int = 100, sample: Path | None = None
) -> list[dict[str, Any]]:
    if not service_key:
        raise TourApiError("DATA_GO_KR_SERVICE_KEY가 비어 있습니다")
    items: list[dict[str, Any]] = []
    page = 1
    with httpx.Client(timeout=10) as http:
        while True:
            response = http.get(
                BASE_URL,
                params={"serviceKey": service_key, "type": "json", "pageNo": page, "numOfRows": rows},
            )
            response.raise_for_status()
            if sample and page == 1:
                sample.parent.mkdir(parents=True, exist_ok=True)
                sample.write_text(response.text, encoding="utf-8")
            batch = _items(response.json())
            items.extend(batch)
            if not batch or len(batch) < rows or (limit and len(items) >= limit):
                break
            page += 1
            time.sleep(REQUEST_INTERVAL_S)
    return items[:limit] if limit else items


def save_raw(path: Path, items: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{path}: 원본 {len(items)}건 저장", file=sys.stderr)
