import json
import sys
import time
from pathlib import Path
from typing import Any

import httpx

from ..knowledge.schema import RestStop
from .tourapi import TourApiError, _items

BASE_URL = "https://api.data.go.kr/openapi/tn_pubr_public_rest_area_api"
REQUEST_INTERVAL_S = 1.0
FACILITIES = {
    "oltYn": "주유소",
    "lpgYn": "LPG 충전소",
    "elctyYn": "전기차 충전소",
    "busTrnsitYn": "버스 환승",
    "shltrYn": "쉼터",
    "toiletYn": "화장실",
    "parmacyYn": "약국",
    "nrsgYn": "수유실",
    "shopYn": "매점",
    "rstrtYn": "음식점",
    "crrpwrkYn": "경정비",
}


def fetch_raw(service_key: str, *, rows: int = 100, sample: Path | None = None) -> list[dict[str, Any]]:
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
            if len(batch) < rows:
                break
            page += 1
            time.sleep(REQUEST_INTERVAL_S)
    return items


def to_rest_stop(item: dict[str, Any]) -> RestStop | None:
    try:
        latitude, longitude = float(item["latitude"]), float(item["longitude"])
    except (KeyError, TypeError, ValueError):
        return None
    name = (item.get("entrpsNm") or "").strip()
    if not name:
        return None
    facilities = [label for key, label in FACILITIES.items() if item.get(key) == "Y"]
    if extra := (item.get("etcCvntl") or "").strip():
        facilities.append(extra)
    menu = [m.strip() for m in (item.get("rprsntvRstrt") or "").replace("+", ",").split(",") if m.strip()]
    return RestStop(
        id=f"rest-{item.get('roadRouteNo', '')}-{name}",
        name=f"{name} 휴게소" if not name.endswith("휴게소") else name,
        highway=(item.get("roadRouteNm") or "").strip(),
        direction=(item.get("roadRouteDrc") or "").strip(),
        latitude=latitude,
        longitude=longitude,
        menu=menu,
        facilities=facilities,
    )


def save_raw(path: Path, items: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{path}: 원본 {len(items)}건 저장", file=sys.stderr)
