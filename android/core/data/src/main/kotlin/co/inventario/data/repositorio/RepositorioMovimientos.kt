package co.inventario.data.repositorio

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.mapeo.aDominio
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.outbox.Escritura
import co.inventario.data.outbox.ResultadoEscritura
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.dto.ConteoEntradaDto
import co.inventario.data.red.dto.MovimientoNuevoDto
import co.inventario.data.red.llamada
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Conteo
import co.inventario.domain.modelo.Motivo
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.Producto
import co.inventario.domain.modelo.TipoMovimiento
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que la usuaria confirmó en pantalla. La cantidad va como la tecleó; aquí se normaliza. */
data class RegistroMovimiento(
    val productoId: String,
    val tipo: TipoMovimiento,
    val cantidad: String,
    val motivo: String,
    val nota: String?,
    val forzar: Boolean = false,
    val direccion: Int? = null,
)

/** RF-INV-005: el 409 de stock insuficiente es un caso propio, no un error genérico. */
sealed interface ResultadoMovimiento {
    data class Confirmado(val movimiento: Movimiento) : ResultadoMovimiento
    data class StockInsuficiente(val disponible: Cantidad, val solicitado: Cantidad, val puedeForzar: Boolean) : ResultadoMovimiento
    /** Guardado en la bandeja sin confirmación del servidor: se reintenta con la misma clave (RNF-06). */
    data class Pendiente(val clave: String, val error: ErrorApp) : ResultadoMovimiento
    data class Rechazado(val error: ErrorApp) : ResultadoMovimiento
}

sealed interface ResultadoConteo {
    data class Confirmado(val conteo: Conteo) : ResultadoConteo
    data class Pendiente(val clave: String, val error: ErrorApp) : ResultadoConteo
    data class Rechazado(val error: ErrorApp) : ResultadoConteo
}

interface RepositorioMovimientos {
    suspend fun producto(productoId: String): Resultado<Producto>
    suspend fun motivos(tipo: TipoMovimiento): Resultado<List<Motivo>>
    suspend fun registrar(registro: RegistroMovimiento): ResultadoMovimiento
    suspend fun reintentar(clave: String): ResultadoMovimiento
    suspend fun contar(productoId: String, cantidadContada: String, nota: String?): ResultadoConteo
    suspend fun anular(movimientoId: String, nota: String?): ResultadoMovimiento
    suspend fun historial(productoId: String, cursor: String? = null): Resultado<Pagina<Movimiento>>
}

/** Todas las escrituras pasan por la bandeja de salida (plan.md §8.5); las lecturas van directas. */
@Singleton
class RepositorioMovimientosApi @Inject constructor(
    private val api: InventarioApi,
    private val bandeja: BandejaSalida,
) : RepositorioMovimientos {

    override suspend fun producto(productoId: String): Resultado<Producto> = llamada({ api.producto(productoId) }) { it.aDominio() }

    override suspend fun motivos(tipo: TipoMovimiento): Resultado<List<Motivo>> =
        llamada({ api.motivos(tipo.codigo) }) { pagina -> pagina.datos.map { it.aDominio() } }

    override suspend fun registrar(registro: RegistroMovimiento): ResultadoMovimiento {
        val dto = MovimientoNuevoDto(
            productoId = registro.productoId,
            tipo = registro.tipo.codigo,
            cantidad = Cantidad.desde(registro.cantidad.trim()).aApi(),
            motivo = registro.motivo,
            nota = registro.nota?.trim()?.ifBlank { null },
            forzar = registro.forzar,
            direccion = registro.direccion,
        )
        return bandeja.confirmar(Escritura.RegistrarMovimiento(dto)).aMovimiento()
    }

    override suspend fun reintentar(clave: String): ResultadoMovimiento =
        bandeja.reintentar(clave)?.aMovimiento()
            ?: ResultadoMovimiento.Rechazado(ErrorApp("OPERACION_NO_PENDIENTE", "Esa operación ya no está pendiente."))

    override suspend fun contar(productoId: String, cantidadContada: String, nota: String?): ResultadoConteo {
        val dto = ConteoEntradaDto(cantidadContada = Cantidad.desde(cantidadContada.trim()).aApi(), nota = nota?.trim()?.ifBlank { null })
        return when (val r = bandeja.confirmar(Escritura.Contar(productoId, dto))) {
            is ResultadoEscritura.Confirmada -> {
                val c = r.conteo()!!
                ResultadoConteo.Confirmado(
                    Conteo(Cantidad.desde(c.stockAnterior), Cantidad.desde(c.cantidadContada), Cantidad.desde(c.diferencia), c.movimiento?.aDominio()),
                )
            }
            is ResultadoEscritura.Pendiente -> ResultadoConteo.Pendiente(r.clave, r.error)
            is ResultadoEscritura.Rechazada -> ResultadoConteo.Rechazado(r.error)
        }
    }

    override suspend fun anular(movimientoId: String, nota: String?): ResultadoMovimiento =
        bandeja.confirmar(Escritura.Anular(movimientoId, nota?.trim()?.ifBlank { null })).aMovimiento()

    override suspend fun historial(productoId: String, cursor: String?): Resultado<Pagina<Movimiento>> =
        llamada({ api.historial(productoId, cursor) }) { it.aDominio { m -> m.aDominio() } }

    private fun ResultadoEscritura.aMovimiento(): ResultadoMovimiento = when (this) {
        is ResultadoEscritura.Confirmada -> ResultadoMovimiento.Confirmado(movimiento()!!.aDominio())
        is ResultadoEscritura.Pendiente -> ResultadoMovimiento.Pendiente(clave, error)
        is ResultadoEscritura.Rechazada ->
            if (error.codigo == "STOCK_INSUFICIENTE") {
                ResultadoMovimiento.StockInsuficiente(
                    disponible = Cantidad.desde(error.detalles["disponible"] ?: "0"),
                    solicitado = Cantidad.desde(error.detalles["solicitado"] ?: "0"),
                    puedeForzar = error.detalles["puede_forzar"] == "true",
                )
            } else {
                ResultadoMovimiento.Rechazado(error)
            }
    }
}
