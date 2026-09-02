package co.inventario.data.outbox

import co.inventario.data.red.dto.ConteoEntradaDto
import co.inventario.data.red.dto.MovimientoNuevoDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una escritura de negocio que pasa por la bandeja de salida (plan.md §8.5). Se serializa tal
 * cual en Room para poder reenviarla al arrancar con la misma clave de idempotencia (RNF-06).
 */
@Serializable
sealed interface Escritura {

    /** RF-INV-001: entrada, salida, merma o ajuste registrados a mano. */
    @Serializable
    @SerialName("registrar_movimiento")
    data class RegistrarMovimiento(val datos: MovimientoNuevoDto) : Escritura

    /** RF-INV-008: anulación por contramovimiento; la nota es obligatoria en el servidor. */
    @Serializable
    @SerialName("anular_movimiento")
    data class Anular(@SerialName("movimiento_id") val movimientoId: String, val nota: String?) : Escritura

    /** RF-INV-013: conteo físico; el servidor calcula y registra la diferencia. */
    @Serializable
    @SerialName("contar")
    data class Contar(@SerialName("producto_id") val productoId: String, val datos: ConteoEntradaDto) : Escritura
}
