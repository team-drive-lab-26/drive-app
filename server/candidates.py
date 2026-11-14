"""요청마다 장소·휴게소 후보 순서를 정한다. 에이전트는 앞쪽 후보부터 고른다."""

from __future__ import annotations

import re
from typing import Any

from doran_ai.knowledge import KnowledgeBase, Point
from doran_ai.knowledge.geo import haversine_km, route_length_km, route_progress_km

FILLER = re.compile(
    r"근처|주변|추천|어디|있어|있나|좋아|가볼|갈\s?만한|찾아|알려|해\s?줘|맛집|먹을|먹자|먹고|싶어|이번|주말|뭐|좀|데|곳|[?!.,]"
)
CATEGORY_WORDS = (
    ("event", ("축제", "행사", "공연", "전시")),
    ("cafe", ("카페", "커피", "디저트")),
    ("food", ("맛집", "밥", "점심", "저녁", "식당", "먹")),
)
# 이미 지나친 곳으로 보는 기준. 경로 진행 거리가 현재보다 이만큼 뒤면 뒤로 미룬다
BEHIND_KM = 2.0
NEARBY_KM = 15.0


def _point(value: Any) -> Point | None:
    if not isinstance(value, dict):
        return None
    lat, lon = value.get("latitude"), value.get("longitude")
    if type(lat) not in (int, float) or type(lon) not in (int, float):
        return None
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        return None
    return Point(latitude=lat, longitude=lon)


def _number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
        return None
    return float(value)


class CandidateRanker:
    def __init__(self, knowledge: KnowledgeBase, places: list[dict[str, Any]], rest_stops: list[dict[str, Any]]):
        if not knowledge.route:
            raise ValueError("경로가 있어야 후보 순서를 정할 수 있습니다")
        self.knowledge = knowledge
        self.route = knowledge.route.points
        self.length_km = route_length_km(self.route)
        self.destination = self.route[-1]
        self.progress = {
            item["id"]: route_progress_km(point, self.route)[0]
            for item in places + rest_stops
            if (point := _point(item))
        }

    def current_progress(self, request: dict[str, Any]) -> tuple[float | None, Point | None]:
        """현재 위치가 오면 경로에 투영하고, 없으면 출발 후 경과 시간과 남은 시간 비율로 추정한다."""
        position = _point(request.get("position"))
        if position:
            return route_progress_km(position, self.route)[0], position
        nav = request.get("navigation") if isinstance(request.get("navigation"), dict) else {}
        elapsed, eta = _number(nav.get("minutes_since_departure")), _number(nav.get("eta_minutes"))
        if elapsed is not None and eta is not None and elapsed + eta > 0:
            return self.length_km * elapsed / (elapsed + eta), None
        return None, None

    def places(self, request: dict[str, Any], places: list[dict[str, Any]]) -> list[dict[str, Any]]:
        progress, position = self.current_progress(request)
        text = request.get("utterance") if request.get("kind") == "user_utterance" else ""
        text = text if isinstance(text, str) else ""
        wanted = {category for category, words in CATEGORY_WORDS if any(word in text for word in words)}
        query = " ".join(word for word in FILLER.sub(" ", text).split() if len(word) >= 2)
        ids = [p["id"] for p in places]
        matched = set(self.knowledge.match_scores(query, ids)) if query else set()
        # 이름·메뉴에 그대로 들어 있으면 소개글에만 스친 곳보다 앞에 둔다
        words = query.split()
        named = {
            p["id"]
            for p in places
            if words and any(w in f"{p.get('name', '')} {' '.join(p.get('menu') or [])}" for w in words)
        }

        def distance(place: dict[str, Any]) -> float:
            point = _point(place)
            if not point:
                return float("inf")
            if position:
                return haversine_km(position, point)
            if progress is not None:
                return abs(self.progress.get(place["id"], 0.0) - progress)
            return haversine_km(self.destination, point)

        def behind(place: dict[str, Any]) -> bool:
            if progress is None or self.progress.get(place["id"], self.length_km) >= progress - BEHIND_KM:
                return False
            point = _point(place)
            # 목적지에 와서 주변을 돌아다닐 때는 경로상 조금 뒤여도 가까우면 후보로 둔다
            return not (position and point and haversine_km(position, point) <= NEARBY_KM)

        if "event" in wanted:
            # 행사를 물었는데 앞으로 갈 길에 진행 중인 행사가 없으면 다른 장소로 답하지 않고 "못 찾았어"가 나오게 한다
            places = [p for p in places if p.get("category") == "event" and not behind(p)]
        return sorted(
            places,
            key=lambda p: (
                behind(p),
                bool(wanted) and p.get("category") not in wanted,
                bool(named) and p["id"] not in named,
                bool(matched) and p["id"] not in matched,
                distance(p),
            ),
        )

    def ahead(self, request: dict[str, Any], rest_stops: list[dict[str, Any]], limit: int = 3) -> list[dict[str, Any]]:
        """앞으로 나올 휴게소를 가까운 순서로, 경로를 따라 남은 거리(km)와 함께. 현재 위치를 모르면 빈 목록."""
        progress, _ = self.current_progress(request)
        if progress is None:
            return []
        ahead = sorted(
            (s for s in rest_stops if self.progress.get(s["id"], -1.0) > progress),
            key=lambda s: self.progress[s["id"]],
        )
        return [
            {"id": s["id"], "name": s.get("name", ""), "km": round(self.progress[s["id"]] - progress, 1)}
            for s in ahead[:limit]
        ]

    def rest_stops(self, request: dict[str, Any], rest_stops: list[dict[str, Any]]) -> list[dict[str, Any]]:
        """앞으로 나올 휴게소를 가까운 순서로. 남은 시간을 알면 휴게소까지 걸리는 시간을 붙인다."""
        progress, _ = self.current_progress(request)
        nav = request.get("navigation") if isinstance(request.get("navigation"), dict) else {}
        eta = _number(nav.get("eta_minutes"))
        start = progress if progress is not None else 0.0
        ahead = sorted(
            (s for s in rest_stops if self.progress.get(s["id"], -1.0) >= start),
            key=lambda s: self.progress[s["id"]],
        )
        passed = [s for s in rest_stops if s not in ahead]
        if progress is None or not eta or self.length_km <= progress:
            return ahead + passed
        km_per_minute = (self.length_km - progress) / eta
        timed = [{**s, "eta_minutes": round((self.progress[s["id"]] - progress) / km_per_minute)} for s in ahead]
        return timed + passed
