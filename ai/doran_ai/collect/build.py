import argparse
import math
import sys
from datetime import date, timedelta

from pydantic import TypeAdapter

from ..config import Settings
from ..knowledge.geo import distance_to_route_km, haversine_km, sample_route
from ..knowledge.schema import Place, Point, RestStop, Route
from ..knowledge.store import PLACES_FILE, REST_STOPS_FILE, ROUTE_FILE, write_json
from .restarea import fetch_raw, save_raw, to_rest_stop
from .route import road_route
from .tourapi import CONTENT_TYPES, TourApiClient

ROUTE_STEP_KM = 15
ROUTE_RADIUS_M = 5000
ALONG_ROUTE_KM = 5.0
# 여행 목적지 주변은 경로에서 조금 떨어져도 후보로 둔다(강릉 경포·안목·초당)
DESTINATION_RADIUS_M = 8000
DESTINATION_ROWS = {12: 30, 14: 15, 28: 15, 38: 10, 39: 60}
FESTIVAL_DESTINATION_KM = 20.0
FESTIVAL_LOOKBACK_DAYS = 180
SKIP_NAME_WORDS = ("화장실", "주차장", "체육센터", "체육관")
REST_STOP_ALONG_ROUTE_KM = 1.5
APPROXIMATE_ROUTE_SLACK_KM = 6.0
REST_STOP_KEEP_KM = 20.0


def _destination_km(place: Point, route: Route) -> float:
    return haversine_km(place, route.points[-1])


def _mark_place(place: Place, route: Route) -> Place:
    distance = round(distance_to_route_km(place, route.points), 2)
    near_destination = _destination_km(place, route) * 1000 <= DESTINATION_RADIUS_M
    return place.model_copy(
        update={"route_distance_km": distance, "along_route": distance <= ALONG_ROUTE_KM or near_destination}
    )


def collect_places(
    client: TourApiClient,
    route: Route,
    *,
    with_details: bool,
    limit: int | None = None,
    today: date | None = None,
) -> list[Place]:
    today = today or date.today()
    found: dict[str, Place] = {}
    centers = sample_route(route.points, ROUTE_STEP_KM)
    rows = max(1, math.ceil(limit / (len(centers) * len(CONTENT_TYPES)))) if limit else 100
    for i, center in enumerate(centers, 1):
        for content_type in CONTENT_TYPES:
            if content_type == 15:
                continue  # 행사는 기간 조건으로 따로 받는다
            for place in client.nearby(center, ROUTE_RADIUS_M, content_type, rows=rows):
                found.setdefault(place.id, place)
        print(f"경로 지점 {i}/{len(centers)}: 누적 {len(found)}곳", file=sys.stderr)

    destination = route.points[-1]
    for content_type, dest_rows in DESTINATION_ROWS.items():
        # --limit 100이 기본 수집량. 작게 주면 목적지 주변도 같은 비율로 줄인다
        dest_rows = max(1, math.ceil(dest_rows * min(1.0, limit / 100))) if limit else dest_rows
        for place in client.nearby(destination, DESTINATION_RADIUS_M, content_type, rows=dest_rows):
            found.setdefault(place.id, place)
    print(f"목적지 주변 포함 누적 {len(found)}곳", file=sys.stderr)

    festivals = 0
    for place in client.festivals(today - timedelta(days=FESTIVAL_LOOKBACK_DAYS)):
        if not place.event_end or place.event_end < today:
            continue
        if distance_to_route_km(place, route.points) <= ALONG_ROUTE_KM or (
            _destination_km(place, route) <= FESTIVAL_DESTINATION_KM
        ):
            found.setdefault(place.id, place)
            festivals += 1
    print(f"진행 중이거나 예정된 행사 {festivals}건", file=sys.stderr)

    places = []
    for place in found.values():
        if any(word in place.name for word in SKIP_NAME_WORDS):
            continue
        place = _mark_place(place, route)
        if place.category == "event" and place.event_end and place.event_end < today:
            continue
        if place.category == "event" and _destination_km(place, route) <= FESTIVAL_DESTINATION_KM:
            place = place.model_copy(update={"along_route": True})
        if with_details and place.along_route:
            update = {"overview": client.overview(place.id.removeprefix("tour-"))}
            update |= {k: v for k, v in client.intro(place).items() if v is not None}
            place = place.model_copy(update=update)
        places.append(place)
    return sorted(places, key=lambda p: p.route_distance_km or 0)


def _on_route_highway(stop: RestStop, route: Route) -> bool:
    if not route.highways:
        return True
    direction = route.highways.get(stop.highway)
    return direction is not None and stop.direction in (direction, "양방향")


def mark_rest_stops(stops: list[RestStop], route: Route) -> list[RestStop]:
    limit_km = REST_STOP_ALONG_ROUTE_KM + (APPROXIMATE_ROUTE_SLACK_KM if route.approximate else 0)
    marked = []
    for stop in stops:
        distance = round(distance_to_route_km(stop, route.points), 2)
        if distance > REST_STOP_KEEP_KM:
            continue
        along = distance <= limit_km and _on_route_highway(stop, route)
        marked.append(stop.model_copy(update={"route_distance_km": distance, "along_route": along}))
    return sorted(marked, key=lambda s: s.route_distance_km or 0)


def main() -> None:
    parser = argparse.ArgumentParser(description="시연 경로 주변 장소·휴게소 데이터를 만든다")
    parser.add_argument("--region", help="ai/data 아래 지역 폴더 이름")
    parser.add_argument("--skip-details", action="store_true", help="소개글·행사 기간 조회 생략")
    parser.add_argument("--limit", type=int, help="테스트용 최대 수집 개수")
    parser.add_argument(
        "--source",
        choices=["tour", "restarea", "route", "remark"],
        default="tour",
        help="tour: 장소, restarea: 휴게소, route: 도로 경로로 route.json 갱신, remark: 경로 판정만 다시",
    )
    args = parser.parse_args()

    settings = Settings()
    if args.region:
        settings.doran_region = args.region
    region_dir = settings.region_dir
    route = TypeAdapter(Route).validate_json((region_dir / ROUTE_FILE).read_text(encoding="utf-8"))

    if args.source == "route":
        route, summary = road_route(route)
        write_json(region_dir / ROUTE_FILE, route)
        print(f"{region_dir / ROUTE_FILE}: {summary}", file=sys.stderr)
        args.source = "remark"

    if args.source == "remark":
        places = TypeAdapter(list[Place]).validate_json((region_dir / PLACES_FILE).read_text(encoding="utf-8"))
        stops = TypeAdapter(list[RestStop]).validate_json((region_dir / REST_STOPS_FILE).read_text(encoding="utf-8"))
        places = sorted((_mark_place(p, route) for p in places), key=lambda p: p.route_distance_km or 0)
        stops = mark_rest_stops(stops, route)
        write_json(region_dir / PLACES_FILE, places)
        write_json(region_dir / REST_STOPS_FILE, stops)
        print(
            f"경로 다시 판정: 장소 {sum(p.along_route for p in places)}/{len(places)}, "
            f"휴게소 {sum(s.along_route for s in stops)}/{len(stops)}",
            file=sys.stderr,
        )
        return

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
