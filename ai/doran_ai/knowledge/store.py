import json
from datetime import date
from pathlib import Path
from typing import Any

from pydantic import BaseModel, TypeAdapter

from .schema import Place, PlaceCategory, RestStop, Route, Track
from .search import BigramIndex

PLACES_FILE = "places.json"
REST_STOPS_FILE = "rest_stops.json"
TRACKS_FILE = "tracks.json"
ROUTE_FILE = "route.json"


def _read[T](path: Path, adapter: TypeAdapter[T], default: T) -> T:
    if not path.exists():
        return default
    return adapter.validate_json(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: BaseModel | list[BaseModel]) -> None:
    data = [v.model_dump(mode="json") for v in value] if isinstance(value, list) else value.model_dump(mode="json")
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _open_on(place: Place, day: date) -> bool:
    if place.category != "event":
        return True
    return bool(place.event_start and place.event_end and place.event_start <= day <= place.event_end)


class KnowledgeBase:
    def __init__(self, places: list[Place], rest_stops: list[RestStop], tracks: list[Track], route: Route | None):
        self.places = {p.id: p for p in places}
        self.rest_stops = {r.id: r for r in rest_stops}
        self.tracks = {t.id: t for t in tracks}
        self.route = route
        self._index = BigramIndex(
            {p.id: f"{p.name} {p.address} {p.overview}" for p in places}
            | {r.id: f"{r.name} {r.highway} {' '.join(r.menu)} {' '.join(r.facilities)}" for r in rest_stops}
        )

    @classmethod
    def load(cls, region_dir: Path) -> "KnowledgeBase":
        return cls(
            _read(region_dir / PLACES_FILE, TypeAdapter(list[Place]), []),
            _read(region_dir / REST_STOPS_FILE, TypeAdapter(list[RestStop]), []),
            _read(region_dir / TRACKS_FILE, TypeAdapter(list[Track]), []),
            _read(region_dir / ROUTE_FILE, TypeAdapter(Route | None), None),
        )

    def find_places(
        self,
        query: str = "",
        *,
        categories: set[PlaceCategory] | None = None,
        on: date | None = None,
        along_route: bool = True,
        limit: int = 3,
    ) -> list[Place]:
        candidates = [
            p
            for p in self.places.values()
            if (not along_route or p.along_route)
            and (categories is None or p.category in categories)
            and (on is None or _open_on(p, on))
        ]
        if not query:
            return sorted(candidates, key=lambda p: p.route_distance_km or 0)[:limit]
        ranked = self._index.rank(query, [p.id for p in candidates])
        return [self.places[doc_id] for doc_id, _ in ranked[:limit]]

    def find_rest_stops(self, query: str = "", *, limit: int = 3) -> list[RestStop]:
        candidates = [r.id for r in self.rest_stops.values() if r.along_route]
        if not query:
            return [self.rest_stops[i] for i in candidates[:limit]]
        return [self.rest_stops[doc_id] for doc_id, _ in self._index.rank(query, candidates)[:limit]]

    def agent_catalog(self, on: date | None = None) -> dict[str, list[dict[str, Any]]]:
        """기존 DoranAgent·AgentService 입력 형식으로 변환한다. 끝난 행사는 뺀다."""
        day = on or date.today()
        return {
            "places": [p.model_dump(mode="json", exclude_none=True) for p in self.places.values() if _open_on(p, day)],
            "rest_stops": [r.model_dump(mode="json", exclude_none=True) for r in self.rest_stops.values()],
            "tracks": [t.model_dump(mode="json") for t in self.tracks.values()],
        }
