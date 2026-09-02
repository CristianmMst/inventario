package co.inventario.data.outbox

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.Json
import co.inventario.data.red.dto.AnulacionDto
import co.inventario.data.red.dto.ConteoSalidaDto
import co.inventario.data.red.dto.MovimientoDto
import co.inventario.data.red.llamada
import java.util.UUID

/** Lo que la UI recibe tras confirmar: solo `Confirmada` puede mostrarse como éxito (RNF-07). */
sealed interface ResultadoEscritura {
    val clave: String

    data class Confirmada(override val clave: String, val escritura: Escritura, val respuesta: String) : ResultadoEscritura {
        fun movimiento(): MovimientoDto? = when (escritura) {
            is Escritura.RegistrarMovimiento, is Escritura.Anular -> Json.decodeFromString(MovimientoDto.serializer(), respuesta)
            is Escritura.Contar -> null
        }

        fun conteo(): ConteoSalidaDto? = when (escritura) {
            is Escritura.Contar -> Json.decodeFromString(ConteoSalidaDto.serializer(), respuesta)
            else -> null
        }
    }

    /** No llegó confirmación: la operación sigue guardada y se reintentará con la misma clave. */
    data class Pendiente(override val clave: String, val error: ErrorApp) : ResultadoEscritura

    /** El servidor la rechazó por negocio (409, 422…): no tiene sentido reintentarla igual. */
    data class Rechazada(override val clave: String, val error: ErrorApp) : ResultadoEscritura
}

/**
 * Bandeja de salida (plan.md §8.5). `confirmar` genera la clave, persiste la operación **antes**
 * de enviarla y luego la envía; `reintentarPendientes` se llama al arrancar la app. Una
 * operación nunca cambia de clave: si el servidor ya la procesó, devuelve el mismo resultado.
 */
class BandejaSalida(
    private val almacen: AlmacenBandeja,
    private val api: InventarioApi,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val generarClave: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun confirmar(escritura: Escritura): ResultadoEscritura {
        val operacion = OperacionPendiente(
            clave = generarClave(),
            escritura = escritura,
            estado = EstadoOperacion.PENDIENTE,
            creadaEn = reloj(),
        )
        almacen.guardar(operacion)
        return enviar(operacion)
    }

    suspend fun reintentar(clave: String): ResultadoEscritura? =
        almacen.obtener(clave)?.takeIf { it.estado == EstadoOperacion.PENDIENTE }?.let { enviar(it) }

    suspend fun reintentarPendientes(): List<ResultadoEscritura> = almacen.pendientes().map { enviar(it) }

    suspend fun pendientes(): List<OperacionPendiente> = almacen.pendientes()

    private suspend fun enviar(operacion: OperacionPendiente): ResultadoEscritura {
        val intento = operacion.copy(intentos = operacion.intentos + 1)
        return when (val resultado = ejecutar(intento)) {
            is Resultado.Exito -> {
                almacen.actualizar(intento.copy(estado = EstadoOperacion.CONFIRMADA, respuesta = resultado.valor, ultimoError = null))
                ResultadoEscritura.Confirmada(intento.clave, intento.escritura, resultado.valor)
            }
            is Resultado.Fallo -> {
                val error = resultado.error
                if (esTransitorio(error.codigo)) {
                    almacen.actualizar(intento.copy(ultimoError = error.codigo))
                    ResultadoEscritura.Pendiente(intento.clave, error)
                } else {
                    almacen.actualizar(intento.copy(estado = EstadoOperacion.RECHAZADA, ultimoError = error.codigo))
                    ResultadoEscritura.Rechazada(intento.clave, error)
                }
            }
        }
    }

    private suspend fun ejecutar(operacion: OperacionPendiente): Resultado<String> =
        when (val escritura = operacion.escritura) {
            is Escritura.RegistrarMovimiento ->
                llamada({ api.registrarMovimiento(operacion.clave, escritura.datos) }) { Json.encodeToString(MovimientoDto.serializer(), it) }
            is Escritura.Anular ->
                llamada({ api.anularMovimiento(operacion.clave, escritura.movimientoId, AnulacionDto(escritura.nota)) }) {
                    Json.encodeToString(MovimientoDto.serializer(), it)
                }
            is Escritura.Contar ->
                llamada({ api.conteo(operacion.clave, escritura.productoId, escritura.datos) }) { Json.encodeToString(ConteoSalidaDto.serializer(), it) }
        }

    /** Sin red, tiempo agotado, 5xx o "todavía en curso": el servidor puede no haber terminado; se reintenta. */
    private fun esTransitorio(codigo: String): Boolean = codigo in TRANSITORIOS

    private companion object {
        val TRANSITORIOS = setOf(
            MapeadorErrores.SIN_RED,
            MapeadorErrores.TIEMPO_AGOTADO,
            MapeadorErrores.ERROR_SERVIDOR,
            "ERROR_INTERNO",
            "OPERACION_EN_CURSO",
        )
    }
}
