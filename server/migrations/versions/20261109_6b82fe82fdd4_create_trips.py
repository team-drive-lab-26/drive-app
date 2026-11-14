"""create trips

Revision ID: 6b82fe82fdd4
Revises:
Create Date: 2026-11-09 18:09:09.229507
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "6b82fe82fdd4"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "trips",
        sa.Column("id", sa.String(length=32), nullable=False),
        sa.Column("session_id", sa.String(length=64), nullable=False),
        sa.Column("region", sa.String(length=32), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ended_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_trips")),
        sa.UniqueConstraint("session_id", name=op.f("uq_trips_session_id")),
    )
    op.create_table(
        "media_assets",
        sa.Column("id", sa.String(length=32), nullable=False),
        sa.Column("trip_id", sa.String(length=32), nullable=False),
        sa.Column("kind", sa.String(length=16), nullable=False),
        sa.Column("storage_key", sa.String(length=255), nullable=False),
        sa.Column("content_type", sa.String(length=64), nullable=False),
        sa.Column("size_bytes", sa.Integer(), nullable=False),
        sa.Column("duration_ms", sa.Integer(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["trip_id"], ["trips.id"], name=op.f("fk_media_assets_trip_id_trips"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_media_assets")),
        sa.UniqueConstraint("storage_key", name=op.f("uq_media_assets_storage_key")),
    )
    with op.batch_alter_table("media_assets", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_media_assets_trip_id"), ["trip_id"], unique=False)

    op.create_table(
        "trip_events",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("trip_id", sa.String(length=32), nullable=False),
        sa.Column("type", sa.String(length=16), nullable=False),
        sa.Column("ref_id", sa.String(length=128), nullable=False),
        sa.Column("name", sa.String(length=80), nullable=False),
        sa.Column("category", sa.String(length=32), nullable=False),
        sa.Column("latitude", sa.Double(), nullable=True),
        sa.Column("longitude", sa.Double(), nullable=True),
        sa.Column("data", sa.JSON(), nullable=False),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["trip_id"], ["trips.id"], name=op.f("fk_trip_events_trip_id_trips"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_trip_events")),
    )
    with op.batch_alter_table("trip_events", schema=None) as batch_op:
        batch_op.create_index("ix_trip_events_trip_id_type", ["trip_id", "type"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("trip_events", schema=None) as batch_op:
        batch_op.drop_index("ix_trip_events_trip_id_type")

    op.drop_table("trip_events")
    with op.batch_alter_table("media_assets", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_media_assets_trip_id"))

    op.drop_table("media_assets")
    op.drop_table("trips")
