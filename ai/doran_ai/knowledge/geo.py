from math import asin, cos, radians, sin, sqrt

from .schema import Point

EARTH_RADIUS_KM = 6371.0


def haversine_km(a: Point, b: Point) -> float:
    dlat = radians(b.latitude - a.latitude)
    dlon = radians(b.longitude - a.longitude)
    h = sin(dlat / 2) ** 2 + cos(radians(a.latitude)) * cos(radians(b.latitude)) * sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_KM * asin(sqrt(h))


def _segment_distance_km(p: Point, a: Point, b: Point) -> float:
    scale = cos(radians((a.latitude + b.latitude) / 2))
    ax, ay = a.longitude * scale, a.latitude
    bx, by = b.longitude * scale, b.latitude
    px, py = p.longitude * scale, p.latitude
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    t = 0.0 if length == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length))
    nearest = Point(latitude=ay + t * dy, longitude=(ax + t * dx) / scale)
    return haversine_km(p, nearest)


def distance_to_route_km(p: Point, route: list[Point]) -> float:
    return min(_segment_distance_km(p, a, b) for a, b in zip(route, route[1:], strict=False))


def sample_route(route: list[Point], step_km: float) -> list[Point]:
    """경로를 따라 step_km마다 점을 찍는다. 짧은 구간이 많은 도로 경로도 간격이 일정하다."""
    samples = [route[0]]
    carried = 0.0
    for a, b in zip(route, route[1:], strict=False):
        segment = haversine_km(a, b)
        position = step_km - carried
        while position <= segment:
            t = position / segment
            samples.append(
                Point(
                    latitude=a.latitude + t * (b.latitude - a.latitude),
                    longitude=a.longitude + t * (b.longitude - a.longitude),
                )
            )
            position += step_km
        carried = segment - (position - step_km)
    if samples[-1] != route[-1]:
        samples.append(route[-1])
    return samples


def route_length_km(route: list[Point]) -> float:
    return sum(haversine_km(a, b) for a, b in zip(route, route[1:], strict=False))


def route_progress_km(p: Point, route: list[Point]) -> tuple[float, float]:
    """경로 출발점부터 p를 경로에 수선으로 내린 지점까지의 거리와, p에서 경로까지의 거리."""
    best = (0.0, float("inf"))
    walked = 0.0
    for a, b in zip(route, route[1:], strict=False):
        offset = _segment_distance_km(p, a, b)
        segment = haversine_km(a, b)
        if offset < best[1]:
            along = min(segment, max(0.0, haversine_km(a, p) ** 2 - offset**2) ** 0.5)
            best = (walked + along, offset)
        walked += segment
    return best


def simplify_route(route: list[Point], step_km: float) -> list[Point]:
    """도로 경로 점을 step_km 간격 정도로 줄인다. 처음과 끝 점은 남긴다."""
    kept = [route[0]]
    for p in route[1:-1]:
        if haversine_km(kept[-1], p) >= step_km:
            kept.append(p)
    kept.append(route[-1])
    return kept
