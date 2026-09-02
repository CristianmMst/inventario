package co.inventario.domain.modelo

/** RF-INV-013: resultado de un conteo físico; `movimiento` es null cuando la diferencia fue cero. */
data class Conteo(
    val stockAnterior: Cantidad,
    val cantidadContada: Cantidad,
    val diferencia: Cantidad,
    val movimiento: Movimiento?,
)
