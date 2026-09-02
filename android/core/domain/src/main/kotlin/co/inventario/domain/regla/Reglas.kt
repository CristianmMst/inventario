package co.inventario.domain.regla

import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.TipoUnidad
import co.inventario.domain.modelo.UnidadMedida

/** Una regla de negocio incumplida. `codigo` coincide con el `code` que devolvería la API. */
class ReglaViolada(val codigo: String, mensaje: String) : IllegalArgumentException(mensaje)

/** RF-INV-009 / RN-07: mayor que cero; entera en unidad discreta; hasta 3 decimales en continua. */
fun validarCantidadMovimiento(cantidad: Cantidad, unidad: UnidadMedida) {
    if (!cantidad.esPositiva()) {
        throw ReglaViolada(
            "CANTIDAD_NO_POSITIVA",
            "La cantidad debe ser mayor que cero. El sentido lo da el tipo de movimiento.",
        )
    }
    if (cantidad.decimales() > unidad.decimales) {
        val mensaje = if (unidad.tipo == TipoUnidad.DISCRETA) {
            "${unidad.nombre} se cuenta por unidades enteras."
        } else {
            "${unidad.nombre} admite como mucho ${unidad.decimales} decimales."
        }
        throw ReglaViolada("CANTIDAD_INVALIDA_PARA_UNIDAD", mensaje)
    }
}

data class Cuadre(val cuadra: Boolean, val diferencia: Dinero)

/** RN-18: base + impuesto = total, exactamente. La diferencia se muestra antes de enviar. */
fun cuadraFactura(base: Dinero, impuesto: Dinero, total: Dinero): Cuadre {
    val suma = base + impuesto
    return Cuadre(cuadra = suma == total, diferencia = total - suma)
}
