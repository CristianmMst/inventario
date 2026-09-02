package co.inventario.data.repositorio

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.mapeo.aDominio
import co.inventario.data.mapeo.aDto
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.outbox.Escritura
import co.inventario.data.outbox.ResultadoEscritura
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.dto.LineaOrdenEntradaDto
import co.inventario.data.red.dto.LineaRecepcionEntradaDto
import co.inventario.data.red.dto.MotivoEntradaDto
import co.inventario.data.red.dto.OrdenEdicionDto
import co.inventario.data.red.dto.OrdenNuevaDto
import co.inventario.data.red.dto.ProveedorDatosDto
import co.inventario.data.red.dto.RecepcionNuevaDto
import co.inventario.data.red.llamada
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Orden
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.Proveedor
import co.inventario.domain.modelo.Recepcion
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** RF-COM-001: campos del proveedor tal como se teclean; vacíos = sin valor. */
data class ProveedorDatos(
    val nombre: String? = null,
    val identificacionFiscal: String? = null,
    val contacto: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
)

data class LineaOrdenNueva(val productoId: String, val cantidad: String, val costoUnitarioEstimado: String? = null)

data class OrdenNueva(
    val proveedorId: String,
    val fechaEsperada: LocalDate? = null,
    val moneda: String,
    val notas: String? = null,
    val lineas: List<LineaOrdenNueva>,
)

data class LineaRecepcionNueva(val productoId: String, val cantidad: String, val costoUnitario: String)

/** RF-COM-004 / RF-COM-005: con `ordenId` null es una recepción directa. */
data class RecepcionNueva(
    val proveedorId: String,
    val ordenId: String? = null,
    val fecha: LocalDate? = null,
    val moneda: String,
    val tasaCambio: String? = null,
    val notas: String? = null,
    val lineas: List<LineaRecepcionNueva>,
)

data class FiltrosOrdenes(val proveedorId: String? = null, val estado: String? = null, val desde: LocalDate? = null, val hasta: LocalDate? = null)

data class FiltrosRecepciones(
    val proveedorId: String? = null,
    val ordenId: String? = null,
    val estado: String? = null,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
)

/** RF-COM-006 / RF-COM-009: confirmar es atómico; el exceso solo entra con confirmación explícita. */
sealed interface ResultadoConfirmacion {
    data class Confirmada(val recepcion: Recepcion) : ResultadoConfirmacion
    data class ExcesoSobreOrden(val ordenNumero: String?, val lineasConExceso: Int) : ResultadoConfirmacion
    data class Pendiente(val clave: String, val error: ErrorApp) : ResultadoConfirmacion
    data class Rechazada(val error: ErrorApp) : ResultadoConfirmacion
}

interface RepositorioCompras {
    suspend fun proveedores(incluirArchivados: Boolean = false): Resultado<List<Proveedor>>
    suspend fun proveedor(id: String): Resultado<Proveedor>
    suspend fun crearProveedor(datos: ProveedorDatos): Resultado<Proveedor>
    suspend fun editarProveedor(id: String, datos: ProveedorDatos): Resultado<Proveedor>
    suspend fun eliminarProveedor(id: String): Resultado<Unit>
    suspend fun archivarProveedor(id: String, archivar: Boolean): Resultado<Proveedor>

    suspend fun ordenes(filtros: FiltrosOrdenes = FiltrosOrdenes(), cursor: String? = null): Resultado<Pagina<Orden>>
    suspend fun orden(id: String): Resultado<Orden>
    suspend fun crearOrden(datos: OrdenNueva): Resultado<Orden>
    suspend fun editarLineasOrden(id: String, lineas: List<LineaOrdenNueva>, moneda: String, notas: String?, fechaEsperada: LocalDate?): Resultado<Orden>
    suspend fun emitirOrden(id: String): Resultado<Orden>
    suspend fun cancelarOrden(id: String, motivo: String): Resultado<Orden>
    suspend fun cerrarOrdenConFaltante(id: String, motivo: String): Resultado<Orden>

    suspend fun recepciones(filtros: FiltrosRecepciones = FiltrosRecepciones(), cursor: String? = null): Resultado<Pagina<Recepcion>>
    suspend fun recepcion(id: String): Resultado<Recepcion>
    suspend fun crearRecepcion(datos: RecepcionNueva): Resultado<Recepcion>
    suspend fun confirmarRecepcion(id: String, confirmarExceso: Boolean): ResultadoConfirmacion
}

@Singleton
class RepositorioComprasApi @Inject constructor(
    private val api: InventarioApi,
    private val bandeja: BandejaSalida,
) : RepositorioCompras {

    override suspend fun proveedores(incluirArchivados: Boolean): Resultado<List<Proveedor>> =
        llamada({ api.proveedores(estado = if (incluirArchivados) null else "activo") }) { p -> p.datos.map { it.aDominio() } }

    override suspend fun proveedor(id: String): Resultado<Proveedor> = llamada({ api.proveedor(id) }) { it.aDominio() }

    override suspend fun crearProveedor(datos: ProveedorDatos): Resultado<Proveedor> =
        llamada({ api.crearProveedor(datos.aDto()) }) { it.aDominio() }

    override suspend fun editarProveedor(id: String, datos: ProveedorDatos): Resultado<Proveedor> =
        llamada({ api.editarProveedor(id, datos.aDto()) }) { it.aDominio() }

    override suspend fun eliminarProveedor(id: String): Resultado<Unit> = llamada({ api.eliminarProveedor(id) }) { }

    override suspend fun archivarProveedor(id: String, archivar: Boolean): Resultado<Proveedor> =
        llamada({ if (archivar) api.archivarProveedor(id) else api.desarchivarProveedor(id) }) { it.aDominio() }

    override suspend fun ordenes(filtros: FiltrosOrdenes, cursor: String?): Resultado<Pagina<Orden>> =
        llamada({ api.ordenes(filtros.proveedorId, filtros.estado, filtros.desde?.toString(), filtros.hasta?.toString(), cursor) }) { it.aDominio { o -> o.aDominio() } }

    override suspend fun orden(id: String): Resultado<Orden> = llamada({ api.orden(id) }) { it.aDominio() }

    override suspend fun crearOrden(datos: OrdenNueva): Resultado<Orden> {
        val moneda = Moneda(datos.moneda)
        val dto = OrdenNuevaDto(
            proveedorId = datos.proveedorId,
            fechaEsperada = datos.fechaEsperada?.toString(),
            moneda = datos.moneda,
            notas = datos.notas.limpio(),
            lineas = datos.lineas.map { it.aDto(moneda) },
        )
        return llamada({ api.crearOrden(dto) }) { it.aDominio() }
    }

    override suspend fun editarLineasOrden(id: String, lineas: List<LineaOrdenNueva>, moneda: String, notas: String?, fechaEsperada: LocalDate?): Resultado<Orden> {
        val dto = OrdenEdicionDto(fechaEsperada = fechaEsperada?.toString(), notas = notas.limpio(), lineas = lineas.map { it.aDto(Moneda(moneda)) })
        return llamada({ api.editarOrden(id, dto) }) { it.aDominio() }
    }

    override suspend fun emitirOrden(id: String): Resultado<Orden> = llamada({ api.emitirOrden(id) }) { it.aDominio() }

    override suspend fun cancelarOrden(id: String, motivo: String): Resultado<Orden> =
        llamada({ api.cancelarOrden(id, MotivoEntradaDto(motivo.trim())) }) { it.aDominio() }

    override suspend fun cerrarOrdenConFaltante(id: String, motivo: String): Resultado<Orden> =
        llamada({ api.cerrarOrdenConFaltante(id, MotivoEntradaDto(motivo.trim())) }) { it.aDominio() }

    override suspend fun recepciones(filtros: FiltrosRecepciones, cursor: String?): Resultado<Pagina<Recepcion>> =
        llamada({
            api.recepciones(filtros.proveedorId, filtros.ordenId, filtros.estado, filtros.desde?.toString(), filtros.hasta?.toString(), cursor)
        }) { it.aDominio { r -> r.aDominio() } }

    override suspend fun recepcion(id: String): Resultado<Recepcion> = llamada({ api.recepcion(id) }) { it.aDominio() }

    override suspend fun crearRecepcion(datos: RecepcionNueva): Resultado<Recepcion> {
        val moneda = Moneda(datos.moneda)
        val dto = RecepcionNuevaDto(
            proveedorId = datos.proveedorId,
            ordenId = datos.ordenId,
            fecha = datos.fecha?.toString(),
            moneda = datos.moneda,
            tasaCambio = datos.tasaCambio.limpio(),
            notas = datos.notas.limpio(),
            lineas = datos.lineas.map {
                LineaRecepcionEntradaDto(it.productoId, Cantidad.desde(it.cantidad.trim()).aApi(), Dinero.desde(it.costoUnitario.trim(), moneda).aDto())
            },
        )
        return llamada({ api.crearRecepcion(dto) }) { it.aDominio() }
    }

    override suspend fun confirmarRecepcion(id: String, confirmarExceso: Boolean): ResultadoConfirmacion =
        when (val r = bandeja.confirmar(Escritura.ConfirmarRecepcion(id, confirmarExceso))) {
            is ResultadoEscritura.Confirmada -> ResultadoConfirmacion.Confirmada(r.recepcion()!!.aDominio())
            is ResultadoEscritura.Pendiente -> ResultadoConfirmacion.Pendiente(r.clave, r.error)
            is ResultadoEscritura.Rechazada ->
                if (r.error.codigo == "EXCESO_SOBRE_ORDEN") {
                    ResultadoConfirmacion.ExcesoSobreOrden(r.error.detalles["orden_numero"], r.error.detalles["lineas_con_exceso"]?.toIntOrNull() ?: 0)
                } else {
                    ResultadoConfirmacion.Rechazada(r.error)
                }
        }

    private fun ProveedorDatos.aDto() = ProveedorDatosDto(
        nombre = nombre.limpio(), identificacionFiscal = identificacionFiscal.limpio(), contacto = contacto.limpio(),
        telefono = telefono.limpio(), email = email.limpio(), direccion = direccion.limpio(), notas = notas.limpio(),
    )

    private fun LineaOrdenNueva.aDto(moneda: Moneda) = LineaOrdenEntradaDto(
        productoId, Cantidad.desde(cantidad.trim()).aApi(), costoUnitarioEstimado.limpio()?.let { Dinero.desde(it, moneda).aDto() },
    )

    private fun String?.limpio(): String? = this?.trim()?.ifBlank { null }
}
