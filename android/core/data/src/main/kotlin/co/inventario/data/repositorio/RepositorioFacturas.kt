package co.inventario.data.repositorio

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.mapeo.aDominio
import co.inventario.data.mapeo.aDto
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.outbox.Escritura
import co.inventario.data.outbox.ResultadoEscritura
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.dto.FacturaNuevaDto
import co.inventario.data.red.dto.MotivoEntradaDto
import co.inventario.data.red.dto.PagoDto
import co.inventario.data.red.dto.RecepcionesVinculacionDto
import co.inventario.data.red.errorDe
import co.inventario.data.red.llamada
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.EstadoPago
import co.inventario.domain.modelo.Factura
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Pagina
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** RF-FAC-001 / RF-FAC-003: importes tal como se teclean; aquí se normalizan al contrato. */
data class FacturaNueva(
    val proveedorId: String,
    val numero: String,
    val fechaEmision: LocalDate,
    val fechaVencimiento: LocalDate?,
    val moneda: String,
    val tasaCambio: String?,
    val baseGravable: String,
    val impuesto: String,
    val total: String,
    val notas: String?,
    val recepciones: List<String>,
)

data class FiltrosFacturas(
    val proveedorId: String? = null,
    val estadoPago: EstadoPago? = null,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
)

/** RF-FAC-008: la página trae el total acumulado del filtro aplicado. */
data class PaginaFacturas(val pagina: Pagina<Factura>, val totalFiltro: Dinero, val cantidadFiltro: Int)

/** RF-FAC-007: el ZIP ya descargado, listo para el menú de compartir. */
data class ArchivoExportado(val nombre: String, val bytes: ByteArray)

sealed interface ResultadoFactura {
    data class Confirmada(val factura: Factura) : ResultadoFactura
    data class Pendiente(val clave: String, val error: ErrorApp) : ResultadoFactura
    data class Rechazada(val error: ErrorApp) : ResultadoFactura
}

interface RepositorioFacturas {
    suspend fun listar(filtros: FiltrosFacturas = FiltrosFacturas(), cursor: String? = null): Resultado<PaginaFacturas>
    suspend fun factura(id: String): Resultado<Factura>
    suspend fun registrar(datos: FacturaNueva): ResultadoFactura
    suspend fun pagar(id: String, fechaPago: LocalDate): Resultado<Factura>
    suspend fun anular(id: String, motivo: String): Resultado<Factura>
    suspend fun vincular(id: String, recepciones: List<String>): Resultado<Factura>
    suspend fun adjuntarImagen(id: String, jpeg: ByteArray): Resultado<Factura>
    suspend fun quitarImagen(id: String, imagenId: String): Resultado<Factura>
    suspend fun exportar(desde: LocalDate, hasta: LocalDate): Resultado<ArchivoExportado>
}

@Singleton
class RepositorioFacturasApi @Inject constructor(
    private val api: InventarioApi,
    private val bandeja: BandejaSalida,
) : RepositorioFacturas {

    override suspend fun listar(filtros: FiltrosFacturas, cursor: String?): Resultado<PaginaFacturas> =
        llamada({
            api.facturas(filtros.proveedorId, filtros.estadoPago?.codigo, filtros.desde?.toString(), filtros.hasta?.toString(), cursor)
        }) { p ->
            PaginaFacturas(Pagina(p.datos.map { it.aDominio() }, p.cursorSiguiente, p.tieneMas), p.totalFiltro.aDominio(), p.cantidadFiltro)
        }

    override suspend fun factura(id: String): Resultado<Factura> = llamada({ api.factura(id) }) { it.aDominio() }

    override suspend fun registrar(datos: FacturaNueva): ResultadoFactura {
        val moneda = Moneda(datos.moneda)
        val dto = FacturaNuevaDto(
            proveedorId = datos.proveedorId,
            numero = datos.numero.trim(),
            fechaEmision = datos.fechaEmision.toString(),
            fechaVencimiento = datos.fechaVencimiento?.toString(),
            moneda = datos.moneda,
            tasaCambio = datos.tasaCambio?.trim()?.ifBlank { null },
            baseGravable = Dinero.desde(datos.baseGravable.trim(), moneda).aDto(),
            impuesto = Dinero.desde(datos.impuesto.trim(), moneda).aDto(),
            total = Dinero.desde(datos.total.trim(), moneda).aDto(),
            notas = datos.notas?.trim()?.ifBlank { null },
            recepciones = datos.recepciones,
        )
        return when (val r = bandeja.confirmar(Escritura.RegistrarFactura(dto))) {
            is ResultadoEscritura.Confirmada -> ResultadoFactura.Confirmada(r.factura()!!.aDominio())
            is ResultadoEscritura.Pendiente -> ResultadoFactura.Pendiente(r.clave, r.error)
            is ResultadoEscritura.Rechazada -> ResultadoFactura.Rechazada(r.error)
        }
    }

    override suspend fun pagar(id: String, fechaPago: LocalDate): Resultado<Factura> =
        llamada({ api.pagarFactura(id, PagoDto(fechaPago.toString())) }) { it.aDominio() }

    override suspend fun anular(id: String, motivo: String): Resultado<Factura> =
        llamada({ api.anularFactura(id, MotivoEntradaDto(motivo.trim())) }) { it.aDominio() }

    override suspend fun vincular(id: String, recepciones: List<String>): Resultado<Factura> =
        llamada({ api.vincularRecepciones(id, RecepcionesVinculacionDto(recepciones)) }) { it.aDominio() }

    override suspend fun adjuntarImagen(id: String, jpeg: ByteArray): Resultado<Factura> {
        val parte = MultipartBody.Part.createFormData("archivo", "factura.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        return llamada({ api.adjuntarImagenFactura(id, parte) }) { it.aDominio() }
    }

    override suspend fun quitarImagen(id: String, imagenId: String): Resultado<Factura> =
        llamada({ api.quitarImagenFactura(id, imagenId) }) { it.aDominio() }

    /** Descarga el ZIP entero (RF-FAC-007): son pocos MB y se entrega al menú de compartir. */
    override suspend fun exportar(desde: LocalDate, hasta: LocalDate): Resultado<ArchivoExportado> =
        try {
            val respuesta = api.exportarFacturas(desde.toString(), hasta.toString())
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                val nombre = respuesta.headers()["Content-Disposition"]
                    ?.let { Regex("filename=\"?([^\";]+)\"?").find(it)?.groupValues?.get(1) }
                    ?: "facturas_${desde}_$hasta.zip"
                Resultado.Exito(ArchivoExportado(nombre, cuerpo.use { it.bytes() }))
            } else {
                Resultado.Fallo(errorDe(respuesta))
            }
        } catch (_: IOException) {
            Resultado.Fallo(co.inventario.common.error.MapeadorErrores.error(co.inventario.common.error.MapeadorErrores.SIN_RED))
        }
}
