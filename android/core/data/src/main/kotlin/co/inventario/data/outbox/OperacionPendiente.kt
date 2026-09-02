package co.inventario.data.outbox

/**
 * Estado de una operación en la bandeja. `PENDIENTE` se reintenta; `CONFIRMADA` tiene la
 * respuesta del servidor; `RECHAZADA` recibió un error definitivo (4xx de negocio) y no se
 * vuelve a enviar.
 */
enum class EstadoOperacion { PENDIENTE, CONFIRMADA, RECHAZADA }

/**
 * Una escritura confirmada por la usuaria, con su clave de idempotencia. La clave se genera
 * al confirmar y no cambia nunca: es lo que impide el duplicado en el servidor (RF-INV-011).
 */
data class OperacionPendiente(
    val clave: String,
    val escritura: Escritura,
    val estado: EstadoOperacion,
    val creadaEn: Long,
    val intentos: Int = 0,
    val ultimoError: String? = null,
    val respuesta: String? = null,
)

/** Persistencia de la bandeja. Room en la app; en memoria en los tests. */
interface AlmacenBandeja {
    suspend fun guardar(operacion: OperacionPendiente)
    suspend fun actualizar(operacion: OperacionPendiente)
    suspend fun obtener(clave: String): OperacionPendiente?
    suspend fun pendientes(): List<OperacionPendiente>
}

class AlmacenBandejaEnMemoria : AlmacenBandeja {
    private val operaciones = linkedMapOf<String, OperacionPendiente>()

    override suspend fun guardar(operacion: OperacionPendiente) {
        operaciones[operacion.clave] = operacion
    }

    override suspend fun actualizar(operacion: OperacionPendiente) {
        operaciones[operacion.clave] = operacion
    }

    override suspend fun obtener(clave: String): OperacionPendiente? = operaciones[clave]

    override suspend fun pendientes(): List<OperacionPendiente> =
        operaciones.values.filter { it.estado == EstadoOperacion.PENDIENTE }.sortedBy { it.creadaEn }

    fun todas(): List<OperacionPendiente> = operaciones.values.toList()
}
