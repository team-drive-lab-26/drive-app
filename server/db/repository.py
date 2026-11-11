from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from .models import Trip, TripEvent

TRIP_KEYS = {"mention": "mentions", "stop": "stops", "capsule": "capsules"}


class TripRepository:
    def __init__(self, sessions: sessionmaker[Session], region: str):
        self._sessions = sessions
        self._region = region

    def _trip(self, db: Session, session_id: str) -> Trip:
        trip = db.scalar(select(Trip).where(Trip.session_id == session_id))
        if trip is None:
            trip = Trip(session_id=session_id, region=self._region)
            db.add(trip)
            db.flush()
        return trip

    def add_event(
        self,
        session_id: str,
        type_: str,
        ref_id: str,
        *,
        name: str = "",
        category: str = "",
        latitude: float | None = None,
        longitude: float | None = None,
    ) -> None:
        with self._sessions.begin() as db:
            trip = self._trip(db, session_id)
            db.add(
                TripEvent(
                    trip_id=trip.id,
                    type=type_,
                    ref_id=ref_id,
                    name=name,
                    category=category,
                    latitude=latitude,
                    longitude=longitude,
                )
            )

    def list_events(self, session_id: str) -> list[dict[str, Any]]:
        with self._sessions() as db:
            events = db.scalars(
                select(TripEvent).join(Trip).where(Trip.session_id == session_id).order_by(TripEvent.id)
            )
            return [
                {
                    "type": e.type,
                    "id": e.ref_id,
                    "name": e.name,
                    "category": e.category,
                    "latitude": e.latitude,
                    "longitude": e.longitude,
                    "recorded_at": e.recorded_at.isoformat(),
                }
                for e in events
            ]

    def load_trip(self, session_id: str) -> dict[str, list[dict[str, Any]]]:
        trip: dict[str, list[dict[str, Any]]] = {key: [] for key in TRIP_KEYS.values()}
        with self._sessions() as db:
            events = db.scalars(
                select(TripEvent).join(Trip).where(Trip.session_id == session_id).order_by(TripEvent.id)
            )
            for event in events:
                entry: dict[str, Any] = {"id": event.ref_id}
                if event.type == "mention":
                    entry |= {"name": event.name, "category": event.category}
                trip[TRIP_KEYS[event.type]].append(entry)
        return trip
    
    def list_trips(self) -> list[dict[str, Any]]:
        with self._sessions() as db:
            trips = db.scalars(select(Trip).order_by(Trip.started_at.desc()))
            return [
                {
                    "session_id": trip.session_id,
                    "region": trip.region,
                    "started_at": trip.started_at.isoformat(),
                    "ended_at": trip.ended_at.isoformat() if trip.ended_at else None,
                    "event_count": len(trip.events),
                }
                for trip in trips
            ]
