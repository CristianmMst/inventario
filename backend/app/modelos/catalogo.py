"""unidades_medida, categorias, productos, codigos_barras — RF-CAT-001..014."""

import sqlalchemy as sa
from sqlalchemy.orm import Mapped, mapped_column

from app.modelos.base import Base

TIPOS_UNIDAD = ("discreta", "continua")


class UnidadMedida(Base):
    """Catálogo global sembrado por migración; no es por negocio (RF-CAT-004, RN-06)."""

    __tablename__ = "unidades_medida"
    __table_args__ = (
        sa.CheckConstraint("tipo in ('discreta', 'continua')", name="tipo_valido"),
        sa.CheckConstraint("decimales between 0 and 3", name="decimales_rango"),
        sa.CheckConstraint("tipo <> 'discreta' or decimales = 0", name="discreta_sin_decimales"),
    )

    codigo: Mapped[str] = mapped_column(sa.String(16), primary_key=True)
    nombre: Mapped[str] = mapped_column(sa.Text, nullable=False)
    tipo: Mapped[str] = mapped_column(sa.Text, nullable=False)
    decimales: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
    orden: Mapped[int] = mapped_column(sa.SmallInteger, nullable=False)
