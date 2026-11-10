from datetime import date
from typing import Literal

from pydantic import BaseModel, Field

PlaceCategory = Literal["attraction", "culture", "leisure", "shopping", "food", "cafe", "event"]
TrackMood = Literal["travel", "bright", "refresh", "calm"]


class Point(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class Place(Point):
    id: str
    name: str
    source: Literal["tour_api"] = "tour_api"
    category: PlaceCategory
    address: str = ""
    overview: str = ""
    image_url: str = ""
    opening_hours: str = ""
    event_start: date | None = None
    event_end: date | None = None
    route_distance_km: float | None = None
    along_route: bool = False
    navi_poi_id: str | None = None
    dummy: bool = False


class RestStop(Point):
    id: str
    name: str
    source: Literal["expressway_api"] = "expressway_api"
    highway: str
    direction: str
    menu: list[str] = []
    facilities: list[str] = []
    route_distance_km: float | None = None
    along_route: bool = False
    navi_poi_id: str | None = None
    dummy: bool = False


class Track(BaseModel):
    id: str
    name: str
    artist: str
    source: Literal["catalog"] = "catalog"
    tags: list[TrackMood]
    dummy: bool = False


class Route(BaseModel):
    name: str
    approximate: bool
    highways: dict[str, str] = {}
    points: list[Point] = Field(min_length=2)
