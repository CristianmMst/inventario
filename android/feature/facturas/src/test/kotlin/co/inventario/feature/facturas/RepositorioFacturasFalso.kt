package co.inventario.feature.facturas

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.repositorio.ArchivoExportado
import co.inventario.data.repositorio.FacturaNueva
import co.inventario.data.repositorio.FiltrosFacturas
import co.inventario.data.repositorio.PaginaFacturas
import co.inventario.data.repositorio.RepositorioFacturas
import co.inventario.data.repositorio.ResultadoFactura
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.EstadoPago
import co.inventario.domain.modelo.Factura
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.ProveedorBreve
import java.time.Instant
import java.time.LocalDate

val COP = Moneda("COP")

fun facturaDePrueba(id: String = "f1", estado: EstadoPago = EstadoPago.PENDIENTE, total: String = "119000") = Factura(
    id = id, proveedor = ProveedorBreve("pr1", "Distribuidora Norte"), numero = "FE-$id", fechaEmision = LocalDate.of(2026, 9, 1), fechaVencimiento = null,
    moneda = COP, tasaCambio = "1.00000000", baseGravable = Dinero.desde("100000", COP), impuesto = Dinero.desde("19000", COP),
    total = Dinero.desde(total, COP), totalBase = Dinero.desde(total, COP), estadoPago = estado, fechaPago = null, motivoAnulacion = null, notas = null,
    recepciones = emptyList(), imagenes = emptyList(), creadaEn = Instant.parse("2026-09-02T10:00:00Z"),
)

class RepositorioFacturasFalso : RepositorioFacturas {
    var facturas = mutableListOf(facturaDePrueba("f1"), facturaDePrueba("f2", EstadoPago.PAGADA, "50000"))
    val registradas = mutableListOf<FacturaNueva>()
    val pagos = mutableListOf<Pair<String, LocalDate>>()
    val vinculaciones = mutableListOf<Pair<String, List<String>>>()
    val imagenes = mutableListOf<Pair<String, ByteArray>>()
    var exportacion: Resultado<ArchivoExportado> = Resultado.Exito(ArchivoExportado("facturas_2026-09-01_2026-09-30.zip", byteArrayOf(0x50, 0x4B)))
    var falloRegistro: ErrorApp? = null

    override suspend fun listar(filtros: FiltrosFacturas, cursor: String?): Resultado<PaginaFacturas> {
        val lista = facturas.filter { filtros.estadoPago == null || it.estadoPago == filtros.estadoPago }
        val total = lista.fold(Dinero.desde("0", COP)) { acc, f -> acc + f.totalBase }
        return Resultado.Exito(PaginaFacturas(Pagina(lista, null, false), total, lista.size))
    }

    override suspend fun factura(id: String) = Resultado.Exito(facturas.first { it.id == id })

    override suspend fun registrar(datos: FacturaNueva): ResultadoFactura {
        falloRegistro?.let { return ResultadoFactura.Rechazada(it) }
        registradas += datos
        val nueva = facturaDePrueba("f${facturas.size + 1}", total = datos.total)
        facturas += nueva
        return ResultadoFactura.Confirmada(nueva)
    }

    override suspend fun pagar(id: String, fechaPago: LocalDate): Resultado<Factura> {
        pagos += id to fechaPago
        val pagada = facturas.first { it.id == id }.copy(estadoPago = EstadoPago.PAGADA, fechaPago = fechaPago)
        facturas.replaceAll { if (it.id == id) pagada else it }
        return Resultado.Exito(pagada)
    }

    override suspend fun anular(id: String, motivo: String) = Resultado.Exito(facturas.first { it.id == id }.copy(estadoPago = EstadoPago.ANULADA, motivoAnulacion = motivo))
    override suspend fun vincular(id: String, recepciones: List<String>): Resultado<Factura> { vinculaciones += id to recepciones; return Resultado.Exito(facturas.first { it.id == id }) }
    override suspend fun adjuntarImagen(id: String, jpeg: ByteArray): Resultado<Factura> { imagenes += id to jpeg; return Resultado.Exito(facturas.first { it.id == id }) }
    override suspend fun quitarImagen(id: String, imagenId: String) = Resultado.Exito(facturas.first { it.id == id })
    override suspend fun exportar(desde: LocalDate, hasta: LocalDate) = exportacion
}
