from typing import Any

import httpx

from ..knowledge.geo import route_length_km, simplify_route
from ..knowledge.schema import Point, Route

# OpenStreetMap 도로망 기준 경로. Pleos 내비 경로와 다를 수 있다
OSRM_URL = "https://router.project-osrm.org/route/v1/driving"
SIMPLIFY_STEP_KM = 0.3


class RouteError(RuntimeError):
    pass


def fetch_road_route(anchors: list[Point]) -> tuple[list[Point], dict[str, Any]]:
    if len(anchors) < 2:
        raise RouteError("출발지와 도착지 좌표가 필요합니다")
    coords = ";".join(f"{p.longitude},{p.latitude}" for p in anchors)
    response = httpx.get(
        f"{OSRM_URL}/{coords}", params={"overview": "full", "geometries": "geojson", "steps": "true"}, timeout=30
    )
    response.raise_for_status()
    data = response.json()
    if data.get("code") != "Ok" or not data.get("routes"):
        raise RouteError(f"경로 조회 실패: {data.get('code')}")
    route = data["routes"][0]
    points = [Point(latitude=lat, longitude=lon) for lon, lat in route["geometry"]["coordinates"]]
    roads = []
    for leg in route["legs"]:
        for step in leg["steps"]:
            name = step.get("ref") or step.get("name")
            if name and step["distance"] >= 3000 and (not roads or roads[-1] != name):
                roads.append(name)
    return points, {"distance_km": route["distance"] / 1000, "duration_min": route["duration"] / 60, "roads": roads}


def road_route(current: Route) -> tuple[Route, dict[str, Any]]:
    """기존 route.json의 anchors(없으면 첫·끝 점)를 이어 실제 도로 경로로 바꾼다. 노선 방향(highways)은 유지한다."""
    anchors = current.anchors or [current.points[0], current.points[-1]]
    points, summary = fetch_road_route(anchors)
    simplified = simplify_route(points, SIMPLIFY_STEP_KM)
    summary["points"] = len(simplified)
    summary["simplified_km"] = round(route_length_km(simplified), 1)
    route = current.model_copy(
        update={"approximate": False, "source": "osrm", "anchors": anchors, "points": simplified}
    )
    return route, summary
