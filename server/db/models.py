from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

from sqlalchemy import JSON, DateTime, ForeignKey, Index, MetaData, String
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

NAMING_CONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


def _now() -> datetime:
    return datetime.now(UTC)


def _uuid() -> str:
    return uuid4().hex


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMING_CONVENTION)
    type_annotation_map = {datetime: DateTime(timezone=True), dict[str, Any]: JSON}


class Trip(Base):
    __tablename__ = "trips"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_uuid)
    session_id: Mapped[str] = mapped_column(String(64), unique=True)
    region: Mapped[str] = mapped_column(String(32))
    started_at: Mapped[datetime] = mapped_column(default=_now)
    ended_at: Mapped[datetime | None]

    events: Mapped[list["TripEvent"]] = relationship(
        back_populates="trip", cascade="all, delete-orphan", order_by="TripEvent.id"
    )
    media: Mapped[list["MediaAsset"]] = relationship(back_populates="trip", cascade="all, delete-orphan")


class TripEvent(Base):
    __tablename__ = "trip_events"
    __table_args__ = (Index("ix_trip_events_trip_id_type", "trip_id", "type"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"))
    type: Mapped[str] = mapped_column(String(16))
    ref_id: Mapped[str] = mapped_column(String(128))
    name: Mapped[str] = mapped_column(String(80), default="")
    category: Mapped[str] = mapped_column(String(32), default="")
    latitude: Mapped[float | None]
    longitude: Mapped[float | None]
    data: Mapped[dict[str, Any]] = mapped_column(default=dict)
    recorded_at: Mapped[datetime] = mapped_column(default=_now)

    trip: Mapped[Trip] = relationship(back_populates="events")


class MediaAsset(Base):
    __tablename__ = "media_assets"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_uuid)
    trip_id: Mapped[str] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(16))
    storage_key: Mapped[str] = mapped_column(String(255), unique=True)
    content_type: Mapped[str] = mapped_column(String(64))
    size_bytes: Mapped[int]
    duration_ms: Mapped[int | None]
    created_at: Mapped[datetime] = mapped_column(default=_now)

    trip: Mapped[Trip] = relationship(back_populates="media")
