from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """Base declarativa de todos los modelos. Convenciones de nombres de constitution.md §1."""

    metadata_naming = {
        "ix": "ix_%(table_name)s_%(column_0_N_name)s",
        "uq": "uq_%(table_name)s_%(column_0_N_name)s",
        "ck": "ck_%(table_name)s_%(constraint_name)s",
        "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
        "pk": "pk_%(table_name)s",
    }


Base.metadata.naming_convention = Base.metadata_naming
