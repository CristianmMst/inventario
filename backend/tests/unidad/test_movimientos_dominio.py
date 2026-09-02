"""RF-INV-001, RF-INV-009, RN-07, RN-16: dominio de movimientos, sin base de datos."""

from decimal import Decimal

import pytest

from app.dominio import errores as err
from app.dominio.movimientos import (
    TipoMovimiento,
    signo_de,
    stock_resultante,
    validar_cantidad_movimiento,
)
from app.dominio.tipos import Cantidad, TipoUnidad, UnidadMedida

UNIDAD = UnidadMedida("unidad", "Unidad", TipoUnidad.DISCRETA, 0)
KILO = UnidadMedida("kg", "Kilogramo", TipoUnidad.CONTINUA, 3)


class TestTipos:
    def test_rf_inv_001_existen_exactamente_los_cinco_tipos(self) -> None:
        assert {t.value for t in TipoMovimiento} == {
            "entrada",
            "salida",
            "ajuste",
            "merma",
            "contramovimiento",
        }

    @pytest.mark.parametrize(
        ("tipo", "signo"),
        [
            (TipoMovimiento.ENTRADA, 1),
            (TipoMovimiento.SALIDA, -1),
            (TipoMovimiento.MERMA, -1),
        ],
    )
    def test_rf_inv_001_el_tipo_determina_el_signo(self, tipo: TipoMovimiento, signo: int) -> None:
        assert signo_de(tipo) == signo

    def test_rn_16_la_merma_es_un_tipo_propio_distinto_de_la_salida(self) -> None:
        assert TipoMovimiento.MERMA is not TipoMovimiento.SALIDA
        assert TipoMovimiento.MERMA.resta_stock and TipoMovimiento.SALIDA.resta_stock

    def test_rf_inv_001_el_ajuste_y_el_contramovimiento_llevan_el_signo_en_la_direccion(
        self,
    ) -> None:
        with pytest.raises(ValueError):
            signo_de(TipoMovimiento.AJUSTE)
        with pytest.raises(ValueError):
            signo_de(TipoMovimiento.CONTRAMOVIMIENTO)


class TestCantidad:
    def test_rf_inv_009_dos_y_medio_en_unidad_discreta_se_rechaza(self) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            validar_cantidad_movimiento(Cantidad(Decimal("2.5")), UNIDAD)
        assert info.value.code == "CANTIDAD_INVALIDA_PARA_UNIDAD"

    def test_rf_inv_009_dos_y_medio_en_kilos_se_acepta(self) -> None:
        validar_cantidad_movimiento(Cantidad(Decimal("2.5")), KILO)

    @pytest.mark.parametrize("valor", ["0", "-1", "-0.001"])
    def test_rn_07_cantidad_cero_o_negativa_se_rechaza(self, valor: str) -> None:
        with pytest.raises(err.ValidacionInvalida) as info:
            validar_cantidad_movimiento(Cantidad(Decimal(valor)), KILO)
        assert info.value.code == "CANTIDAD_NO_POSITIVA"

    def test_rn_07_el_sentido_lo_da_el_tipo_nunca_el_signo(self) -> None:
        stock = Cantidad(Decimal("10"))
        assert stock_resultante(stock, TipoMovimiento.SALIDA, Cantidad(Decimal("3"))) == Cantidad(
            Decimal("7")
        )
        assert stock_resultante(stock, TipoMovimiento.MERMA, Cantidad(Decimal("3"))) == Cantidad(
            Decimal("7")
        )
        assert stock_resultante(stock, TipoMovimiento.ENTRADA, Cantidad(Decimal("3"))) == Cantidad(
            Decimal("13")
        )

    def test_rn_15_el_ajuste_lleva_direccion_explicita(self) -> None:
        stock = Cantidad(Decimal("10"))
        assert stock_resultante(
            stock, TipoMovimiento.AJUSTE, Cantidad(Decimal("2")), direccion=-1
        ) == Cantidad(Decimal("8"))
        assert stock_resultante(
            stock, TipoMovimiento.AJUSTE, Cantidad(Decimal("2")), direccion=1
        ) == Cantidad(Decimal("12"))

    def test_rn_07_un_ajuste_sin_direccion_es_un_error_de_programacion(self) -> None:
        with pytest.raises(ValueError):
            stock_resultante(Cantidad(Decimal("10")), TipoMovimiento.AJUSTE, Cantidad(Decimal("2")))
