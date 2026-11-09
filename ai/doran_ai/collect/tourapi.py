import time
from datetime import date, datetime
from typing import Any

import httpx

from ..knowledge.schema import Place, PlaceCategory, Point

BASE_URL = "https://apis.data.go.kr/B551011/KorService2"
CONTENT_TYPES: dict[int, PlaceCategory] = {
    12: "attraction",
    14: "culture",
    15: "event",
    28: "leisure",
    38: "shopping",
    39: "food",
}
CAFE_CAT3 = "A05020900"
REQUEST_INTERVAL_S = 1.0
MAX_RADIUS_M = 20000


class TourApiError(RuntimeError):
    pass


def _items(payload: dict[str, Any]) -> list[dict[str, Any]]:
    header = payload.get("response", {}).get("header", {})
    if header.get("resultCode") not in (None, "0000"):
        raise TourApiError(f"{header.get('resultCode')} {header.get('resultMsg')}")
    items = payload.get("response", {}).get("body", {}).get("items") or {}
    item = items.get("item", []) if isinstance(items, dict) else []
    return item if isinstance(item, list) else [item]


def _date(value: str | None) -> date | None:
    try:
        return datetime.strptime(value or "", "%Y%m%d").date()
    except ValueError:
        return None


class TourApiClient:
    def __init__(self, service_key: str, app_name: str = "Doran"):
        if not service_key:
            raise TourApiError("TOURAPI_SERVICE_KEY가 비어 있습니다")
        self._params = {"serviceKey": service_key, "MobileOS": "ETC", "MobileApp": app_name, "_type": "json"}
        self._http = httpx.Client(base_url=BASE_URL, timeout=10)
        self._last_call = 0.0

    def close(self) -> None:
        self._http.close()

    def _get(self, operation: str, **params: Any) -> list[dict[str, Any]]:
        wait = REQUEST_INTERVAL_S - (time.monotonic() - self._last_call)
        if wait > 0:
            time.sleep(wait)
        response = self._http.get(f"/{operation}", params=self._params | params)
        self._last_call = time.monotonic()
        response.raise_for_status()
        try:
            return _items(response.json())
        except ValueError as error:
            raise TourApiError(response.text[:200]) from error

    def nearby(self, center: Point, radius_m: int, content_type: int, rows: int = 100) -> list[Place]:
        raw = self._get(
            "locationBasedList2",
            mapX=center.longitude,
            mapY=center.latitude,
            radius=min(radius_m, MAX_RADIUS_M),
            contentTypeId=content_type,
            numOfRows=rows,
            pageNo=1,
            arrange="E",
        )
        return [self._place(item) for item in raw if item.get("mapx") and item.get("mapy")]

    def overview(self, content_id: str) -> str:
        items = self._get("detailCommon2", contentId=content_id)
        return (items[0].get("overview") or "").strip() if items else ""

    def event_period(self, content_id: str) -> tuple[date | None, date | None]:
        items = self._get("detailIntro2", contentId=content_id, contentTypeId=15)
        if not items:
            return None, None
        return _date(items[0].get("eventstartdate")), _date(items[0].get("eventenddate"))

    @staticmethod
    def _place(item: dict[str, Any]) -> Place:
        category = CONTENT_TYPES.get(int(item.get("contenttypeid", 0)), "attraction")
        if category == "food" and item.get("cat3") == CAFE_CAT3:
            category = "cafe"
        return Place(
            id=f"tour-{item['contentid']}",
            name=item["title"].strip(),
            category=category,
            latitude=float(item["mapy"]),
            longitude=float(item["mapx"]),
            address=" ".join(filter(None, [item.get("addr1"), item.get("addr2")])).strip(),
            image_url=item.get("firstimage") or "",
        )
