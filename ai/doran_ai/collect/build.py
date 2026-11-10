import argparse
import math
import sys

from pydantic import TypeAdapter

from ..config import Settings
from ..knowledge.geo import distance_to_route_km, sample_route
from ..knowledge.schema import Place, RestStop, Route
from ..knowledge.store import PLACES_FILE, REST_STOPS_FILE, ROUTE_FILE, write_json
from .restarea import fetch_raw, save_raw, to_rest_stop
from .tourapi import CONTENT_TYPES, TourApiClient

ROUTE_STEP_KM = 15
ROUTE_RADIUS_M = 5000
ALONG_ROUTE_KM = 5.0
REST_STOP_ALONG_ROUTE_KM = 1.5
APPROXIMATE_ROUTE_SLACK_KM = 4.0
REST_STOP_KEEP_KM = 20.0


def collect_places(client: TourApiClient, route: Route, *, with_details: bool, limit: int | None = None) -> list[Place]:
    found: dict[str, Place] = {}
    centers = sample_route(route.points, ROUTE_STEP_KM)
    rows = max(1, math.ceil(limit / (len(centers) * len(CONTENT_TYPES)))) if limit else 100
    for i, center in enumerate(centers, 1):
        for content_type in CONTENT_TYPES:
            for place in client.nearby(center, ROUTE_RADIUS_M, content_type, rows=rows):
                found.setdefault(place.id, place)
        print(f"경로 지점 {i}/{len(centers)}: 누적 {len(found)}곳", file=sys.stderr)

    places = []
    for place in found.values():
        distance = round(distance_to_route_km(place, route.points), 2)
        place = place.model_copy(update={"route_distance_km": distance, "along_route": distance <= ALONG_ROUTE_KM})
        if with_details and place.along_route:
            content_id = place.id.removeprefix("tour-")
            update = {"overview": client.overview(content_id)}
            if place.category == "event":
                update["event_start"], update["event_end"] = client.event_period(content_id)
            place = place.model_copy(update=update)
        places.append(place)
    return sorted(places, key=lambda p: p.route_distance_km or 0)


def mark_rest_stops(stops: list[RestStop], route: Route) -> list[RestStop]:
    limit_km = REST_STOP_ALONG_ROUTE_KM + (APPROXIMATE_ROUTE_SLACK_KM if route.approximate else 0)
    marked = []
    for stop in stops:
        distance = round(distance_to_route_km(stop, route.points), 2)
        if distance > REST_STOP_KEEP_KM:
            continue
        marked.append(stop.model_copy(update={"route_distance_km": distance, "along_route": distance <= limit_km}))
    return sorted(marked, key=lambda s: s.route_distance_km or 0)


def main() -> None:
    parser = argparse.ArgumentParser(description="시연 경로 주변 장소·휴게소 데이터를 만든다")
    parser.add_argument("--region", help="ai/data 아래 지역 폴더 이름")
    parser.add_argument("--skip-details", action="store_true", help="소개글·행사 기간 조회 생략")
    parser.add_argument("--limit", type=int, help="테스트용 최대 수집 개수")
    parser.add_argument("--source", choices=["tour", "restarea"], default="tour", help="수집할 API")
    args = parser.parse_args()

    settings = Settings()
    if args.region:
        settings.doran_region = args.region
    region_dir = settings.region_dir
    route = TypeAdapter(Route).validate_json((region_dir / ROUTE_FILE).read_text(encoding="utf-8"))

    if args.source == "restarea":
        key = settings.data_go_kr_service_key or settings.tourapi_service_key
        raw_dir = region_dir / "raw"
        raw = fetch_raw(key, sample=raw_dir / "response_page1.json")
        save_raw(raw_dir / "rest_areas.json", raw)
        stops = mark_rest_stops([s for s in map(to_rest_stop, raw) if s], route)
        write_json(region_dir / REST_STOPS_FILE, stops)
        along = sum(s.along_route for s in stops)
        print(
            f"{region_dir / REST_STOPS_FILE}: 경로 {REST_STOP_KEEP_KM}km 안 {len(stops)}곳, 경로 위 {along}곳",
            file=sys.stderr,
        )
        return

    client = TourApiClient(settings.tourapi_service_key)
    try:
        places = collect_places(client, route, with_details=not args.skip_details, limit=args.limit)
    finally:
        client.close()
    write_json(region_dir / PLACES_FILE, places)
    along = sum(p.along_route for p in places)
    print(f"{region_dir / PLACES_FILE}: {len(places)}곳 저장, 경로 {ALONG_ROUTE_KM}km 이내 {along}곳", file=sys.stderr)


if __name__ == "__main__":
    main()
