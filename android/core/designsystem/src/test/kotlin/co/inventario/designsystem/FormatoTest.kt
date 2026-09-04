package co.inventario.designsystem

import co.inventario.designsystem.componentes.Formato
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RNF-09: un número que no se puede leer de un vistazo no cumple. Y la app es en español, así
 * que los separadores son los del castellano, no los del idioma en que esté el teléfono.
 */
class FormatoTest {

    @Test
    fun `agrupa los miles al estilo espanol`() {
        assertEquals("66.129", Formato.monto("66129.0000"))
        assertEquals("1.148", Formato.cantidad(BigDecimal("1148")))
    }

    @Test
    fun `quita los ceros de adorno pero conserva los decimales que existen`() {
        assertEquals("14.900", Formato.monto("14900.0000"))
        // La enmienda E-01 existe porque un costo unitario puede caer bajo la unidad mínima de
        // la moneda: esos decimales son datos, no ruido, y no se redondean al mostrarlos.
        assertEquals("12,5", Formato.monto("12.5000"))
        assertEquals("0,0125", Formato.monto("0.0125"))
    }

    @Test
    fun `anade la moneda solo cuando se le pasa`() {
        assertEquals("14.900 COP", Formato.monto("14900.0000", "COP"))
        assertEquals("14.900", Formato.monto("14900.0000"))
    }

    @Test
    fun `un monto que no es un numero se devuelve tal cual, sin romper la pantalla`() {
        assertEquals("no-es-un-numero COP", Formato.monto("no-es-un-numero", "COP"))
    }

    @Test
    fun `las cantidades admiten hasta tres decimales, como el dominio`() {
        assertEquals("2,5", Formato.cantidad(BigDecimal("2.500")))
        assertEquals("0", Formato.cantidad(BigDecimal("0.000")))
        assertEquals("38", Formato.cantidad("38"))
    }
}
