package co.inventario.domain

import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.TipoUnidad
import co.inventario.domain.modelo.UnidadMedida
import co.inventario.domain.regla.ReglaViolada
import co.inventario.domain.regla.cuadraFactura
import co.inventario.domain.regla.validarCantidadMovimiento
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** RN-07, RN-18, RF-INV-009: reglas puras del dominio, en la JVM, sin Android. */
class TiposTest {

    private val cop = Moneda("COP")
    private val unidad = UnidadMedida("unidad", "Unidad", TipoUnidad.DISCRETA, 0)
    private val kilo = UnidadMedida("kg", "Kilogramo", TipoUnidad.CONTINUA, 3)

    @Test
    fun `rn 07 dos y medio en unidad discreta se rechaza`() {
        val error = assertFailsWith<ReglaViolada> {
            validarCantidadMovimiento(Cantidad.desde("2.5"), unidad)
        }
        assertEquals("CANTIDAD_INVALIDA_PARA_UNIDAD", error.codigo)
    }

    @Test
    fun `rn 07 dos y medio en kilos se acepta`() {
        validarCantidadMovimiento(Cantidad.desde("2.5"), kilo)
    }

    @Test
    fun `rn 07 cantidad cero o negativa se rechaza`() {
        for (valor in listOf("0", "-1", "-0.001")) {
            val error = assertFailsWith<ReglaViolada> {
                validarCantidadMovimiento(Cantidad.desde(valor), kilo)
            }
            assertEquals("CANTIDAD_NO_POSITIVA", error.codigo)
        }
    }

    @Test
    fun `rn 07 la cantidad rechaza mas de tres decimales y no usa double`() {
        assertFailsWith<ReglaViolada> { Cantidad.desde("1.0005") }
        assertEquals("5.000", Cantidad.desde("5").aApi())
        assertEquals(Cantidad.desde("0.3"), Cantidad.desde("0.1") + Cantidad.desde("0.2"))
    }

    @Test
    fun `e01 el dinero es decimal exacto y viaja como cadena mas iso 4217`() {
        val tornillo = Dinero.desde("12.50", cop)
        assertEquals("12.5000", tornillo.aApi().monto)
        assertEquals("COP", tornillo.aApi().moneda)
        assertEquals(BigDecimal("50000.0000"), (tornillo * Cantidad.desde("4000")).monto)
        assertFailsWith<ReglaViolada> { Dinero.desde("0.00001", cop) }
        assertFailsWith<ReglaViolada> { Dinero.desde("doce", cop) }
    }

    @Test
    fun `e01 no se suman monedas distintas`() {
        val error = assertFailsWith<ReglaViolada> { Dinero.desde("1", cop) + Dinero.desde("1", Moneda("USD")) }
        assertEquals("MONEDAS_DISTINTAS", error.codigo)
    }

    @Test
    fun `e01 la moneda es un codigo iso 4217`() {
        for (codigo in listOf("cop", "CO", "COPX", "")) {
            assertFailsWith<ReglaViolada> { Moneda(codigo) }
        }
    }

    @Test
    fun `rn 18 base mas impuesto debe igualar exactamente el total`() {
        val ok = cuadraFactura(Dinero.desde("100", cop), Dinero.desde("19", cop), Dinero.desde("119", cop))
        assertTrue(ok.cuadra)
        val mal = cuadraFactura(Dinero.desde("100", cop), Dinero.desde("19", cop), Dinero.desde("119.01", cop))
        assertTrue(!mal.cuadra)
        assertEquals("0.0100", mal.diferencia.aApi().monto)
    }

    @Test
    fun `rf cat 004 una unidad discreta no admite decimales`() {
        assertFailsWith<ReglaViolada> { UnidadMedida("caja", "Caja", TipoUnidad.DISCRETA, 2) }
    }
}
