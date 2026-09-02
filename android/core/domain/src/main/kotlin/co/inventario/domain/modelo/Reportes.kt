package co.inventario.domain.modelo

import java.time.Instant

/** RF-REP-001: ordenado por criticidad (déficit relativo al mínimo). */
data class FilaBajoMinimo(val producto: ProductoBreve, val stockActual: Cantidad, val stockMinimo: Cantidad, val deficit: Cantidad, val deficitRelativo: String)

/** RF-REP-007. */
data class FilaAgotado(val producto: ProductoBreve, val stockActual: Cantidad, val stockMinimo: Cantidad?)

/** RF-REP-002. */
data class FilaSinMovimiento(val producto: ProductoBreve, val stockActual: Cantidad, val valorACosto: Dinero?, val ultimoMovimientoEn: Instant?, val creadoEn: Instant)

data class ValorCategoria(val categoria: Categoria?, val productos: Int, val valor: Dinero)

data class FilaNoValorizable(val producto: ProductoBreve, val stockActual: Cantidad)

/** RF-REP-003: los no valorizables van aparte, nunca como cero. */
data class Valorizacion(val total: Dinero, val productosValorizados: Int, val porCategoria: List<ValorCategoria>, val noValorizables: Pagina<FilaNoValorizable>)

data class ComprasProveedor(val proveedor: ProveedorBreve, val totalRecibido: Dinero, val totalFacturado: Dinero)

data class ComprasCategoria(val categoria: Categoria?, val totalRecibido: Dinero)

/** RF-REP-005. */
data class ResumenCompras(
    val desde: String,
    val hasta: String,
    val totalRecibido: Dinero,
    val totalFacturado: Dinero,
    val recepciones: Int,
    val facturas: Int,
    val porProveedor: List<ComprasProveedor>,
    val porCategoria: List<ComprasCategoria>,
)

data class MermaMotivo(val motivo: String, val etiqueta: String, val cantidad: Cantidad, val valor: Dinero)

data class MermaProducto(val producto: ProductoBreve, val cantidad: Cantidad, val valor: Dinero)

/** RF-REP-006 / RN-16. */
data class ResumenMermas(
    val desde: String,
    val hasta: String,
    val totalCantidad: Cantidad,
    val totalValor: Dinero,
    val porMotivo: List<MermaMotivo>,
    val porProducto: Pagina<MermaProducto>,
)

/** RF-INV-006: movimientos forzados, consultables como discrepancias. */
data class FilaDiscrepancia(
    val movimientoId: String,
    val producto: ProductoBreve,
    val tipo: TipoMovimiento,
    val cantidad: Cantidad,
    val stockResultante: Cantidad,
    val motivo: String,
    val nota: String?,
    val ocurridoEn: Instant,
    val autorTipo: String,
)
