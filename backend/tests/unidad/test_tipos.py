"""RN-07 y plan.md §0 (E-01): dinero y cantidades sin coma flotante, decimal exacto."""

from decimal import Decimal

import pytest

from app.dominio import errores as err
from app.dominio.tipos import Cantidad, Dinero, Moneda, TipoUnidad, UnidadMedida

COP = Moneda("COP")
UNIDAD = UnidadMedida(codigo="unidad", nombre="Unidad", tipo=TipoUnidad.DISCRETA, decimales=0)
KILO = UnidadMedida(codigo="kg", nombre="Kilogramo", tipo=TipoUnidad.CONTINUA, decimales=3)


class TestDinero:
    def test_e01_no_acepta_float(self) -> None:
        with pytest.raises(TypeError):
            Dinero(12.5, COP)  # type: ignore[arg-type]

    def test_e01_serializa_como_cadena_decimal_con_cuatro_decimales_y_moneda(self) -> None:
        assert Dinero(Decimal("12.5"), COP).a_api() == {"monto": "12.5000", "moneda": "COP"}

    def test_e01_se_construye_desde_cadena_decimal(self) -> None:
        assert Dinero.desde_api({"monto": "12.50", "moneda": "COP"}) == Dinero(Decimal("12.5"), COP)

    def test_e01_una_cadena_no_numerica_es_error_de_validacion(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            Dinero.desde_api({"monto": "doce", "moneda": "COP"})
        assert info.value.code == "MONTO_INVALIDO"

    def test_e01_mas_de_cuatro_decimales_se_rechaza(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            Dinero(Decimal("0.00001"), COP)
        assert info.value.code == "MONTO_INVALIDO"

    def test_e01_un_costo_bajo_la_unidad_minima_se_conserva_exacto(self) -> None:
        tornillo = Dinero(Decimal("12.5"), COP)
        assert (tornillo * Cantidad(Decimal("4000"))).monto == Decimal("50000.0000")

    def test_e01_no_se_suman_monedas_distintas(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            Dinero(Decimal(1), COP) + Dinero(Decimal(1), Moneda("USD"))
        assert info.value.code == "MONEDAS_DISTINTAS"

    def test_e01_suma_exacta_en_la_misma_moneda(self) -> None:
        total = Dinero(Decimal("0.1"), COP) + Dinero(Decimal("0.2"), COP)
        assert total == Dinero(Decimal("0.3"), COP)


class TestMoneda:
    @pytest.mark.parametrize("codigo", ["cop", "CO", "COPX", "12A", ""])
    def test_e01_solo_acepta_codigos_iso_4217(self, codigo: str) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            Moneda(codigo)
        assert info.value.code == "MONEDA_INVALIDA"

    def test_e01_el_codigo_se_expone_tal_cual(self) -> None:
        assert str(Moneda("USD")) == "USD"


class TestCantidad:
    def test_rn_07_no_acepta_float(self) -> None:
        with pytest.raises(TypeError):
            Cantidad(2.5)  # type: ignore[arg-type]

    def test_rn_07_rechaza_mas_de_tres_decimales(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            Cantidad(Decimal("1.0005"))
        assert info.value.code == "CANTIDAD_INVALIDA"

    def test_rn_07_serializa_como_cadena_con_tres_decimales(self) -> None:
        assert Cantidad(Decimal("5")).a_api() == "5.000"

    def test_rn_07_en_unidad_discreta_debe_ser_entera(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            Cantidad(Decimal("2.5")).validar_para(UNIDAD)
        assert info.value.code == "CANTIDAD_INVALIDA_PARA_UNIDAD"
        assert info.value.details["unidad"] == "unidad"

    def test_rn_07_en_unidad_continua_admite_hasta_tres_decimales(self) -> None:
        Cantidad(Decimal("2.125")).validar_para(KILO)

    def test_rn_07_una_unidad_continua_puede_limitar_los_decimales(self) -> None:
        litro_medio = UnidadMedida("l", "Litro", TipoUnidad.CONTINUA, decimales=1)
        with pytest.raises(err.ValidacionInvalida):
            Cantidad(Decimal("1.25")).validar_para(litro_medio)

    def test_rn_07_suma_y_resta_exactas(self) -> None:
        assert Cantidad(Decimal("0.1")) + Cantidad(Decimal("0.2")) == Cantidad(Decimal("0.3"))
        assert Cantidad(Decimal("1")) - Cantidad(Decimal("3")) == Cantidad(Decimal("-2"))


class TestUnidadMedida:
    def test_rf_cat_004_una_unidad_discreta_no_admite_decimales(self) -> None:
        with pytest.raises(err.ValidacionInvalida):
            UnidadMedida("caja", "Caja", TipoUnidad.DISCRETA, decimales=2)
