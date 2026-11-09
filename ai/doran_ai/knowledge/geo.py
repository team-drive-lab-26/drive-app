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
    samples = [route[0]]
    for a, b in zip(route, route[1:], strict=False):
        steps = max(1, int(haversine_km(a, b) // step_km))
        for i in range(1, steps + 1):
            t = i / steps
            samples.append(
                Point(
                    latitude=a.latitude + t * (b.latitude - a.latitude),
                    longitude=a.longitude + t * (b.longitude - a.longitude),
                )
            )
    return samples
